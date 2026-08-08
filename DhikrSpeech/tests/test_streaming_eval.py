"""Tests for event matching, FA/hour and threshold calibration.

These are the numbers a release decision is made on, and every one of them is
arithmetic that can be quietly wrong: counting a duplicate as a false positive
flatters precision, dividing by the wrong duration flatters FA/hour, and a
calibration policy that falls back to "pick the strictest threshold" turns a
model that cannot work into a model that looks quiet. So the cases here are built
so that the right answer is known by hand.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import CalibrationConfig, DetectorConfig, EventMatchConfig, StreamingConfig
from src.streaming import StreamingEvent, WindowScan
from src.streaming_eval import (
    AnnotatedEvent,
    StreamingClip,
    calibrate_per_class,
    calibrate_threshold,
    clip_threshold_sweep,
    evaluate_streaming,
    load_annotations,
    match_events,
    negative_stress_test,
)

LABELS = ["006", "007", "unknown"]
HOP = 0.25


def scan(sequence, source="test.wav", extra_seconds=2.0):
    rows = []
    for label, confidence in sequence:
        row = np.zeros(len(LABELS), dtype=np.float32)
        index = LABELS.index(label)
        row[index] = confidence
        spare = max(1.0 - confidence, 0.0)
        for position in range(len(LABELS)):
            if position != index:
                row[position] = spare / (len(LABELS) - 1)
        rows.append(row / row.sum())
    array = np.stack(rows)
    return WindowScan(
        times=(np.arange(len(sequence)) * HOP).astype(np.float32),
        probabilities=array,
        raw_probabilities=array,
        labels=LABELS,
        duration=len(sequence) * HOP + extra_seconds,
        hop_seconds=HOP,
        window_seconds=2.0,
        source=source,
    )


def event(label, start, end, trigger=None, confidence=0.9):
    return StreamingEvent(
        label=label,
        start=start,
        end=end,
        trigger_time=trigger if trigger is not None else start,
        peak_confidence=confidence,
    )


# ---------------------------------------------------------------------------
# Annotations
# ---------------------------------------------------------------------------
def test_load_annotations_reads_the_documented_format(tmp_path):
    (tmp_path / "audio").mkdir()
    (tmp_path / "audio" / "test_001.wav").write_bytes(b"")
    (tmp_path / "annotations.json").write_text(
        json.dumps(
            {
                "files": [
                    {
                        "file": "test_001.wav",
                        "events": [
                            {"class": "006", "start": 12.3, "end": 13.8},
                            {"class": "007", "start": 25.1, "end": 27.0},
                        ],
                    },
                    {"file": "negative.wav", "events": [], "negative_type": "tv"},
                ]
            }
        ),
        encoding="utf-8",
    )
    clips = load_annotations(tmp_path / "annotations.json", tmp_path / "audio")
    assert [clip.name for clip in clips] == ["test_001.wav", "negative.wav"]
    assert clips[0].events[0] == AnnotatedEvent("006", 12.3, 13.8)
    assert not clips[0].is_negative
    assert clips[1].is_negative and clips[1].negative_type == "tv"


def test_load_annotations_accepts_a_bare_list_and_a_label_key(tmp_path):
    (tmp_path / "annotations.json").write_text(
        json.dumps([{"file": "a.wav", "events": [{"label": "006", "start": 1, "end": 2}]}]),
        encoding="utf-8",
    )
    clips = load_annotations(tmp_path / "annotations.json")
    assert clips[0].events == [AnnotatedEvent("006", 1.0, 2.0)]


def test_load_annotations_reports_a_missing_file():
    with pytest.raises(FileNotFoundError):
        load_annotations("/nonexistent/annotations.json")


# ---------------------------------------------------------------------------
# Matching
# ---------------------------------------------------------------------------
def test_overlapping_detection_matches_its_annotation():
    result = match_events([event("006", 12.0, 13.0)], [AnnotatedEvent("006", 12.3, 13.8)])
    assert result.true_positives == 1
    assert not result.false_positives and not result.missed


def test_a_detection_just_outside_the_span_still_matches_within_tolerance():
    result = match_events(
        [event("006", 14.2, 14.6, trigger=14.4)],
        [AnnotatedEvent("006", 12.3, 13.8)],
        EventMatchConfig(tolerance_seconds=1.0),
    )
    assert result.true_positives == 1


def test_a_detection_far_from_any_annotation_is_a_false_positive():
    result = match_events([event("006", 40.0, 41.0)], [AnnotatedEvent("006", 12.3, 13.8)])
    assert len(result.false_positives) == 1
    assert len(result.missed) == 1


def test_the_wrong_class_does_not_match():
    result = match_events([event("007", 12.4, 13.0)], [AnnotatedEvent("006", 12.3, 13.8)])
    assert len(result.false_positives) == 1
    assert len(result.missed) == 1


def test_a_second_detection_on_the_same_utterance_is_a_duplicate():
    """Counting one dhikr twice is a different bug from firing at nothing, and
    the report has to keep them apart to be actionable."""
    result = match_events(
        [event("006", 12.3, 12.8, trigger=12.5), event("006", 13.0, 13.5, trigger=13.2)],
        [AnnotatedEvent("006", 12.3, 13.8)],
    )
    assert result.true_positives == 1
    assert len(result.duplicates) == 1
    assert not result.false_positives


def test_each_detection_takes_the_nearest_free_annotation():
    result = match_events(
        [event("006", 10.0, 10.5, trigger=10.2), event("006", 12.0, 12.5, trigger=12.2)],
        [AnnotatedEvent("006", 10.0, 10.6), AnnotatedEvent("006", 12.0, 12.6)],
    )
    assert result.true_positives == 2
    assert not result.duplicates


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------
def test_metrics_follow_the_documented_arithmetic():
    """47 correct of 48 expected, 2 false, 1 duplicate -> P 95.9 / R 97.9 / F1 96.9."""
    from src.streaming_eval import StreamingMetrics

    metrics = StreamingMetrics(
        expected=48,
        detected=50,
        true_positives=47,
        false_positives=2,
        false_negatives=1,
        duplicates=1,
        duration_seconds=4.75 * 3600.0,
    )
    assert metrics.precision == pytest.approx(0.959, abs=0.001)
    assert metrics.recall == pytest.approx(0.979, abs=0.001)
    assert metrics.f1 == pytest.approx(0.969, abs=0.001)
    assert metrics.false_activations_per_hour == pytest.approx(0.42, abs=0.01)
    # The duplicate is not in `precision` but is in the number the user sees.
    assert metrics.counting_precision == pytest.approx(47 / 50)
    assert metrics.duplicate_rate == pytest.approx(1 / 48)


def test_false_activations_per_hour_uses_the_audio_duration():
    from src.streaming_eval import StreamingMetrics

    metrics = StreamingMetrics(
        expected=0,
        detected=3,
        true_positives=0,
        false_positives=3,
        false_negatives=0,
        duplicates=0,
        duration_seconds=1800.0,  # half an hour
    )
    assert metrics.false_activations_per_hour == pytest.approx(6.0)


def test_evaluate_streaming_scores_a_recording_end_to_end():
    sequence = (
        [("unknown", 0.95)] * 4
        + [("006", 0.95)] * 4
        + [("unknown", 0.95)] * 8
        + [("007", 0.95)] * 4
        + [("unknown", 0.95)] * 4
    )
    clip = StreamingClip(
        path=Path("a.wav"),
        events=[AnnotatedEvent("006", 1.0, 2.0), AnnotatedEvent("007", 4.5, 5.5)],
    )
    metrics = evaluate_streaming([(clip, scan(sequence))], DetectorConfig())
    assert metrics.expected == 2
    assert metrics.true_positives == 2
    assert metrics.false_positives == 0
    assert metrics.recall == pytest.approx(1.0)
    assert set(metrics.per_label) == {"006", "007"}


def test_negative_recordings_feed_the_negative_false_activation_rate():
    negative = scan([("unknown", 0.99)] * 20 + [("006", 0.95)] * 4, "tv.wav")
    clip = StreamingClip(
        path=Path("tv.wav"), events=[], negative_only=True, negative_type="tv"
    )
    metrics = evaluate_streaming([(clip, negative)], DetectorConfig())
    assert metrics.false_positives == 1
    assert metrics.negative_false_positives == 1
    assert metrics.per_negative_type["tv"]["false_positives"] == 1
    assert metrics.negative_false_activations_per_hour > 0


# ---------------------------------------------------------------------------
# Negative-only stress test
# ---------------------------------------------------------------------------
def test_stress_test_reports_rate_distribution_and_worst_moment():
    negative = scan([("unknown", 0.99)] * 40 + [("006", 0.93)] * 4, "street.wav")
    result = negative_stress_test([negative], DetectorConfig())
    assert result.files == 1
    assert result.false_events == 1
    assert result.per_label == {"006": 1}
    assert result.predicted_distribution["unknown"] == 40
    assert result.highest_confidence_label == "006"
    assert result.highest_confidence == pytest.approx(0.93, abs=0.02)
    assert result.false_activations_per_hour > 0


def test_a_clean_stress_test_reports_zero():
    result = negative_stress_test([scan([("unknown", 0.99)] * 100)], DetectorConfig())
    assert result.false_events == 0
    assert result.false_activations_per_hour == 0.0


# ---------------------------------------------------------------------------
# Calibration
# ---------------------------------------------------------------------------
def _calibration_pairs():
    """Real dhikr at 0.97; an hour of negative audio with one 0.72 near-miss."""
    positive = scan(
        [("unknown", 0.95)] * 4
        + [("006", 0.97)] * 4
        + [("unknown", 0.95)] * 8
        + [("007", 0.97)] * 4,
        "positive.wav",
    )
    positive_clip = StreamingClip(
        path=Path("positive.wav"),
        events=[AnnotatedEvent("006", 1.0, 2.0), AnnotatedEvent("007", 4.5, 5.5)],
    )
    negative = scan(
        [("unknown", 0.99)] * 1200 + [("006", 0.72)] * 3 + [("unknown", 0.99)] * 600,
        "tv.wav",
    )
    negative_clip = StreamingClip(
        path=Path("tv.wav"), events=[], negative_only=True, negative_type="tv"
    )
    return [(positive_clip, positive), (negative_clip, negative)]


def test_calibration_picks_the_lowest_threshold_inside_the_budget():
    result = calibrate_threshold(_calibration_pairs(), StreamingConfig())
    assert result.satisfied
    # 0.72 is where the false burst sits, so anything at or below it is over
    # budget and anything just above it is not.
    assert result.threshold > 0.72
    assert result.chosen.recall == pytest.approx(1.0)


def test_calibration_reports_failure_instead_of_hiding_behind_a_high_threshold():
    """When nothing meets the budget the answer is 'no operating point', not 0.95."""
    negative = scan([("unknown", 0.99)] * 100 + [("006", 0.99)] * 4, "tv.wav")
    clip = StreamingClip(path=Path("tv.wav"), events=[], negative_only=True)
    result = calibrate_threshold([(clip, negative)], StreamingConfig())
    assert result.threshold is None
    assert not result.satisfied
    assert "NO threshold" in result.reason


def test_calibration_flags_a_threshold_that_meets_the_budget_by_never_firing():
    positive = scan([("unknown", 0.9)] * 4 + [("006", 0.55)] * 6, "positive.wav")
    positive_clip = StreamingClip(
        path=Path("positive.wav"), events=[AnnotatedEvent("006", 1.0, 2.5)]
    )
    negative = scan([("unknown", 0.99)] * 800 + [("006", 0.62)] * 4, "tv.wav")
    negative_clip = StreamingClip(path=Path("tv.wav"), events=[], negative_only=True)
    result = calibrate_threshold(
        [(positive_clip, positive), (negative_clip, negative)], StreamingConfig()
    )
    assert result.satisfied  # inside the budget on paper...
    assert "recall there is only" in result.reason  # ...and useless in the app


def test_per_class_calibration_gives_each_phrase_its_own_threshold():
    results = calibrate_per_class(_calibration_pairs(), StreamingConfig(), LABELS)
    assert set(results) == {"006", "007"}
    # 006 is the class the negative audio fires on, so it needs the stricter one.
    assert results["006"].threshold > results["007"].threshold
    assert "unknown" not in results


def test_the_sweep_covers_the_configured_range():
    config = CalibrationConfig(min_threshold=0.5, max_threshold=0.9, step=0.1)
    result = calibrate_threshold(_calibration_pairs(), StreamingConfig(), config)
    assert [round(point.threshold, 2) for point in result.points] == [
        0.5,
        0.6,
        0.7,
        0.8,
        0.9,
    ]


# ---------------------------------------------------------------------------
# Clip-level fallback
# ---------------------------------------------------------------------------
def test_clip_threshold_sweep_reports_recall_and_unknown_acceptance():
    y_true = np.array([0, 0, 2, 2])
    y_prob = np.array(
        [
            [0.95, 0.03, 0.02],  # a real 006, confident
            [0.60, 0.20, 0.20],  # a real 006, unsure
            [0.10, 0.10, 0.80],  # unknown, correctly quiet
            [0.70, 0.10, 0.20],  # unknown that looks like 006
        ],
        dtype=np.float32,
    )
    rows = clip_threshold_sweep(
        y_true,
        y_prob,
        LABELS,
        thresholds=[0.5, 0.8],
        negative_types=["", "", "noise", "hard_negative"],
    )
    assert rows[0]["target recall"] == pytest.approx(1.0)
    assert rows[0]["unknown accepted"] == pytest.approx(0.5)
    assert rows[0]["hard-negative accepted"] == pytest.approx(1.0)
    assert rows[1]["target recall"] == pytest.approx(0.5)
    assert rows[1]["unknown accepted"] == pytest.approx(0.0)

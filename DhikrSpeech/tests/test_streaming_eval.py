"""Tests for event matching, FA/hour and threshold calibration.

The arithmetic here is what turns a timeline into a release decision, so it is
tested on hand-built inputs whose right answer is known: a duplicate must be
counted as a duplicate and not as a false positive, a false activation must be
attributed to the audio category that produced it, and calibration must report
failure rather than reach for an extreme threshold.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import CalibrationConfig, DetectorConfig
from src.streaming import Event, ScoreTimeline
from src.streaming_eval import (
    EventMetrics,
    ScoredClip,
    StreamingClip,
    calibrate_threshold,
    evaluate_timelines,
    load_annotations,
    match_events,
)

HOP = 0.2
WINDOW = 1.0


def event(time: float, score: float = 0.9) -> Event:
    return Event(start=time - 0.4, end=time + 0.4, time=time, peak_score=score)


def timeline(scores) -> ScoreTimeline:
    values = np.asarray(scores, dtype=np.float32)
    return ScoreTimeline(
        times=np.arange(values.size, dtype=np.float32) * HOP,
        scores=values,
        window_seconds=WINDOW,
        hop_seconds=HOP,
    )


def detector(activation: float = 0.7) -> DetectorConfig:
    return DetectorConfig(
        activation_threshold=activation,
        release_threshold=activation * 0.6,
        min_consecutive_hits=2,
        release_windows=2,
        cooldown_ms=200.0,
    )


# ---------------------------------------------------------------------------
# Matching
# ---------------------------------------------------------------------------
def test_a_detection_inside_the_annotation_matches() -> None:
    result = match_events([event(12.9)], [(12.3, 14.1)], tolerance=0.75)
    assert result.matched == [(0, 0)]
    assert not result.false_detections and not result.missed


def test_tolerance_is_applied_on_both_sides() -> None:
    assert match_events([event(11.7)], [(12.3, 14.1)], tolerance=0.75).matched
    assert match_events([event(14.8)], [(12.3, 14.1)], tolerance=0.75).matched
    assert not match_events([event(11.4)], [(12.3, 14.1)], tolerance=0.75).matched


def test_a_second_detection_on_one_repetition_is_a_duplicate() -> None:
    result = match_events([event(12.5), event(13.5)], [(12.3, 14.1)], tolerance=0.75)
    assert len(result.matched) == 1
    assert len(result.duplicates) == 1
    assert not result.false_detections  # a duplicate is not a false positive


def test_an_unmatched_detection_is_false_and_an_unmatched_truth_is_missed() -> None:
    result = match_events([event(40.0)], [(12.3, 14.1)], tolerance=0.75)
    assert result.false_detections == [0]
    assert result.missed == [0]


def test_each_detection_takes_the_closest_repetition() -> None:
    result = match_events(
        [event(1.0), event(3.0)], [(0.8, 1.2), (2.8, 3.2)], tolerance=0.75
    )
    assert sorted(result.matched) == [(0, 0), (1, 1)]


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------
def test_false_activations_per_hour() -> None:
    metrics = EventMetrics(false_events=3, duration_seconds=1800.0)
    assert metrics.false_activations_per_hour == pytest.approx(6.0)


def test_fa_per_hour_is_undefined_without_audio() -> None:
    assert not np.isfinite(EventMetrics(false_events=1).false_activations_per_hour)


def test_precision_recall_f1_match_the_worked_example() -> None:
    """The example from the requirements: 100 expected, 98 correct, 1 false."""
    metrics = EventMetrics(
        expected=100,
        detected=99,
        correct=98,
        missed=2,
        false_events=1,
        duplicates=0,
        duration_seconds=5 * 3600.0,
    )
    assert metrics.precision == pytest.approx(0.9899, abs=1e-3)
    assert metrics.recall == pytest.approx(0.98)
    assert metrics.false_activations_per_hour == pytest.approx(0.2)
    assert metrics.duplicate_rate == pytest.approx(0.0)


def test_duplicates_count_against_precision() -> None:
    """On device a duplicate *is* an extra count, so it cannot be free."""
    clean = EventMetrics(expected=10, detected=10, correct=10, duration_seconds=60.0)
    duplicated = EventMetrics(
        expected=10, detected=12, correct=10, duplicates=2, duration_seconds=60.0
    )
    assert duplicated.precision < clean.precision
    assert duplicated.recall == clean.recall


def test_metrics_add_up() -> None:
    left = EventMetrics(expected=2, correct=2, duration_seconds=60.0, false_by_category={"tv": 1})
    right = EventMetrics(expected=3, correct=1, duration_seconds=30.0, false_by_category={"tv": 2})
    total = left + right
    assert total.expected == 5 and total.correct == 3
    assert total.duration_seconds == pytest.approx(90.0)
    assert total.false_by_category == {"tv": 3}


# ---------------------------------------------------------------------------
# Evaluating timelines
# ---------------------------------------------------------------------------
def utterance_at(index: int, length: int = 4, total: int = 40, peak: float = 0.95):
    scores = np.full(total, 0.02, dtype=np.float32)
    scores[index : index + length] = peak
    return scores


def test_a_session_of_three_repetitions_scores_three() -> None:
    scores = np.full(60, 0.02, dtype=np.float32)
    truth = []
    for start in (5, 20, 35):
        scores[start : start + 4] = 0.95
        centre = start * HOP + WINDOW / 2.0
        truth.append((centre - 0.3, centre + 0.6))
    clip = StreamingClip(file="session.wav", events=truth, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, timeline(scores))], detector())
    assert evaluation.metrics.expected == 3
    assert evaluation.metrics.correct == 3
    assert evaluation.metrics.false_events == 0
    assert evaluation.metrics.recall == pytest.approx(1.0)


def test_a_negative_only_recording_reports_fa_per_hour_and_its_category() -> None:
    scores = utterance_at(10, total=int(600 / HOP))  # 10 minutes of audio, one burst
    clip = StreamingClip(file="tv.wav", expected_count=0, target="007", category="background_audio")
    evaluation = evaluate_timelines([ScoredClip(clip, timeline(scores))], detector())
    metrics = evaluation.metrics
    assert metrics.expected == 0
    assert metrics.false_events == 1
    assert metrics.false_by_category == {"background_audio": 1}
    assert metrics.false_activations_per_hour == pytest.approx(6.0, rel=0.05)
    assert evaluation.negative_only.max_negative_score == pytest.approx(0.95)


def test_silence_produces_no_events() -> None:
    clip = StreamingClip(file="room.wav", expected_count=0, category="noise")
    scores = np.full(500, 0.05, dtype=np.float32)
    evaluation = evaluate_timelines([ScoredClip(clip, timeline(scores))], detector())
    assert evaluation.metrics.false_events == 0
    assert evaluation.metrics.false_activations_per_hour == pytest.approx(0.0)


def test_worst_clips_are_ranked_by_false_activations() -> None:
    quiet = ScoredClip(
        StreamingClip(file="quiet.wav", expected_count=0, category="noise"),
        timeline(np.full(100, 0.05, dtype=np.float32)),
    )
    noisy_scores = np.full(100, 0.05, dtype=np.float32)
    noisy_scores[10:14] = 0.95
    noisy_scores[40:44] = 0.95
    noisy = ScoredClip(
        StreamingClip(file="noisy.wav", expected_count=0, category="other_dhikr"),
        timeline(noisy_scores),
    )
    evaluation = evaluate_timelines([quiet, noisy], detector())
    assert evaluation.worst_clips()[0].file == "noisy.wav"
    assert "noisy.wav" in evaluation.summary()


def test_evaluation_serialises() -> None:
    clip = StreamingClip(file="a.wav", events=[(1.0, 2.0)], target="007")
    evaluation = evaluate_timelines(
        [ScoredClip(clip, timeline(utterance_at(4, total=30)))], detector()
    )
    payload = json.loads(json.dumps(evaluation.to_dict()))
    assert payload["overall"]["expected"] == 1
    assert payload["per_clip"][0]["file"] == "a.wav"


# ---------------------------------------------------------------------------
# Annotations
# ---------------------------------------------------------------------------
def test_annotations_load_from_the_documented_shape(tmp_path: Path) -> None:
    path = tmp_path / "annotations.json"
    path.write_text(
        json.dumps(
            [
                {
                    "file": "session_001.wav",
                    "target": "007",
                    "events": [{"start": 12.3, "end": 14.1}],
                },
                {"file": "tv.wav", "target": "007", "category": "background_audio",
                 "events": [], "expected_count": 0},
            ]
        ),
        encoding="utf-8",
    )
    clips = load_annotations(path)
    assert clips[0].events == [(12.3, 14.1)]
    assert clips[1].is_negative_only and clips[1].category == "background_audio"


def test_annotations_reject_a_backwards_event(tmp_path: Path) -> None:
    path = tmp_path / "annotations.json"
    path.write_text(
        json.dumps([{"file": "a.wav", "events": [{"start": 5.0, "end": 1.0}]}]), encoding="utf-8"
    )
    with pytest.raises(ValueError, match="ends before it starts"):
        load_annotations(path)


def test_clips_are_filtered_by_target() -> None:
    assert StreamingClip(file="a.wav", target="7").matches_target("007")
    assert StreamingClip(file="a.wav", target="006").matches_target("007") is False
    # Shared negative material has no target and counts for every model.
    assert StreamingClip(file="tv.wav", target=None).matches_target("007")


# ---------------------------------------------------------------------------
# Calibration
# ---------------------------------------------------------------------------
def calibration_set():
    """One 10-minute session with 3 repetitions, plus 10 minutes of near-miss
    audio whose confidence peaks at 0.8 - so only thresholds above 0.8 are quiet."""
    windows = int(600 / HOP)
    positive = np.full(windows, 0.02, dtype=np.float32)
    truth = []
    for start in (100, 500, 900):
        positive[start : start + 4] = 0.92
        centre = start * HOP + WINDOW / 2.0
        truth.append((centre - 0.3, centre + 0.6))

    negative = np.full(windows, 0.02, dtype=np.float32)
    for start in (200, 1200):
        negative[start : start + 4] = 0.80

    return [
        ScoredClip(StreamingClip("session.wav", truth, target="007"), timeline(positive)),
        ScoredClip(
            StreamingClip("hard.wav", expected_count=0, target="007", category="hard_negative"),
            timeline(negative),
        ),
    ]


def test_calibration_picks_the_lowest_threshold_inside_the_budget() -> None:
    result = calibrate_threshold(
        calibration_set(),
        CalibrationConfig(min_threshold=0.40, max_threshold=0.95, step=0.05,
                          target_false_activations_per_hour=0.5),
        detector(),
    )
    assert result.satisfied
    # 0.80 still accepts the near-misses; the first quiet threshold is just above.
    assert 0.80 < result.activation <= 0.90
    assert result.chosen.metrics.false_events == 0
    assert result.chosen.metrics.correct == 3
    assert result.release < result.activation


def test_calibration_reports_failure_instead_of_an_extreme_threshold() -> None:
    """A model whose false activations survive every threshold must fail loudly."""
    windows = int(600 / HOP)
    scores = np.full(windows, 0.02, dtype=np.float32)
    for start in (100, 400, 700, 1000):
        scores[start : start + 4] = 0.999
    scored = [
        ScoredClip(
            StreamingClip("tv.wav", expected_count=0, target="007", category="background_audio"),
            timeline(scores),
        )
    ]
    result = calibrate_threshold(
        scored,
        CalibrationConfig(min_threshold=0.4, max_threshold=0.99, step=0.05,
                          target_false_activations_per_hour=0.5),
        detector(),
    )
    assert not result.satisfied
    assert result.activation is None
    assert "CALIBRATION FAILED" in result.summary()
    assert result.best_by_fa() is not None


def test_calibration_fails_when_recall_cannot_be_met() -> None:
    result = calibrate_threshold(
        calibration_set(),
        CalibrationConfig(
            min_threshold=0.4,
            max_threshold=0.99,
            step=0.05,
            target_false_activations_per_hour=0.5,
            min_event_recall=0.99,
        ),
        detector(),
    )
    # Quiet thresholds exist, but above 0.92 nothing is detected at all.
    assert result.satisfied is (result.activation is not None)
    if not result.satisfied:
        assert "recall" in result.reason


def test_calibration_on_an_empty_set_is_a_failure_not_a_crash() -> None:
    result = calibrate_threshold([], CalibrationConfig(), detector())
    assert not result.satisfied
    assert "no annotated streaming recordings" in result.reason


def test_calibration_sweep_serialises() -> None:
    result = calibrate_threshold(
        calibration_set(),
        CalibrationConfig(min_threshold=0.5, max_threshold=0.9, step=0.1),
        detector(),
    )
    payload = json.loads(json.dumps(result.to_dict()))
    assert len(payload["sweep"]) == 5
    assert payload["sweep"][0]["activation_threshold"] == pytest.approx(0.5)


# ---------------------------------------------------------------------------
# Annotation scaffolding
# ---------------------------------------------------------------------------
def test_annotation_template_lists_every_recording(tmp_path: Path) -> None:
    from src.streaming_eval import annotation_template

    for name in ("session_001.wav", "tv.flac", "notes.txt"):
        (tmp_path / name).write_bytes(b"")
    entries = annotation_template(tmp_path, target="007")
    assert [entry["file"] for entry in entries] == ["session_001.wav", "tv.flac"]
    assert all(entry["target"] == "007" for entry in entries)


def test_the_template_defaults_to_negative_only(tmp_path: Path) -> None:
    """An empty `events` list is a valid negative-only stress recording, so a
    folder of TV and street audio needs no editing at all."""
    from src.streaming_eval import load_annotations, write_annotation_template

    (tmp_path / "tv.wav").write_bytes(b"")
    path = write_annotation_template(tmp_path / "annotations.json", tmp_path, target="007")
    clips = load_annotations(path)
    assert len(clips) == 1 and clips[0].is_negative_only


def test_the_template_refuses_to_overwrite_hand_made_annotations(tmp_path: Path) -> None:
    from src.streaming_eval import write_annotation_template

    (tmp_path / "a.wav").write_bytes(b"")
    path = tmp_path / "annotations.json"
    path.write_text('[{"file": "a.wav", "events": [{"start": 1.0, "end": 2.0}]}]', encoding="utf-8")
    with pytest.raises(FileExistsError, match="hand-made"):
        write_annotation_template(path, tmp_path)
    assert "1.0" in path.read_text(encoding="utf-8")


def test_an_empty_folder_gives_an_empty_template(tmp_path: Path) -> None:
    from src.streaming_eval import annotation_template

    assert annotation_template(tmp_path / "missing") == []


# ---------------------------------------------------------------------------
# Annotations the collector already wrote
# ---------------------------------------------------------------------------
# SpeechCollector's repetition recorder asks for the dhikr N times and writes N
# into the filename, so a take states its own phrase and its own event count.
# These check that it is read rather than assumed - and that what it cannot say
# is still not said.
TAKE = "007_x10_sp8d358495_20260803_183015_ab12cd.webm"


def collector_folder(tmp_path: Path, *names: str) -> Path:
    """`streaming/007/…`, the layout the collector uploads."""
    folder = tmp_path / "007"
    folder.mkdir(parents=True, exist_ok=True)
    for name in names or (TAKE,):
        (folder / name).write_bytes(b"")
    return tmp_path


def test_the_count_and_the_phrase_are_read_off_the_collector_filename() -> None:
    from src.streaming_eval import parse_collector_take

    assert parse_collector_take(TAKE) == (10, "007")


def test_the_phrase_folder_names_the_target(tmp_path: Path) -> None:
    """The folder is checked before the filename: a rename cannot quietly move a
    take to another phrase's evaluation set."""
    from src.streaming_eval import parse_collector_take

    root = collector_folder(tmp_path, "renamed_x10_take.webm")
    assert parse_collector_take(root / "007" / "renamed_x10_take.webm", root) == (10, "007")


def test_a_file_with_no_repetition_tag_is_never_given_a_count() -> None:
    """A hand-added recording is not a repetition take, and inventing a count for
    it would be inventing ground truth."""
    from src.streaming_eval import parse_collector_take

    assert parse_collector_take("007_sp8d358495_20260803_183015_ab12cd.webm") == (None, "007")


def test_collector_takes_annotate_themselves(tmp_path: Path) -> None:
    from src.streaming_eval import MODE_COUNT, load_annotations, write_collector_annotations

    root = collector_folder(tmp_path)
    path = write_collector_annotations(root / "annotations.json", root)
    clips = load_annotations(path)

    assert len(clips) == 1
    assert clips[0].file == f"007/{TAKE}"
    assert clips[0].target == "007"
    assert clips[0].expected == 10
    assert clips[0].mode == MODE_COUNT


def test_a_derived_take_cannot_measure_false_activations(tmp_path: Path) -> None:
    """The honest limit of deriving annotations: a recording that contains the
    target can never show a detection to be wrong, so FA/hour stays unmeasured
    however many takes are collected."""
    from src.streaming_eval import load_annotations, write_collector_annotations

    root = collector_folder(tmp_path)
    clips = load_annotations(write_collector_annotations(root / "annotations.json", root))
    assert not clips[0].measures_false_activations

    scored = [ScoredClip(clip=clips[0], timeline=timeline([0.1] * 10))]
    evaluation = evaluate_timelines(scored, DetectorConfig())
    assert not evaluation.measures_false_activations
    assert evaluation.counts.recordings == 1


def test_an_unidentifiable_take_is_listed_but_not_shared(tmp_path: Path) -> None:
    """A take whose phrase cannot be told would otherwise be scored against every
    target as repetitions that are not in its audio."""
    from src.streaming_eval import MODE_UNANNOTATED, collector_annotations

    (tmp_path / "mystery_x10_take.wav").write_bytes(b"")
    entry = collector_annotations(tmp_path)[0]
    assert entry["target"] is None
    assert entry["expected_count"] is None
    assert "target" in entry["notes"]

    from src.streaming_eval import StreamingClip

    assert StreamingClip(file=str(entry["file"])).mode == MODE_UNANNOTATED


def test_merging_never_edits_an_existing_entry(tmp_path: Path) -> None:
    """Re-running after each round of uploads is the normal case, so hand-written
    timestamps and negative-only entries have to survive it untouched."""
    from src.streaming_eval import load_annotations, write_collector_annotations

    root = collector_folder(tmp_path)
    (root / "tv.wav").write_bytes(b"")
    path = root / "annotations.json"
    path.write_text(
        json.dumps(
            [
                {"file": f"007/{TAKE}", "target": "007",
                 "events": [{"start": 1.0, "end": 2.0}], "category": "reviewed_by_hand"},
                {"file": "tv.wav", "target": "007", "events": [], "expected_count": 0},
            ]
        ),
        encoding="utf-8",
    )
    (root / "007" / f"second_x10_{TAKE}").write_bytes(b"")

    clips = {clip.file: clip for clip in load_annotations(write_collector_annotations(path, root))}
    assert len(clips) == 3
    assert clips[f"007/{TAKE}"].events == [(1.0, 2.0)]        # kept verbatim
    assert clips["tv.wav"].is_negative_only
    assert clips[f"007/second_x10_{TAKE}"].expected == 10     # newly derived


def test_the_streaming_set_derives_its_own_annotations(tmp_path: Path) -> None:
    """End to end: collector takes on disk, no annotations.json, nobody asked."""
    from src.config import Config
    from src.streaming_eval import load_streaming_set, streaming_status

    root = tmp_path / "p" / Config().paths.streaming_dir
    collector_folder(root)
    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "p", "target.phrase_id": 7}
    )

    clips = load_streaming_set(config)
    assert [clip.expected for clip in clips] == [10]
    assert (root / "annotations.json").is_file()
    assert "1 counted take(s)" in streaming_status(config)


def test_untagged_recordings_are_left_alone(tmp_path: Path) -> None:
    """Nothing states a count, so nothing is written - and the template's refusal
    to clobber hand-made annotations is still the guard it was."""
    from src.config import Config
    from src.streaming_eval import ensure_collector_annotations

    root = tmp_path / "p" / Config().paths.streaming_dir
    root.mkdir(parents=True)
    (root / "session_001.wav").write_bytes(b"")
    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "p"}
    )

    assert ensure_collector_annotations(config) is None
    assert not (root / "annotations.json").exists()


# ---------------------------------------------------------------------------
# Finding the streaming set
# ---------------------------------------------------------------------------
def streaming_project(tmp_path: Path, folder: str, annotations: bool = True):
    """A project whose streaming set lives in `folder`."""
    from src.config import Config

    audio = tmp_path / "p" / folder / "audio"
    audio.mkdir(parents=True)
    (audio / "session.wav").write_bytes(b"")
    (audio / "tv.wav").write_bytes(b"")
    if annotations:
        (tmp_path / "p" / folder / "annotations.json").write_text(
            json.dumps(
                [
                    {"file": "session.wav", "target": "007",
                     "events": [{"start": 1.0, "end": 2.0}]},
                    {"file": "tv.wav", "target": "007", "events": [], "expected_count": 0},
                ]
            ),
            encoding="utf-8",
        )
    return Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "p", "target.phrase_id": 7}
    )


def test_the_streaming_set_is_found_under_its_configured_name(tmp_path: Path) -> None:
    from src.config import Config
    from src.streaming_eval import load_streaming_set

    config = streaming_project(tmp_path, Config().paths.streaming_dir)
    assert config.paths.resolve_streaming_path().name == Config().paths.streaming_dir
    assert len(load_streaming_set(config)) == 2


def test_a_streaming_folder_under_another_name_is_still_found(tmp_path: Path) -> None:
    """Reporting "missing" at a folder the user is looking straight at is worse
    than reading one the config does not name."""
    from src.streaming_eval import load_streaming_set, streaming_audio_root

    config = streaming_project(tmp_path, "streaming_test")   # not the configured name
    assert config.paths.resolve_streaming_path().name == "streaming_test"
    assert len(load_streaming_set(config)) == 2
    assert streaming_audio_root(config).name == "audio"


def test_the_fallback_is_reported_not_silent(tmp_path: Path, caplog) -> None:
    import logging

    from src import config as config_module

    config_module._STREAMING_FALLBACKS_REPORTED.clear()
    config = streaming_project(tmp_path, "streaming_test")   # not the configured name
    with caplog.at_level(logging.WARNING, logger="src.config"):
        config.paths.resolve_streaming_path()
    assert "paths.streaming_dir" in caplog.text


def test_a_flat_streaming_folder_works(tmp_path: Path) -> None:
    """No `audio/` subfolder: the recordings sit next to annotations.json."""
    from src.config import Config
    from src.streaming_eval import streaming_audio_root

    root = tmp_path / "p" / Config().paths.streaming_dir
    root.mkdir(parents=True)
    (root / "tv.wav").write_bytes(b"")
    (root / "annotations.json").write_text(
        '[{"file": "tv.wav", "events": [], "expected_count": 0}]', encoding="utf-8"
    )
    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "p"}
    )
    assert streaming_audio_root(config) == root


def test_a_folder_without_annotations_says_so(tmp_path: Path, caplog) -> None:
    """A different problem from a missing folder, and a different fix."""
    import logging

    from src.streaming_eval import load_streaming_set, streaming_status

    config = streaming_project(tmp_path, "streaming", annotations=False)
    with caplog.at_level(logging.WARNING, logger="src.streaming_eval"):
        assert load_streaming_set(config) == []
    assert "no annotations.json" in streaming_status(config)
    assert "2 recording(s)" in caplog.text


def test_a_genuinely_absent_set_reports_missing(tmp_path: Path) -> None:
    from src.config import Config
    from src.streaming_eval import load_streaming_set, streaming_status

    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "p"}
    )
    assert load_streaming_set(config) == []
    assert streaming_status(config).startswith("MISSING")


def test_status_summarises_an_annotated_set(tmp_path: Path) -> None:
    from src.streaming_eval import streaming_status

    from src.config import Config

    status = streaming_status(streaming_project(tmp_path, Config().paths.streaming_dir))
    assert status.startswith("ok")
    assert "2 annotated" in status and "1 timestamped repetition(s)" in status
    assert "0 counted take(s)" in status and "1 negative-only" in status


# ---------------------------------------------------------------------------
# Positive-only sessions: count-only annotation, and what it cannot measure
# ---------------------------------------------------------------------------
def session_timeline(repetitions: int = 5, windows: int = 600):
    """A long recording with `repetitions` well-separated utterances."""
    scores = np.full(windows, 0.02, dtype=np.float32)
    for index in range(repetitions):
        start = 50 + index * 100
        scores[start : start + 4] = 0.95
    return timeline(scores)


def test_a_count_only_session_scores_the_count(tmp_path: Path) -> None:
    """"I said it five times" is enough to check the counter reaches five."""
    clip = StreamingClip("session.wav", expected_count=5, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert evaluation.counts.recordings == 1
    assert evaluation.counts.expected == 5
    assert evaluation.counts.detected == 5
    assert evaluation.counts.count_accuracy == pytest.approx(1.0)


def test_a_miscount_is_reported_with_its_direction() -> None:
    clip = StreamingClip("session.wav", expected_count=8, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert evaluation.counts.detected == 5
    assert evaluation.counts.per_file["session.wav"]["error"] == -3
    assert evaluation.counts.count_accuracy == pytest.approx(1 - 3 / 8)


def test_count_only_sessions_never_produce_false_activations() -> None:
    """The central rule for this annotation mode: a detection in a count-only
    recording cannot be called false, so it must not reach FA/hour."""
    clip = StreamingClip("session.wav", expected_count=2, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert evaluation.metrics.false_events == 0
    assert evaluation.metrics.duration_seconds == 0.0
    assert not np.isfinite(evaluation.metrics.false_activations_per_hour)


def test_a_positive_only_set_says_fa_per_hour_is_unmeasured() -> None:
    clip = StreamingClip("session.wav", expected_count=5, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert not evaluation.measures_false_activations
    assert "FALSE ACTIVATIONS PER HOUR IS UNMEASURED" in evaluation.summary()


def test_an_unannotated_recording_is_excluded_not_called_negative(caplog) -> None:
    """The trap this replaces: an empty annotation used to mean "no target in
    here", so a session of somebody reciting the target scored as pure false
    activations - the worst possible number, and a wrong one."""
    import logging

    clip = StreamingClip("mystery.wav", target="007")   # nothing stated
    with caplog.at_level(logging.WARNING, logger="src.streaming_eval"):
        evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert evaluation.skipped == ["mystery.wav"]
    assert evaluation.metrics.false_events == 0
    assert "expected_count" in caplog.text


def test_expected_count_zero_is_a_negative_only_recording() -> None:
    clip = StreamingClip("tv.wav", expected_count=0, target="007", category="noise")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(3))], detector())
    assert evaluation.metrics.expected == 0
    assert evaluation.metrics.false_events == 3
    assert evaluation.measures_false_activations


def test_timestamps_still_beat_both(tmp_path: Path) -> None:
    """A timestamped session measures everything the other two modes cannot."""
    truth = [(index * 20.0 + 10.0 - 0.3, index * 20.0 + 10.0 + 0.6) for index in range(5)]
    clip = StreamingClip("session.wav", events=truth, target="007")
    evaluation = evaluate_timelines([ScoredClip(clip, session_timeline(5))], detector())
    assert evaluation.metrics.expected == 5
    assert evaluation.metrics.duration_seconds > 0
    assert evaluation.measures_false_activations


# ---------------------------------------------------------------------------
# Proposing events from loudness
# ---------------------------------------------------------------------------
def spoken_session(repetitions: int, sample_rate: int = 16000, gap: float = 7.0):
    audio = np.zeros(int((3 + repetitions * gap) * sample_rate), dtype=np.float32)
    truth = []
    for index in range(repetitions):
        start = int((3 + index * gap) * sample_rate)
        length = int(1.5 * sample_rate)
        audio[start : start + length] = 0.3 * np.sin(
            2 * np.pi * 220 * np.arange(length) / sample_rate
        )
        truth.append((start / sample_rate, (start + length) / sample_rate))
    return audio, truth


def test_proposed_events_recover_well_separated_repetitions() -> None:
    from src.streaming_eval import propose_events

    audio, truth = spoken_session(8)
    proposed = propose_events(audio, 16000)
    assert len(proposed) == len(truth)
    for (start, end), (true_start, true_end) in zip(proposed, truth):
        assert abs(start - true_start) < 0.1
        assert abs(end - true_end) < 0.1


def test_proposal_is_model_free() -> None:
    """It must not use the detector being evaluated: scoring a model against its
    own output makes recall 100% however bad the model is.

    Checked on the compiled names rather than the source text - the docstring
    explains why it is model-free and therefore says "model" a great deal."""
    from src.streaming_eval import propose_events

    referenced = set(propose_events.__code__.co_names)
    assert not referenced & {
        "detect_events", "EventDetector", "StreamingDetector", "scorer", "predict"
    }


def test_silence_proposes_nothing() -> None:
    from src.streaming_eval import propose_events

    assert propose_events(np.zeros(16000 * 5, dtype=np.float32), 16000) == []


def test_a_proposal_fills_the_template(tmp_path: Path) -> None:
    from src.audio import write_wav
    from src.streaming_eval import load_annotations, write_annotation_template

    audio, truth = spoken_session(4)
    write_wav(tmp_path / "session.wav", audio, 16000)
    path = write_annotation_template(
        tmp_path / "annotations.json", tmp_path, target="007", propose=True, expected_count=None
    )
    clips = load_annotations(path)
    assert clips[0].mode == "timestamps"
    assert clips[0].expected == len(truth)


def test_a_template_without_proposals_defaults_to_negative_only(tmp_path: Path) -> None:
    from src.streaming_eval import load_annotations, write_annotation_template

    (tmp_path / "tv.wav").write_bytes(b"")
    path = write_annotation_template(tmp_path / "annotations.json", tmp_path, target="007")
    clips = load_annotations(path)
    assert clips[0].mode == "negative"

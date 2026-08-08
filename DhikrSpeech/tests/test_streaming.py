"""Tests for the streaming window geometry, smoothing and the event detector.

The detector is where a working model becomes a working counter, and every rule
in it exists to stop one specific way of counting wrong. Each of those is pinned
down here on hand-built probability sequences: one long utterance must be one
count, a confidence wobble must not become two, a single confident window must
not become one at all, and two different phrases said back to back must stay two.

No audio, no model, no TensorFlow - the state machine is fed the numbers a model
would have produced.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import DetectorConfig, SmoothingConfig
from src.streaming import (
    CANDIDATE,
    CONFIRMED,
    IDLE,
    EventDetector,
    ProbabilitySmoother,
    detect_events,
    smooth_probabilities,
    window_starts,
)

LABELS = ["006", "007", "unknown"]
HOP = 0.25


def probabilities(sequence):
    """``[("006", 0.9), ("unknown", 0.95), ...]`` -> a (windows, 3) array."""
    rows = []
    for label, confidence in sequence:
        row = np.zeros(len(LABELS), dtype=np.float32)
        index = LABELS.index(label)
        row[index] = confidence
        spare = max(1.0 - confidence, 0.0)
        others = [position for position in range(len(LABELS)) if position != index]
        for position in others:
            row[position] = spare / len(others)
        rows.append(row / row.sum())
    return np.stack(rows)


def times(count):
    return [index * HOP for index in range(count)]


def events_for(sequence, config=None):
    array = probabilities(sequence)
    return detect_events(times(len(sequence)), array, LABELS, config or DetectorConfig())


# ---------------------------------------------------------------------------
# Window geometry
# ---------------------------------------------------------------------------
def test_window_starts_covers_the_tail():
    starts = window_starts(num_samples=100, window=40, hop=30)
    assert starts[0] == 0
    assert starts[-1] + 40 == 100  # the last window reaches the end


def test_window_starts_on_a_signal_shorter_than_one_window():
    assert window_starts(num_samples=10, window=40, hop=10) == [0]


def test_window_starts_rejects_a_zero_window():
    with pytest.raises(ValueError):
        window_starts(100, 0, 10)


# ---------------------------------------------------------------------------
# Smoothing
# ---------------------------------------------------------------------------
def test_moving_average_is_causal_and_keeps_rows_normalised():
    array = probabilities([("006", 0.9)] * 3 + [("unknown", 0.9)] * 3)
    smoothed = smooth_probabilities(
        array, SmoothingConfig(enabled=True, method="moving_average", window=3)
    )
    assert np.allclose(smoothed.sum(axis=1), 1.0)
    # Causal: the first row cannot have been influenced by later windows.
    assert np.allclose(smoothed[0], array[0])


def test_ema_smoothing_lags_a_step_change():
    array = probabilities([("unknown", 0.95)] * 3 + [("006", 0.95)] * 3)
    smoothed = smooth_probabilities(
        array, SmoothingConfig(enabled=True, method="ema", alpha=0.5)
    )
    target = LABELS.index("006")
    assert smoothed[3, target] < array[3, target]
    assert smoothed[5, target] > smoothed[3, target]


def test_smoothing_can_be_disabled():
    array = probabilities([("006", 0.9)] * 4)
    assert np.allclose(
        smooth_probabilities(array, SmoothingConfig(enabled=False)), array
    )


def test_online_smoother_matches_the_offline_pass():
    array = probabilities([("006", 0.9), ("006", 0.4), ("unknown", 0.8), ("006", 0.7)])
    for config in (
        SmoothingConfig(enabled=True, method="moving_average", window=3),
        SmoothingConfig(enabled=True, method="ema", alpha=0.4),
    ):
        offline = smooth_probabilities(array, config)
        smoother = ProbabilitySmoother(config)
        online = np.stack([smoother.push(row) for row in array])
        assert np.allclose(offline, online, atol=1e-6)


# ---------------------------------------------------------------------------
# The counting rules
# ---------------------------------------------------------------------------
def test_one_long_utterance_is_one_event():
    """Eight confident windows in a row are one dhikr, not eight."""
    sequence = [("unknown", 0.95)] * 2 + [("006", 0.9)] * 8 + [("unknown", 0.95)] * 4
    events = events_for(sequence)
    assert len(events) == 1
    assert events[0].label == "006"
    assert events[0].windows == 8


def test_a_dip_above_the_release_threshold_does_not_split_an_event():
    """Hysteresis: confidence wobbling mid-phrase must not count twice."""
    sequence = [
        ("006", 0.9),
        ("006", 0.9),
        ("006", 0.5),  # between release (0.4) and activation (0.8)
        ("006", 0.5),
        ("006", 0.9),
        ("unknown", 0.95),
        ("unknown", 0.95),
    ]
    assert len(events_for(sequence)) == 1


def test_a_single_confident_window_is_not_an_event():
    sequence = [("unknown", 0.95), ("006", 0.99), ("unknown", 0.95), ("unknown", 0.95)]
    assert events_for(sequence) == []


def test_min_consecutive_hits_is_honoured():
    config = DetectorConfig(min_consecutive_hits=4)
    sequence = [("006", 0.9)] * 3 + [("unknown", 0.95)] * 4
    assert events_for(sequence, config) == []
    assert len(events_for([("006", 0.9)] * 4 + [("unknown", 0.95)] * 4, config)) == 1


def test_cooldown_suppresses_a_second_count_from_the_same_utterance():
    """A brief drop and recovery inside the cooldown is one dhikr, not two."""
    sequence = [("006", 0.9)] * 3 + [("unknown", 0.95)] + [("006", 0.9)] * 3
    assert len(events_for(sequence)) == 1


def test_a_gap_longer_than_the_cooldown_gives_two_counts():
    sequence = [("006", 0.9)] * 3 + [("unknown", 0.95)] * 8 + [("006", 0.9)] * 3
    events = events_for(sequence)
    assert len(events) == 2
    assert events[1].trigger_time > events[0].end


def test_two_different_phrases_back_to_back_stay_two_counts():
    """The cooldown is per class, so a different phrase is never suppressed."""
    events = events_for([("006", 0.9)] * 3 + [("007", 0.9)] * 3)
    assert [event.label for event in events] == ["006", "007"]


def test_unknown_is_never_counted():
    assert events_for([("unknown", 0.99)] * 20) == []


def test_min_event_duration_rejects_a_short_burst():
    config = DetectorConfig(min_event_duration=1.0, min_consecutive_hits=2)
    # Two windows = 0.25 s of span, under the 1 s minimum.
    assert events_for([("006", 0.95)] * 2 + [("unknown", 0.95)] * 4, config) == []
    assert len(events_for([("006", 0.95)] * 8 + [("unknown", 0.95)] * 4, config)) == 1


def test_per_class_threshold_overrides_the_global_one():
    config = DetectorConfig(
        confidence_threshold=0.8, per_class_thresholds={"007": 0.95}
    )
    assert len(events_for([("006", 0.85)] * 4, config)) == 1
    assert events_for([("007", 0.85)] * 4, config) == []


def test_an_event_still_open_at_the_end_of_the_stream_is_kept():
    events = events_for([("unknown", 0.95)] * 2 + [("006", 0.9)] * 4)
    assert len(events) == 1


def test_event_span_and_peak_are_reported():
    sequence = [("unknown", 0.95)] * 2 + [("006", 0.85), ("006", 0.99), ("006", 0.85)]
    event = events_for(sequence)[0]
    assert event.start == pytest.approx(0.5)
    assert event.end == pytest.approx(1.0)
    assert event.peak_confidence == pytest.approx(0.99, abs=0.02)
    assert event.trigger_time >= event.start


# ---------------------------------------------------------------------------
# Online use
# ---------------------------------------------------------------------------
def test_push_returns_the_event_at_the_moment_it_is_confirmed():
    detector = EventDetector(LABELS, DetectorConfig())
    array = probabilities([("unknown", 0.95), ("006", 0.9), ("006", 0.9), ("006", 0.9)])
    returned = [detector.push(index * HOP, row) for index, row in enumerate(array)]
    assert returned[0] is None
    assert returned[1] is None  # one hit is not yet an event
    assert returned[2] is not None  # confirmed on the second consecutive hit
    assert returned[3] is None  # still the same event, not a new one
    assert len(detector.flush()) == 1


def test_states_move_idle_candidate_confirmed():
    detector = EventDetector(LABELS, DetectorConfig(min_consecutive_hits=2))
    array = probabilities([("unknown", 0.95), ("006", 0.9), ("006", 0.9)])
    assert detector.state == IDLE
    detector.push(0.0, array[0])
    assert detector.state == IDLE
    detector.push(HOP, array[1])
    assert detector.state == CANDIDATE
    detector.push(2 * HOP, array[2])
    assert detector.state == CONFIRMED


def test_detector_rejects_a_probability_row_of_the_wrong_width():
    detector = EventDetector(LABELS, DetectorConfig())
    with pytest.raises(ValueError, match="labels.txt"):
        detector.push(0.0, np.array([0.5, 0.5], dtype=np.float32))


def test_reset_clears_events_and_cooldowns():
    detector = EventDetector(LABELS, DetectorConfig())
    for index, row in enumerate(probabilities([("006", 0.9)] * 4)):
        detector.push(index * HOP, row)
    detector.reset()
    assert detector.events == []
    assert detector.state == IDLE
    assert not detector.in_cooldown("006", 0.0)

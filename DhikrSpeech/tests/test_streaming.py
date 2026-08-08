"""Tests for the streaming event detector.

Hand-built score timelines with a known right answer. These are the tests that
decide whether the counter on the phone is trustworthy: one utterance must make
exactly one event, four quick repetitions must make four, and a score hovering
around the threshold must not make a stream of them.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import DetectorConfig, SmoothingConfig
from src.streaming import (
    DetectorState,
    EventDetector,
    ScoreTimeline,
    detect_events,
    detector_with_threshold,
    smooth_scores,
    target_score,
)

HOP = 0.2
WINDOW = 1.0


def timeline(scores, hop: float = HOP, window: float = WINDOW) -> ScoreTimeline:
    values = np.asarray(scores, dtype=np.float32)
    return ScoreTimeline(
        times=np.arange(values.size, dtype=np.float32) * hop,
        scores=values,
        window_seconds=window,
        hop_seconds=hop,
    )


def config(**kwargs) -> DetectorConfig:
    base = dict(
        activation_threshold=0.7,
        release_threshold=0.4,
        min_consecutive_hits=2,
        release_windows=2,
        cooldown_ms=200.0,
    )
    base.update(kwargs)
    return DetectorConfig(**base)


def utterance(length: int = 5, peak: float = 0.95):
    """A rise, a plateau above activation, and a fall back to silence."""
    return [0.05, 0.3] + [peak] * length + [0.3, 0.05, 0.02]


# ---------------------------------------------------------------------------
# The core promise: one utterance, one event
# ---------------------------------------------------------------------------
def test_one_utterance_makes_exactly_one_event() -> None:
    events = detect_events(timeline(utterance()), config())
    assert len(events) == 1
    assert events[0].peak_score == pytest.approx(0.95)


def test_a_long_utterance_still_makes_one_event() -> None:
    """A phrase outlasting any plausible refractory timer must not be counted twice."""
    events = detect_events(timeline(utterance(length=30)), config())
    assert len(events) == 1


def test_event_span_covers_the_utterance() -> None:
    events = detect_events(timeline(utterance(length=5)), config())
    event = events[0]
    assert event.start == pytest.approx(0.4)
    assert event.end >= event.start + 0.8
    assert event.start <= event.time <= event.end


# ---------------------------------------------------------------------------
# Rapid repetitions (requirement 19)
# ---------------------------------------------------------------------------
def test_four_rapid_repetitions_make_four_events() -> None:
    scores = []
    for _ in range(4):
        scores += [0.9, 0.9, 0.9, 0.1, 0.05]  # ~0.6 s phrase, ~0.4 s gap
    events = detect_events(timeline(scores), config())
    assert len(events) == 4


def test_release_rearms_before_the_cooldown_would() -> None:
    """Re-arming is release-driven; the cooldown is a safety net, not the separator."""
    scores = [0.9, 0.9, 0.05, 0.05, 0.9, 0.9, 0.05, 0.05]
    quick = detect_events(timeline(scores), config(cooldown_ms=0.0))
    assert len(quick) == 2


def test_an_over_long_cooldown_merges_repetitions() -> None:
    """Documents the failure mode: cooldown must stay short."""
    scores = [0.9, 0.9, 0.05, 0.05, 0.9, 0.9, 0.05, 0.05]
    merged = detect_events(timeline(scores), config(cooldown_ms=5000.0))
    assert len(merged) == 1


def test_cooldown_expiring_on_a_window_boundary_admits_that_window() -> None:
    """Regression: window times accumulate from the hop, so a cooldown that ends
    exactly on a window must not be decided by float representation error.

    With a 0.2 s hop and a 200 ms cooldown the second repetition arrives exactly
    as the cooldown expires; before the tolerance in `push` it was dropped
    whenever `0.6000000000000001 + 0.2` landed above `0.8`."""
    scores = [0.9, 0.9, 0.05, 0.05, 0.9, 0.9, 0.05, 0.05]
    events = detect_events(timeline(scores), config(cooldown_ms=200.0))
    assert len(events) == 2


def test_no_dip_means_no_second_event() -> None:
    """Two repetitions with no gap at all are indistinguishable from one long one."""
    events = detect_events(timeline([0.9] * 20), config())
    assert len(events) == 1


# ---------------------------------------------------------------------------
# Hysteresis
# ---------------------------------------------------------------------------
def test_hovering_between_the_thresholds_makes_one_event() -> None:
    scores = [0.9, 0.9, 0.5, 0.75, 0.5, 0.8, 0.55, 0.9, 0.05, 0.02]
    events = detect_events(timeline(scores), config())
    assert len(events) == 1


def test_a_single_spike_is_rejected() -> None:
    """min_consecutive_hits is what stops one noisy window becoming a count."""
    events = detect_events(timeline([0.05, 0.99, 0.05, 0.02]), config(min_consecutive_hits=2))
    assert events == []


def test_one_hit_is_enough_when_configured() -> None:
    events = detect_events(timeline([0.05, 0.99, 0.05]), config(min_consecutive_hits=1))
    assert len(events) == 1


def test_minimum_event_duration_rejects_a_short_burst() -> None:
    scores = [0.9, 0.9, 0.05, 0.05, 0.05]
    assert detect_events(timeline(scores), config(min_event_seconds=1.0)) == []
    assert len(detect_events(timeline(scores), config(min_event_seconds=0.0))) == 1


def test_scores_below_release_never_fire() -> None:
    assert detect_events(timeline([0.3] * 30), config()) == []


def test_release_windows_require_a_sustained_drop() -> None:
    """A one-window dip inside an utterance must not re-arm the detector."""
    scores = [0.9, 0.9, 0.9, 0.1, 0.9, 0.9, 0.05, 0.05, 0.05]
    events = detect_events(timeline(scores), config(release_windows=3))
    assert len(events) == 1


# ---------------------------------------------------------------------------
# Online behaviour
# ---------------------------------------------------------------------------
def test_push_returns_the_event_at_confirmation() -> None:
    detector = EventDetector(config=config(), window_seconds=WINDOW)
    emitted = [detector.push(index * HOP, score) for index, score in enumerate(utterance())]
    confirmed = [event for event in emitted if event is not None]
    assert len(confirmed) == 1
    assert detector.state in (DetectorState.IDLE, DetectorState.COOLDOWN)


def test_the_emitted_event_keeps_growing_until_release() -> None:
    detector = EventDetector(config=config(), window_seconds=WINDOW)
    event = None
    for index, score in enumerate(utterance(length=6)):
        event = detector.push(index * HOP, score) or event
    assert event is not None
    assert event.windows > 2  # it was updated after it was handed back


def test_reset_clears_state() -> None:
    detector = EventDetector(config=config(), window_seconds=WINDOW)
    for index, score in enumerate(utterance()):
        detector.push(index * HOP, score)
    detector.reset()
    assert detector.state is DetectorState.IDLE
    assert detector.events == []


# ---------------------------------------------------------------------------
# Scores and smoothing
# ---------------------------------------------------------------------------
def test_target_score_reads_a_sigmoid_head() -> None:
    assert target_score(np.array([[0.2], [0.9]])).tolist() == pytest.approx([0.2, 0.9])


def test_target_score_reads_the_softmax_target_column() -> None:
    assert target_score(np.array([[0.8, 0.2], [0.1, 0.9]])).tolist() == pytest.approx([0.2, 0.9])


def test_target_score_refuses_a_multiclass_model() -> None:
    with pytest.raises(ValueError, match="one model per dhikr"):
        target_score(np.zeros((3, 5)))


def test_sigmoid_mode_rejects_a_two_output_model() -> None:
    with pytest.raises(ValueError, match="sigmoid"):
        target_score(np.zeros((3, 2)), output_mode="sigmoid")


def test_smoothing_none_is_a_passthrough() -> None:
    values = [0.1, 0.9, 0.2]
    assert smooth_scores(values, SmoothingConfig(mode="none")).tolist() == pytest.approx(values)


def test_ema_smoothing_is_causal_and_starts_at_the_first_value() -> None:
    smoothed = smooth_scores([1.0, 0.0, 0.0], SmoothingConfig(mode="ema", ema_alpha=0.5))
    assert smoothed[0] == pytest.approx(1.0)
    assert smoothed[1] == pytest.approx(0.5)
    assert smoothed[2] == pytest.approx(0.25)


def test_moving_average_uses_only_past_windows() -> None:
    smoothed = smooth_scores([0.0, 1.0, 1.0], SmoothingConfig(mode="moving_average", window=2))
    assert smoothed.tolist() == pytest.approx([0.0, 0.5, 1.0])


RAPID_PAIR = [0.9, 0.9, 0.05, 0.05, 0.9, 0.9, 0.05, 0.05]


def test_light_smoothing_keeps_two_repetitions_apart() -> None:
    smoothed = smooth_scores(RAPID_PAIR, SmoothingConfig(mode="ema", ema_alpha=0.8))
    assert len(detect_events(timeline(smoothed), config())) == 2


def test_heavy_smoothing_loses_a_rapid_repetition() -> None:
    """The cost of smoothing, pinned down rather than described.

    At alpha 0.4 the EMA needs several windows to climb back after the dip, so the
    second repetition never gets its two consecutive hits and the count is lost.
    This is why `streaming.smoothing.mode` defaults to `none` and why the
    calibration sweep should be re-run after changing it.
    """
    smoothed = smooth_scores(RAPID_PAIR, SmoothingConfig(mode="ema", ema_alpha=0.4))
    assert len(detect_events(timeline(smoothed), config())) < 2


# ---------------------------------------------------------------------------
# Threshold helper
# ---------------------------------------------------------------------------
def test_release_scales_with_the_activation_threshold() -> None:
    tuned = detector_with_threshold(config(), 0.9, release_ratio=0.6)
    assert tuned.activation_threshold == pytest.approx(0.9)
    assert tuned.release_threshold == pytest.approx(0.54)


def test_explicit_release_is_clamped_below_activation() -> None:
    tuned = detector_with_threshold(config(), 0.5, release=0.9)
    assert tuned.release_threshold <= tuned.activation_threshold


# ---------------------------------------------------------------------------
# Timeline geometry
# ---------------------------------------------------------------------------
def test_timeline_duration_includes_the_last_window() -> None:
    assert timeline([0.1] * 5).duration_seconds == pytest.approx(0.8 + WINDOW)


def test_timeline_rejects_mismatched_lengths() -> None:
    with pytest.raises(ValueError, match="same length"):
        ScoreTimeline(times=np.zeros(3), scores=np.zeros(4), window_seconds=1.0, hop_seconds=0.2)

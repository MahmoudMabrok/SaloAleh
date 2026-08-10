"""Continuous-microphone inference: sliding windows and the event detector.

Production input is not a clip, it is an open microphone. That changes the
problem in two ways this module exists to handle.

**The window slides.** A phrase is scored many times as it passes through
overlapping windows, so the model output is a *timeline*, not a prediction. The
front-end here is deliberately the training front-end (``src.audio`` +
``src.features``) with one difference: windows are never silence-trimmed.
Trimming a clip a user recorded on purpose is right; trimming a window cut out of
a stream slides its content in time and destroys the alignment the whole timeline
depends on.

**Counting windows is not counting dhikr.** One utterance holds the score above
the threshold for as long as it lasts, so ``P(target) > threshold`` would count a
single repetition four or five times. A plain refractory timer is no better: any
timer long enough to swallow a 2-second phrase also swallows the next repetition
of someone saying the dhikr quickly.

So counting is a state machine with hysteresis::

    IDLE --score>=activation--> CANDIDATE --enough hits--> CONFIRMED
      ^                                                        |
      |                                          score<release for
      |                                          release_windows
      |                                                        v
      +---------------- cooldown elapsed ------------------ COOLDOWN

Re-arming is driven by the **release**, not by the cooldown: the detector becomes
ready again as soon as the confidence has genuinely fallen away, so four quick
repetitions produce four events. ``cooldown_ms`` is a short safety net on top of
that, not the separator - set it long enough to be the separator and rapid
repetitions get merged (requirement 19).
"""

from __future__ import annotations

import logging
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .audio import fit_length, normalize_loudness
from .config import AudioConfig, Config, DetectorConfig, SmoothingConfig
from .features import FeatureStats, LogMelExtractor
from .tflite_runtime import (
    dequantize_output,
    make_interpreter,
    quantize_input,
    tflite_predict,
)

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "DetectorState",
    "Event",
    "EventDetector",
    "KerasScorer",
    "ScoreTimeline",
    "StreamingDetector",
    "StreamingFrontend",
    "StreamingResult",
    "TFLiteScorer",
    "dequantize_output",
    "detect_events",
    "detector_with_threshold",
    "make_interpreter",
    "make_scorer",
    "quantize_input",
    "smooth_scores",
    "target_score",
    "tflite_predict",
]

# Column of P(target) in a 2-output softmax; see src.targets.TARGET_INDEX.
TARGET_COLUMN = 1

# Window timestamps accumulate from the hop, so "has the cooldown expired?" at an
# exact boundary is otherwise decided by float representation error rather than by
# the configured value: with a 0.2 s hop and a 200 ms cooldown, whether the very
# next window is admitted depends on 0.6000000000000001 + 0.2 > 0.8. A window
# arriving exactly `cooldown_ms` later is outside the cooldown, so the comparison
# is made with a tolerance far below any real hop.
_TIME_EPSILON = 1e-6


# ---------------------------------------------------------------------------
# Scores
# ---------------------------------------------------------------------------
def target_score(probabilities: np.ndarray, output_mode: Optional[str] = None) -> np.ndarray:
    """Reduce a model's output to a 1-D ``P(target)`` per window.

    Accepts both production heads - one sigmoid output, or a two-output softmax
    whose column 1 is the target. A wider output is refused rather than guessed
    at: a multi-class model is not a single-target detector, and silently reading
    one of its columns as ``P(target)`` would produce a plausible timeline with no
    meaning.
    """
    array = np.asarray(probabilities, dtype=np.float32)
    if array.ndim == 1:
        return array.astype(np.float32)
    if array.ndim != 2:
        raise ValueError(f"expected (windows,) or (windows, outputs), got shape {array.shape}")

    outputs = array.shape[1]
    if output_mode == "sigmoid" and outputs != 1:
        raise ValueError(f"output_mode is 'sigmoid' but the model has {outputs} outputs")
    if outputs == 1:
        return array[:, 0].astype(np.float32)
    if outputs == 2:
        return array[:, TARGET_COLUMN].astype(np.float32)
    raise ValueError(
        f"a single-target detector needs 1 or 2 outputs, this model has {outputs}. "
        "Multi-class models are not detectors - train one model per dhikr "
        "(target.phrase_id) or use the 06 comparison experiment."
    )


def smooth_scores(scores: Sequence[float], config: SmoothingConfig) -> np.ndarray:
    """Optional temporal smoothing of the score timeline.

    Causal in both modes, so the offline timeline and the on-device stream see
    the same value at the same window. Keep it light: smoothing wide enough to
    bridge the gap between two repetitions merges them into one event, and a
    missed count is exactly what this project is trying to avoid.
    """
    values = np.asarray(list(scores), dtype=np.float32)
    if values.size == 0 or config.mode == "none":
        return values
    if config.mode == "ema":
        alpha = float(config.ema_alpha)
        smoothed = np.empty_like(values)
        running = float(values[0])
        for index, value in enumerate(values):
            running = alpha * float(value) + (1.0 - alpha) * running if index else float(value)
            smoothed[index] = running
        return smoothed
    if config.mode == "moving_average":
        width = int(config.window)
        smoothed = np.empty_like(values)
        for index in range(values.size):
            start = max(index - width + 1, 0)
            smoothed[index] = float(values[start : index + 1].mean())
        return smoothed
    raise ValueError(f"unsupported smoothing mode '{config.mode}'")


# ---------------------------------------------------------------------------
# Front-end
# ---------------------------------------------------------------------------
@dataclass
class StreamingFrontend:
    """Waveform -> per-window log-mel features, identical to training.

    ``window_seconds`` defaults to the clip length the model was trained on. It
    is separate from ``audio.clip_seconds`` only so that an experiment can scan
    with a different window than it trained with; production must keep them
    equal, and :meth:`window_samples` is what the exported metadata reports.
    """

    audio: AudioConfig
    extractor: LogMelExtractor
    window_seconds: Optional[float] = None
    hop_seconds: float = 0.2
    # Per-window loudness normalisation, matching how training clips were
    # conditioned. Off means the model sees raw levels and its scores move with
    # the microphone gain.
    normalize: bool = True

    @classmethod
    def from_config(
        cls, config: Config, stats: Optional[FeatureStats] = None
    ) -> "StreamingFrontend":
        return cls(
            audio=config.audio,
            extractor=LogMelExtractor(
                config=config.features, sample_rate=config.audio.sample_rate, stats=stats
            ),
            window_seconds=config.streaming.window_for(config.audio.clip_seconds),
            hop_seconds=config.streaming.hop_seconds,
            normalize=config.audio.normalize.enabled,
        )

    @property
    def sample_rate(self) -> int:
        return int(self.audio.sample_rate)

    @property
    def window_samples(self) -> int:
        seconds = self.window_seconds or self.audio.clip_seconds
        return int(round(float(seconds) * self.sample_rate))

    @property
    def hop_samples(self) -> int:
        return max(int(round(float(self.hop_seconds) * self.sample_rate)), 1)

    def condition(self, window: np.ndarray) -> np.ndarray:
        """Level-normalise and fit one window - never trim (see module docstring)."""
        result = np.asarray(window, dtype=np.float32)
        if self.normalize:
            result = normalize_loudness(
                result, self.audio.normalize.target_dbfs, self.audio.normalize.peak_ceiling
            )
        return fit_length(result, self.window_samples, self.audio.fit_mode)

    def features(self, window: np.ndarray) -> np.ndarray:
        return self.extractor(self.condition(window))

    def windows(self, samples: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
        """``(start_times, features)`` over a recording of any length.

        The tail is always covered: a recording shorter than one window, or a
        leftover shorter than one hop, is padded into a final window rather than
        dropped - a dhikr said in the last second of a session still counts.
        """
        audio = np.asarray(samples, dtype=np.float32)
        window = self.window_samples
        hop = self.hop_samples

        if audio.size <= window:
            starts = [0]
        else:
            starts = list(range(0, audio.size - window + 1, hop))
            if starts[-1] + window < audio.size:
                starts.append(audio.size - window)

        stacked = [self.features(audio[start : start + window]) for start in starts]
        times = np.asarray(starts, dtype=np.float32) / float(self.sample_rate)
        return times, np.stack(stacked).astype(np.float32)


# ---------------------------------------------------------------------------
# Scorers
# ---------------------------------------------------------------------------
class KerasScorer:
    """Scores windows with a Keras model (the training-time reference)."""

    backend = "keras"

    def __init__(self, model, output_mode: Optional[str] = None) -> None:
        self.model = model
        self.output_mode = output_mode

    def __call__(self, features: np.ndarray) -> np.ndarray:
        batch = _as_batch(features)
        raw = np.asarray(self.model(batch, training=False), dtype=np.float32)
        return target_score(raw, self.output_mode)


class TFLiteScorer:
    """Scores windows with an exported ``.tflite`` model.

    This is the one that matters: it runs the same flatbuffer Android ships,
    through the same quantisation, so a streaming evaluation done here is a
    statement about the shipped artefact rather than about the checkpoint.
    """

    backend = "tflite"

    def __init__(
        self,
        model_path: PathLike,
        num_threads: int = 1,
        output_mode: Optional[str] = None,
    ) -> None:
        self.path = Path(model_path)
        self.output_mode = output_mode
        self.interpreter = make_interpreter(self.path, num_threads=num_threads)
        self.interpreter.allocate_tensors()

    def __call__(self, features: np.ndarray) -> np.ndarray:
        batch = _as_batch(features)
        raw = tflite_predict(self.interpreter, batch)
        return target_score(np.asarray(raw, dtype=np.float32), self.output_mode)


def _as_batch(features: np.ndarray) -> np.ndarray:
    batch = np.asarray(features, dtype=np.float32)
    if batch.ndim == 2:  # one window (frames, mels)
        batch = batch[np.newaxis, ...]
    if batch.ndim == 3:  # (N, frames, mels) -> add the channel axis
        batch = batch[..., np.newaxis]
    return batch


def make_scorer(
    model: Union[PathLike, object],
    output_mode: Optional[str] = None,
    num_threads: int = 1,
) -> Union[KerasScorer, TFLiteScorer]:
    """Build the right scorer for a ``.tflite`` path or an in-memory Keras model."""
    if isinstance(model, (str, Path)):
        path = Path(model)
        if path.suffix.lower() == ".tflite":
            return TFLiteScorer(path, num_threads=num_threads, output_mode=output_mode)
        raise ValueError(f"unsupported model file for streaming: {path}")
    return KerasScorer(model, output_mode=output_mode)


# ---------------------------------------------------------------------------
# Timeline
# ---------------------------------------------------------------------------
@dataclass
class ScoreTimeline:
    """``P(target)`` per window over one recording."""

    times: np.ndarray            # window start times, seconds
    scores: np.ndarray           # P(target), same length
    window_seconds: float
    hop_seconds: float
    raw_scores: Optional[np.ndarray] = None  # before smoothing, when it was applied

    def __post_init__(self) -> None:
        self.times = np.asarray(self.times, dtype=np.float32)
        self.scores = np.asarray(self.scores, dtype=np.float32)
        if self.times.shape != self.scores.shape:
            raise ValueError("times and scores must have the same length")

    @property
    def windows(self) -> int:
        return int(self.scores.size)

    @property
    def duration_seconds(self) -> float:
        """Audio covered, i.e. the last window's end."""
        if not self.windows:
            return 0.0
        return float(self.times[-1] + self.window_seconds)

    @property
    def max_score(self) -> float:
        return float(self.scores.max()) if self.windows else 0.0

    def centre_times(self) -> np.ndarray:
        return self.times + float(self.window_seconds) / 2.0


# ---------------------------------------------------------------------------
# Event detector
# ---------------------------------------------------------------------------
class DetectorState(str, Enum):
    IDLE = "idle"
    CANDIDATE = "candidate"
    CONFIRMED = "confirmed"
    COOLDOWN = "cooldown"


@dataclass
class Event:
    """One counted repetition.

    ``start`` is the first window that crossed the activation threshold and
    ``end`` the last window that stayed above release, so ``[start, end]`` covers
    the audio the detector was firing on. ``time`` is the centre of the most
    confident window - the single timestamp to compare against an annotation.
    """

    start: float
    end: float
    time: float
    peak_score: float
    windows: int = 1
    confirmed_at: float = 0.0

    @property
    def duration(self) -> float:
        return max(float(self.end) - float(self.start), 0.0)

    def to_dict(self) -> Dict[str, float]:
        return {**asdict(self), "duration": self.duration}


@dataclass
class EventDetector:
    """Hysteresis state machine turning a score stream into counted events.

    Online by construction: :meth:`push` takes one window at a time and returns
    an event at the moment it is confirmed, which is what an Android service
    needs (the counter increments while the user is still speaking, not when the
    phrase ends). :func:`detect_events` is the offline convenience wrapper.

    The returned :class:`Event` keeps being updated - ``end``, ``peak_score`` and
    ``windows`` grow until the utterance releases - so a caller holding the object
    sees the final span without waiting for it.
    """

    config: DetectorConfig
    window_seconds: float = 2.0

    state: DetectorState = field(default=DetectorState.IDLE, init=False)
    _hits: int = field(default=0, init=False)
    _below: int = field(default=0, init=False)
    _current: Optional[Event] = field(default=None, init=False)
    _cooldown_until: float = field(default=0.0, init=False)
    events: List[Event] = field(default_factory=list, init=False)

    def reset(self) -> None:
        self.state = DetectorState.IDLE
        self._hits = 0
        self._below = 0
        self._current = None
        self._cooldown_until = 0.0
        self.events = []

    # -- online ------------------------------------------------------------
    def push(self, time: float, score: float) -> Optional[Event]:
        """Feed one window. Returns the event when this window confirms one."""
        time = float(time)
        score = float(score)
        config = self.config

        if self.state is DetectorState.COOLDOWN:
            if time < self._cooldown_until - _TIME_EPSILON:
                return None
            # Cooldown is a safety net, not a separator: the moment it expires the
            # same window is re-examined, so a fast repetition is not thrown away
            # for arriving one window too early.
            self.state = DetectorState.IDLE

        if self.state is DetectorState.IDLE:
            if score >= config.activation_threshold:
                self._current = Event(
                    start=time,
                    end=time + self.window_seconds,
                    time=time + self.window_seconds / 2.0,
                    peak_score=score,
                    windows=1,
                )
                self._hits = 1
                self._below = 0
                self.state = DetectorState.CANDIDATE
                return self._maybe_confirm(time)
            return None

        if self.state is DetectorState.CANDIDATE:
            assert self._current is not None
            if score >= config.activation_threshold:
                self._hits += 1
                self._track(time, score)
                return self._maybe_confirm(time)
            if score >= config.release_threshold:
                # In the hysteresis band: the utterance is still going, but this
                # window does not count towards min_consecutive_hits.
                self._track(time, score)
                return None
            self.state = DetectorState.IDLE
            self._current = None
            self._hits = 0
            return None

        # CONFIRMED - wait for the score to fall away before re-arming.
        assert self._current is not None
        if score >= config.release_threshold:
            self._below = 0
            self._track(time, score)
            return None
        self._below += 1
        if self._below >= config.release_windows:
            self.state = DetectorState.COOLDOWN
            self._cooldown_until = time + config.cooldown_ms / 1000.0
            self._current = None
            self._hits = 0
            self._below = 0
        return None

    def _track(self, time: float, score: float) -> None:
        event = self._current
        if event is None:
            return
        event.end = time + self.window_seconds
        event.windows += 1
        if score > event.peak_score:
            event.peak_score = score
            event.time = time + self.window_seconds / 2.0

    def _maybe_confirm(self, time: float) -> Optional[Event]:
        event = self._current
        if event is None or self.state is not DetectorState.CANDIDATE:
            return None
        if self._hits < self.config.min_consecutive_hits:
            return None
        if (time - event.start) < self.config.min_event_seconds - _TIME_EPSILON:
            return None
        self.state = DetectorState.CONFIRMED
        self._below = 0
        event.confirmed_at = time
        self.events.append(event)
        return event

    # -- offline -----------------------------------------------------------
    def run(self, timeline: ScoreTimeline) -> List[Event]:
        self.reset()
        self.window_seconds = float(timeline.window_seconds)
        for time, score in zip(timeline.times, timeline.scores):
            self.push(float(time), float(score))
        return list(self.events)


def detect_events(timeline: ScoreTimeline, config: DetectorConfig) -> List[Event]:
    """Run the state machine over a finished timeline."""
    return EventDetector(config=config, window_seconds=timeline.window_seconds).run(timeline)


# ---------------------------------------------------------------------------
# End to end
# ---------------------------------------------------------------------------
@dataclass
class StreamingResult:
    """A scored recording plus the events counted in it."""

    timeline: ScoreTimeline
    events: List[Event] = field(default_factory=list)
    source: str = ""

    @property
    def count(self) -> int:
        return len(self.events)

    @property
    def duration_seconds(self) -> float:
        return self.timeline.duration_seconds

    def to_dict(self) -> Dict[str, object]:
        return {
            "source": self.source,
            "duration_seconds": round(self.duration_seconds, 3),
            "windows": self.timeline.windows,
            "max_score": round(self.timeline.max_score, 4),
            "count": self.count,
            "events": [event.to_dict() for event in self.events],
        }


@dataclass
class StreamingDetector:
    """Front-end + model + smoothing + event detector, in one object.

    This is the Python mirror of what the Android service does, and the thing
    every streaming metric is measured through, so the numbers in a report
    describe the deployed behaviour and not a clip-level proxy for it.
    """

    frontend: StreamingFrontend
    scorer: Callable[[np.ndarray], np.ndarray]
    detector: DetectorConfig
    smoothing: SmoothingConfig = field(default_factory=SmoothingConfig)

    @classmethod
    def from_config(
        cls,
        config: Config,
        model: Union[PathLike, object],
        stats: Optional[FeatureStats] = None,
        detector: Optional[DetectorConfig] = None,
        num_threads: int = 1,
    ) -> "StreamingDetector":
        return cls(
            frontend=StreamingFrontend.from_config(config, stats=stats),
            scorer=make_scorer(model, output_mode=config.target.output_mode, num_threads=num_threads),
            detector=detector or config.streaming.detector,
            smoothing=config.streaming.smoothing,
        )

    def score(self, samples: np.ndarray) -> ScoreTimeline:
        times, features = self.frontend.windows(samples)
        raw = np.asarray(self.scorer(features), dtype=np.float32)
        smoothed = smooth_scores(raw, self.smoothing)
        return ScoreTimeline(
            times=times,
            scores=smoothed,
            window_seconds=float(self.frontend.window_samples) / self.frontend.sample_rate,
            hop_seconds=float(self.frontend.hop_seconds),
            raw_scores=raw if self.smoothing.mode != "none" else None,
        )

    def run(self, samples: np.ndarray, source: str = "") -> StreamingResult:
        timeline = self.score(samples)
        return StreamingResult(
            timeline=timeline,
            events=detect_events(timeline, self.detector),
            source=source,
        )

    def run_file(self, path: PathLike) -> StreamingResult:
        from .audio import load_audio

        samples = load_audio(path, self.frontend.sample_rate)
        return self.run(samples, source=str(path))


def detector_with_threshold(
    config: DetectorConfig,
    activation: float,
    release: Optional[float] = None,
    release_ratio: float = 0.6,
) -> DetectorConfig:
    """Copy of ``config`` at a new operating point, for a threshold sweep.

    ``release`` defaults to ``activation x release_ratio`` so the hysteresis gap
    scales with the threshold instead of collapsing at high activation values -
    at activation 0.98 a fixed release of 0.40 is not hysteresis, it is a cliff.
    """
    resolved_release = float(release) if release is not None else activation * release_ratio
    return DetectorConfig(
        activation_threshold=float(activation),
        release_threshold=min(float(resolved_release), float(activation)),
        min_consecutive_hits=config.min_consecutive_hits,
        min_event_seconds=config.min_event_seconds,
        release_windows=config.release_windows,
        cooldown_ms=config.cooldown_ms,
    )

"""Streaming inference: sliding windows, and turning them into counted events.

The app does not classify clips. It holds the microphone open while the user
recites, and the counter has to go up exactly once per utterance. That is a
different problem from the one clip accuracy measures, and it has its own ways of
being wrong:

* one dhikr covers several windows, so counting windows counts it several times;
* confidence wobbles around any single threshold, so a run splits in two and the
  counter jumps by two for one phrase;
* a long phrase outlasts a plain refractory timer, which then counts it twice;
* every second of unrelated speech is another chance to fire.

So this module has two halves. :class:`StreamingFrontend` slides the model's
window over a continuous signal using **exactly** the training front-end - no
second implementation of the conditioning, because a front-end that drifts from
the trained one is an accuracy loss nothing reports. :class:`EventDetector` is a
state machine over the resulting per-window probabilities:

    IDLE --(conf >= activation)--> CANDIDATE --(enough hits)--> CONFIRMED
      ^                                |                            |
      |                          (released)                  (released, then)
      +------------------------------ + <-------------------- COOLDOWN

with hysteresis (activation > release) so a wobble cannot restart an event, and a
per-class cooldown so an utterance cannot be counted twice while two different
phrases said back to back still count as two.

Nothing here imports TensorFlow. A TFLite interpreter comes from LiteRT when it
is installed, so this module runs in the Hugging Face Space and in any
lightweight environment; ``src.export`` and the Space both call into it rather
than keeping their own copies.
"""

from __future__ import annotations

import logging
import warnings
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .audio import fit_length, normalize_loudness, trim_silence
from .config import AudioConfig, Config, DetectorConfig, SmoothingConfig, StreamingConfig
from .features import FeatureStats, LogMelExtractor

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "EventDetector",
    "ProbabilitySmoother",
    "StreamingEvent",
    "StreamingFrontend",
    "WindowScan",
    "dequantize_output",
    "detect_events",
    "make_interpreter",
    "quantize_input",
    "scan_signal",
    "smooth_probabilities",
    "tflite_predict",
    "tflite_runner",
    "window_starts",
]

# Detector states. Strings rather than an enum so they survive JSON round-trips
# and read plainly in a report.
IDLE = "idle"
CANDIDATE = "candidate"
CONFIRMED = "confirmed"
COOLDOWN = "cooldown"


# ---------------------------------------------------------------------------
# Front-end
# ---------------------------------------------------------------------------
def window_starts(num_samples: int, window: int, hop: int) -> List[int]:
    """Sample offsets of each sliding window, tail always covered.

    A signal shorter than one window, or a leftover shorter than the hop, still
    produces a final window (zero-padded / back-aligned) rather than being
    dropped - a dhikr at the very end of a recording must not be invisible.
    """
    if window <= 0:
        raise ValueError("window must be positive")
    hop = max(int(hop), 1)
    if num_samples <= window:
        return [0]
    starts = list(range(0, num_samples - window + 1, hop))
    if starts[-1] + window < num_samples:
        starts.append(num_samples - window)
    return starts


@dataclass
class StreamingFrontend:
    """Waveform -> ``(frames, n_mels)`` log-mel windows, exactly as in training.

    The one implementation of the conditioning + feature path outside the
    training input pipeline: the notebook, the export verification and the
    Hugging Face Space all use this class, so none of them can drift from what
    the weights were trained on.
    """

    config: Config
    stats: Optional[FeatureStats] = None
    extractor: LogMelExtractor = field(init=False)

    def __post_init__(self) -> None:
        self.extractor = LogMelExtractor(
            config=self.config.features,
            sample_rate=self.config.audio.sample_rate,
            stats=self.stats,
        )

    # -- geometry -----------------------------------------------------------
    @property
    def audio(self) -> AudioConfig:
        return self.config.audio

    @property
    def sample_rate(self) -> int:
        return self.config.audio.sample_rate

    @property
    def clip_samples(self) -> int:
        return self.config.audio.clip_samples

    @property
    def clip_seconds(self) -> float:
        return float(self.config.audio.clip_seconds)

    @property
    def input_shape(self) -> Tuple[int, int, int]:
        return self.config.input_shape

    # -- conditioning -------------------------------------------------------
    def condition(self, samples: np.ndarray, trim: bool = True) -> np.ndarray:
        """Trim, loudness-normalise and fit one clip to the model's length.

        ``trim`` is a parameter rather than a config read because silence
        trimming is right for a clip the user recorded on purpose and wrong for a
        window cut out of a stream: trimming a window slides its content in time
        and destroys the alignment every event timestamp depends on.
        """
        result = np.asarray(samples, dtype=np.float32)
        audio = self.audio
        if trim and audio.trim.enabled:
            result = trim_silence(result, self.sample_rate, audio.trim.top_db, audio.trim.pad_ms)
        if audio.normalize.enabled:
            result = normalize_loudness(
                result, audio.normalize.target_dbfs, audio.normalize.peak_ceiling
            )
        return fit_length(result, self.clip_samples, audio.fit_mode)

    def features(self, samples: np.ndarray, trim: bool = True) -> np.ndarray:
        return self.extractor(self.condition(samples, trim=trim))

    def windows(
        self,
        samples: np.ndarray,
        hop_seconds: float,
        window_seconds: Optional[float] = None,
    ) -> Tuple[np.ndarray, np.ndarray]:
        """Slide the window over a longer recording.

        Returns ``(start_times, features)`` with ``features`` shaped
        ``(N, frames, n_mels)``. ``window_seconds`` defaults to the model's clip
        length; overriding it is only meaningful for experiments, since the model
        was trained on one length.
        """
        audio = np.asarray(samples, dtype=np.float32)
        window = (
            int(round(window_seconds * self.sample_rate))
            if window_seconds
            else self.clip_samples
        )
        hop = max(int(round(hop_seconds * self.sample_rate)), 1)
        starts = window_starts(audio.size, window, hop)

        stacked = [self.features(audio[start : start + window], trim=False) for start in starts]
        times = np.asarray(starts, dtype=np.float32) / float(self.sample_rate)
        return times, np.stack(stacked).astype(np.float32)


# ---------------------------------------------------------------------------
# TFLite runtime (shared with src.export and the Space)
# ---------------------------------------------------------------------------
def make_interpreter(model_path: PathLike, num_threads: int = 1):
    """Build a TFLite interpreter, preferring LiteRT.

    ``ai-edge-litert`` is a ~30 MB wheel and is what ``tf.lite.Interpreter``
    became; TensorFlow is ~600 MB and warns on every construction from TF 2.20.
    Preferring LiteRT is what lets this module - and the Space - run without
    TensorFlow at all, while ``tf.lite`` stays the fallback for older installs.
    """
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=str(model_path), num_threads=num_threads)
    except ImportError:
        pass
    try:
        import tensorflow as tf  # type: ignore
    except ImportError as exc:  # pragma: no cover - environment problem, not logic
        raise RuntimeError(
            "no TFLite runtime available - install 'ai-edge-litert' (preferred) "
            "or 'tensorflow'"
        ) from exc
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        return tf.lite.Interpreter(model_path=str(model_path), num_threads=num_threads)


def quantize_input(array: np.ndarray, detail: Dict) -> np.ndarray:
    """Cast float features into the interpreter's input dtype."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(dtype)
    info = np.iinfo(dtype)
    return np.clip(np.round(array / scale) + zero_point, info.min, info.max).astype(dtype)


def dequantize_output(array: np.ndarray, detail: Dict) -> np.ndarray:
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(np.float32)
    return (array.astype(np.float32) - zero_point) * scale


def tflite_predict(interpreter, features: np.ndarray) -> np.ndarray:
    """Run one clip (or a batch, one clip at a time) through an interpreter."""
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]

    batch = np.asarray(features, dtype=np.float32)
    if batch.ndim == 2:
        batch = batch[np.newaxis, ...]
    if batch.ndim == 3 and batch.shape[-1] != 1:
        batch = batch[..., np.newaxis]

    outputs: List[np.ndarray] = []
    for sample in batch:
        interpreter.set_tensor(
            input_detail["index"], quantize_input(sample[np.newaxis, ...], input_detail)
        )
        interpreter.invoke()
        raw = interpreter.get_tensor(output_detail["index"])
        outputs.append(dequantize_output(raw, output_detail)[0])
    return np.stack(outputs)


def tflite_runner(model_path: PathLike, num_threads: int = 1) -> Callable[[np.ndarray], np.ndarray]:
    """``(N, frames, mels) -> (N, classes)`` backed by a ``.tflite`` file.

    The counterpart for Keras is the model itself; :func:`scan_signal` accepts
    either, so the same streaming code scores the checkpoint and the export and
    the two can be compared window by window.
    """
    interpreter = make_interpreter(model_path, num_threads=num_threads)
    interpreter.allocate_tensors()

    def run(features: np.ndarray) -> np.ndarray:
        return tflite_predict(interpreter, features)

    return run


def _as_runner(model) -> Callable[[np.ndarray], np.ndarray]:
    """Accept a Keras model, a ``.tflite`` path or a plain callable."""
    if isinstance(model, (str, Path)):
        return tflite_runner(model)
    if callable(model):
        def run(features: np.ndarray) -> np.ndarray:
            batch = np.asarray(features, dtype=np.float32)
            if batch.ndim == 3:
                batch = batch[..., np.newaxis]
            try:
                output = model(batch, training=False)
            except TypeError:  # a plain callable that takes no `training` flag
                output = model(batch)
            return np.asarray(output, dtype=np.float32)

        return run
    raise TypeError(f"cannot run a model of type {type(model).__name__}")


# ---------------------------------------------------------------------------
# Smoothing
# ---------------------------------------------------------------------------
def smooth_probabilities(
    probabilities: np.ndarray, config: SmoothingConfig
) -> np.ndarray:
    """Temporal smoothing of ``(windows, classes)`` probabilities.

    Causal on purpose - each window is smoothed with the ones *before* it, never
    after - so an offline sweep and the phone produce the same numbers. Both
    methods keep the rows summing to one, so a smoothed value is still a
    probability and the thresholds keep their meaning.
    """
    array = np.asarray(probabilities, dtype=np.float32)
    if not config.enabled or array.ndim != 2 or array.shape[0] == 0:
        return array

    if config.method == "ema":
        smoothed = np.empty_like(array)
        state = array[0]
        smoothed[0] = state
        for index in range(1, array.shape[0]):
            state = config.alpha * array[index] + (1.0 - config.alpha) * state
            smoothed[index] = state
    else:
        width = max(int(config.window), 1)
        if width == 1:
            return array
        smoothed = np.empty_like(array)
        for index in range(array.shape[0]):
            start = max(0, index - width + 1)
            smoothed[index] = array[start : index + 1].mean(axis=0)

    totals = np.clip(smoothed.sum(axis=1, keepdims=True), 1e-12, None)
    return (smoothed / totals).astype(np.float32)


@dataclass
class ProbabilitySmoother:
    """Online form of :func:`smooth_probabilities`, for a real stream."""

    config: SmoothingConfig
    _history: List[np.ndarray] = field(default_factory=list, init=False)
    _state: Optional[np.ndarray] = field(default=None, init=False)

    def reset(self) -> None:
        self._history.clear()
        self._state = None

    def push(self, probabilities: np.ndarray) -> np.ndarray:
        row = np.asarray(probabilities, dtype=np.float32)
        if not self.config.enabled:
            return row
        if self.config.method == "ema":
            self._state = (
                row
                if self._state is None
                else self.config.alpha * row + (1.0 - self.config.alpha) * self._state
            )
            smoothed = self._state
        else:
            self._history.append(row)
            if len(self._history) > max(int(self.config.window), 1):
                self._history.pop(0)
            smoothed = np.mean(np.stack(self._history), axis=0)
        total = float(np.clip(smoothed.sum(), 1e-12, None))
        return (smoothed / total).astype(np.float32)


# ---------------------------------------------------------------------------
# Events
# ---------------------------------------------------------------------------
@dataclass
class StreamingEvent:
    """One counted dhikr.

    ``start`` is the first window of the run, ``end`` the last one still above
    the release threshold, and ``trigger_time`` the moment the event was
    *confirmed* - the instant the counter would tick on the phone, which is what
    latency should be measured against. ``peak_confidence`` is the highest
    probability seen anywhere in the run.
    """

    label: str
    start: float
    end: float
    trigger_time: float
    peak_confidence: float
    windows: int = 1
    peak_time: float = 0.0

    @property
    def duration(self) -> float:
        return max(self.end - self.start, 0.0)

    def overlaps(self, start: float, end: float) -> float:
        """Seconds of overlap with ``[start, end]`` (0 when they are disjoint)."""
        return max(0.0, min(self.end, end) - max(self.start, start))

    def to_dict(self) -> Dict[str, object]:
        return {
            "label": self.label,
            "start": round(float(self.start), 3),
            "end": round(float(self.end), 3),
            "trigger_time": round(float(self.trigger_time), 3),
            "peak_confidence": round(float(self.peak_confidence), 4),
            "windows": int(self.windows),
        }


@dataclass
class _ActiveEvent:
    """Detector bookkeeping for the run currently being tracked."""

    label: str
    index: int
    hits: int
    misses: int
    event: StreamingEvent
    confirmed: bool = False


class EventDetector:
    """One count per utterance, from a stream of per-window probabilities.

    Feed windows in with :meth:`push` (online, exactly what Android does) or hand
    a whole recording to :func:`detect_events`. :meth:`push` returns an event at
    the moment it is *confirmed*; the same object keeps being extended while the
    utterance continues, so the caller's list ends up holding final spans.

    The rules, and what each one is there to stop:

    * **activation threshold** - a window must be this confident to start or feed
      a candidate;
    * **min_consecutive_hits** - a single confident window is a glitch, not a
      dhikr;
    * **release threshold** (below activation) - an ongoing event survives a dip,
      so one utterance stays one event;
    * **release_windows** - how long that dip may last before the event closes;
    * **cooldown** - dead time per class after an event, so the tail of an
      utterance cannot immediately start another;
    * **ignore_labels** - ``unknown`` is the model saying "not a dhikr" and is
      never counted.
    """

    def __init__(
        self,
        labels: Sequence[str],
        config: Optional[DetectorConfig] = None,
        hop_seconds: Optional[float] = None,
    ) -> None:
        self.labels = list(labels)
        self.config = config or DetectorConfig()
        self.hop_seconds = float(hop_seconds) if hop_seconds else None
        self._ignored = {name.lower() for name in self.config.ignore_labels}
        self._candidates = [
            index for index, label in enumerate(self.labels) if label.lower() not in self._ignored
        ]
        self._active: Optional[_ActiveEvent] = None
        self._cooldown_until: Dict[str, float] = {}
        self._index = -1
        self.events: List[StreamingEvent] = []

    # -- state --------------------------------------------------------------
    @property
    def state(self) -> str:
        if self._active is None:
            return IDLE
        return CONFIRMED if self._active.confirmed else CANDIDATE

    def reset(self) -> None:
        self._active = None
        self._cooldown_until.clear()
        self._index = -1
        self.events = []

    def in_cooldown(self, label: str, time: float) -> bool:
        return time < self._cooldown_until.get(label, float("-inf"))

    # -- the machine --------------------------------------------------------
    def push(self, time: float, probabilities: np.ndarray) -> Optional[StreamingEvent]:
        """Feed one window. Returns the event if this window confirmed one."""
        self._index += 1
        row = np.asarray(probabilities, dtype=np.float32).ravel()
        if row.size != len(self.labels):
            raise ValueError(
                f"expected {len(self.labels)} probabilities, got {row.size} - the "
                f"model and labels.txt disagree"
            )

        best_label, best_score = self._best_candidate(row)
        confirmed: Optional[StreamingEvent] = None

        if self._active is not None:
            confirmed = self._advance(time, best_label, best_score, row)
            if confirmed is not None or self._active is not None:
                return confirmed
            # The event closed on this window. A different phrase may already be
            # sounding - two dhikr with no gap between them - so the same window
            # is allowed to start the next candidate rather than being lost.

        if best_label is not None and best_score >= self.config.threshold_for(best_label):
            if not self.in_cooldown(best_label, time):
                self._active = _ActiveEvent(
                    label=best_label,
                    index=self._index,
                    hits=1,
                    misses=0,
                    event=StreamingEvent(
                        label=best_label,
                        start=float(time),
                        end=float(time),
                        trigger_time=float(time),
                        peak_confidence=float(best_score),
                        windows=1,
                        peak_time=float(time),
                    ),
                )
                confirmed = self._maybe_confirm(time)
        return confirmed

    def _best_candidate(self, row: np.ndarray) -> Tuple[Optional[str], float]:
        """Highest-scoring countable class in one window.

        Read over the countable classes only, so a window where ``unknown`` wins
        outright still shows how close the runner-up phrase came - the detector's
        own thresholds decide, not an argmax over classes it can never count.
        """
        if not self._candidates:
            return (None, 0.0)
        best = max(self._candidates, key=lambda index: float(row[index]))
        return (self.labels[best], float(row[best]))

    def _advance(
        self,
        time: float,
        best_label: Optional[str],
        best_score: float,
        row: np.ndarray,
    ) -> Optional[StreamingEvent]:
        active = self._active
        assert active is not None
        score = float(row[self.labels.index(active.label)])
        activation = self.config.threshold_for(active.label)

        if score >= activation and (best_label == active.label or best_label is None):
            active.hits += 1
            active.misses = 0
            active.event.end = float(time)
            active.event.windows += 1
            if score > active.event.peak_confidence:
                active.event.peak_confidence = score
                active.event.peak_time = float(time)
            return self._maybe_confirm(time)

        if score >= self.config.release_threshold:
            # Between the thresholds: the utterance is still going, it just dipped.
            active.misses = 0
            active.event.end = float(time)
            active.event.windows += 1
            if score > active.event.peak_confidence:
                active.event.peak_confidence = score
                active.event.peak_time = float(time)
            return None

        # Below the release threshold. A confidently different phrase closes the
        # event straight away; otherwise it takes `release_windows` quiet windows,
        # which is what keeps one utterance from breaking into two counts.
        other_wins = (
            best_label is not None
            and best_label != active.label
            and best_score >= self.config.threshold_for(best_label)
        )
        active.misses += 1
        if other_wins or active.misses >= self.config.release_windows:
            self._close(time)
        return None

    def _maybe_confirm(self, time: float) -> Optional[StreamingEvent]:
        active = self._active
        assert active is not None
        if active.confirmed:
            return None
        if active.hits < self.config.min_consecutive_hits:
            return None
        if active.event.duration < self.config.min_event_duration:
            # Long enough in hits but not yet in time: keep collecting rather than
            # rejecting, so a slow phrase is confirmed a window later.
            return None
        active.confirmed = True
        active.event.trigger_time = float(time)
        self.events.append(active.event)
        return active.event

    def _close(self, time: float) -> None:
        active = self._active
        self._active = None
        if active is None:
            return
        if active.confirmed:
            self._cooldown_until[active.label] = (
                float(active.event.end) + self.config.cooldown_ms / 1000.0
            )

    def flush(self) -> List[StreamingEvent]:
        """Close any event still open at the end of the stream.

        An utterance that runs to the end of a recording is a real utterance; it
        just has no window after it to release on.
        """
        if self._active is not None:
            self._close(self._active.event.end)
        return list(self.events)


def detect_events(
    times: Sequence[float],
    probabilities: np.ndarray,
    labels: Sequence[str],
    config: Optional[DetectorConfig] = None,
    smoothing: Optional[SmoothingConfig] = None,
) -> List[StreamingEvent]:
    """Run the detector over a whole recording's windows.

    Smoothing, when supplied, is applied first - the same order the phone uses,
    so a threshold calibrated here means the same thing there.
    """
    array = np.asarray(probabilities, dtype=np.float32)
    if smoothing is not None:
        array = smooth_probabilities(array, smoothing)
    detector = EventDetector(labels, config)
    for time, row in zip(times, array):
        detector.push(float(time), row)
    return detector.flush()


# ---------------------------------------------------------------------------
# Scanning a recording
# ---------------------------------------------------------------------------
@dataclass
class WindowScan:
    """Per-window probabilities over one recording, plus what produced them."""

    times: np.ndarray
    probabilities: np.ndarray  # (windows, classes), after smoothing
    raw_probabilities: np.ndarray  # before smoothing
    labels: List[str]
    duration: float
    hop_seconds: float
    window_seconds: float
    source: str = ""

    @property
    def num_windows(self) -> int:
        return int(self.probabilities.shape[0])

    def scores(self, label: str) -> np.ndarray:
        """The probability of one class over time."""
        return self.probabilities[:, self.labels.index(label)]

    def target_scores(self, ignore: Sequence[str] = ("unknown",)) -> np.ndarray:
        """Best countable-class probability per window."""
        ignored = {name.lower() for name in ignore}
        columns = [
            index for index, label in enumerate(self.labels) if label.lower() not in ignored
        ]
        if not columns:
            return np.zeros(self.num_windows, dtype=np.float32)
        return self.probabilities[:, columns].max(axis=1)

    def predicted_distribution(self) -> Dict[str, int]:
        """How many windows each class won - the shape of a stress test's noise."""
        counts = np.bincount(
            self.probabilities.argmax(axis=1), minlength=len(self.labels)
        )
        return {
            self.labels[index]: int(counts[index])
            for index in np.argsort(counts)[::-1]
        }

    def detect(
        self, config: Optional[DetectorConfig] = None
    ) -> List[StreamingEvent]:
        """Events from the already-smoothed probabilities."""
        return detect_events(self.times, self.probabilities, self.labels, config)


def scan_signal(
    samples: np.ndarray,
    model,
    frontend: StreamingFrontend,
    streaming: StreamingConfig,
    labels: Sequence[str],
    source: str = "",
) -> WindowScan:
    """Slide the window over a recording and score every window.

    ``model`` is a Keras model, a path to a ``.tflite`` file, or any callable
    taking ``(N, frames, mels, 1)``. Everything downstream - events, metrics,
    calibration - reads the returned :class:`WindowScan`, so a recording is
    scored once and every threshold in a sweep reuses those probabilities instead
    of re-running inference.
    """
    audio = np.asarray(samples, dtype=np.float32)
    window_seconds = streaming.window_for(frontend.audio)
    times, features = frontend.windows(
        audio, streaming.hop_seconds, window_seconds=window_seconds
    )
    runner = _as_runner(model)
    raw = np.asarray(runner(features), dtype=np.float32)
    if raw.ndim != 2 or raw.shape[0] != features.shape[0]:
        raise ValueError(
            f"model returned {raw.shape} for {features.shape[0]} windows - expected "
            f"(windows, classes)"
        )
    if raw.shape[1] != len(labels):
        raise ValueError(
            f"model has {raw.shape[1]} outputs but {len(labels)} labels were given - "
            f"export labels.txt together with the model"
        )
    # An INT8 model dequantises to values that only approximately sum to 1.
    # Renormalise so a probability is always a probability and a threshold means
    # the same thing for every variant.
    totals = np.clip(raw.sum(axis=1, keepdims=True), 1e-12, None)
    normalised = (raw / totals).astype(np.float32)

    return WindowScan(
        times=np.asarray(times, dtype=np.float32),
        probabilities=smooth_probabilities(normalised, streaming.smoothing),
        raw_probabilities=normalised,
        labels=list(labels),
        duration=float(audio.size) / float(frontend.sample_rate),
        hop_seconds=float(streaming.hop_seconds),
        window_seconds=float(window_seconds),
        source=source,
    )

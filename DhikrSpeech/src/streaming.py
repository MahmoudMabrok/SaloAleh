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
# TFLite / LiteRT runtime helpers
# ---------------------------------------------------------------------------
# Shared by the notebook, export.py, TFLiteScorer and the Hugging Face Space so
# quantisation / dequantisation is identical everywhere. Prefer LiteRT so the
# Space can run without TensorFlow installed.
def make_interpreter(model_path: PathLike, num_threads: int = 1):
    """Create a TFLite interpreter. Prefers ``ai-edge-litert``, falls back to TF."""
    path = str(model_path)
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=path, num_threads=num_threads)
    except ImportError:
        pass
    try:
        import tensorflow as tf  # type: ignore
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "no TFLite runtime available — install 'ai-edge-litert' (preferred) "
            "or 'tensorflow'"
        ) from exc
    return tf.lite.Interpreter(model_path=path, num_threads=num_threads)


def quantize_input(array: np.ndarray, detail: dict) -> np.ndarray:
    """Convert a float32 array into the dtype / scale the interpreter expects."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return np.asarray(array, dtype=np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return np.asarray(array, dtype=dtype)
    info = np.iinfo(dtype)
    quantized = np.round(np.asarray(array, dtype=np.float32) / scale) + zero_point
    return np.clip(quantized, info.min, info.max).astype(dtype)


def dequantize_output(array: np.ndarray, detail: dict) -> np.ndarray:
    """Convert an interpreter output back to float32 probabilities."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return np.asarray(array, dtype=np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return np.asarray(array, dtype=np.float32)
    return (np.asarray(array, dtype=np.float32) - zero_point) * scale


def tflite_predict(interpreter, batch: np.ndarray) -> np.ndarray:
    """Run a feature batch through a TFLite interpreter with proper quantisation.

    Handles models whose input shape has batch=1 (the usual case) by looping,
    and models that accept a full batch in one invoke.
    """
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    batch = np.asarray(batch, dtype=np.float32)

    # Ensure channel axis: (N, frames, mels) -> (N, frames, mels, 1)
    expected_rank = len(input_detail["shape"])
    if batch.ndim == expected_rank - 1:
        batch = batch[..., np.newaxis]

    expected_batch = int(input_detail["shape"][0])
    outputs: list = []

    if expected_batch == 1 or expected_batch == -1:
        # Most exported models are fixed to batch=1.
        for sample in batch:
            payload = quantize_input(sample[np.newaxis, ...], input_detail)
            interpreter.set_tensor(input_detail["index"], payload)
            interpreter.invoke()
            raw = interpreter.get_tensor(output_detail["index"])
            outputs.append(dequantize_output(raw, output_detail)[0])
        return np.stack(outputs, axis=0).astype(np.float32)

    # Interpreter accepts the full batch.
    payload = quantize_input(batch, input_detail)
    interpreter.set_tensor(input_detail["index"], payload)
    interpreter.invoke()
    raw = interpreter.get_tensor(output_detail["index"])
    return dequantize_output(raw, output_detail).astype(np.float32)


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

"""The Android contract: one metadata file and one parity fixture.

Two things have to survive the trip from this repository to a phone, and both
fail silently when they are wrong.

**The operating point.** The model file alone does not describe the system. The
window and hop, the activation and release thresholds, the consecutive hits, the
cooldown, the smoothing - those are what turn probabilities into a counter, and
they were *calibrated* here against real recordings. Shipping the ``.tflite``
without them means the app invents its own, and every number this pipeline
measured stops applying. :func:`write_model_metadata` writes all of it, plus the
tensor shapes and quantisation parameters, into ``exports/model_metadata.json``.

**The front-end.** The model consumes log-mel features, not audio, so Android has
to reproduce the feature extraction exactly. A window length off by one sample,
a periodic-vs-symmetric Hann window, a natural log where the reference took a log
of a mel with a different offset - none of these crash, they just quietly move
every feature and cost accuracy nobody can attribute. :func:`write_frontend_parity`
exports a fixed WAV and the exact tensor this pipeline produces from it, so the
Android implementation can be checked against a number instead of a description.

Deliberately free of TensorFlow: this module writes JSON and WAV, and it is
imported by tests that must run anywhere.
"""

from __future__ import annotations

import hashlib
import json
import logging
from dataclasses import asdict
from pathlib import Path
from typing import Dict, Optional, Sequence, Union

import numpy as np

from .audio import fit_length, normalize_loudness, read_wav, write_wav
from .config import Config

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "METADATA_VERSION",
    "build_model_metadata",
    "interpreter_specs",
    "parity_signal",
    "sha256_file",
    "tensor_spec",
    "write_frontend_parity",
    "write_model_metadata",
]

# Bumped when the *shape* of model_metadata.json changes, so an Android build can
# refuse a file it does not understand instead of reading a missing key as zero.
METADATA_VERSION = 1

PARITY_WAV = "frontend_test.wav"
PARITY_FEATURES = "frontend_expected.npy"
PARITY_CONDITIONED = "frontend_conditioned.npy"
PARITY_METADATA = "frontend_metadata.json"


def sha256_file(path: PathLike) -> str:
    """Hex digest of a file - the model's identity in the metadata."""
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


# ---------------------------------------------------------------------------
# Tensor specification
# ---------------------------------------------------------------------------
def tensor_spec(detail: Optional[Dict]) -> Dict[str, object]:
    """One TFLite tensor as plain JSON: shape, dtype and quantisation.

    An INT8 model's input is not "the features": it is
    ``round(features / scale) + zero_point`` clamped to the int8 range, and the
    output is ``(raw - zero_point) * scale``. Those two numbers are the whole
    difference between a working quantised model and garbage, and they live in
    the flatbuffer where the app cannot guess them.
    """
    if not detail:
        return {}
    dtype = detail.get("dtype")
    quantization = detail.get("quantization") or (0.0, 0)
    scale = float(quantization[0]) if len(quantization) > 0 else 0.0
    zero_point = int(quantization[1]) if len(quantization) > 1 else 0
    return {
        "name": str(detail.get("name", "")),
        "shape": [int(value) for value in detail.get("shape", [])],
        "dtype": np.dtype(dtype).name if dtype is not None else "",
        "quantized": bool(scale),
        "scale": scale,
        "zero_point": zero_point,
    }


def interpreter_specs(interpreter) -> Dict[str, Dict[str, object]]:
    """``{"input": ..., "output": ...}`` from a TFLite interpreter."""
    return {
        "input": tensor_spec(interpreter.get_input_details()[0]),
        "output": tensor_spec(interpreter.get_output_details()[0]),
    }


# ---------------------------------------------------------------------------
# The contract file
# ---------------------------------------------------------------------------
def build_model_metadata(
    config: Config,
    class_names: Sequence[str],
    frontend: Dict,
    model_path: Optional[PathLike] = None,
    tensors: Optional[Dict[str, Dict[str, object]]] = None,
    thresholds: Optional[Dict[str, float]] = None,
    global_threshold: Optional[float] = None,
    model_version: Optional[str] = None,
    calibration: Optional[Dict[str, object]] = None,
    metrics: Optional[Dict[str, object]] = None,
) -> Dict[str, object]:
    """Everything Android needs to reproduce this system, as a plain dict.

    ``thresholds`` and ``global_threshold`` come from calibration when it has
    run; without them the configured values are written and the file says the
    thresholds are uncalibrated, so nobody reads a default as a measurement.
    """
    frames, mels, channels = config.input_shape
    streaming = config.streaming
    detector = streaming.detector
    smoothing = streaming.smoothing

    calibrated = bool(thresholds) or global_threshold is not None
    per_class = {
        label: float(value)
        for label, value in (thresholds or detector.per_class_thresholds).items()
        if label in set(class_names)
    }
    activation = float(
        global_threshold
        if global_threshold is not None
        else detector.confidence_threshold
    )
    # Hysteresis is a ratio, not an absolute: a calibrated activation of 0.9 with
    # the configured release of 0.4 is a much wider band than the one that was
    # measured, so the release moves with the activation it was set against.
    release = float(
        min(detector.release_threshold, activation)
        if global_threshold is None
        else round(
            activation
            * (detector.release_threshold / max(detector.confidence_threshold, 1e-6)),
            4,
        )
    )

    payload: Dict[str, object] = {
        "metadata_version": METADATA_VERSION,
        "model_version": model_version or "",
        "model": {
            "file": Path(model_path).name if model_path else "",
            "sha256": sha256_file(model_path) if model_path and Path(model_path).is_file() else "",
            "architecture": config.model.name,
            "input_shape": [frames, mels, channels],
            "input_layout": "(frames, mel_bins, 1) log-mel, time major",
            "output": "softmax probabilities, one per class",
            "num_classes": len(class_names),
        },
        "audio": {
            "sample_rate": config.audio.sample_rate,
            "channels": config.audio.channels,
            "bit_depth": config.audio.bit_depth,
            "clip_seconds": config.audio.clip_seconds,
            "clip_samples": config.audio.clip_samples,
            "fit_mode": config.audio.fit_mode,
            "normalize": asdict(config.audio.normalize),
            # Trimming is for recorded clips, never for a stream window: it slides
            # the window's content in time and breaks every event timestamp.
            "trim_on_stream_windows": False,
            "trim": asdict(config.audio.trim),
        },
        "frontend": dict(frontend),
        "streaming": {
            "window_seconds": streaming.window_for(config.audio),
            "window_samples": int(
                round(streaming.window_for(config.audio) * config.audio.sample_rate)
            ),
            "hop_seconds": streaming.hop_seconds,
            "hop_samples": int(round(streaming.hop_seconds * config.audio.sample_rate)),
            "smoothing": asdict(smoothing),
        },
        "detector": {
            "confidence_threshold": activation,
            "release_threshold": release,
            "per_class_thresholds": per_class,
            "min_consecutive_hits": detector.min_consecutive_hits,
            "min_event_duration_seconds": detector.min_event_duration,
            "release_windows": detector.release_windows,
            "cooldown_ms": detector.cooldown_ms,
            "ignore_labels": list(detector.ignore_labels),
            "calibrated": calibrated,
            "state_machine": (
                "idle -> candidate (>= activation) -> confirmed "
                "(min_consecutive_hits) -> cooldown (per class); an active event "
                "survives windows above the release threshold, and closes after "
                "release_windows below it. Count on confirmation, once."
            ),
        },
        "classes": [
            {"index": index, "label": label} for index, label in enumerate(class_names)
        ],
        "labels": list(class_names),
        "tensors": tensors or {},
        "calibration": calibration or {},
        "metrics": metrics or {},
        "seed": config.seed,
    }
    if not calibrated:
        payload["warnings"] = [
            "thresholds are the configured defaults, not calibrated values - run "
            "section 06 of the notebook against real streaming recordings before "
            "shipping them"
        ]
    return payload


def write_model_metadata(path: PathLike, payload: Dict[str, object]) -> Path:
    """Write ``model_metadata.json``."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    LOGGER.info("Android metadata written to %s", destination)
    return destination


# ---------------------------------------------------------------------------
# Front-end parity
# ---------------------------------------------------------------------------
def parity_signal(sample_rate: int, seconds: float, seed: int = 1337) -> np.ndarray:
    """A deterministic test signal, built to expose front-end mistakes.

    Three tones through the mel range plus a short noise burst and a silent
    stretch: the tones pin the filterbank and the frequency axis, the burst pins
    the framing (an off-by-one hop moves it), and the silence pins the log offset,
    which is invisible on loud audio and dominates quiet frames.
    """
    count = int(round(seconds * sample_rate))
    time_axis = np.arange(count, dtype=np.float32) / float(sample_rate)
    signal = np.zeros(count, dtype=np.float32)
    for frequency, amplitude in ((220.0, 0.35), (1200.0, 0.25), (3400.0, 0.15)):
        signal += amplitude * np.sin(2.0 * np.pi * frequency * time_axis).astype(np.float32)

    rng = np.random.default_rng(seed)
    burst_start = count // 3
    burst_end = min(burst_start + count // 8, count)
    signal[burst_start:burst_end] += 0.3 * rng.standard_normal(
        burst_end - burst_start
    ).astype(np.float32)

    silence_start = (2 * count) // 3
    silence_end = min(silence_start + count // 8, count)
    signal[silence_start:silence_end] = 0.0

    peak = float(np.max(np.abs(signal))) or 1.0
    return (signal / peak * 0.9).astype(np.float32)


def write_frontend_parity(
    directory: PathLike,
    extractor,
    config: Config,
    samples: Optional[np.ndarray] = None,
) -> Dict[str, Path]:
    """Export the fixed WAV, the expected feature tensor, and how to reproduce it.

    The reference is computed from the audio **as it is read back from the WAV**,
    not from the float array in memory: Android decodes PCM16, so the reference
    has to start from the same quantised samples or the comparison would carry a
    rounding difference nobody can explain.

    The conditioned waveform is exported alongside the features, because it
    splits the two things that can be wrong - the loudness/length conditioning
    and the spectrogram - and a mismatch that shows up in only one of them is a
    ten-minute fix instead of an afternoon.
    """
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)

    audio = config.audio
    signal = (
        parity_signal(audio.sample_rate, audio.clip_seconds, config.seed)
        if samples is None
        else np.asarray(samples, dtype=np.float32)
    )
    wav_path = write_wav(target / PARITY_WAV, signal, audio.sample_rate)

    decoded, _ = read_wav(wav_path)
    # The stream path, exactly: no trimming, loudness normalisation if enabled,
    # then fit to the model's window.
    conditioned = decoded
    if audio.normalize.enabled:
        conditioned = normalize_loudness(
            conditioned, audio.normalize.target_dbfs, audio.normalize.peak_ceiling
        )
    conditioned = fit_length(conditioned, audio.clip_samples, audio.fit_mode)
    features = np.asarray(extractor(conditioned), dtype=np.float32)

    features_path = target / PARITY_FEATURES
    np.save(features_path, features)
    conditioned_path = target / PARITY_CONDITIONED
    np.save(conditioned_path, conditioned.astype(np.float32))

    metadata = {
        "purpose": (
            "Front-end parity fixture. Decode frontend_test.wav on Android, run "
            "the steps below, and compare against frontend_expected.npy. Any "
            "difference beyond ~1e-4 is a front-end mismatch, and it costs "
            "accuracy silently - the model still returns confident-looking "
            "numbers from features it was never trained on."
        ),
        "files": {
            "audio": PARITY_WAV,
            "expected_features": PARITY_FEATURES,
            "expected_conditioned_audio": PARITY_CONDITIONED,
        },
        "audio": {
            "sample_rate": audio.sample_rate,
            "channels": 1,
            "encoding": f"PCM{audio.bit_depth} little-endian",
            "samples": int(decoded.size),
            "clip_samples": audio.clip_samples,
        },
        "pipeline": [
            {
                "step": "decode",
                "detail": (
                    f"read the WAV as mono float32 in [-1, 1] "
                    f"(int16 / 32768 is close enough; this pipeline divides by "
                    f"32767 on write and reads back with libsndfile)"
                ),
            },
            {
                "step": "normalize",
                "enabled": audio.normalize.enabled,
                "detail": (
                    f"scale to {audio.normalize.target_dbfs:g} dBFS RMS, then clamp "
                    f"the peak to {audio.normalize.peak_ceiling:g}. RMS is "
                    f"sqrt(mean(x^2)) over the whole window; gain is "
                    f"10^((target - current)/20), capped at ceiling/peak."
                ),
            },
            {
                "step": "trim",
                "enabled": False,
                "detail": (
                    "NOT applied to stream windows. Trimming silence slides the "
                    "window content in time and destroys event timing; it only "
                    "belongs on a clip the user recorded deliberately."
                ),
            },
            {
                "step": "fit_length",
                "detail": (
                    f"pad or crop to exactly {audio.clip_samples} samples, "
                    f"mode '{audio.fit_mode}' "
                    f"({'symmetric around the centre' if audio.fit_mode == 'center' else 'from the end'})"
                ),
            },
            {
                "step": "stft",
                "detail": (
                    f"frame the signal with win_length="
                    f"{config.features.win_length(audio.sample_rate)}, hop_length="
                    f"{config.features.hop_length(audio.sample_rate)}, n_fft="
                    f"{config.features.n_fft}; periodic Hann window; "
                    f"center={config.features.center} "
                    f"({'librosa pads by n_fft//2' if config.features.center else 'no padding - the first frame starts at sample 0'}); "
                    f"take the magnitude squared"
                ),
            },
            {
                "step": "mel",
                "detail": (
                    f"multiply by the {config.features.n_mels}-band Slaney mel "
                    f"filterbank (fmin {config.features.fmin:g} Hz, fmax "
                    f"{min(config.features.fmax, audio.sample_rate / 2.0):g} Hz, "
                    f"Slaney area normalisation). The exact matrix is exported as "
                    f"mel_filterbank.json - use it rather than recomputing, the "
                    f"normalisation conventions differ between libraries."
                ),
            },
            {
                "step": "log",
                "detail": f"natural log of (mel + {config.features.log_offset:g})",
            },
            {
                "step": "transpose",
                "detail": "to (frames, mel_bins) - time major, as the model expects",
            },
            {
                "step": "normalize_features",
                "detail": _feature_normalisation_detail(config, extractor),
            },
        ],
        "expected": {
            "shape": [int(value) for value in features.shape],
            "dtype": "float32",
            "mean": float(features.mean()),
            "std": float(features.std()),
            "min": float(features.min()),
            "max": float(features.max()),
            "first_frame_head": [round(float(value), 6) for value in features[0][:8]],
            "last_frame_head": [round(float(value), 6) for value in features[-1][:8]],
            "checksum_sha256": hashlib.sha256(
                np.ascontiguousarray(features, dtype=np.float32).tobytes()
            ).hexdigest(),
        },
        "tolerance": {
            "max_absolute_difference": 1e-4,
            "note": (
                "float32 arithmetic reorders slightly between implementations; a "
                "difference of a few 1e-5 is fine, 1e-2 is a bug."
            ),
        },
        "frontend": extractor.metadata(),
    }
    metadata_path = target / PARITY_METADATA
    metadata_path.write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    LOGGER.info("front-end parity fixture written to %s", target)
    return {
        "audio": wav_path,
        "features": features_path,
        "conditioned": conditioned_path,
        "metadata": metadata_path,
    }


def _feature_normalisation_detail(config: Config, extractor) -> str:
    mode = config.features.normalize
    if mode == "per_example":
        return (
            "per_example: subtract the mean and divide by the standard deviation "
            "of the whole (frames, mel_bins) tensor, computed over every value in "
            "this window. epsilon 1e-8 in the denominator."
        )
    if mode == "global":
        stats = getattr(extractor, "stats", None)
        if stats is not None:
            return (
                f"global: subtract {stats.mean:.6f} and divide by "
                f"{stats.std:.6f} (dataset statistics, epsilon 1e-8). These "
                f"constants are part of the model - do not recompute them on device."
            )
        return "global normalisation configured but no statistics were exported"
    return "none: the log-mel values are fed to the model unchanged"

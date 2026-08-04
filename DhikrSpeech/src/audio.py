"""Audio I/O and single-clip signal conditioning.

Every stage of the pipeline funnels through :func:`prepare_clip`, so the audio a
model trains on is bit-for-bit the audio the exported model expects: 16 kHz mono,
silence trimmed, loudness normalised, fixed length.
"""

from __future__ import annotations

import hashlib
import logging
import warnings
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple, Union

import librosa
import numpy as np
import soundfile as sf

from .config import AudioConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "AudioFileInfo",
    "content_hash",
    "db_to_amplitude",
    "fit_length",
    "load_audio",
    "normalize_loudness",
    "prepare_clip",
    "probe_audio",
    "rms_dbfs",
    "to_pcm16",
    "trim_silence",
    "write_wav",
]

_EPS = 1e-12


@dataclass(frozen=True)
class AudioFileInfo:
    """Container metadata read without decoding the whole file."""

    path: Path
    ok: bool
    duration: float = 0.0
    sample_rate: int = 0
    channels: int = 0
    frames: int = 0
    subtype: str = ""
    error: str = ""


def probe_audio(path: PathLike) -> AudioFileInfo:
    """Read header metadata. Never raises - failures come back as ``ok=False``."""
    path = Path(path)
    try:
        info = sf.info(str(path))
        return AudioFileInfo(
            path=path,
            ok=True,
            duration=float(info.duration),
            sample_rate=int(info.samplerate),
            channels=int(info.channels),
            frames=int(info.frames),
            subtype=str(info.subtype),
        )
    except Exception as exc:  # noqa: BLE001 - any decoder error means "unusable file"
        # Formats libsndfile cannot open (mp3/m4a on older builds) still decode
        # through audioread, so fall back to a full load before giving up. The
        # fallback is expected here, so its warnings are not worth showing.
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore")
                samples, sample_rate = librosa.load(str(path), sr=None, mono=False)
            channels = 1 if samples.ndim == 1 else int(samples.shape[0])
            frames = samples.shape[-1]
            return AudioFileInfo(
                path=path,
                ok=True,
                duration=float(frames) / float(sample_rate),
                sample_rate=int(sample_rate),
                channels=channels,
                frames=int(frames),
                subtype="COMPRESSED",
            )
        except Exception as fallback_exc:  # noqa: BLE001
            LOGGER.debug("probe failed for %s: %s / %s", path, exc, fallback_exc)
            return AudioFileInfo(path=path, ok=False, error=f"{type(exc).__name__}: {exc}")


def load_audio(
    path: PathLike,
    sample_rate: int,
    mono: bool = True,
    offset: float = 0.0,
    duration: Optional[float] = None,
) -> np.ndarray:
    """Decode a file to float32 in ``[-1, 1]``, resampling to ``sample_rate``."""
    samples, _ = librosa.load(
        str(path), sr=sample_rate, mono=mono, offset=offset, duration=duration
    )
    return np.asarray(samples, dtype=np.float32)


def rms_dbfs(samples: np.ndarray) -> float:
    """Full-scale RMS in dB. Silence returns ``-inf``."""
    if samples.size == 0:
        return float("-inf")
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    if rms <= _EPS:
        return float("-inf")
    return 20.0 * float(np.log10(rms))


def db_to_amplitude(decibels: float) -> float:
    return float(10.0 ** (decibels / 20.0))


def trim_silence(
    samples: np.ndarray, sample_rate: int, top_db: float, pad_ms: float = 0.0
) -> np.ndarray:
    """Drop leading/trailing silence, keeping ``pad_ms`` of context on each side."""
    if samples.size == 0:
        return samples
    trimmed, index = librosa.effects.trim(samples, top_db=top_db)
    if trimmed.size == 0:
        # Entirely below the threshold: hand the original back and let the
        # validator flag it as silent rather than returning nothing.
        return samples
    pad = int(round(pad_ms * sample_rate / 1000.0))
    start = max(int(index[0]) - pad, 0)
    end = min(int(index[1]) + pad, samples.size)
    return np.asarray(samples[start:end], dtype=np.float32)


def normalize_loudness(
    samples: np.ndarray, target_dbfs: float, peak_ceiling: float = 0.99
) -> np.ndarray:
    """Scale to a target RMS, then clamp the peak so the gain cannot clip."""
    if samples.size == 0:
        return samples
    current = rms_dbfs(samples)
    if not np.isfinite(current):
        return samples
    gain = db_to_amplitude(target_dbfs - current)
    peak = float(np.max(np.abs(samples)))
    if peak > 0.0:
        gain = min(gain, peak_ceiling / peak)
    return np.asarray(samples * gain, dtype=np.float32)


def fit_length(samples: np.ndarray, num_samples: int, mode: str = "center") -> np.ndarray:
    """Zero-pad or crop to exactly ``num_samples``."""
    if num_samples <= 0:
        raise ValueError("num_samples must be positive")
    length = samples.size
    if length == num_samples:
        return np.asarray(samples, dtype=np.float32)
    if length > num_samples:
        start = (length - num_samples) // 2 if mode == "center" else 0
        return np.asarray(samples[start : start + num_samples], dtype=np.float32)
    missing = num_samples - length
    left = missing // 2 if mode == "center" else 0
    right = missing - left
    return np.pad(np.asarray(samples, dtype=np.float32), (left, right), mode="constant")


def prepare_clip(
    samples: np.ndarray, config: AudioConfig, fixed_length: bool = True
) -> np.ndarray:
    """Trim, normalise and (optionally) fit a clip to the configured length.

    This is the one conditioning path used by preprocessing, training,
    evaluation and export.
    """
    result = np.asarray(samples, dtype=np.float32)
    if config.trim.enabled:
        result = trim_silence(
            result, config.sample_rate, config.trim.top_db, config.trim.pad_ms
        )
    if config.normalize.enabled:
        result = normalize_loudness(
            result, config.normalize.target_dbfs, config.normalize.peak_ceiling
        )
    if fixed_length:
        result = fit_length(result, config.clip_samples, config.fit_mode)
    return result


def to_pcm16(samples: np.ndarray) -> np.ndarray:
    """Convert float ``[-1, 1]`` to int16, clipping instead of wrapping."""
    clipped = np.clip(np.asarray(samples, dtype=np.float32), -1.0, 1.0)
    return np.asarray(np.round(clipped * 32767.0), dtype=np.int16)


def write_wav(path: PathLike, samples: np.ndarray, sample_rate: int) -> Path:
    """Write mono PCM16 WAV, creating parent directories."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(destination), to_pcm16(samples), sample_rate, subtype="PCM_16")
    return destination


def content_hash(samples: np.ndarray) -> str:
    """Stable hash of decoded audio - identifies duplicates across containers."""
    return hashlib.md5(to_pcm16(samples).tobytes()).hexdigest()


def read_wav(path: PathLike) -> Tuple[np.ndarray, int]:
    """Read an already-conditioned WAV without resampling."""
    samples, sample_rate = sf.read(str(path), dtype="float32", always_2d=False)
    if samples.ndim > 1:
        samples = np.mean(samples, axis=1)
    return np.asarray(samples, dtype=np.float32), int(sample_rate)

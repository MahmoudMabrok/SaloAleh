"""Log mel spectrogram front-end.

The exported TFLite model consumes features, not raw audio, so this front-end
has to be reproducible outside Python. Every parameter that affects the output
is in :meth:`LogMelExtractor.metadata`, and the exact filterbank can be dumped
with :meth:`LogMelExtractor.save_filterbank` for the Android port.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, Optional, Tuple, Union

import librosa
import numpy as np

from .config import FeatureConfig

LOGGER = logging.getLogger(__name__)

__all__ = ["FeatureStats", "LogMelExtractor", "compute_global_stats"]

_EPS = 1e-8


@dataclass(frozen=True)
class FeatureStats:
    """Dataset-level mean/std used by ``features.normalize: global``."""

    mean: float
    std: float

    def to_dict(self) -> Dict[str, float]:
        return {"mean": float(self.mean), "std": float(self.std)}

    @classmethod
    def from_dict(cls, data: Dict[str, float]) -> "FeatureStats":
        return cls(mean=float(data["mean"]), std=float(data["std"]))

    def save(self, path: Union[str, Path]) -> Path:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(self.to_dict(), indent=2), encoding="utf-8")
        return destination

    @classmethod
    def load(cls, path: Union[str, Path]) -> "FeatureStats":
        return cls.from_dict(json.loads(Path(path).read_text(encoding="utf-8")))


@dataclass
class LogMelExtractor:
    """Turn a mono float32 clip into a ``(frames, n_mels)`` log mel spectrogram."""

    config: FeatureConfig
    sample_rate: int
    stats: Optional[FeatureStats] = None
    _mel_basis: np.ndarray = field(init=False, repr=False)
    _window: np.ndarray = field(init=False, repr=False)

    def __post_init__(self) -> None:
        win_length = self.config.win_length(self.sample_rate)
        if win_length > self.config.n_fft:
            raise ValueError(
                f"features.window_ms ({self.config.window_ms} ms = {win_length} samples) "
                f"exceeds features.n_fft ({self.config.n_fft}); raise n_fft"
            )
        fmax = min(self.config.fmax, self.sample_rate / 2.0)
        self._mel_basis = librosa.filters.mel(
            sr=self.sample_rate,
            n_fft=self.config.n_fft,
            n_mels=self.config.n_mels,
            fmin=self.config.fmin,
            fmax=fmax,
            htk=False,
            norm="slaney",
        ).astype(np.float32)
        self._window = librosa.filters.get_window(
            "hann", win_length, fftbins=True
        ).astype(np.float32)
        if self.config.normalize == "global" and self.stats is None:
            LOGGER.warning(
                "features.normalize is 'global' but no FeatureStats were supplied; "
                "falling back to unnormalised features"
            )

    # -- geometry -----------------------------------------------------------
    @property
    def win_length(self) -> int:
        return self.config.win_length(self.sample_rate)

    @property
    def hop_length(self) -> int:
        return self.config.hop_length(self.sample_rate)

    def num_frames(self, num_samples: int) -> int:
        return self.config.num_frames(num_samples, self.sample_rate)

    def output_shape(self, num_samples: int) -> Tuple[int, int]:
        return (self.num_frames(num_samples), self.config.n_mels)

    @property
    def mel_filterbank(self) -> np.ndarray:
        """``(n_mels, n_fft // 2 + 1)`` Slaney-normalised triangular filters."""
        return self._mel_basis

    # -- extraction ---------------------------------------------------------
    def power_spectrogram(self, samples: np.ndarray) -> np.ndarray:
        stft = librosa.stft(
            np.asarray(samples, dtype=np.float32),
            n_fft=self.config.n_fft,
            hop_length=self.hop_length,
            win_length=self.win_length,
            window="hann",
            center=self.config.center,
            pad_mode="constant",
        )
        return np.abs(stft, dtype=np.float32) ** 2

    def __call__(self, samples: np.ndarray) -> np.ndarray:
        """Return ``(frames, n_mels)`` float32 features."""
        power = self.power_spectrogram(samples)
        mel = self._mel_basis @ power
        features = np.log(mel + self.config.log_offset, dtype=np.float32)
        features = features.T  # (frames, n_mels)
        return self.normalize(features)

    def batch(self, clips: Iterable[np.ndarray]) -> np.ndarray:
        """Stack features for clips that already share a length."""
        stacked = [self(clip) for clip in clips]
        if not stacked:
            return np.zeros((0, 0, self.config.n_mels), dtype=np.float32)
        return np.stack(stacked).astype(np.float32)

    def normalize(self, features: np.ndarray) -> np.ndarray:
        mode = self.config.normalize
        if mode == "per_example":
            mean = float(np.mean(features))
            std = float(np.std(features))
            return ((features - mean) / (std + _EPS)).astype(np.float32)
        if mode == "global" and self.stats is not None:
            return (
                (features - self.stats.mean) / (self.stats.std + _EPS)
            ).astype(np.float32)
        return features.astype(np.float32)

    # -- portability --------------------------------------------------------
    def metadata(self) -> Dict[str, object]:
        """Everything an Android implementation needs to match this front-end."""
        return {
            "sample_rate": int(self.sample_rate),
            "n_mels": int(self.config.n_mels),
            "n_fft": int(self.config.n_fft),
            "win_length": int(self.win_length),
            "hop_length": int(self.hop_length),
            "window": "hann_periodic",
            "center": bool(self.config.center),
            "fmin": float(self.config.fmin),
            "fmax": float(min(self.config.fmax, self.sample_rate / 2.0)),
            "mel_scale": "slaney",
            "mel_norm": "slaney",
            "log": f"log(mel + {self.config.log_offset:g})",
            "log_offset": float(self.config.log_offset),
            "normalize": self.config.normalize,
            "stats": self.stats.to_dict() if self.stats else None,
        }

    def save_filterbank(self, path: Union[str, Path], decimals: int = 8) -> Path:
        """Dump the mel matrix so Android can use identical filters."""
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "shape": list(self._mel_basis.shape),
            "layout": "row_major[n_mels][n_fft/2+1]",
            "filters": np.round(self._mel_basis, decimals).tolist(),
        }
        destination.write_text(json.dumps(payload), encoding="utf-8")
        return destination


def compute_global_stats(
    features: Iterable[np.ndarray], max_batches: Optional[int] = None
) -> FeatureStats:
    """Streaming mean/std over feature arrays, for ``features.normalize: global``."""
    total = 0
    accumulated = 0.0
    accumulated_sq = 0.0
    for index, array in enumerate(features):
        if max_batches is not None and index >= max_batches:
            break
        values = np.asarray(array, dtype=np.float64).ravel()
        total += values.size
        accumulated += float(values.sum())
        accumulated_sq += float(np.square(values).sum())
    if total == 0:
        raise ValueError("no features supplied")
    mean = accumulated / total
    variance = max(accumulated_sq / total - mean * mean, 0.0)
    return FeatureStats(mean=float(mean), std=float(np.sqrt(variance)))

"""On-the-fly augmentation for the training split.

Waveform augmentation runs before the front-end (:class:`WaveformAugmentor`),
SpecAugment runs after it (:func:`spec_augment`). Both are driven entirely by
``augmentation`` in ``config.yaml`` and by an explicit ``numpy`` generator, so a
run is reproducible from the seed.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Sequence, Union

import librosa
import numpy as np

from .audio import db_to_amplitude, fit_length, load_audio, rms_dbfs
from .config import AudioConfig, AugmentationConfig

LOGGER = logging.getLogger(__name__)

__all__ = ["NoiseBank", "WaveformAugmentor", "spec_augment"]

_EPS = 1e-12


@dataclass
class NoiseBank:
    """Background noise pool: real recordings when available, synthetic otherwise."""

    clips: List[np.ndarray] = field(default_factory=list)
    sample_rate: int = 16000
    allow_synthetic: bool = True

    @classmethod
    def load(
        cls,
        directory: Union[str, Path, None],
        sample_rate: int,
        # The collector's noise card uploads whatever the browser recorded, which
        # is .webm on Chrome and .m4a on Safari far more often than .wav. Leaving
        # those out made a full noise folder look empty and silently fell back to
        # synthetic noise.
        extensions: Sequence[str] = (".wav", ".flac", ".ogg", ".mp3", ".m4a", ".webm"),
        max_files: int = 64,
        allow_synthetic: bool = True,
    ) -> "NoiseBank":
        clips: List[np.ndarray] = []
        if directory is not None:
            root = Path(directory)
            if root.is_dir():
                suffixes = {ext.lower() for ext in extensions}
                files = sorted(
                    path
                    for path in root.rglob("*")
                    if path.is_file() and path.suffix.lower() in suffixes
                )
                for path in files[:max_files]:
                    try:
                        clips.append(load_audio(path, sample_rate))
                    except Exception as exc:  # noqa: BLE001
                        LOGGER.warning("skipping unreadable noise file %s: %s", path, exc)
        if not clips:
            LOGGER.info(
                "no background-noise files found; using synthetic noise (%s)",
                "enabled" if allow_synthetic else "disabled",
            )
        return cls(clips=clips, sample_rate=sample_rate, allow_synthetic=allow_synthetic)

    def __len__(self) -> int:
        return len(self.clips)

    @property
    def available(self) -> bool:
        return bool(self.clips) or self.allow_synthetic

    def sample(self, rng: np.random.Generator, length: int) -> np.ndarray:
        """A noise segment of exactly ``length`` samples."""
        if self.clips:
            clip = self.clips[int(rng.integers(len(self.clips)))]
            if clip.size >= length:
                start = int(rng.integers(clip.size - length + 1))
                return np.asarray(clip[start : start + length], dtype=np.float32)
            repeats = int(np.ceil(length / max(clip.size, 1)))
            return np.asarray(np.tile(clip, repeats)[:length], dtype=np.float32)
        return _synthetic_noise(rng, length)


def _synthetic_noise(rng: np.random.Generator, length: int) -> np.ndarray:
    """White or pink noise, so augmentation still works with an empty noise dir."""
    white = rng.standard_normal(length).astype(np.float32)
    if rng.random() < 0.5:
        return white / (float(np.max(np.abs(white))) + _EPS)
    spectrum = np.fft.rfft(white)
    frequencies = np.arange(spectrum.size, dtype=np.float64)
    frequencies[0] = 1.0
    pink = np.fft.irfft(spectrum / np.sqrt(frequencies), n=length).astype(np.float32)
    return pink / (float(np.max(np.abs(pink))) + _EPS)


@dataclass
class WaveformAugmentor:
    """Applies the configured waveform transforms, each with its own probability."""

    config: AugmentationConfig
    audio: AudioConfig
    noise_bank: Optional[NoiseBank] = None

    def __call__(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        if not self.config.enabled:
            return np.asarray(samples, dtype=np.float32)

        result = np.asarray(samples, dtype=np.float32)
        result = self._speed_perturb(result, rng)
        result = self._pitch_shift(result, rng)
        result = self._time_shift(result, rng)
        result = self._add_noise(result, rng)
        result = self._gain(result, rng)
        result = fit_length(result, self.audio.clip_samples, self.audio.fit_mode)
        return np.clip(result, -1.0, 1.0).astype(np.float32)

    # -- individual transforms ---------------------------------------------
    def _speed_perturb(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.speed_perturb
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        rate = float(rng.uniform(cfg.min_rate, cfg.max_rate))
        if abs(rate - 1.0) < 1e-3:
            return samples
        try:
            # Resampling changes tempo *and* pitch, which is the classic Kaldi
            # style speed perturbation and is cheaper than a phase vocoder.
            # Playing back at `rate` scales the length by 1 / rate.
            source_rate = self.audio.sample_rate
            target_rate = max(int(round(source_rate / rate)), 1)
            return librosa.resample(
                samples, orig_sr=source_rate, target_sr=target_rate, res_type="soxr_hq"
            ).astype(np.float32)
        except Exception as exc:  # noqa: BLE001
            LOGGER.debug("speed perturbation failed, using the original clip: %s", exc)
            return samples

    def _pitch_shift(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.pitch_shift
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        steps = float(rng.uniform(cfg.min_semitones, cfg.max_semitones))
        if abs(steps) < 1e-3:
            return samples
        try:
            return librosa.effects.pitch_shift(
                samples, sr=self.audio.sample_rate, n_steps=steps
            ).astype(np.float32)
        except Exception as exc:  # noqa: BLE001
            LOGGER.debug("pitch shift failed, using the original clip: %s", exc)
            return samples

    def _time_shift(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.time_shift
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        max_shift = int(round(cfg.max_shift_ms * self.audio.sample_rate / 1000.0))
        if max_shift <= 0:
            return samples
        shift = int(rng.integers(-max_shift, max_shift + 1))
        if shift == 0:
            return samples
        shifted = np.zeros_like(samples)
        if shift > 0:
            shifted[shift:] = samples[: samples.size - shift]
        else:
            shifted[:shift] = samples[-shift:]
        return shifted

    def _add_noise(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.background_noise
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        bank = self.noise_bank
        if bank is None or not bank.available:
            return samples
        signal_db = rms_dbfs(samples)
        if not np.isfinite(signal_db):
            return samples
        noise = bank.sample(rng, samples.size)
        noise_db = rms_dbfs(noise)
        if not np.isfinite(noise_db):
            return samples
        snr_db = float(rng.uniform(cfg.min_snr_db, cfg.max_snr_db))
        gain = db_to_amplitude(signal_db - snr_db - noise_db)
        return (samples + noise * gain).astype(np.float32)

    def _gain(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.gain
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        decibels = float(rng.uniform(cfg.min_db, cfg.max_db))
        return (samples * db_to_amplitude(decibels)).astype(np.float32)


def spec_augment(
    features: np.ndarray, config: AugmentationConfig, rng: np.random.Generator
) -> np.ndarray:
    """Mask random frequency bands and time steps of a ``(frames, mels)`` array."""
    cfg = config.spec_augment
    if not config.enabled or not cfg.enabled or rng.random() >= cfg.probability:
        return features

    masked = np.array(features, dtype=np.float32, copy=True)
    frames, mels = masked.shape[0], masked.shape[1]
    fill = float(np.mean(masked))

    for _ in range(max(cfg.freq_masks, 0)):
        width = int(rng.integers(0, min(cfg.freq_mask_width, mels) + 1))
        if width <= 0:
            continue
        start = int(rng.integers(0, mels - width + 1))
        masked[:, start : start + width] = fill

    for _ in range(max(cfg.time_masks, 0)):
        width = int(rng.integers(0, min(cfg.time_mask_width, frames) + 1))
        if width <= 0:
            continue
        start = int(rng.integers(0, frames - width + 1))
        masked[start : start + width, :] = fill

    return masked

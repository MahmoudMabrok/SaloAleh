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

__all__ = [
    "NOISE_COLLECTION_GUIDE",
    "NoiseBank",
    "WaveformAugmentor",
    "reverberate",
    "spec_augment",
]

_EPS = 1e-12

# What to record when the noise folder is empty. Ordered by how much each one
# buys: the first few cover most of where the app is actually used.
NOISE_COLLECTION_GUIDE = (
    "quiet room (the baseline - it is not silence)",
    "fan / air conditioning",
    "street traffic",
    "inside a car",
    "TV or radio in the background",
    "people talking nearby",
    "children",
    "mosque or background Quran recitation",
    "microphone handling: the phone being picked up, put down, rubbed",
    "the same room at different phone distances (10 cm, arm's length, on a table)",
)


@dataclass
class NoiseBank:
    """Background noise pool: real recordings when available, synthetic otherwise.

    Synthetic white/pink noise keeps augmentation working with an empty folder,
    but it is not a stand-in for a room. Real noise has structure - speech
    babble, a TV, traffic - and structure is exactly what a false activation
    comes from; flat noise mostly teaches the model to ignore a hiss.
    """

    clips: List[np.ndarray] = field(default_factory=list)
    sample_rate: int = 16000
    allow_synthetic: bool = True
    directory: Optional[str] = None
    files: List[str] = field(default_factory=list)
    skipped: int = 0

    @classmethod
    def load(
        cls,
        directory: Union[str, Path, None],
        sample_rate: int,
        extensions: Sequence[str] = (".wav", ".flac", ".ogg", ".mp3"),
        max_files: int = 64,
        allow_synthetic: bool = True,
    ) -> "NoiseBank":
        clips: List[np.ndarray] = []
        loaded: List[str] = []
        skipped = 0
        if directory is not None:
            root = Path(directory)
            if root.is_dir():
                suffixes = {ext.lower() for ext in extensions}
                files = sorted(
                    path
                    for path in root.rglob("*")
                    if path.is_file() and path.suffix.lower() in suffixes
                )
                if len(files) > max_files:
                    LOGGER.info(
                        "%d noise files found, loading the first %d",
                        len(files),
                        max_files,
                    )
                for path in files[:max_files]:
                    try:
                        clips.append(load_audio(path, sample_rate))
                        loaded.append(str(path))
                    except Exception as exc:  # noqa: BLE001
                        skipped += 1
                        LOGGER.warning("skipping unreadable noise file %s: %s", path, exc)
        if not clips:
            LOGGER.info(
                "no background-noise files found; using synthetic noise (%s)",
                "enabled" if allow_synthetic else "disabled",
            )
        return cls(
            clips=clips,
            sample_rate=sample_rate,
            allow_synthetic=allow_synthetic,
            directory=str(directory) if directory is not None else None,
            files=loaded,
            skipped=skipped,
        )

    def __len__(self) -> int:
        return len(self.clips)

    @property
    def available(self) -> bool:
        return bool(self.clips) or self.allow_synthetic

    @property
    def synthetic_active(self) -> bool:
        """True when every noise sample drawn will be generated, not recorded."""
        return not self.clips and self.allow_synthetic

    @property
    def total_seconds(self) -> float:
        return sum(clip.size for clip in self.clips) / float(max(self.sample_rate, 1))

    def report(self) -> str:
        """What the augmenter will actually mix in, and what to collect."""
        lines = [
            f"noise directory  : {self.directory or '(none configured)'}",
            f"real noise files : {len(self.clips)}"
            + (f" ({self.skipped} unreadable, skipped)" if self.skipped else ""),
            f"real noise audio : {self.total_seconds / 60.0:.1f} minutes",
            f"synthetic fallback: {'ACTIVE' if self.synthetic_active else 'not used'}",
        ]
        if self.synthetic_active:
            lines.append(
                "\n!! every background-noise augmentation will be generated white or "
                "pink noise. That is not a realistic acoustic environment: it has no "
                "speech babble, no TV, no room, and those are what a false activation "
                "is made of. Record a few minutes each (phone, same as the dhikr "
                "recordings) of:"
            )
            lines.extend(f"     - {item}" for item in NOISE_COLLECTION_GUIDE)
            lines.append(f"   and drop the files in {self.directory or 'paths.noise_dir'}.")
        elif self.total_seconds < 60.0:
            lines.append(
                f"\n!! only {self.total_seconds:.0f} s of real noise, so the same few "
                f"seconds are mixed into many clips and the model can learn the noise "
                f"itself. A few minutes of each environment is the target."
            )
        return "\n".join(lines)

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


def reverberate(
    samples: np.ndarray,
    sample_rate: int,
    decay_ms: float,
    wet: float,
    rng: np.random.Generator,
) -> np.ndarray:
    """Convolve with a generated room impulse response.

    The impulse response is exponentially decaying noise - the standard cheap
    stand-in for a small room, and enough to teach the model that a phrase spoken
    across a room is the same phrase. No corpus of measured impulse responses and
    no extra dependency: ``numpy.convolve`` at these lengths costs microseconds.

    ``wet`` is the mix; the dry signal is always kept, because a fully wet clip is
    not something a phone microphone ever records.
    """
    length = int(round(max(decay_ms, 1.0) * sample_rate / 1000.0))
    if length < 2 or samples.size == 0:
        return np.asarray(samples, dtype=np.float32)

    time_axis = np.arange(length, dtype=np.float32)
    # -60 dB over the decay length, i.e. the usual RT60 convention.
    envelope = np.exp(-6.9078 * time_axis / float(length)).astype(np.float32)
    impulse = rng.standard_normal(length).astype(np.float32) * envelope
    impulse[0] = 1.0  # direct sound
    energy = float(np.sqrt(np.sum(np.square(impulse))))
    if energy <= _EPS:
        return np.asarray(samples, dtype=np.float32)
    impulse /= energy

    wet_signal = np.convolve(samples, impulse)[: samples.size].astype(np.float32)
    mix = float(np.clip(wet, 0.0, 1.0))
    return ((1.0 - mix) * samples + mix * wet_signal).astype(np.float32)


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
        # Reverb before the noise: in a real room the microphone hears the
        # reverberated voice *and* the background, not a reverberated mixture.
        result = self._reverb(result, rng)
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

    def _reverb(self, samples: np.ndarray, rng: np.random.Generator) -> np.ndarray:
        cfg = self.config.reverb
        if not cfg.enabled or rng.random() >= cfg.probability:
            return samples
        decay = float(rng.uniform(cfg.min_decay_ms, cfg.max_decay_ms))
        wet = float(rng.uniform(cfg.min_wet, cfg.max_wet))
        try:
            return reverberate(samples, self.audio.sample_rate, decay, wet, rng)
        except Exception as exc:  # noqa: BLE001
            LOGGER.debug("reverb failed, using the original clip: %s", exc)
            return samples

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

"""Front-end parity assets: proof that Android computes the same features.

The model does not take audio, it takes a log-mel tensor. So the Kotlin
front-end has to reproduce this one *exactly* - the same framing, the same Hann
window, the same Slaney mel filters, the same log offset, the same
normalisation. When it does not, nothing fails: the app runs, the model returns
confident nonsense, and the mistake is indistinguishable from a bad model.

These assets make the mismatch checkable in one line on the device::

    exports/<target>/frontend_test.wav        conditioned audio, exactly one window
    exports/<target>/frontend_expected.npy    the features this pipeline computes
    exports/<target>/frontend_metadata.json   shapes, hashes, tolerance, recipe

The WAV is written **already conditioned** - one window long, level-normalised -
so the Android check is unambiguous: decode the file, run the front-end straight
over the samples, compare against ``frontend_expected``. No trimming, no padding,
no ordering questions. It is PCM16 rather than float so both sides decode
bit-identical integers.
"""

from __future__ import annotations

import hashlib
import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Union

import numpy as np

from .audio import prepare_clip, read_wav, write_wav
from .config import AudioConfig
from .features import LogMelExtractor

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "ParityAssets",
    "ParityComparison",
    "PARITY_AUDIO_NAME",
    "PARITY_EXPECTED_NAME",
    "PARITY_METADATA_NAME",
    "build_parity_signal",
    "compare_parity",
    "write_parity_assets",
]

PARITY_AUDIO_NAME = "frontend_test.wav"
PARITY_EXPECTED_NAME = "frontend_expected.npy"
PARITY_METADATA_NAME = "frontend_metadata.json"

# Absolute tolerance for a passing comparison. Two correct implementations still
# differ a little - different FFT libraries, float32 accumulation order - and
# 1e-3 on a log-mel value is far below anything the model can notice, while a
# genuine mismatch (wrong window, wrong mel norm, missing log offset) is off by
# whole units.
DEFAULT_TOLERANCE = 1e-3


def build_parity_signal(audio: AudioConfig, seed: int = 1337) -> np.ndarray:
    """A deterministic test signal that exercises the whole front-end.

    Sine sweeps put energy in specific mel bands (so a wrong filterbank shows up
    as a shifted peak), the onsets and the silent gap exercise framing and the
    log offset (so a wrong hop shows up as a shifted column), and a little noise
    keeps every bin non-zero - a filter multiplied by an exactly-zero band would
    hide a mismatch behind ``log(offset)``.
    """
    rng = np.random.default_rng(seed)
    length = audio.clip_samples
    time = np.arange(length, dtype=np.float64) / float(audio.sample_rate)
    signal = np.zeros(length, dtype=np.float64)

    for frequency, amplitude, start, end in (
        (220.0, 0.35, 0.00, 0.30),
        (880.0, 0.25, 0.20, 0.55),
        (3000.0, 0.20, 0.45, 0.75),
        (6500.0, 0.12, 0.70, 1.00),
    ):
        window = (time >= start * time[-1]) & (time <= end * time[-1])
        signal[window] += amplitude * np.sin(2.0 * np.pi * frequency * time[window])

    # A silent gap in the middle third of the second half: log(mel + offset) is
    # the only thing keeping this finite, so an implementation that forgot the
    # offset produces -inf here rather than a small difference.
    gap = slice(int(length * 0.60), int(length * 0.66))
    signal[gap] = 0.0

    signal += 0.01 * rng.standard_normal(length)
    peak = float(np.max(np.abs(signal))) or 1.0
    return (signal / peak * 0.8).astype(np.float32)


@dataclass
class ParityAssets:
    """Where the assets were written and what they contain."""

    audio_path: Path
    expected_path: Path
    metadata_path: Path
    shape: List[int]
    tolerance: float
    audio_sha256: str

    def to_dict(self) -> Dict[str, object]:
        return {
            "audio": self.audio_path.name,
            "expected": self.expected_path.name,
            "metadata": self.metadata_path.name,
            "feature_shape": list(self.shape),
            "tolerance": self.tolerance,
            "audio_sha256": self.audio_sha256,
        }


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def write_parity_assets(
    directory: PathLike,
    extractor: LogMelExtractor,
    audio: AudioConfig,
    seed: int = 1337,
    tolerance: float = DEFAULT_TOLERANCE,
) -> ParityAssets:
    """Generate the three parity files for one exported front-end.

    The expected features are computed from the audio **as written and read
    back**, not from the in-memory float signal, so the PCM16 rounding is on both
    sides of the comparison and the file on disk is the whole contract.
    """
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)

    signal = build_parity_signal(audio, seed=seed)
    conditioned = prepare_clip(signal, audio, fixed_length=True)

    audio_path = target / PARITY_AUDIO_NAME
    write_wav(audio_path, conditioned, audio.sample_rate)

    decoded, sample_rate = read_wav(audio_path)
    if sample_rate != audio.sample_rate:  # pragma: no cover - writer controls this
        raise RuntimeError(f"parity audio written at {sample_rate} Hz, expected {audio.sample_rate}")
    features = np.ascontiguousarray(extractor(decoded).astype(np.float32))

    expected_path = target / PARITY_EXPECTED_NAME
    np.save(expected_path, features)

    metadata = {
        "purpose": (
            "Front-end parity check. Decode frontend_test.wav, run the Android "
            "log-mel front-end directly over the decoded samples, and compare the "
            "result against frontend_expected.npy element-wise."
        ),
        "recipe": [
            "decode frontend_test.wav (PCM16 mono, already exactly one window long)",
            "do NOT trim, pad or re-normalise - the file is already conditioned",
            "compute the log-mel features with the parameters under 'frontend'",
            "compare against frontend_expected with |a - b| <= tolerance",
        ],
        "audio": {
            "file": PARITY_AUDIO_NAME,
            "sample_rate": int(audio.sample_rate),
            "channels": 1,
            "encoding": "PCM16",
            "samples": int(audio.clip_samples),
            "seconds": float(audio.clip_seconds),
            "sha256": _sha256(audio_path),
        },
        "expected": {
            "file": PARITY_EXPECTED_NAME,
            "format": "numpy .npy, little-endian float32, C order",
            "shape": [int(value) for value in features.shape],
            "layout": "(frames, mel_bins)",
            "dtype": "float32",
            "sha256": _sha256(expected_path),
            "min": float(features.min()),
            "max": float(features.max()),
            "mean": float(features.mean()),
        },
        "tolerance": float(tolerance),
        "frontend": extractor.metadata(),
        "seed": int(seed),
    }
    metadata_path = target / PARITY_METADATA_NAME
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")

    LOGGER.info("front-end parity assets written to %s", target)
    return ParityAssets(
        audio_path=audio_path,
        expected_path=expected_path,
        metadata_path=metadata_path,
        shape=[int(value) for value in features.shape],
        tolerance=float(tolerance),
        audio_sha256=metadata["audio"]["sha256"],
    )


@dataclass
class ParityComparison:
    """Outcome of checking a candidate front-end against the expected tensor."""

    shape_matches: bool
    max_delta: float
    mean_delta: float
    tolerance: float
    expected_shape: List[int]
    actual_shape: List[int]

    @property
    def passed(self) -> bool:
        return self.shape_matches and self.max_delta <= self.tolerance

    def summary(self) -> str:
        if not self.shape_matches:
            return (
                f"FAIL - shape {tuple(self.actual_shape)} does not match the expected "
                f"{tuple(self.expected_shape)}. Check hop_length, win_length, n_fft and "
                f"whether the implementation centre-pads (this one does not)."
            )
        verdict = "PASS" if self.passed else "FAIL"
        return (
            f"{verdict} - max |delta| {self.max_delta:.2e}, mean {self.mean_delta:.2e} "
            f"(tolerance {self.tolerance:.1e})"
        )


def compare_parity(
    features: np.ndarray,
    directory: PathLike,
    tolerance: Optional[float] = None,
) -> ParityComparison:
    """Compare a candidate feature tensor against the stored expectation.

    Used by the notebook to prove the Python side still reproduces its own
    assets, and by anyone porting the front-end who can dump a tensor to ``.npy``.
    """
    target = Path(directory)
    expected = np.load(target / PARITY_EXPECTED_NAME)
    actual = np.asarray(features, dtype=np.float32)

    if tolerance is None:
        metadata_path = target / PARITY_METADATA_NAME
        tolerance = DEFAULT_TOLERANCE
        if metadata_path.is_file():
            payload = json.loads(metadata_path.read_text(encoding="utf-8"))
            tolerance = float(payload.get("tolerance", DEFAULT_TOLERANCE))

    if actual.shape != expected.shape:
        return ParityComparison(
            shape_matches=False,
            max_delta=float("inf"),
            mean_delta=float("inf"),
            tolerance=float(tolerance),
            expected_shape=list(expected.shape),
            actual_shape=list(actual.shape),
        )

    delta = np.abs(expected - actual)
    return ParityComparison(
        shape_matches=True,
        max_delta=float(delta.max()),
        mean_delta=float(delta.mean()),
        tolerance=float(tolerance),
        expected_shape=list(expected.shape),
        actual_shape=list(actual.shape),
    )

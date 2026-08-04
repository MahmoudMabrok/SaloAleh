"""Typed configuration for the DhikrSpeech pipeline.

``configs/config.yaml`` is the single source of truth. This module turns it into
a tree of dataclasses so that every other module gets attribute access, type
hints and an early, explicit error when a key is missing or misspelled.
"""

from __future__ import annotations

import dataclasses
import logging
import os
from dataclasses import dataclass, field, is_dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Union, get_args, get_origin, get_type_hints

import yaml

LOGGER = logging.getLogger(__name__)

__all__ = [
    "AudioConfig",
    "AugmentationConfig",
    "Config",
    "EvaluationConfig",
    "ExportConfig",
    "FeatureConfig",
    "ModelConfig",
    "PathsConfig",
    "SplitConfig",
    "TrainingConfig",
    "load_config",
]


# ---------------------------------------------------------------------------
# Generic YAML -> dataclass conversion
# ---------------------------------------------------------------------------
def _is_optional(annotation: Any) -> bool:
    return get_origin(annotation) is Union and type(None) in get_args(annotation)


def _unwrap_optional(annotation: Any) -> Any:
    args = [arg for arg in get_args(annotation) if arg is not type(None)]
    return args[0] if len(args) == 1 else Any


def _convert(value: Any, annotation: Any, path: str) -> Any:
    """Coerce a raw YAML value to ``annotation``."""
    if _is_optional(annotation):
        if value is None:
            return None
        annotation = _unwrap_optional(annotation)

    if annotation is Any or annotation is None:
        return value

    if is_dataclass(annotation):
        if not isinstance(value, dict):
            raise TypeError(f"'{path}' must be a mapping, got {type(value).__name__}")
        return _build(annotation, value, path)

    origin = get_origin(annotation)
    if origin in (list, List):
        if not isinstance(value, (list, tuple)):
            raise TypeError(f"'{path}' must be a list, got {type(value).__name__}")
        (item_type,) = get_args(annotation) or (Any,)
        return [_convert(item, item_type, f"{path}[{i}]") for i, item in enumerate(value)]
    if origin in (dict, Dict):
        if not isinstance(value, dict):
            raise TypeError(f"'{path}' must be a mapping, got {type(value).__name__}")
        return dict(value)

    if annotation is bool:
        if isinstance(value, bool):
            return value
        raise TypeError(f"'{path}' must be a boolean, got {value!r}")
    if annotation is int:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise TypeError(f"'{path}' must be a number, got {value!r}")
        return int(value)
    if annotation is float:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise TypeError(f"'{path}' must be a number, got {value!r}")
        return float(value)
    if annotation is str:
        return str(value)
    return value


def _build(cls: type, data: Dict[str, Any], path: str = "") -> Any:
    """Instantiate dataclass ``cls`` from a plain mapping, rejecting unknown keys."""
    hints = get_type_hints(cls)
    names = {f.name for f in dataclasses.fields(cls)}
    unknown = set(data) - names
    if unknown:
        where = path or cls.__name__
        raise ValueError(f"unknown key(s) in '{where}': {', '.join(sorted(unknown))}")

    kwargs: Dict[str, Any] = {}
    for f in dataclasses.fields(cls):
        if f.name not in data:
            continue
        child = f"{path}.{f.name}" if path else f.name
        kwargs[f.name] = _convert(data[f.name], hints[f.name], child)
    return cls(**kwargs)


def _plain(value: Any) -> Any:
    if is_dataclass(value):
        return {f.name: _plain(getattr(value, f.name)) for f in dataclasses.fields(value)}
    if isinstance(value, dict):
        return {k: _plain(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_plain(v) for v in value]
    if isinstance(value, Path):
        return str(value)
    return value


# ---------------------------------------------------------------------------
# Sections
# ---------------------------------------------------------------------------
@dataclass
class PathsConfig:
    drive_root: str = "/content/drive/MyDrive"
    project_dir: str = "DhikrDataset"
    dataset_dir: str = "dataset"
    processed_dir: str = "processed"
    noise_dir: str = "noise"
    checkpoints_dir: str = "checkpoints"
    exports_dir: str = "exports"
    logs_dir: str = "logs"
    reports_dir: str = "reports"
    phrases_file: str = "phrases.json"
    unknown_class: str = "unknown"

    @property
    def root(self) -> Path:
        return Path(self.drive_root).expanduser() / self.project_dir

    def resolve(self, value: str) -> Path:
        """Absolute paths win; everything else hangs off ``root``."""
        candidate = Path(value).expanduser()
        return candidate if candidate.is_absolute() else self.root / candidate

    @property
    def dataset_path(self) -> Path:
        return self.resolve(self.dataset_dir)

    @property
    def processed_path(self) -> Path:
        return self.resolve(self.processed_dir)

    @property
    def noise_path(self) -> Path:
        return self.resolve(self.noise_dir)

    @property
    def checkpoints_path(self) -> Path:
        return self.resolve(self.checkpoints_dir)

    @property
    def exports_path(self) -> Path:
        return self.resolve(self.exports_dir)

    @property
    def logs_path(self) -> Path:
        return self.resolve(self.logs_dir)

    @property
    def reports_path(self) -> Path:
        return self.resolve(self.reports_dir)

    @property
    def phrases_path(self) -> Path:
        return self.resolve(self.phrases_file)

    @property
    def manifest_path(self) -> Path:
        return self.processed_path / "manifest.csv"

    @property
    def backup_path(self) -> Path:
        return self.checkpoints_path / "backup"

    def writable_dirs(self) -> List[Path]:
        return [
            self.processed_path,
            self.checkpoints_path,
            self.exports_path,
            self.logs_path,
            self.reports_path,
        ]

    def ensure_dirs(self) -> None:
        for directory in self.writable_dirs():
            directory.mkdir(parents=True, exist_ok=True)


@dataclass
class TrimConfig:
    enabled: bool = True
    top_db: float = 30.0
    pad_ms: float = 50.0


@dataclass
class NormalizeConfig:
    enabled: bool = True
    target_dbfs: float = -20.0
    peak_ceiling: float = 0.99


@dataclass
class AudioConfig:
    sample_rate: int = 16000
    channels: int = 1
    bit_depth: int = 16
    clip_seconds: float = 2.0
    file_extensions: List[str] = field(default_factory=lambda: [".wav"])
    min_duration: float = 0.30
    max_duration: float = 8.0
    silence_dbfs: float = -55.0
    trim: TrimConfig = field(default_factory=TrimConfig)
    normalize: NormalizeConfig = field(default_factory=NormalizeConfig)
    fit_mode: str = "center"

    @property
    def clip_samples(self) -> int:
        return int(round(self.clip_seconds * self.sample_rate))

    def __post_init__(self) -> None:
        if self.channels != 1:
            raise ValueError("only mono (channels: 1) is supported")
        if self.fit_mode not in ("center", "end"):
            raise ValueError("audio.fit_mode must be 'center' or 'end'")


@dataclass
class FeatureConfig:
    n_mels: int = 40
    window_ms: float = 30.0
    hop_ms: float = 10.0
    n_fft: int = 512
    fmin: float = 20.0
    fmax: float = 7600.0
    log_offset: float = 1e-6
    center: bool = False
    normalize: str = "per_example"

    def __post_init__(self) -> None:
        if self.normalize not in ("none", "per_example", "global"):
            raise ValueError("features.normalize must be none|per_example|global")

    def win_length(self, sample_rate: int) -> int:
        return int(round(self.window_ms * sample_rate / 1000.0))

    def hop_length(self, sample_rate: int) -> int:
        return int(round(self.hop_ms * sample_rate / 1000.0))

    def num_frames(self, num_samples: int, sample_rate: int) -> int:
        """Frame count for a clip, matching librosa's framing exactly."""
        hop = self.hop_length(sample_rate)
        if self.center:
            return num_samples // hop + 1
        win = max(self.win_length(sample_rate), self.n_fft)
        if num_samples < win:
            return 0
        return 1 + (num_samples - win) // hop


@dataclass
class BackgroundNoiseConfig:
    enabled: bool = True
    probability: float = 0.5
    min_snr_db: float = 5.0
    max_snr_db: float = 25.0
    synthetic_when_missing: bool = True


@dataclass
class PitchShiftConfig:
    enabled: bool = True
    probability: float = 0.3
    min_semitones: float = -2.0
    max_semitones: float = 2.0


@dataclass
class SpeedPerturbConfig:
    enabled: bool = True
    probability: float = 0.3
    min_rate: float = 0.9
    max_rate: float = 1.1


@dataclass
class GainConfig:
    enabled: bool = True
    probability: float = 0.5
    min_db: float = -6.0
    max_db: float = 6.0


@dataclass
class TimeShiftConfig:
    enabled: bool = True
    probability: float = 0.5
    max_shift_ms: float = 150.0


@dataclass
class SpecAugmentConfig:
    enabled: bool = True
    probability: float = 0.5
    freq_masks: int = 1
    freq_mask_width: int = 6
    time_masks: int = 2
    time_mask_width: int = 12


@dataclass
class AugmentationConfig:
    enabled: bool = True
    background_noise: BackgroundNoiseConfig = field(default_factory=BackgroundNoiseConfig)
    pitch_shift: PitchShiftConfig = field(default_factory=PitchShiftConfig)
    speed_perturb: SpeedPerturbConfig = field(default_factory=SpeedPerturbConfig)
    gain: GainConfig = field(default_factory=GainConfig)
    time_shift: TimeShiftConfig = field(default_factory=TimeShiftConfig)
    spec_augment: SpecAugmentConfig = field(default_factory=SpecAugmentConfig)


@dataclass
class SplitConfig:
    val_ratio: float = 0.15
    test_ratio: float = 0.10
    stratified: bool = True
    group_regex: Optional[str] = None

    def __post_init__(self) -> None:
        if not 0.0 < self.val_ratio < 1.0:
            raise ValueError("split.val_ratio must be in (0, 1)")
        if not 0.0 <= self.test_ratio < 1.0:
            raise ValueError("split.test_ratio must be in [0, 1)")
        if self.val_ratio + self.test_ratio >= 1.0:
            raise ValueError("split.val_ratio + split.test_ratio must stay below 1.0")


@dataclass
class ModelConfig:
    name: str = "ds_cnn"
    stem_filters: int = 64
    stem_kernel: List[int] = field(default_factory=lambda: [10, 4])
    stem_stride: List[int] = field(default_factory=lambda: [2, 2])
    blocks: int = 4
    block_filters: int = 64
    block_kernel: List[int] = field(default_factory=lambda: [3, 3])
    width_multiplier: float = 1.0
    dropout: float = 0.2
    use_bias: bool = False
    activation: str = "relu"
    pool: str = "gap"
    bn_momentum: float = 0.9

    def __post_init__(self) -> None:
        for name in ("stem_kernel", "stem_stride", "block_kernel"):
            value = getattr(self, name)
            if len(value) != 2:
                raise ValueError(f"model.{name} must hold exactly two values (time, freq)")
        if self.pool not in ("gap", "flatten"):
            raise ValueError("model.pool must be 'gap' or 'flatten'")


@dataclass
class EarlyStoppingConfig:
    enabled: bool = True
    monitor: str = "val_accuracy"
    mode: str = "max"
    patience: int = 12
    min_delta: float = 0.001
    restore_best_weights: bool = True


@dataclass
class CheckpointConfig:
    monitor: str = "val_accuracy"
    mode: str = "max"
    save_best_only: bool = True
    save_freq: str = "epoch"


@dataclass
class TensorBoardConfig:
    enabled: bool = True
    histogram_freq: int = 0
    update_freq: str = "epoch"


@dataclass
class TrainingConfig:
    epochs: int = 60
    batch_size: int = 64
    shuffle_buffer: int = 4096
    mixed_precision: bool = True
    class_weights: bool = True
    label_smoothing: float = 0.1
    optimizer: str = "adam"
    learning_rate: float = 1e-3
    weight_decay: float = 1e-4
    momentum: float = 0.9
    gradient_clip_norm: Optional[float] = 1.0
    lr_schedule: str = "cosine"
    warmup_epochs: int = 2
    min_learning_rate: float = 1e-5
    plateau_factor: float = 0.5
    plateau_patience: int = 4
    early_stopping: EarlyStoppingConfig = field(default_factory=EarlyStoppingConfig)
    checkpoint: CheckpointConfig = field(default_factory=CheckpointConfig)
    tensorboard: TensorBoardConfig = field(default_factory=TensorBoardConfig)
    resume: bool = True
    cache: bool = True
    prefetch: bool = True

    def __post_init__(self) -> None:
        if self.optimizer not in ("adam", "adamw", "sgd", "rmsprop"):
            raise ValueError("training.optimizer must be adam|adamw|sgd|rmsprop")
        if self.lr_schedule not in ("cosine", "exponential", "plateau", "none"):
            raise ValueError("training.lr_schedule must be cosine|exponential|plateau|none")


@dataclass
class EvaluationConfig:
    batch_size: int = 128
    split: str = "test"
    roc: bool = True
    top_k_confusions: int = 15
    error_examples: int = 12
    confidence_threshold: float = 0.5

    def __post_init__(self) -> None:
        if self.split not in ("test", "val", "train"):
            raise ValueError("evaluation.split must be test|val|train")


@dataclass
class ExportConfig:
    saved_model: bool = True
    float32: bool = True
    dynamic_range: bool = True
    int8: bool = True
    representative_samples: int = 300
    benchmark_runs: int = 100
    benchmark_warmup: int = 10
    android_latency_factor: float = 3.0
    verify_tolerance: float = 0.05


@dataclass
class Config:
    seed: int = 1337
    paths: PathsConfig = field(default_factory=PathsConfig)
    audio: AudioConfig = field(default_factory=AudioConfig)
    features: FeatureConfig = field(default_factory=FeatureConfig)
    augmentation: AugmentationConfig = field(default_factory=AugmentationConfig)
    split: SplitConfig = field(default_factory=SplitConfig)
    model: ModelConfig = field(default_factory=ModelConfig)
    training: TrainingConfig = field(default_factory=TrainingConfig)
    evaluation: EvaluationConfig = field(default_factory=EvaluationConfig)
    export: ExportConfig = field(default_factory=ExportConfig)

    source_path: Optional[str] = None

    # -- construction -------------------------------------------------------
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Config":
        return _build(cls, data or {})

    @classmethod
    def load(cls, path: Optional[Union[str, Path]] = None) -> "Config":
        resolved = Path(path) if path is not None else default_config_path()
        with open(resolved, "r", encoding="utf-8") as handle:
            data = yaml.safe_load(handle) or {}
        config = cls.from_dict(data)
        config.source_path = str(resolved)
        LOGGER.info("loaded configuration from %s", resolved)
        return config

    # -- derived shapes -----------------------------------------------------
    @property
    def input_shape(self) -> tuple:
        """Model input shape (frames, mel bins, 1)."""
        frames = self.features.num_frames(self.audio.clip_samples, self.audio.sample_rate)
        if frames <= 0:
            raise ValueError(
                "clip_seconds is shorter than one analysis window; "
                "increase audio.clip_seconds or reduce features.n_fft/window_ms"
            )
        return (frames, self.features.n_mels, 1)

    # -- helpers ------------------------------------------------------------
    def to_dict(self) -> Dict[str, Any]:
        """Plain nested dict of the configuration itself.

        ``source_path`` is provenance, not configuration, so it is left out and a
        saved snapshot reloads to an identical object.
        """
        data = _plain(self)
        data.pop("source_path", None)
        return data

    def save(self, path: Union[str, Path]) -> Path:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        with open(destination, "w", encoding="utf-8") as handle:
            yaml.safe_dump(self.to_dict(), handle, allow_unicode=True, sort_keys=False)
        return destination

    def with_overrides(self, overrides: Dict[str, Any]) -> "Config":
        """Return a copy with dotted keys replaced, e.g. ``{"training.epochs": 3}``."""
        data = self.to_dict()
        for dotted, value in overrides.items():
            node = data
            *parents, leaf = dotted.split(".")
            for part in parents:
                if part not in node or not isinstance(node[part], dict):
                    raise KeyError(f"unknown configuration section '{part}' in '{dotted}'")
                node = node[part]
            if leaf not in node:
                raise KeyError(f"unknown configuration key '{dotted}'")
            node[leaf] = value
        data.pop("source_path", None)
        updated = Config.from_dict(data)
        updated.source_path = self.source_path
        return updated

    def summary(self) -> str:
        frames, mels, _ = self.input_shape
        return "\n".join(
            [
                f"project root      : {self.paths.root}",
                f"sample rate       : {self.audio.sample_rate} Hz, mono, PCM{self.audio.bit_depth}",
                f"clip length       : {self.audio.clip_seconds:g} s "
                f"({self.audio.clip_samples} samples)",
                f"features          : {mels} log-mel bins x {frames} frames "
                f"(window {self.features.window_ms:g} ms / hop {self.features.hop_ms:g} ms)",
                f"model             : {self.model.name} "
                f"({self.model.blocks} blocks x {self.model.block_filters} filters)",
                f"training          : {self.training.epochs} epochs, "
                f"batch {self.training.batch_size}, optimizer {self.training.optimizer}",
                f"augmentation      : {'on' if self.augmentation.enabled else 'off'}",
                f"seed              : {self.seed}",
            ]
        )


def package_root() -> Path:
    """Repository folder that holds ``src/``, ``configs/`` and ``notebooks/``."""
    return Path(__file__).resolve().parent.parent


def default_config_path() -> Path:
    """``DHIKR_CONFIG`` if set, otherwise ``configs/config.yaml`` next to ``src/``."""
    override = os.environ.get("DHIKR_CONFIG")
    if override:
        return Path(override).expanduser()
    return package_root() / "configs" / "config.yaml"


def load_config(path: Optional[Union[str, Path]] = None) -> Config:
    """Convenience wrapper used by the notebooks."""
    return Config.load(path)

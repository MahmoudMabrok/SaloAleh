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
    "CalibrationConfig",
    "ClassesConfig",
    "Config",
    "DetectorConfig",
    "EvaluationConfig",
    "ExportConfig",
    "FeatureConfig",
    "ModelConfig",
    "NegativeSamplingConfig",
    "PathsConfig",
    "ReadinessConfig",
    "SmoothingConfig",
    "SplitConfig",
    "StreamingConfig",
    "TargetConfig",
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
    project_dir: str = "Dhikr Speech Dataset"
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
class ClassesConfig:
    """Which phrases the model is trained to tell apart.

    Restricting the vocabulary is the cheapest way to get a working model out of
    a small dataset: the same recordings give more clips per class, and chance
    accuracy rises from 1/10 to 1/4, so validation numbers start meaning
    something much sooner.

    The selection is applied when the dataset is indexed, so it decides the class
    vocabulary, the class indices frozen into the manifest and the width of the
    model's output. **Changing it means re-running preprocessing** and training
    under a fresh run name.
    """

    include_phrases: Optional[List[int]] = None
    include_unknown: bool = True

    def __post_init__(self) -> None:
        if self.include_phrases is None:
            return
        ids = [int(value) for value in self.include_phrases]
        if not ids:
            self.include_phrases = None  # an empty list reads as "no filter"
            return
        if len(set(ids)) != len(ids):
            raise ValueError("classes.include_phrases contains duplicate ids")
        if any(identifier < 1 for identifier in ids):
            raise ValueError("classes.include_phrases ids must be 1 or greater")
        self.include_phrases = sorted(set(ids))

    @property
    def enabled(self) -> bool:
        return self.include_phrases is not None

    @property
    def folders(self) -> Optional[List[str]]:
        """The selected ids as zero-padded folder names, or None for "all"."""
        if self.include_phrases is None:
            return None
        return [f"{identifier:03d}" for identifier in self.include_phrases]

    def selects(self, label: str, unknown_class: str = "unknown") -> bool:
        """Whether a dataset folder belongs to the configured vocabulary."""
        if not self.enabled:
            return True
        if label == unknown_class:
            return self.include_unknown
        return label in (self.folders or [])


@dataclass
class TargetConfig:
    """Single-target (binary phrase spotting) mode - the production architecture.

    Android loads one model per dhikr: the user picks a phrase, the app loads that
    phrase's ``.tflite``, and the model answers one question - *was this exact
    phrase just spoken, completely?* Everything else, including the other dhikr and
    incomplete versions of this one, is a negative.

    ``phrase_id: null`` leaves the pipeline in the legacy multi-class mode, which
    is kept for the ``06 - Experiment`` comparison and for old manifests.
    """

    phrase_id: Optional[int] = None
    # softmax = 2 outputs (target, unknown), sigmoid = 1 output P(target).
    # Both are supported so the two can be compared rather than assumed.
    output_mode: str = "softmax"
    # Other phrase folders (dataset/001, dataset/002, ...) become negatives.
    auto_other_dhikr_negatives: bool = True
    # negatives/hard/<other id>/ folders hold negatives designed to fool *another*
    # target. They are excluded by default: a hard negative for 006
    # ("سبحان الله وبحمده") may well be a recording of 007's full phrase, which
    # would be labelled negative for 007 and poison the model.
    include_other_hard_negatives: bool = False
    negatives_dir: str = "negatives"
    # Per-target overrides, keyed by the zero-padded folder name:
    #   phrase_overrides: {"007": {clip_seconds: 2.5}}
    # Applied by Config.for_target(). Only `clip_seconds` is honoured today.
    phrase_overrides: Dict[str, Dict[str, Any]] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.output_mode not in ("softmax", "sigmoid"):
            raise ValueError("target.output_mode must be 'softmax' or 'sigmoid'")
        if self.phrase_id is not None and int(self.phrase_id) < 1:
            raise ValueError("target.phrase_id must be 1 or greater")

    @property
    def enabled(self) -> bool:
        return self.phrase_id is not None

    @property
    def folder(self) -> Optional[str]:
        """The target's zero-padded dataset folder, e.g. 7 -> ``007``."""
        return None if self.phrase_id is None else f"{int(self.phrase_id):03d}"

    @property
    def num_outputs(self) -> int:
        return 1 if self.output_mode == "sigmoid" else 2

    def overrides_for(self, phrase_id: int) -> Dict[str, Any]:
        """Overrides for one target, accepting ``7``, ``"7"`` or ``"007"`` as the key."""
        keys = (f"{int(phrase_id):03d}", str(int(phrase_id)), int(phrase_id))
        for key in keys:
            value = self.phrase_overrides.get(key)  # type: ignore[arg-type]
            if isinstance(value, dict):
                return dict(value)
        return {}


@dataclass
class NegativeSamplingConfig:
    """How much of the negative pool one run trains on, and which parts of it.

    The shared negative pool grows without bound while positives stay in the
    hundreds, so training on every negative every run would drown the phrase.
    ``ratio`` caps negatives at ``ratio x positives``; ``weights`` decides which
    negatives survive that cut - hard negatives and partial phrases are worth far
    more per clip than another minute of room tone.
    """

    enabled: bool = True
    ratio: float = 2.0
    weights: Dict[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.ratio <= 0.0:
            raise ValueError("negative_sampling.ratio must be positive")
        cleaned: Dict[str, float] = {}
        for key, value in (self.weights or {}).items():
            weight = float(value)
            if weight < 0.0:
                raise ValueError(f"negative_sampling.weights['{key}'] must be >= 0")
            cleaned[str(key)] = weight
        self.weights = cleaned

    def weight_for(self, negative_type: Optional[str], default: float = 1.0) -> float:
        if not negative_type:
            return default
        return float(self.weights.get(negative_type, default))


@dataclass
class SplitConfig:
    val_ratio: float = 0.15
    test_ratio: float = 0.10
    stratified: bool = True
    group_regex: Optional[str] = None
    # Speaker identity. `speaker_regex` is matched against the recording's file
    # name (falling back to its path); a named group `speaker` wins, otherwise
    # group 1, otherwise the whole match. `speaker_metadata` is a JSON mapping of
    # file name (or relative path) -> speaker id and takes precedence over it.
    speaker_regex: Optional[str] = None
    speaker_metadata: Optional[str] = None
    # Keep every recording of one speaker inside one split. Off means the printed
    # numbers are NOT speaker-independent, and the pipeline says so out loud.
    speaker_safe: bool = True
    # Refuse to build a manifest when a speaker straddles two splits, or (with
    # `speaker_safe`) when speaker ids could not be resolved at all.
    fail_on_leakage: bool = True
    require_speaker_ids: bool = False

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
    dropout: float = 0.3
    use_bias: bool = False
    activation: str = "relu"
    pool: str = "gap"
    bn_momentum: float = 0.9
    # TC-ResNet8 only. The mel axis becomes the channel axis and convolution runs
    # along time alone, which is why it is so much cheaper than DS-CNN at the same
    # accuracy on short keywords.
    tc_channels: List[int] = field(default_factory=lambda: [16, 24, 32, 48])
    tc_kernel: int = 9
    tc_stem_kernel: int = 3

    def __post_init__(self) -> None:
        for name in ("stem_kernel", "stem_stride", "block_kernel"):
            value = getattr(self, name)
            if len(value) != 2:
                raise ValueError(f"model.{name} must hold exactly two values (time, freq)")
        if self.pool not in ("gap", "flatten"):
            raise ValueError("model.pool must be 'gap' or 'flatten'")
        if len(self.tc_channels) < 2:
            raise ValueError("model.tc_channels needs a stem channel plus at least one block")


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
    # adamw so that `weight_decay` is actually applied; plain adam ignores it.
    optimizer: str = "adamw"
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
class SmoothingConfig:
    """Optional smoothing of the per-window score before the detector sees it.

    Deliberately weak by default. Smoothing wide enough to bridge the dip between
    two repetitions merges them into one event, which costs a count - the failure
    mode this project cares about least is a missed *dip*, not a missed peak.
    """

    mode: str = "none"          # none | ema | moving_average
    ema_alpha: float = 0.6      # weight of the newest window (1.0 = no smoothing)
    window: int = 3             # moving_average only, in windows

    def __post_init__(self) -> None:
        if self.mode not in ("none", "ema", "moving_average"):
            raise ValueError("streaming.smoothing.mode must be none|ema|moving_average")
        if not 0.0 < self.ema_alpha <= 1.0:
            raise ValueError("streaming.smoothing.ema_alpha must be in (0, 1]")
        if self.window < 1:
            raise ValueError("streaming.smoothing.window must be >= 1")


@dataclass
class DetectorConfig:
    """Hysteresis event detector - one complete utterance must make one event.

    ``activation_threshold`` must sit above ``release_threshold``: the gap is what
    stops a score hovering near one threshold from flickering into a stream of
    events. Re-arming is release-driven (see :mod:`src.streaming`), so a user
    repeating the dhikr quickly is separated by the dip between repetitions rather
    than by waiting out a cooldown.
    """

    activation_threshold: float = 0.70
    release_threshold: float = 0.40
    min_consecutive_hits: int = 2
    min_event_seconds: float = 0.0
    release_windows: int = 2
    cooldown_ms: float = 200.0

    def __post_init__(self) -> None:
        if not 0.0 < self.activation_threshold <= 1.0:
            raise ValueError("detector.activation_threshold must be in (0, 1]")
        if not 0.0 <= self.release_threshold <= 1.0:
            raise ValueError("detector.release_threshold must be in [0, 1]")
        if self.release_threshold > self.activation_threshold:
            raise ValueError(
                "detector.release_threshold must not exceed activation_threshold - "
                "hysteresis needs activation > release"
            )
        if self.min_consecutive_hits < 1:
            raise ValueError("detector.min_consecutive_hits must be >= 1")
        if self.release_windows < 1:
            raise ValueError("detector.release_windows must be >= 1")
        if self.cooldown_ms < 0.0:
            raise ValueError("detector.cooldown_ms must be >= 0")


@dataclass
class StreamingConfig:
    """Continuous-microphone inference: window geometry, smoothing, detector."""

    # null = audio.clip_seconds, i.e. the window the model was trained on. Only
    # set this if the model really was trained on a different length.
    window_seconds: Optional[float] = None
    hop_seconds: float = 0.20
    smoothing: SmoothingConfig = field(default_factory=SmoothingConfig)
    detector: DetectorConfig = field(default_factory=DetectorConfig)
    # Long-form recordings + annotations.json, relative to the project root.
    test_dir: str = "streaming_test"
    annotations_file: str = "annotations.json"
    audio_dirname: str = "audio"
    # A detection counts for a ground-truth event when it falls inside
    # [start - tolerance, end + tolerance].
    match_tolerance_seconds: float = 0.75

    def __post_init__(self) -> None:
        if self.hop_seconds <= 0.0:
            raise ValueError("streaming.hop_seconds must be positive")
        if self.window_seconds is not None and self.window_seconds <= 0.0:
            raise ValueError("streaming.window_seconds must be positive when set")
        if self.match_tolerance_seconds < 0.0:
            raise ValueError("streaming.match_tolerance_seconds must be >= 0")

    def window_for(self, clip_seconds: float) -> float:
        return float(self.window_seconds if self.window_seconds else clip_seconds)


@dataclass
class CalibrationConfig:
    """Threshold sweep driven by a false-activation budget, not by accuracy.

    0.5 is not a threshold, it is a default. The activation threshold that ships
    is the lowest one whose measured false activations per hour stay inside
    ``target_false_activations_per_hour`` - and when no threshold manages that,
    calibration reports a failure instead of picking the most extreme value and
    calling it tuned.
    """

    enabled: bool = True
    min_threshold: float = 0.40
    max_threshold: float = 0.99
    step: float = 0.01
    target_false_activations_per_hour: float = 0.5
    min_event_recall: float = 0.0
    # release_threshold = activation x this, unless release_threshold is set.
    release_ratio: float = 0.6
    release_threshold: Optional[float] = None

    def __post_init__(self) -> None:
        if not 0.0 < self.min_threshold <= self.max_threshold <= 1.0:
            raise ValueError("calibration thresholds must satisfy 0 < min <= max <= 1")
        if self.step <= 0.0:
            raise ValueError("calibration.step must be positive")
        if not 0.0 < self.release_ratio <= 1.0:
            raise ValueError("calibration.release_ratio must be in (0, 1]")
        if self.target_false_activations_per_hour < 0.0:
            raise ValueError("calibration.target_false_activations_per_hour must be >= 0")


@dataclass
class ReadinessConfig:
    """Release criteria for one target. Project policy, not scientific constants.

    Every one of these is a threshold somebody chose; they are here so the choice
    is explicit and reviewable, and so "READY" can never mean "validation accuracy
    looked high".
    """

    max_speaker_leakage: int = 0
    min_positive_speakers: int = 10
    min_positive_clips: int = 100
    # The dataset size at which the numbers stop being provisional.
    recommended_positive_clips: int = 200
    recommended_positive_speakers: int = 20
    min_event_precision: float = 0.95
    min_event_recall: float = 0.90
    max_false_activations_per_hour: float = 0.5
    max_hard_negative_fp_rate: float = 0.05
    max_duplicate_rate: float = 0.05
    # INT8 is rejected as the production model when quantisation costs more than
    # this many extra false activations per hour, or drifts more than this.
    max_int8_fa_per_hour_increase: float = 0.10
    max_int8_probability_drift: float = 0.05


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
    classes: ClassesConfig = field(default_factory=ClassesConfig)
    target: TargetConfig = field(default_factory=TargetConfig)
    audio: AudioConfig = field(default_factory=AudioConfig)
    features: FeatureConfig = field(default_factory=FeatureConfig)
    augmentation: AugmentationConfig = field(default_factory=AugmentationConfig)
    negative_sampling: NegativeSamplingConfig = field(default_factory=NegativeSamplingConfig)
    split: SplitConfig = field(default_factory=SplitConfig)
    model: ModelConfig = field(default_factory=ModelConfig)
    model_presets: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    training: TrainingConfig = field(default_factory=TrainingConfig)
    evaluation: EvaluationConfig = field(default_factory=EvaluationConfig)
    streaming: StreamingConfig = field(default_factory=StreamingConfig)
    calibration: CalibrationConfig = field(default_factory=CalibrationConfig)
    readiness: ReadinessConfig = field(default_factory=ReadinessConfig)
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

    # -- single-target mode -------------------------------------------------
    def for_target(self, phrase_id: Optional[int]) -> "Config":
        """Copy of this config bound to one target phrase.

        Applies ``target.phrase_overrides[<id>]`` on top - today that is
        ``clip_seconds``, because a two-word dhikr and a seven-word one do not
        belong in the same window (see ``README`` / requirement 15). Passing
        ``None`` returns a copy in legacy multi-class mode.
        """
        if phrase_id is None:
            return self.with_overrides({"target.phrase_id": None})

        identifier = int(phrase_id)
        overrides: Dict[str, Any] = {"target.phrase_id": identifier}
        target_overrides = self.target.overrides_for(identifier)
        unknown = set(target_overrides) - {"clip_seconds"}
        if unknown:
            raise KeyError(
                f"target.phrase_overrides['{identifier:03d}'] has unsupported key(s): "
                f"{', '.join(sorted(unknown))} (only 'clip_seconds' is honoured)"
            )
        if "clip_seconds" in target_overrides:
            overrides["audio.clip_seconds"] = float(target_overrides["clip_seconds"])
        return self.with_overrides(overrides)

    @property
    def target_folder(self) -> Optional[str]:
        return self.target.folder

    @property
    def clip_tag(self) -> str:
        """Identifies the *audio geometry* a processed clip was written with.

        Conditioned clips are fitted to ``clip_seconds`` at ``sample_rate``, so two
        targets with the same geometry can share the same cached files and two with
        different geometry must not. Used as the cache directory name, which is
        what makes the shared negative pool preprocessed once rather than once per
        target (requirement 32).
        """
        return f"{self.audio.sample_rate}hz_{self.audio.clip_seconds:g}s"

    def clip_cache_path(self) -> Path:
        """Where conditioned clips for this geometry live."""
        return self.paths.processed_path / "audio" / self.clip_tag

    def target_manifest_path(self, phrase_id: Optional[int] = None) -> Path:
        """Manifest for one target. Split and labels are target-specific."""
        identifier = self.target.phrase_id if phrase_id is None else int(phrase_id)
        if identifier is None:
            return self.paths.manifest_path
        return self.paths.processed_path / "manifests" / f"target_{int(identifier):03d}.csv"

    def target_export_path(self, phrase_id: Optional[int] = None) -> Path:
        """``exports/<target id>/`` - one independent model per dhikr."""
        identifier = self.target.phrase_id if phrase_id is None else int(phrase_id)
        if identifier is None:
            return self.paths.exports_path
        return self.paths.exports_path / f"{int(identifier):03d}"

    @property
    def streaming_path(self) -> Path:
        return self.paths.resolve(self.streaming.test_dir)

    def resolved_model(self) -> ModelConfig:
        """``model`` with ``model_presets[model.name]`` applied on top.

        Presets keep the architecture comparison honest: every architecture reads
        the same ``model`` block and only overrides the handful of values that make
        it that architecture, so a comparison run cannot accidentally also change
        dropout or BatchNorm momentum.
        """
        preset = self.model_presets.get(self.model.name)
        if not preset:
            return self.model
        known = {f.name for f in dataclasses.fields(ModelConfig)}
        unknown = set(preset) - known
        if unknown:
            raise KeyError(
                f"model_presets['{self.model.name}'] has unknown key(s): "
                f"{', '.join(sorted(unknown))}"
            )
        data = {f.name: getattr(self.model, f.name) for f in dataclasses.fields(ModelConfig)}
        data.update(preset)
        data["name"] = self.model.name
        return ModelConfig(**data)

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
        if self.target.enabled:
            vocabulary = (
                f"single target {self.target.folder} vs everything else "
                f"({self.target.output_mode}, {self.target.num_outputs} output"
                f"{'s' if self.target.num_outputs > 1 else ''})"
            )
        elif self.classes.enabled:
            vocabulary = f"multi-class: phrases {self.classes.include_phrases} only"
        else:
            vocabulary = "multi-class: every folder in the dataset"
        return "\n".join(
            [
                f"project root      : {self.paths.root}",
                f"classes           : {vocabulary}",
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

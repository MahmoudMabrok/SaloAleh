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
    "ClassesConfig",
    "Config",
    "DetectorConfig",
    "EvaluationConfig",
    "ExportConfig",
    "FeatureConfig",
    "ModelConfig",
    "PathsConfig",
    "QualityConfig",
    "ReadinessConfig",
    "SmoothingConfig",
    "SpeakerConfig",
    "SplitConfig",
    "StreamingConfig",
    "TrainingConfig",
    "available_presets",
    "load_config",
    "preset_path",
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


def _deep_merge(base: Dict[str, Any], overlay: Dict[str, Any]) -> Dict[str, Any]:
    """``overlay`` wins, merging nested mappings rather than replacing them."""
    merged = dict(base)
    for key, value in (overlay or {}).items():
        existing = merged.get(key)
        if isinstance(existing, dict) and isinstance(value, dict):
            merged[key] = _deep_merge(existing, value)
        else:
            merged[key] = value
    return merged


def _flatten_mapping(data: Dict[str, Any], prefix: str = "") -> Dict[str, Any]:
    """``{"training": {"epochs": 3}}`` -> ``{"training.epochs": 3}``."""
    flat: Dict[str, Any] = {}
    for key, value in (data or {}).items():
        dotted = f"{prefix}{key}"
        if isinstance(value, dict):
            flat.update(_flatten_mapping(value, f"{dotted}."))
        else:
            flat[dotted] = value
    return flat


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
    streaming_dir: str = "streaming_test"
    phrases_file: str = "phrases.json"
    unknown_class: str = "unknown"
    speakers_file: str = "speakers.csv"

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
    def speakers_path(self) -> Path:
        """Optional ``speakers.csv`` mapping recordings to speaker ids."""
        return self.resolve(self.speakers_file)

    @property
    def streaming_path(self) -> Path:
        """Long-form recordings + ``annotations.json`` for streaming evaluation."""
        return self.resolve(self.streaming_dir)

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
class ReverbConfig:
    """Mild synthetic room reverberation.

    A phone held at arm's length in a tiled room is a different signal from the
    close-mic recordings volunteers usually send, and reverberation is the part
    of that difference augmentation can imitate honestly. The impulse response is
    generated (exponentially decaying noise), so this costs no new dependency and
    no impulse-response corpus. Keep the decay short: long tails smear the
    consonants that separate the nested phrases from one another.
    """

    enabled: bool = False
    probability: float = 0.25
    min_decay_ms: float = 80.0
    max_decay_ms: float = 300.0
    # Wet/dry mix. Above ~0.5 the phrase stops sounding like a phone recording.
    min_wet: float = 0.1
    max_wet: float = 0.35


@dataclass
class AugmentationConfig:
    enabled: bool = True
    background_noise: BackgroundNoiseConfig = field(default_factory=BackgroundNoiseConfig)
    pitch_shift: PitchShiftConfig = field(default_factory=PitchShiftConfig)
    speed_perturb: SpeedPerturbConfig = field(default_factory=SpeedPerturbConfig)
    gain: GainConfig = field(default_factory=GainConfig)
    time_shift: TimeShiftConfig = field(default_factory=TimeShiftConfig)
    reverb: ReverbConfig = field(default_factory=ReverbConfig)
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
    # Category names expected under ``dataset/unknown/``. Every file below
    # ``unknown/`` trains as the single ``unknown`` output class whatever its
    # subfolder; the subfolder is kept in the manifest as ``negative_type`` so the
    # evaluation can report false positives per category. Names outside this list
    # are still indexed - the list only decides what counts as "expected" in the
    # dataset report.
    negative_types: List[str] = field(
        default_factory=lambda: [
            "normal_speech",
            "hard_negative",
            "partial_phrase",
            "other_dhikr",
            "noise",
            "unknown",
        ]
    )
    # Off by default and deliberately so: splitting the negatives into their own
    # output classes changes the model's output width and needs far more data per
    # category than a first dataset has.
    split_negative_classes: bool = False

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
class SpeakerConfig:
    """How a recording is traced back to the person who spoke it.

    Speaker identity is the single most important thing this pipeline can know
    about the dataset. Without it the split can put the same voice in train and
    test, and the reported accuracy answers "can the model recognise this
    speaker's recordings again", not "does it work for a new user".
    """

    # auto     - metadata file if present, else regex if it matches most files,
    #            else parent-folder ids, else none (with a loud warning)
    # metadata - speakers.csv only; a file with no row is unassigned
    # filename - regex against the file name only
    # parent   - the recording's parent folder inside the class folder
    # none     - no speaker information at all (evaluation is NOT speaker-independent)
    source: str = "auto"
    # Must contain a group; a named ``(?P<speaker>...)`` group wins, otherwise
    # group 1, otherwise the whole match.
    regex: str = r"^(?P<speaker>[A-Za-z0-9\-]+?)[_\-]"
    metadata_file: Optional[str] = None  # defaults to paths.speakers_file
    # When speakers are known, a speaker appearing in two splits is a hard error
    # rather than a warning: the numbers it produces are not the numbers anyone
    # thinks they are reading.
    require_disjoint: bool = True
    # Below this the split is speaker-safe but still not speaker-*diverse*; the
    # dataset report says so instead of pretending the test set generalises.
    min_speakers: int = 10
    # Fraction of files the regex must match before `source: auto` trusts it.
    auto_match_ratio: float = 0.6

    def __post_init__(self) -> None:
        if self.source not in ("auto", "metadata", "filename", "parent", "none"):
            raise ValueError(
                "split.speaker.source must be auto|metadata|filename|parent|none"
            )
        if not 0.0 < self.auto_match_ratio <= 1.0:
            raise ValueError("split.speaker.auto_match_ratio must be in (0, 1]")


@dataclass
class SplitConfig:
    val_ratio: float = 0.15
    test_ratio: float = 0.10
    stratified: bool = True
    # Legacy: a bare regex applied to the file name. Still honoured - it is read
    # as `speaker.regex` with `speaker.source: filename` when the speaker section
    # is left at its defaults - so existing configs keep working unchanged.
    group_regex: Optional[str] = None
    speaker: SpeakerConfig = field(default_factory=SpeakerConfig)

    def __post_init__(self) -> None:
        if not 0.0 < self.val_ratio < 1.0:
            raise ValueError("split.val_ratio must be in (0, 1)")
        if not 0.0 <= self.test_ratio < 1.0:
            raise ValueError("split.test_ratio must be in [0, 1)")
        if self.val_ratio + self.test_ratio >= 1.0:
            raise ValueError("split.val_ratio + split.test_ratio must stay below 1.0")

    @property
    def train_ratio(self) -> float:
        return 1.0 - self.val_ratio - self.test_ratio

    def resolved_speaker(self) -> SpeakerConfig:
        """The speaker settings actually in force, folding in ``group_regex``."""
        speaker = self.speaker
        if self.group_regex and speaker.source == "auto":
            return dataclasses.replace(
                speaker, source="filename", regex=self.group_regex
            )
        return speaker


@dataclass
class QualityConfig:
    """Dataset-size recommendations. Advisory - nothing here blocks a run.

    Prototype scale (what the pipeline is verified with) is ~100 recordings and
    ~10 speakers per target phrase. A first model anyone should trust in the app
    needs several times that, and the numbers below are what the dataset report
    measures against.
    """

    prototype_recordings_per_class: int = 100
    prototype_speakers: int = 10
    recommended_recordings_per_class: int = 200
    recommended_speakers: int = 20
    # Unknown clips as a fraction of the target clips. Negatives are what keep the
    # false-activation rate down, so a model with almost none cannot be measured
    # for the thing that matters.
    min_unknown_ratio: float = 0.5
    # largest class / smallest class before the imbalance is called out
    max_class_imbalance: float = 3.0
    # Minimum clips per named negative category before its false-positive rate is
    # worth reporting as a number rather than an anecdote.
    min_negative_category: int = 20


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
    # Also log macro F1 (and val_macro_f1), which unlike accuracy drops when a
    # class is being ignored - the usual failure on an unbalanced split. Reported
    # by default; set checkpoint.monitor to `val_macro_f1` to select on it.
    macro_f1: bool = True
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


# ---------------------------------------------------------------------------
# Streaming - continuous listening, event counting and calibration
# ---------------------------------------------------------------------------
@dataclass
class SmoothingConfig:
    """Optional temporal smoothing of the per-window probabilities.

    Smoothing trades reaction time for stability. Too much of it and two dhikr
    said back to back merge into one event, which is exactly the failure the
    counter must not have - so this is deliberately mild and easy to switch off.
    """

    enabled: bool = True
    method: str = "moving_average"  # moving_average | ema
    window: int = 3                 # moving_average: windows averaged
    alpha: float = 0.5              # ema: weight of the newest window

    def __post_init__(self) -> None:
        if self.method not in ("moving_average", "ema"):
            raise ValueError("streaming.smoothing.method must be moving_average|ema")
        if self.window < 1:
            raise ValueError("streaming.smoothing.window must be >= 1")
        if not 0.0 < self.alpha <= 1.0:
            raise ValueError("streaming.smoothing.alpha must be in (0, 1]")


@dataclass
class DetectorConfig:
    """Event detector: how window probabilities become counted dhikr.

    None of these numbers is a good default until it has been calibrated against
    real streaming recordings (section 06 of the notebook). They are a starting
    point chosen to be conservative, because a false count is worse than a missed
    one.
    """

    # Activation: a window at or above this can start / sustain a candidate.
    confidence_threshold: float = 0.80
    # Hysteresis. Below this the event is releasing; between the two the event
    # neither starts nor ends, which is what stops a confidence wobble around a
    # single threshold from producing two counts for one utterance.
    release_threshold: float = 0.40
    # Windows above the activation threshold before an event is confirmed.
    min_consecutive_hits: int = 2
    # Seconds an event must last before it counts. 0 disables the check.
    min_event_duration: float = 0.0
    # Windows below the release threshold before an active event is closed.
    release_windows: int = 2
    # Dead time after an event of the same class closes. Per class, so two
    # different phrases spoken back to back still count as two.
    cooldown_ms: float = 700.0
    # Classes that never produce an event. `unknown` is the model's way of saying
    # "not a dhikr", so it is never counted.
    ignore_labels: List[str] = field(default_factory=lambda: ["unknown"])
    # Per-class activation thresholds, e.g. {"006": 0.82, "007": 0.90}. A class
    # with no entry uses `confidence_threshold`.
    per_class_thresholds: Dict[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not 0.0 < self.confidence_threshold <= 1.0:
            raise ValueError("streaming.detector.confidence_threshold must be in (0, 1]")
        if not 0.0 <= self.release_threshold <= 1.0:
            raise ValueError("streaming.detector.release_threshold must be in [0, 1]")
        if self.release_threshold > self.confidence_threshold:
            raise ValueError(
                "streaming.detector.release_threshold must not exceed "
                "confidence_threshold - hysteresis needs activation >= release"
            )
        if self.min_consecutive_hits < 1:
            raise ValueError("streaming.detector.min_consecutive_hits must be >= 1")
        if self.release_windows < 1:
            raise ValueError("streaming.detector.release_windows must be >= 1")

    def threshold_for(self, label: str) -> float:
        """Activation threshold for one class."""
        return float(self.per_class_thresholds.get(label, self.confidence_threshold))


@dataclass
class EventMatchConfig:
    """How a detected event is matched to an annotated one.

    Annotating a long recording by hand is not millisecond-accurate, so matching
    is deliberately tolerant: an overlap, or a trigger within
    ``tolerance_seconds`` of the annotated span, is a hit.
    """

    tolerance_seconds: float = 1.0
    min_overlap_seconds: float = 0.0
    # Only events of the same class match. Off means any target event matches any
    # annotated event, which measures "did it fire" rather than "did it count the
    # right phrase" - useful as a diagnostic, wrong as a headline.
    require_label_match: bool = True


@dataclass
class CalibrationConfig:
    """Threshold sweep and the policy that picks the operating point."""

    min_threshold: float = 0.40
    max_threshold: float = 0.95
    step: float = 0.05
    # Pick the lowest threshold whose false-activation rate stays inside the
    # budget, maximising recall. Anything else optimises the wrong thing: recall
    # alone drives the threshold to 0, accuracy alone hides the false counts.
    policy: str = "min_threshold_within_budget"
    per_class: bool = True

    def __post_init__(self) -> None:
        if self.policy not in ("min_threshold_within_budget", "max_f1"):
            raise ValueError(
                "streaming.calibration.policy must be "
                "min_threshold_within_budget|max_f1"
            )
        if self.step <= 0.0:
            raise ValueError("streaming.calibration.step must be positive")
        if not 0.0 < self.min_threshold <= self.max_threshold <= 1.0:
            raise ValueError(
                "streaming.calibration thresholds must satisfy 0 < min <= max <= 1"
            )

    def thresholds(self) -> List[float]:
        values: List[float] = []
        current = self.min_threshold
        while current <= self.max_threshold + 1e-9:
            values.append(round(current, 6))
            current += self.step
        return values


@dataclass
class StreamingConfig:
    """Continuous-listening configuration - the deployed shape of the model."""

    # Defaults to audio.clip_seconds: the window must be the length the model was
    # trained on, and a mismatch is a silent accuracy loss rather than an error.
    window_seconds: Optional[float] = None
    hop_seconds: float = 0.25
    # The release-critical number: how often the counter fires when nobody said a
    # dhikr. Calibration will not pick a threshold that breaks this budget.
    target_false_activations_per_hour: float = 0.5
    audio_subdir: str = "audio"
    annotations_file: str = "annotations.json"
    smoothing: SmoothingConfig = field(default_factory=SmoothingConfig)
    detector: DetectorConfig = field(default_factory=DetectorConfig)
    matching: EventMatchConfig = field(default_factory=EventMatchConfig)
    calibration: CalibrationConfig = field(default_factory=CalibrationConfig)

    def __post_init__(self) -> None:
        if self.hop_seconds <= 0.0:
            raise ValueError("streaming.hop_seconds must be positive")
        if self.window_seconds is not None and self.window_seconds <= 0.0:
            raise ValueError("streaming.window_seconds must be positive")
        if self.target_false_activations_per_hour < 0.0:
            raise ValueError(
                "streaming.target_false_activations_per_hour must not be negative"
            )

    def window_for(self, audio: "AudioConfig") -> float:
        """The window length in force, defaulting to the model's clip length."""
        return float(self.window_seconds or audio.clip_seconds)


@dataclass
class ReadinessConfig:
    """Thresholds the production-readiness report checks against.

    Every one of them is configurable, and the report prints the value it used
    next to the measurement, so "READY" always says what it means by ready.
    """

    enabled: bool = True
    max_speaker_leaks: int = 0
    min_speakers: int = 10
    min_clips_per_target: int = 100
    min_event_recall: float = 0.90
    min_event_precision: float = 0.95
    max_false_activations_per_hour: float = 0.5
    max_hard_negative_fp_rate: float = 0.02
    # Clip accuracy the quantised model may lose against float32.
    max_tflite_accuracy_drop: float = 0.02
    # Extra false activations per hour INT8 may add before it is rejected.
    max_tflite_extra_false_activations_per_hour: float = 0.1
    min_streaming_minutes: float = 20.0


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
    audio: AudioConfig = field(default_factory=AudioConfig)
    features: FeatureConfig = field(default_factory=FeatureConfig)
    augmentation: AugmentationConfig = field(default_factory=AugmentationConfig)
    split: SplitConfig = field(default_factory=SplitConfig)
    quality: QualityConfig = field(default_factory=QualityConfig)
    model: ModelConfig = field(default_factory=ModelConfig)
    training: TrainingConfig = field(default_factory=TrainingConfig)
    evaluation: EvaluationConfig = field(default_factory=EvaluationConfig)
    streaming: StreamingConfig = field(default_factory=StreamingConfig)
    readiness: ReadinessConfig = field(default_factory=ReadinessConfig)
    export: ExportConfig = field(default_factory=ExportConfig)

    source_path: Optional[str] = None
    preset: Optional[str] = None

    # -- construction -------------------------------------------------------
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Config":
        return _build(cls, data or {})

    @classmethod
    def load(
        cls,
        path: Optional[Union[str, Path]] = None,
        preset: Optional[str] = None,
    ) -> "Config":
        """Read ``config.yaml``, optionally with a named preset applied on top.

        ``configs/config.yaml`` is where settings live; a preset
        (``configs/presets/<name>.yaml``) is an opt-in overlay of explicit values
        for a documented situation - a tiny prototype dataset, a large one. It is
        applied only when asked for (argument or ``DHIKR_PRESET``), every key it
        changes is logged, and the result is a plain resolved config: what the
        modules read and what ``Trainer`` saves next to the checkpoint. No preset
        indirection survives into the run.
        """
        resolved = Path(path) if path is not None else default_config_path()
        with open(resolved, "r", encoding="utf-8") as handle:
            data = yaml.safe_load(handle) or {}

        chosen = preset if preset is not None else os.environ.get("DHIKR_PRESET") or None
        if chosen:
            overlay = _read_preset(chosen)
            for dotted, value in sorted(_flatten_mapping(overlay).items()):
                LOGGER.info("preset '%s' sets %s = %r", chosen, dotted, value)
            data = _deep_merge(data, overlay)

        config = cls.from_dict(data)
        config.source_path = str(resolved)
        config.preset = chosen
        LOGGER.info(
            "loaded configuration from %s%s",
            resolved,
            f" (preset '{chosen}')" if chosen else "",
        )
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
        updated.preset = self.preset
        return updated

    def summary(self) -> str:
        frames, mels, _ = self.input_shape
        return "\n".join(
            [
                f"project root      : {self.paths.root}",
                f"classes           : "
                + (
                    f"phrases {self.classes.include_phrases} only"
                    if self.classes.enabled
                    else "every folder in the dataset"
                ),
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
                f"speakers          : split.speaker.source = "
                f"{self.split.resolved_speaker().source}",
                f"streaming         : window "
                f"{self.streaming.window_for(self.audio):g} s / hop "
                f"{self.streaming.hop_seconds:g} s, activation "
                f"{self.streaming.detector.confidence_threshold:.2f} / release "
                f"{self.streaming.detector.release_threshold:.2f}, "
                f"FA budget {self.streaming.target_false_activations_per_hour:g}/h",
                f"seed              : {self.seed}",
            ]
            + ([f"preset            : {self.preset}"] if self.preset else [])
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


def presets_dir() -> Path:
    """``configs/presets/`` - the documented experiment overlays."""
    return package_root() / "configs" / "presets"


def available_presets() -> List[str]:
    directory = presets_dir()
    if not directory.is_dir():
        return []
    return sorted(path.stem for path in directory.glob("*.yaml"))


def preset_path(name: str) -> Path:
    """Path of a preset by name, or by explicit path when one is given."""
    candidate = Path(name).expanduser()
    if candidate.suffix in (".yaml", ".yml") or candidate.is_absolute():
        return candidate
    return presets_dir() / f"{name}.yaml"


def _read_preset(name: str) -> Dict[str, Any]:
    path = preset_path(name)
    if not path.is_file():
        known = ", ".join(available_presets()) or "none"
        raise FileNotFoundError(
            f"preset '{name}' not found at {path} (available: {known})"
        )
    with open(path, "r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle) or {}
    if not isinstance(data, dict):
        raise ValueError(f"preset '{name}' must be a mapping of config keys")
    # Provenance and documentation only; never a config key.
    for key in ("name", "description"):
        data.pop(key, None)
    return data


def load_config(
    path: Optional[Union[str, Path]] = None, preset: Optional[str] = None
) -> Config:
    """Convenience wrapper used by the notebooks."""
    return Config.load(path, preset=preset)

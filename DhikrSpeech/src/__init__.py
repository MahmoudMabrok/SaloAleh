"""DhikrSpeech - offline Arabic dhikr phrase spotting.

Submodules are resolved lazily so that importing ``src`` stays cheap: notebooks
01 and 02 never pay for the TensorFlow import, which only ``models``,
``trainer`` and ``export`` actually need.

    from src import load_config, scan_dataset      # light, no TensorFlow
    from src.trainer import Trainer                # pulls TensorFlow in
"""

from __future__ import annotations

import importlib
from typing import TYPE_CHECKING, Any

__version__ = "1.0.0"

_EXPORTS = {
    # module -> symbols re-exported at package level
    "config": ("Config", "load_config", "default_config_path", "package_root"),
    "audio": ("load_audio", "prepare_clip", "probe_audio", "read_wav", "write_wav"),
    "features": ("FeatureStats", "LogMelExtractor", "compute_global_stats"),
    "augmentation": ("NoiseBank", "WaveformAugmentor", "spec_augment"),
    "dataset": (
        "DatasetIndex",
        "ManifestRecord",
        "assign_splits",
        "class_names_from_manifest",
        "compute_class_weights",
        "filter_split",
        "load_manifest",
        "load_phrases",
        "make_tf_dataset",
        "preprocess_dataset",
        "save_manifest",
        "scan_dataset",
        "validate_dataset",
    ),
    "metrics": (
        "DetectorEvaluation",
        "EvaluationResult",
        "evaluate_detector",
        "evaluate_model",
        "wilson_interval",
    ),
    "targets": (
        "TargetDatasetReport",
        "build_target_report",
        "sample_negatives",
        "scan_target_dataset",
    ),
    "splitting": (
        "SpeakerIsolationReport",
        "assign_speaker_splits",
        "resolve_speakers",
        "speaker_stats",
        "verify_speaker_isolation",
    ),
    "streaming": (
        "EventDetector",
        "ScoreTimeline",
        "StreamingDetector",
        "StreamingFrontend",
        "detect_events",
        "target_score",
    ),
    "streaming_eval": (
        "EventMetrics",
        "StreamingEvaluation",
        "calibrate_threshold",
        "evaluate_timelines",
        "load_annotations",
        "score_clips",
    ),
    "readiness": ("ReadinessReport", "assess_readiness"),
    "parity": ("compare_parity", "write_parity_assets"),
    "target_export": ("build_target_metadata", "compare_variants"),
    "pipeline": ("TargetPreparation", "TargetRun", "prepare_target", "training_records"),
    "experiments": ("ComparisonReport", "compare_one_vs_rest", "train_one_vs_rest"),
    "models": ("build_model", "model_summary_text"),
    "trainer": ("Trainer", "set_global_seed"),
    "export": (
        "benchmark_tflite",
        "convert_tflite",
        "export_all",
        "export_saved_model",
        "to_float32_model",
        "verify_tflite",
    ),
}

_SYMBOL_TO_MODULE = {
    symbol: module for module, symbols in _EXPORTS.items() for symbol in symbols
}

__all__ = sorted(_SYMBOL_TO_MODULE) + sorted(_EXPORTS) + ["__version__"]

if TYPE_CHECKING:  # pragma: no cover - import-time hints for editors only
    from . import (
        audio,
        augmentation,
        config,
        dataset,
        experiments,
        export,
        features,
        metrics,
        models,
        parity,
        pipeline,
        readiness,
        splitting,
        streaming,
        streaming_eval,
        target_export,
        targets,
        trainer,
    )
    from .config import Config, default_config_path, load_config, package_root


def __getattr__(name: str) -> Any:
    if name in _EXPORTS:
        return importlib.import_module(f".{name}", __name__)
    module_name = _SYMBOL_TO_MODULE.get(name)
    if module_name is None:
        raise AttributeError(f"module 'src' has no attribute '{name}'")
    return getattr(importlib.import_module(f".{module_name}", __name__), name)


def __dir__() -> list:
    return sorted(set(__all__) | set(globals()))

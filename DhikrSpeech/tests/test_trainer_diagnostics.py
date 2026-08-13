"""Tests for the training diagnostics and the resume guard.

Both are read-only commentary on a finished (or about to start) run, and both
fail in the same way when they are wrong: silently. A diagnosis that does not
fire costs the hour it takes to notice by hand; a resume guard that does not
fire costs a whole training run that quietly used the previous config. Neither
shows up as an exception, so they are worth pinning down here.

``src.trainer`` imports TensorFlow at module scope for the parts of it that
build and fit models. Nothing under test here touches those, so when TensorFlow
is absent the smallest possible stub stands in and the suite keeps working
without it. When it is installed the real one is used, so this file can never
shadow it for a test that does need it.
"""

from __future__ import annotations

import importlib.util
import logging
import sys
import types
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def _install_tensorflow_stubs() -> None:
    """Register the smallest tensorflow/keras that lets src.trainer import."""
    if importlib.util.find_spec("tensorflow") is not None:
        return  # the real thing is available - leave it alone

    if "tensorflow" not in sys.modules:
        tensorflow = types.ModuleType("tensorflow")
        tensorflow.config = types.SimpleNamespace(list_physical_devices=lambda kind=None: [])
        tensorflow.random = types.SimpleNamespace(set_seed=lambda seed: None)
        sys.modules["tensorflow"] = tensorflow

    if "keras" not in sys.modules:
        keras = types.ModuleType("keras")
        keras.saving = types.SimpleNamespace(
            register_keras_serializable=lambda package=None: (lambda cls: cls)
        )
        keras.optimizers = types.SimpleNamespace(
            schedules=types.SimpleNamespace(LearningRateSchedule=type("LRSchedule", (), {}))
        )

        class _Loss:
            def __init__(self, name=None, **kwargs):
                self.name = name

        keras.losses = types.SimpleNamespace(Loss=_Loss)
        keras.utils = types.SimpleNamespace(set_random_seed=lambda seed: None)
        keras.mixed_precision = types.SimpleNamespace(set_global_policy=lambda policy: None)
        keras.Model = type("Model", (), {})

        class _Metric:
            def __init__(self, name=None, **kwargs):
                self.name = name

            def add_weight(self, name=None, initializer=None, **kwargs):
                return 0.0

        # Deliberately without F1Score: this stands in for a Keras build that
        # predates it, which is the path `build_metrics` has to degrade over.
        keras.metrics = types.SimpleNamespace(
            Metric=_Metric,
            BinaryAccuracy=_Metric,
            Precision=_Metric,
            Recall=_Metric,
            AUC=_Metric,
            SparseCategoricalAccuracy=_Metric,
            SparseTopKCategoricalAccuracy=_Metric,
        )
        sys.modules["keras"] = keras


_install_tensorflow_stubs()

from src.config import Config, TrainingConfig  # noqa: E402
from src.trainer import (  # noqa: E402
    CHANCE_TOLERANCE,
    COARSE_VAL_SPLIT,
    OVERFIT_GAP,
    SparseMacroF1,
    Trainer,
    TrainingArtifacts,
    build_metrics,
    config_differences,
)


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def artifacts(
    accuracy,
    val_accuracy,
    *,
    num_classes: int = 3,
    val_size=None,
    resumed: bool = False,
    positive_rate=None,
    checkpoint_monitor: str = "val_accuracy",
    checkpoint_mode: str = "max",
    extra_history=None,
) -> TrainingArtifacts:
    placeholder = Path("checkpoints/ds_cnn/best_model.keras")
    history = {}
    if accuracy is not None:
        history["accuracy"] = [float(value) for value in accuracy]
    if val_accuracy is not None:
        history["val_accuracy"] = [float(value) for value in val_accuracy]
    history.update(extra_history or {})
    return TrainingArtifacts(
        run_name="ds_cnn",
        history=history,
        best_model_path=placeholder,
        last_weights_path=placeholder,
        history_path=placeholder,
        tensorboard_dir=placeholder,
        csv_log_path=placeholder,
        epochs_completed=len(next(iter(history.values()), [])),
        num_classes=num_classes,
        resumed=resumed,
        val_size=val_size,
        positive_rate=positive_rate,
        checkpoint_monitor=checkpoint_monitor,
        checkpoint_mode=checkpoint_mode,
    )


def ramp(start: float, stop: float, count: int):
    if count == 1:
        return [stop]
    step = (stop - start) / (count - 1)
    return [start + step * index for index in range(count)]


def overfit_run(val_size=20) -> TrainingArtifacts:
    """The reported run: train to 1.0 by epoch 32, val peaking at 0.80 at epoch 10."""
    accuracy = ramp(0.34, 0.87, 10) + ramp(0.88, 0.99, 22) + [1.0] * 18
    val_accuracy = ramp(0.35, 0.80, 10) + [0.75, 0.70] * 17 + [0.70] * 6
    assert len(accuracy) == 50 and len(val_accuracy) == 50
    return artifacts(accuracy, val_accuracy, val_size=val_size)


def note_matching(notes, needle: str):
    found = [note for note in notes if needle in note]
    assert len(found) == 1, f"expected exactly one note containing {needle!r}, got {notes}"
    return found[0]


# ---------------------------------------------------------------------------
# value_at / val_resolution
# ---------------------------------------------------------------------------
def test_value_at_reads_a_single_epoch():
    result = artifacts([0.1, 0.2, 0.3], [0.4, 0.5, 0.6])
    assert result.value_at("accuracy", 1) == pytest.approx(0.1)
    assert result.value_at("val_accuracy", 3) == pytest.approx(0.6)


@pytest.mark.parametrize("epoch", [0, -1, 4, 99])
def test_value_at_rejects_epochs_outside_the_history(epoch):
    assert artifacts([0.1, 0.2, 0.3], [0.4, 0.5, 0.6]).value_at("accuracy", epoch) is None


def test_value_at_unknown_metric_is_none():
    assert artifacts([0.1], [0.2]).value_at("f1", 1) is None


def test_val_resolution_is_one_clip():
    assert artifacts([0.9], [0.8], val_size=20).val_resolution == pytest.approx(0.05)
    assert artifacts([0.9], [0.8], val_size=None).val_resolution is None
    assert artifacts([0.9], [0.8], val_size=0).val_resolution is None


# ---------------------------------------------------------------------------
# diagnose - the overfitting branch
# ---------------------------------------------------------------------------
def test_overfitting_run_reports_gap_early_peak_and_resolution():
    notes = overfit_run().diagnose()
    assert len(notes) == 3

    gap = note_matching(notes, "overfitting:")
    assert "1.00" in gap and "0.80" in gap
    assert "20 points" in gap
    # The pair that shipped, not the max of each curve separately.
    assert "val_accuracy checkpoint is epoch 10" in gap

    peak = note_matching(notes, "validation peaked")
    assert "epoch 10 of 50" in peak

    coarse = note_matching(notes, "validation split is")
    assert "20 clips" in coarse
    assert "5.0 accuracy points" in coarse


def test_a_run_that_generalises_says_nothing():
    result = artifacts(ramp(0.40, 0.89, 50), ramp(0.38, 0.87, 50), val_size=400)
    assert result.diagnose() == []


def test_gap_just_under_the_threshold_is_not_flagged():
    below = OVERFIT_GAP - 0.01
    result = artifacts(ramp(0.5, 0.90, 50), ramp(0.5, 0.90 - below, 50), val_size=400)
    assert not any("overfitting:" in note for note in result.diagnose())


def test_late_peak_is_not_flagged_as_an_early_peak():
    # Validation still improving at the end: nothing was wasted.
    result = artifacts(ramp(0.4, 0.99, 50), ramp(0.38, 0.86, 50), val_size=400)
    assert not any("validation peaked" in note for note in result.diagnose())


def test_short_runs_are_not_flagged_as_an_early_peak():
    # Three epochs cannot say anything about where validation peaked.
    result = artifacts([0.5, 0.9, 1.0], [0.8, 0.7, 0.7], val_size=400)
    assert not any("validation peaked" in note for note in result.diagnose())


def test_coarse_split_note_tracks_the_threshold():
    assert any(
        "validation split is" in note
        for note in overfit_run(val_size=COARSE_VAL_SPLIT - 1).diagnose()
    )
    assert not any(
        "validation split is" in note
        for note in overfit_run(val_size=COARSE_VAL_SPLIT).diagnose()
    )


def test_unknown_val_size_omits_the_resolution_note():
    notes = overfit_run(val_size=None).diagnose()
    assert len(notes) == 2
    assert not any("validation split is" in note for note in notes)


# ---------------------------------------------------------------------------
# diagnose - the pre-existing branches must be untouched
# ---------------------------------------------------------------------------
def test_a_dead_run_reports_that_training_task_was_not_learned():
    notes = artifacts([0.33] * 20, [0.33] * 20).diagnose()
    assert len(notes) == 1
    assert "not to have learned the training task" in notes[0]


def test_validation_at_chance_still_reports_memorisation():
    notes = artifacts([0.99] * 20, [0.34] * 20).diagnose()
    assert len(notes) == 1
    assert "stayed at the majority-class baseline" in notes[0]
    # The generalisation branch must not double up on the same failure.
    assert not any("overfitting:" in note for note in notes)


def test_high_majority_baseline_does_not_call_learned_training_chance():
    result = artifacts(
        [0.6667, 0.9957],
        [0.94, 0.94],
        num_classes=1,
        val_size=200,
        positive_rate=0.06,
    )
    text = "\n".join(result.diagnose()).lower()
    assert "learned/memorized the training set" in text
    assert "training accuracy is at chance too" not in text
    assert "never learned anything" not in text
    assert CHANCE_TOLERANCE == pytest.approx(0.02)


def test_binary_progress_prevents_a_nothing_learned_diagnosis():
    result = artifacts(
        [0.94, 0.95],
        [0.94, 0.94],
        num_classes=1,
        positive_rate=0.06,
        extra_history={"val_pr_auc": [0.06, 0.45], "val_target_f1": [0.0, 0.4]},
    )
    text = "\n".join(result.diagnose()).lower()
    assert "target metrics improved" in text
    assert "never learned anything" not in text


def test_a_resumed_run_is_still_flagged():
    result = artifacts(ramp(0.4, 0.89, 50), ramp(0.38, 0.87, 50), val_size=400, resumed=True)
    notes = result.diagnose()
    assert len(notes) == 1
    assert "RESUMED" in notes[0]


def test_empty_history_diagnoses_nothing():
    assert artifacts(None, None).diagnose() == []
    artifacts(None, None).summary()  # must not raise


# ---------------------------------------------------------------------------
# summary
# ---------------------------------------------------------------------------
def test_summary_separates_the_checkpoint_from_the_best_training_epoch():
    text = overfit_run().summary()
    # Best training accuracy (a later, discarded model) and the restored checkpoint.
    assert "best accuracy      : 1.0000" in text
    assert "best val_accuracy: 0.8000 (epoch 10)" in text
    assert "restored weights : epoch 10 - train 0.8700 / val 0.8000" in text
    assert "val split        : 20 clips (one clip = 5.0 accuracy points)" in text
    assert "!! overfitting:" in text


def test_summary_omits_the_val_split_line_when_the_size_is_unknown():
    assert "val split" not in overfit_run(val_size=None).summary()


def test_binary_summary_uses_the_configured_checkpoint_monitor():
    result = artifacts(
        [0.70, 0.90, 0.99],
        [0.95, 0.96, 0.97],
        num_classes=1,
        val_size=200,
        positive_rate=0.06,
        checkpoint_monitor="val_pr_auc",
        extra_history={
            "val_pr_auc": [0.20, 0.80, 0.60],
            "val_target_f1": [0.1, 0.7, 0.6],
            "val_precision": [0.2, 0.9, 0.8],
            "val_recall": [0.1, 0.6, 0.5],
        },
    )
    text = result.summary()
    assert "checkpoint metric  : val_pr_auc" in text
    assert "checkpoint epoch   : 2" in text
    assert "best val_pr_auc    : 0.8000" in text
    assert "best val_target_f1 : 0.7000" in text
    assert "checkpoint epoch   : 3" not in text


# ---------------------------------------------------------------------------
# config_differences
# ---------------------------------------------------------------------------
@pytest.fixture
def config() -> Config:
    return Config()


def test_identical_configs_have_no_differences(config):
    assert config_differences(config, Config()) == []


def test_production_defaults_use_sigmoid_pr_auc_and_clean_runs(config):
    assert config.target.output_mode == "sigmoid"
    assert config.training.checkpoint.monitor == "val_pr_auc"
    assert config.training.early_stopping.monitor == "val_pr_auc"
    assert config.training.resume is False
    yaml_config = Config.load(Path(__file__).resolve().parents[1] / "configs" / "config.yaml")
    assert yaml_config.target.output_mode == "sigmoid"
    assert yaml_config.training.checkpoint.monitor == "val_pr_auc"
    assert yaml_config.training.early_stopping.monitor == "val_pr_auc"
    assert yaml_config.training.resume is False


def test_differences_name_every_changed_key(config):
    changed = config.with_overrides({"training.optimizer": "adam", "model.dropout": 0.2})
    differences = config_differences(changed, config)
    assert ("model.dropout", 0.2, 0.3) in differences
    assert ("training.optimizer", "adam", "adamw") in differences
    assert len(differences) == 2


def test_differences_are_sorted_by_key(config):
    changed = config.with_overrides(
        {"training.epochs": 1, "model.blocks": 1, "augmentation.enabled": False}
    )
    keys = [key for key, _, _ in config_differences(changed, config)]
    assert keys == sorted(keys)


@pytest.mark.parametrize(
    "override",
    [
        {"paths.dataset_dir": "somewhere_else"},
        {"evaluation.batch_size": 1},
        {"export.int8": False},
    ],
)
def test_sections_that_do_not_change_the_run_are_ignored(config, override):
    assert config_differences(config.with_overrides(override), config) == []


def test_sections_can_be_narrowed(config):
    changed = config.with_overrides({"training.epochs": 1, "model.blocks": 1})
    differences = config_differences(changed, config, sections=("model",))
    assert [key for key, _, _ in differences] == ["model.blocks"]


# ---------------------------------------------------------------------------
# the resume guard
# ---------------------------------------------------------------------------
def trainer_with_snapshot(tmp_path: Path, live: Config, snapshot=None) -> Trainer:
    """A Trainer whose run directory holds ``snapshot`` as its config snapshot."""
    live = live.with_overrides({"paths.drive_root": str(tmp_path), "paths.project_dir": "run"})
    trainer = Trainer(config=live, model=None, num_classes=3, steps_per_epoch=7)
    if snapshot is not None:
        snapshot.save(trainer.snapshot_path)
    return trainer


def test_a_changed_class_vocabulary_is_fatal(tmp_path, config):
    snapshot = config.with_overrides({"classes.include_phrases": [1, 2, 3, 4]})
    trainer = trainer_with_snapshot(tmp_path, config, snapshot)
    with pytest.raises(ValueError, match="output width changed"):
        trainer._check_resume_compatibility()


def test_config_drift_requires_a_fresh_run(tmp_path, config):
    snapshot = config.with_overrides({"training.optimizer": "adam", "model.dropout": 0.2})
    trainer = trainer_with_snapshot(tmp_path, config, snapshot)

    with pytest.raises(ValueError, match=r"2 setting\(s\) changed") as error:
        trainer._check_resume_compatibility()
    message = str(error.value)
    assert "2 setting(s) changed" in message
    assert "model.dropout: 0.2 -> 0.3" in message
    assert "training.optimizer: 'adam' -> 'adamw'" in message
    assert "FRESH_START = True" in message


def test_an_unchanged_config_resumes_quietly(tmp_path, config, caplog):
    trainer = trainer_with_snapshot(tmp_path, config, config)
    with caplog.at_level(logging.WARNING, logger="src.trainer"):
        trainer._check_resume_compatibility()
    assert caplog.records == []


def test_a_moved_dataset_path_does_not_warn(tmp_path, config, caplog):
    # trainer_with_snapshot rewrites paths.* on the live config, so the snapshot
    # and the live config differ by exactly that - and must still be silent.
    trainer = trainer_with_snapshot(tmp_path, config, config)
    with caplog.at_level(logging.WARNING, logger="src.trainer"):
        trainer._check_resume_compatibility()
    assert caplog.records == []


def test_a_missing_snapshot_cannot_be_resumed_safely(tmp_path, config):
    trainer = trainer_with_snapshot(tmp_path, config, snapshot=None)
    assert not trainer.snapshot_path.is_file()
    with pytest.raises(ValueError, match="no config snapshot"):
        trainer._check_resume_compatibility()


def test_best_and_last_models_have_separate_paths(tmp_path, config):
    trainer = trainer_with_snapshot(tmp_path, config)
    assert trainer.best_model_path.name == "best_model.keras"
    assert trainer.last_model_path.name == "last_model.keras"
    assert trainer.best_model_path != trainer.last_model_path


def test_an_unreadable_snapshot_cannot_be_resumed_safely(tmp_path, config):
    trainer = trainer_with_snapshot(tmp_path, config, config)
    trainer.snapshot_path.write_text("{ not: valid: yaml: [", encoding="utf-8")

    with pytest.raises(ValueError, match="unreadable config snapshot"):
        trainer._check_resume_compatibility()


# ---------------------------------------------------------------------------
# metrics
# ---------------------------------------------------------------------------
def test_macro_f1_degrades_gracefully_on_a_keras_build_without_it(caplog):
    """A missing metric must cost the metric, never the run.

    The stub above deliberately has no ``F1Score``, standing in for Keras builds
    older than 2.13; the training run still has to compile.
    """
    assert SparseMacroF1 is None  # the stub has no F1Score to subclass
    with caplog.at_level(logging.INFO, logger="src.trainer"):
        metrics = build_metrics(5, TrainingConfig(macro_f1=True))
    assert [metric.name for metric in metrics] == ["accuracy", "top3_accuracy", "pr_auc"]
    assert "no F1Score metric" in caplog.text


def test_macro_f1_can_be_switched_off_without_a_message(caplog):
    with caplog.at_level(logging.INFO, logger="src.trainer"):
        metrics = build_metrics(5, TrainingConfig(macro_f1=False))
    assert [metric.name for metric in metrics] == ["accuracy", "top3_accuracy", "pr_auc"]
    assert "F1Score" not in caplog.text


def test_sigmoid_metrics_include_pr_auc_and_target_f1():
    metrics = build_metrics(1, TrainingConfig())
    assert [metric.name for metric in metrics] == [
        "accuracy", "precision", "recall", "auc", "pr_auc", "target_f1"
    ]


def test_legacy_two_output_softmax_keeps_pr_auc_monitor_available():
    metrics = build_metrics(2, TrainingConfig())
    assert [metric.name for metric in metrics] == [
        "accuracy", "precision", "recall", "auc", "pr_auc", "target_f1"
    ]

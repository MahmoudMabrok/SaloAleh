"""Training loop: seeding, mixed precision, schedules, callbacks, resume.

A run is identified by ``run_name`` (default: the model name). Re-running
notebook 03 with the same name picks the run up where it stopped, because
``BackupAndRestore`` keeps the optimiser state and epoch counter in
``checkpoints/<run_name>/backup``.
"""

from __future__ import annotations

import json
import logging
import os
import random
import shutil
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple, Union

import numpy as np
import tensorflow as tf

try:  # Keras 3 ships standalone and is what TensorFlow >= 2.16 uses
    import keras
except ImportError:  # pragma: no cover - TensorFlow 2.15 and older
    from tensorflow import keras

from .config import Config, TrainingConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

# Thresholds used by TrainingArtifacts.diagnose(). They describe the small-dataset
# regime this project lives in - a few hundred clips - not a general rule.
#
# OVERFIT_GAP: training accuracy this far above the best validation accuracy is a
#   model that learned the clips rather than the phrase. 15 points is well outside
#   what the dropout/augmentation gap alone explains.
# EARLY_PEAK_FRACTION: validation peaking inside this fraction of the run means the
#   remaining epochs only fitted the training split harder.
# COARSE_VAL_SPLIT: below this many validation clips one clip is worth more than
#   three accuracy points, so a "gap" can be two unlucky recordings.
OVERFIT_GAP = 0.15
EARLY_PEAK_FRACTION = 0.4
COARSE_VAL_SPLIT = 30

# Config sections whose values change what a run produces, and which therefore make
# a resumed run a different experiment from the one the config now describes.
# `paths` only says where things live; `evaluation` and `export` run after training.
RESUME_SENSITIVE_SECTIONS = (
    "seed",
    "classes",
    "audio",
    "features",
    "augmentation",
    "split",
    "model",
    "training",
)

__all__ = [
    "SparseCategoricalCrossentropyWithSmoothing",
    "SparseMacroF1",
    "Trainer",
    "build_metrics",
    "TrainingArtifacts",
    "WarmupCosineDecay",
    "build_loss",
    "build_metrics",
    "config_differences",
    "configure_mixed_precision",
    "sanity_overfit",
    "sanity_overfit_report",
    "set_global_seed",
]


def _flatten(data: Dict[str, object], prefix: str = "") -> Dict[str, object]:
    """Nested config dict -> ``{"training.optimizer": "adamw", ...}``."""
    flat: Dict[str, object] = {}
    for key, value in data.items():
        dotted = f"{prefix}{key}"
        if isinstance(value, dict):
            flat.update(_flatten(value, f"{dotted}."))
        else:
            flat[dotted] = value
    return flat


def config_differences(
    before: Config,
    after: Config,
    sections: Sequence[str] = RESUME_SENSITIVE_SECTIONS,
) -> List[Tuple[str, object, object]]:
    """``(dotted key, old, new)`` for every setting that changed between two configs.

    Restricted to ``sections`` - the parts of the config that decide what a run
    produces - so a moved dataset path does not read as a changed experiment.
    """
    left = _flatten(before.to_dict())
    right = _flatten(after.to_dict())
    wanted = set(sections)
    changes: List[Tuple[str, object, object]] = []
    for key in sorted(set(left) | set(right)):
        if wanted and key.split(".")[0] not in wanted:
            continue
        old = left.get(key, "<absent>")
        new = right.get(key, "<absent>")
        if old != new:
            changes.append((key, old, new))
    return changes


def set_global_seed(seed: int) -> None:
    """Seed Python, NumPy and TensorFlow, and ask TF for deterministic kernels."""
    os.environ["PYTHONHASHSEED"] = str(seed)
    random.seed(seed)
    np.random.seed(seed)
    tf.random.set_seed(seed)
    try:
        keras.utils.set_random_seed(seed)
    except AttributeError:  # pragma: no cover - very old Keras
        pass
    LOGGER.info("global seed set to %d", seed)


def configure_mixed_precision(enabled: bool) -> bool:
    """Enable ``mixed_float16`` when a GPU is present. Returns the state applied."""
    if not enabled:
        keras.mixed_precision.set_global_policy("float32")
        return False
    if not tf.config.list_physical_devices("GPU"):
        LOGGER.info("no GPU visible - staying on float32")
        keras.mixed_precision.set_global_policy("float32")
        return False
    keras.mixed_precision.set_global_policy("mixed_float16")
    LOGGER.info("mixed precision enabled (mixed_float16)")
    return True


@keras.saving.register_keras_serializable(package="dhikrspeech")
class WarmupCosineDecay(keras.optimizers.schedules.LearningRateSchedule):
    """Linear warmup into cosine decay, implemented locally for version stability."""

    def __init__(
        self,
        initial_learning_rate: float,
        total_steps: int,
        warmup_steps: int = 0,
        min_learning_rate: float = 0.0,
        name: str = "warmup_cosine_decay",
    ) -> None:
        super().__init__()
        self.initial_learning_rate = float(initial_learning_rate)
        self.total_steps = max(int(total_steps), 1)
        self.warmup_steps = max(int(warmup_steps), 0)
        self.min_learning_rate = float(min_learning_rate)
        self.name = name

    def __call__(self, step):
        step = tf.cast(step, tf.float32)
        warmup_steps = tf.cast(self.warmup_steps, tf.float32)
        total_steps = tf.cast(self.total_steps, tf.float32)
        peak = tf.cast(self.initial_learning_rate, tf.float32)
        floor = tf.cast(self.min_learning_rate, tf.float32)

        warmup_lr = peak * (step + 1.0) / tf.maximum(warmup_steps, 1.0)
        decay_steps = tf.maximum(total_steps - warmup_steps, 1.0)
        progress = tf.clip_by_value((step - warmup_steps) / decay_steps, 0.0, 1.0)
        cosine = 0.5 * (1.0 + tf.cos(np.pi * progress))
        decayed = floor + (peak - floor) * cosine

        return tf.where(step < warmup_steps, warmup_lr, decayed)

    def get_config(self) -> Dict[str, object]:
        return {
            "initial_learning_rate": self.initial_learning_rate,
            "total_steps": self.total_steps,
            "warmup_steps": self.warmup_steps,
            "min_learning_rate": self.min_learning_rate,
            "name": self.name,
        }


@keras.saving.register_keras_serializable(package="dhikrspeech")
class SparseCategoricalCrossentropyWithSmoothing(keras.losses.Loss):
    """Label smoothing on integer labels.

    Keras only offers ``label_smoothing`` on the one-hot loss, but ``class_weight``
    in ``fit`` requires integer labels. Smoothing inside the loss gives both.
    """

    def __init__(
        self,
        num_classes: int,
        label_smoothing: float = 0.0,
        name: str = "sparse_categorical_crossentropy_smoothed",
        **kwargs,
    ) -> None:
        super().__init__(name=name, **kwargs)
        self.num_classes = int(num_classes)
        self.label_smoothing = float(label_smoothing)

    def call(self, y_true, y_pred):
        y_true = tf.cast(tf.reshape(y_true, [-1]), tf.int32)
        one_hot = tf.one_hot(y_true, depth=self.num_classes, dtype=y_pred.dtype)
        return keras.losses.categorical_crossentropy(
            one_hot, y_pred, label_smoothing=self.label_smoothing
        )

    def get_config(self) -> Dict[str, object]:
        config = super().get_config()
        config.update(
            {"num_classes": self.num_classes, "label_smoothing": self.label_smoothing}
        )
        return config


_F1_BASE = getattr(getattr(keras, "metrics", None), "F1Score", None)

if _F1_BASE is not None:

    @keras.saving.register_keras_serializable(package="dhikrspeech")
    class SparseMacroF1(_F1_BASE):  # type: ignore[misc, valid-type]
        """Macro F1 on integer labels.

        Keras' ``F1Score`` expects one-hot targets, but ``class_weight`` in
        ``fit`` needs integer labels - the same mismatch
        :class:`SparseCategoricalCrossentropyWithSmoothing` solves for the loss.
        One-hotting inside ``update_state`` gives both.
        """

        def __init__(self, num_classes: int, name: str = "macro_f1", **kwargs) -> None:
            kwargs.setdefault("average", "macro")
            super().__init__(**kwargs)
            self.num_classes = int(num_classes)

        def update_state(self, y_true, y_pred, sample_weight=None):
            labels = tf.cast(tf.reshape(y_true, [-1]), tf.int32)
            one_hot = tf.one_hot(labels, depth=self.num_classes, dtype=y_pred.dtype)
            return super().update_state(one_hot, y_pred, sample_weight)

        def get_config(self) -> Dict[str, object]:
            config = super().get_config()
            config["num_classes"] = self.num_classes
            return config

else:  # pragma: no cover - Keras builds older than 2.13

    SparseMacroF1 = None  # type: ignore[assignment]


def build_loss(num_classes: int, config: TrainingConfig) -> keras.losses.Loss:
    """Loss for ``num_classes`` model outputs.

    One output is the sigmoid detector: integer 0/1 labels go straight into
    binary crossentropy, which applies ``label_smoothing`` itself.
    """
    if num_classes == 1:
        return keras.losses.BinaryCrossentropy(label_smoothing=config.label_smoothing)
    if config.label_smoothing > 0.0:
        return SparseCategoricalCrossentropyWithSmoothing(
            num_classes=num_classes, label_smoothing=config.label_smoothing
        )
    return keras.losses.SparseCategoricalCrossentropy()


def build_metrics(num_classes: int, config: TrainingConfig) -> List:
    """Metrics that mean something for the width of the output.

    A **detector** (one sigmoid output) is judged on precision and recall, never
    on accuracy: with negatives at twice the positives, a model that never fires
    is already 67% accurate. `top3` is meaningless below four classes and macro
    F1 is redundant on two, so neither is added there.

    For the legacy multi-class model, `val_accuracy` alone is just as poor a
    guide - a model answering `unknown` to everything scores whatever share
    `unknown` has. Macro F1 weighs every class equally and drops when a class is
    being ignored, which is the failure that dataset produces most often. It is a
    *metric*, not a callback: `keras.metrics.F1Score` accumulates per-class
    true/false positives on device, so there is nothing fragile to schedule, and
    Keras builds without it simply do not get the metric rather than failing at
    compile time.
    """
    if num_classes == 1:
        return [
            keras.metrics.BinaryAccuracy(name="accuracy"),
            keras.metrics.Precision(name="precision"),
            keras.metrics.Recall(name="recall"),
            keras.metrics.AUC(name="auc"),
        ]

    metrics: List = [keras.metrics.SparseCategoricalAccuracy(name="accuracy")]
    if num_classes > 3:
        metrics.append(keras.metrics.SparseTopKCategoricalAccuracy(k=3, name="top3_accuracy"))
    # Two classes are the target/unknown detector: macro F1 over two columns adds
    # nothing that precision and recall do not already say.
    if num_classes <= 2:
        return metrics

    if not config.macro_f1 or SparseMacroF1 is None:
        if config.macro_f1 and SparseMacroF1 is None:
            LOGGER.info(
                "this Keras build has no F1Score metric - training with accuracy only"
            )
        return metrics
    try:
        metrics.append(SparseMacroF1(num_classes=num_classes, name="macro_f1"))
    except Exception as exc:  # noqa: BLE001 - a metric must never fail a run
        LOGGER.warning("could not add the macro F1 metric: %s", exc)
    return metrics


def build_learning_rate(config: TrainingConfig, steps_per_epoch: int):
    """Learning-rate value or schedule object, per ``training.lr_schedule``."""
    steps_per_epoch = max(int(steps_per_epoch), 1)
    if config.lr_schedule == "cosine":
        return WarmupCosineDecay(
            initial_learning_rate=config.learning_rate,
            total_steps=steps_per_epoch * max(config.epochs, 1),
            warmup_steps=steps_per_epoch * max(config.warmup_epochs, 0),
            min_learning_rate=config.min_learning_rate,
        )
    if config.lr_schedule == "exponential":
        return keras.optimizers.schedules.ExponentialDecay(
            initial_learning_rate=config.learning_rate,
            decay_steps=steps_per_epoch * 10,
            decay_rate=0.5,
            staircase=True,
        )
    # "plateau" is driven by the ReduceLROnPlateau callback, "none" is constant.
    return config.learning_rate


def build_optimizer(config: TrainingConfig, steps_per_epoch: int) -> keras.optimizers.Optimizer:
    learning_rate = build_learning_rate(config, steps_per_epoch)
    kwargs: Dict[str, object] = {"learning_rate": learning_rate}
    if config.gradient_clip_norm:
        kwargs["clipnorm"] = float(config.gradient_clip_norm)

    if config.optimizer == "adam":
        return keras.optimizers.Adam(**kwargs)
    if config.optimizer == "adamw":
        return keras.optimizers.AdamW(weight_decay=config.weight_decay, **kwargs)
    if config.optimizer == "sgd":
        return keras.optimizers.SGD(momentum=config.momentum, nesterov=True, **kwargs)
    if config.optimizer == "rmsprop":
        return keras.optimizers.RMSprop(momentum=config.momentum, **kwargs)
    raise ValueError(f"unsupported optimizer '{config.optimizer}'")


@dataclass
class TrainingArtifacts:
    """Everything a finished run leaves on disk."""

    run_name: str
    history: Dict[str, List[float]]
    best_model_path: Path
    last_weights_path: Path
    history_path: Path
    tensorboard_dir: Path
    csv_log_path: Path
    epochs_completed: int
    num_classes: Optional[int] = None
    resumed: bool = False
    val_size: Optional[int] = None
    # Share of positives in the validation split. For a single-target detector
    # "chance" is the majority class, not 1/num_classes: on a 1:3 split a model
    # that never fires already scores 0.75.
    positive_rate: Optional[float] = None

    def best_epoch(self, monitor: str = "val_accuracy", mode: str = "max") -> Optional[int]:
        values = self.history.get(monitor)
        if not values:
            return None
        return int(np.argmax(values) if mode == "max" else np.argmin(values)) + 1

    def best_value(self, monitor: str = "val_accuracy", mode: str = "max") -> Optional[float]:
        values = self.history.get(monitor)
        if not values:
            return None
        return float(max(values) if mode == "max" else min(values))

    def value_at(self, monitor: str, epoch: int) -> Optional[float]:
        """One metric at one (1-based) epoch, or None when it was not recorded."""
        values = self.history.get(monitor)
        if not values or epoch < 1 or epoch > len(values):
            return None
        return float(values[epoch - 1])

    @property
    def chance_level(self) -> Optional[float]:
        """Accuracy a constant prediction reaches on this split.

        Balanced multi-class: ``1 / num_classes``. A detector: the majority
        class, which is the number a model that never fires actually scores - and
        is far above 0.5 whenever negatives outnumber positives.
        """
        if self.positive_rate is not None and 0.0 <= self.positive_rate <= 1.0:
            return float(max(self.positive_rate, 1.0 - self.positive_rate))
        if not self.num_classes or self.num_classes < 2:
            return None
        return 1.0 / float(self.num_classes)

    @property
    def val_resolution(self) -> Optional[float]:
        """Accuracy one validation clip is worth - the granularity of val_accuracy."""
        if not self.val_size or self.val_size < 1:
            return None
        return 1.0 / float(self.val_size)

    def diagnose(self) -> List[str]:
        """Plain-language reading of the curves, so a dead run says so out loud."""
        notes: List[str] = []
        chance = self.chance_level
        best_val = self.best_value("val_accuracy")
        best_train = self.best_value("accuracy")
        if chance is None or best_val is None or best_train is None:
            return notes

        # A hair above chance is still chance: a constant prediction scores
        # exactly 1/num_classes on a balanced split.
        margin = 1.25 * chance
        if best_val <= margin and best_train <= margin:
            notes.append(
                "the model never learned anything - training accuracy is at chance too. "
                "Look at the input pipeline, the label mapping and the number of optimiser "
                "steps (steps_per_epoch x epochs) before touching the topology."
            )
        elif best_val <= margin:
            notes.append(
                "training accuracy climbed but validation stayed at chance: the model "
                "memorised the training clips. Usually too few recordings or too few "
                "speakers, sometimes BatchNorm moving statistics that never converged."
            )
        else:
            notes.extend(self._generalisation_notes(best_train, best_val))

        if self.resumed:
            notes.append(
                "this run RESUMED an earlier one (BackupAndRestore). The weights, the "
                "optimiser state and the history above carry over from that run, so a "
                "config change you made since then did not start from scratch. Use a new "
                "RUN_NAME or Trainer.reset_run() for a clean comparison."
            )
        return notes

    def _generalisation_notes(self, best_train: float, best_val: float) -> List[str]:
        """Notes for a run that learned something but did not generalise.

        The "stayed at chance" cases above are their own failure; this is the other
        one, and the one a small dataset produces by default: validation climbs off
        chance, plateaus early, and training accuracy keeps going to 1.0 without it.
        """
        notes: List[str] = []
        gap = best_train - best_val
        best_epoch = self.best_epoch() or 0

        if gap >= OVERFIT_GAP:
            restored = ""
            train_at_best = self.value_at("accuracy", best_epoch)
            if train_at_best is not None:
                restored = (
                    f" The weights that were kept are epoch {best_epoch}'s "
                    f"(train {train_at_best:.2f} / val {best_val:.2f}); the "
                    f"{best_train:.2f} training accuracy belongs to a later, worse model."
                )
            notes.append(
                f"overfitting: training accuracy reached {best_train:.2f} but validation "
                f"stopped at {best_val:.2f}, a gap of {gap * 100:.0f} points. The model is "
                f"learning these recordings, not the phrases.{restored} In the order that "
                "actually works: more recordings and more speakers first (variety beats "
                "count - the same voice recorded twice is close to one recording), then "
                "less capacity (model.width_multiplier 1.0 -> 0.5, model.dropout up), then "
                "stronger augmentation.*. Reaching for the hyperparameters first buys a "
                "point or two and hides the real limit."
            )

        if (
            best_epoch
            and self.epochs_completed >= 10
            and best_epoch <= max(int(self.epochs_completed * EARLY_PEAK_FRACTION), 1)
        ):
            notes.append(
                f"validation peaked at epoch {best_epoch} of {self.epochs_completed} and "
                "never beat it again: the run had learned everything this dataset can teach "
                "it within the first few epochs, and the rest only fitted the training split "
                f"harder. Early stopping restored the epoch-{best_epoch} weights, so the "
                "checkpoint is the best one seen - but more epochs will not help this "
                "dataset, and neither will a longer patience."
            )

        resolution = self.val_resolution
        if resolution is not None and self.val_size and self.val_size < COARSE_VAL_SPLIT:
            notes.append(
                f"the validation split is {self.val_size} clips, so one clip is "
                f"{resolution * 100:.1f} accuracy points and val_accuracy can only take "
                f"{self.val_size + 1} distinct values. Treat every number above as coarse: "
                "a 'gap' of two clips is not a measurement. Grow the dataset before reading "
                "anything into a difference this small."
            )
        return notes

    def summary(self) -> str:
        lines = [f"run              : {self.run_name}", f"epochs completed : {self.epochs_completed}"]
        best_train_epoch = self.best_epoch("accuracy")
        if best_train_epoch is not None:
            lines.append(
                f"best accuracy    : {self.best_value('accuracy'):.4f} "
                f"(epoch {best_train_epoch}, training split)"
            )
        best_epoch = self.best_epoch()
        if best_epoch is not None:
            chance = self.chance_level
            suffix = f" - chance is {chance:.4f}" if chance else ""
            lines.append(
                f"best val_accuracy: {self.best_value():.4f} (epoch {best_epoch}){suffix}"
            )
            # The two "best" lines above are two different models: the best training
            # accuracy is whatever the run drifted to, the checkpoint is the best
            # validation epoch. Spell out the pair that actually shipped, so the
            # train/val gap being read is the gap of one model.
            restored_train = self.value_at("accuracy", best_epoch)
            restored_val = self.value_at("val_accuracy", best_epoch)
            if restored_train is not None and restored_val is not None:
                lines.append(
                    f"restored weights : epoch {best_epoch} - train {restored_train:.4f} / "
                    f"val {restored_val:.4f} (this is the checkpoint)"
                )
        if self.val_size:
            resolution = self.val_resolution or 0.0
            lines.append(
                f"val split        : {self.val_size} clips "
                f"(one clip = {resolution * 100:.1f} accuracy points)"
            )
        lines.append(f"best model       : {self.best_model_path}")
        for note in self.diagnose():
            lines.append(f"\n!! {note}")
        return "\n".join(lines)


@dataclass
class Trainer:
    """Compiles a model and runs ``fit`` with the configured callbacks."""

    config: Config
    model: keras.Model
    num_classes: int
    steps_per_epoch: int
    run_name: Optional[str] = None
    _compiled: bool = field(default=False, init=False, repr=False)

    def __post_init__(self) -> None:
        self.run_name = self.run_name or self.config.model.name
        for directory in (self.checkpoint_dir, self.log_dir):
            directory.mkdir(parents=True, exist_ok=True)

    # -- paths --------------------------------------------------------------
    @property
    def checkpoint_dir(self) -> Path:
        return self.config.paths.checkpoints_path / str(self.run_name)

    @property
    def log_dir(self) -> Path:
        return self.config.paths.logs_path / str(self.run_name)

    @property
    def backup_dir(self) -> Path:
        return self.checkpoint_dir / "backup"

    @property
    def best_model_path(self) -> Path:
        return self.checkpoint_dir / "best_model.keras"

    @property
    def snapshot_path(self) -> Path:
        return self.checkpoint_dir / "config_snapshot.yaml"

    @property
    def last_weights_path(self) -> Path:
        return self.checkpoint_dir / "last.weights.h5"

    @property
    def history_path(self) -> Path:
        return self.checkpoint_dir / "history.json"

    @property
    def csv_log_path(self) -> Path:
        return self.log_dir / "training_log.csv"

    # -- resume state -------------------------------------------------------
    @property
    def is_resuming(self) -> bool:
        """True when ``BackupAndRestore`` would pick an earlier run back up.

        ``resume: true`` means re-running the notebook restores the weights *and*
        the optimiser state of the previous run, so a config change made in
        between is applied on top of the old model rather than from scratch.
        """
        if not self.config.training.resume or not self.backup_dir.is_dir():
            return False
        return any(self.backup_dir.iterdir())

    def reset_run(self) -> None:
        """Delete every artefact of this run so the next ``fit`` starts clean.

        Removes the backup (epoch counter + optimiser state), the checkpoint, the
        merged history and the CSV log. Use it whenever a hyperparameter changed
        and the previous run must not bleed into the new one.
        """
        for target in (self.checkpoint_dir, self.log_dir):
            if target.exists():
                shutil.rmtree(target)
        for directory in (self.checkpoint_dir, self.log_dir):
            directory.mkdir(parents=True, exist_ok=True)
        LOGGER.info("run '%s' reset - next fit starts from scratch", self.run_name)

    # -- compilation --------------------------------------------------------
    def compile(self) -> keras.Model:
        training = self.config.training
        self.model.compile(
            optimizer=build_optimizer(training, self.steps_per_epoch),
            loss=build_loss(self.num_classes, training),
            metrics=build_metrics(self.num_classes, training),
        )
        self._compiled = True
        return self.model

    # -- callbacks ----------------------------------------------------------
    def build_callbacks(self) -> List[keras.callbacks.Callback]:
        training = self.config.training
        callbacks: List[keras.callbacks.Callback] = [
            keras.callbacks.ModelCheckpoint(
                filepath=str(self.best_model_path),
                monitor=training.checkpoint.monitor,
                mode=training.checkpoint.mode,
                save_best_only=training.checkpoint.save_best_only,
                save_weights_only=False,
                save_freq=training.checkpoint.save_freq,
                verbose=1,
            ),
            keras.callbacks.CSVLogger(str(self.csv_log_path), append=True),
        ]

        if training.early_stopping.enabled:
            callbacks.append(
                keras.callbacks.EarlyStopping(
                    monitor=training.early_stopping.monitor,
                    mode=training.early_stopping.mode,
                    patience=training.early_stopping.patience,
                    min_delta=training.early_stopping.min_delta,
                    restore_best_weights=training.early_stopping.restore_best_weights,
                    verbose=1,
                )
            )

        if training.lr_schedule == "plateau":
            callbacks.append(
                keras.callbacks.ReduceLROnPlateau(
                    monitor=training.early_stopping.monitor,
                    mode=training.early_stopping.mode,
                    factor=training.plateau_factor,
                    patience=training.plateau_patience,
                    min_lr=training.min_learning_rate,
                    verbose=1,
                )
            )

        if training.tensorboard.enabled:
            callbacks.append(
                keras.callbacks.TensorBoard(
                    log_dir=str(self.log_dir / "tensorboard"),
                    histogram_freq=training.tensorboard.histogram_freq,
                    update_freq=training.tensorboard.update_freq,
                )
            )

        if training.resume:
            callbacks.append(
                keras.callbacks.BackupAndRestore(backup_dir=str(self.backup_dir))
            )
        return callbacks

    # -- fit ----------------------------------------------------------------
    def fit(
        self,
        train_dataset,
        validation_dataset,
        class_weight: Optional[Dict[int, float]] = None,
        epochs: Optional[int] = None,
        initial_epoch: int = 0,
        verbose: int = 1,
        val_size: Optional[int] = None,
        positive_rate: Optional[float] = None,
    ) -> TrainingArtifacts:
        """Run ``fit`` and return the artefacts.

        ``val_size`` is the number of validation *clips* (not batches). It is only
        used to report how coarse ``val_accuracy`` is - on a split of a few dozen
        clips a single recording moves it by several points, which is the difference
        between a real train/val gap and two unlucky takes.
        """
        if not self._compiled:
            self.compile()

        resumed = self.is_resuming
        if resumed:
            self._check_resume_compatibility()
            LOGGER.warning(
                "resuming run '%s' from %s - weights, optimiser state and epoch "
                "counter come from the previous run. Call reset_run() or pick a new "
                "run_name to start fresh.",
                self.run_name,
                self.backup_dir,
            )

        history = self.model.fit(
            train_dataset,
            validation_data=validation_dataset,
            epochs=epochs or self.config.training.epochs,
            initial_epoch=initial_epoch,
            class_weight=class_weight if self.config.training.class_weights else None,
            callbacks=self.build_callbacks(),
            verbose=verbose,
        )

        merged = self._merge_history(history.history) if resumed else {
            key: [float(value) for value in values] for key, values in history.history.items()
        }
        self.model.save_weights(str(self.last_weights_path))
        self.history_path.write_text(json.dumps(merged, indent=2), encoding="utf-8")
        self.config.save(self.checkpoint_dir / "config_snapshot.yaml")

        if not self.best_model_path.exists():
            # save_best_only never fired (single epoch, or validation missing).
            self.model.save(str(self.best_model_path))

        epochs_completed = len(next(iter(merged.values()), []))
        LOGGER.info("training finished after %d epochs", epochs_completed)
        return TrainingArtifacts(
            run_name=str(self.run_name),
            history=merged,
            best_model_path=self.best_model_path,
            last_weights_path=self.last_weights_path,
            history_path=self.history_path,
            tensorboard_dir=self.log_dir / "tensorboard",
            csv_log_path=self.csv_log_path,
            epochs_completed=epochs_completed,
            num_classes=int(self.num_classes),
            resumed=resumed,
            val_size=int(val_size) if val_size else None,
            positive_rate=float(positive_rate) if positive_rate is not None else None,
        )

    def _check_resume_compatibility(self) -> None:
        """Guard a resume against the config having moved since the backup.

        Two failure modes, and only one of them is fatal:

        * ``classes.include_phrases`` changes the width of the model's output, so
          restoring the old optimiser state fails deep inside Keras with a shape
          error that says nothing about the cause. That is raised.
        * Every other setting - dropout, optimizer, learning rate, augmentation -
          restores *successfully* and silently produces a run that is not the one
          the config describes. The changed weights and optimiser slots come from
          the old settings, the history splices both runs together, and the new
          value never takes effect. That is warned about, loudly, because it is
          the one that costs a full run before anyone notices.
        """
        if not self.snapshot_path.is_file():
            return
        try:
            previous = Config.load(self.snapshot_path)
        except Exception as exc:  # noqa: BLE001 - a stale snapshot must not block a run
            LOGGER.warning("ignoring unreadable config snapshot at %s: %s", self.snapshot_path, exc)
            return

        before = previous.classes.include_phrases
        now = self.config.classes.include_phrases
        if before != now:
            raise ValueError(
                f"run '{self.run_name}' was trained on classes.include_phrases={before}, "
                f"but the config now says {now}. The model's output width changed, so the "
                f"backup cannot be restored. Set FRESH_START = True (or pick a new RUN_NAME) "
                f"and re-run 02_preprocessing so the manifest matches."
            )

        changes = config_differences(previous, self.config)
        if not changes:
            return
        detail = "\n".join(f"    {key}: {old!r} -> {new!r}" for key, old, new in changes)
        LOGGER.warning(
            "run '%s' is being RESUMED but %d setting(s) changed since its backup was "
            "written:\n%s\n  The restored weights and optimiser state come from the old "
            "settings, so these values will not take effect and the reported history "
            "splices both runs together. Set FRESH_START = True (or pick a new RUN_NAME) "
            "for the change to mean anything.",
            self.run_name,
            len(changes),
            detail,
        )

    def _merge_history(self, history: Dict[str, List[float]]) -> Dict[str, List[float]]:
        """Append to any history from an earlier, interrupted run of the same name."""
        merged: Dict[str, List[float]] = {
            key: [float(value) for value in values] for key, values in history.items()
        }
        if not self.history_path.exists():
            return merged
        try:
            previous = json.loads(self.history_path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as exc:
            LOGGER.warning("ignoring unreadable history at %s: %s", self.history_path, exc)
            return merged
        for key, values in merged.items():
            merged[key] = [float(value) for value in previous.get(key, [])] + values
        return merged

    def load_best(self) -> keras.Model:
        """Load the best checkpoint of this run."""
        if not self.best_model_path.exists():
            raise FileNotFoundError(f"no checkpoint at {self.best_model_path}")
        self.model = load_trained_model(self.best_model_path)
        self._compiled = False
        return self.model


def sanity_overfit(
    model: keras.Model,
    dataset,
    steps: int = 200,
    learning_rate: float = 1e-3,
    verbose: int = 0,
) -> Dict[str, float]:
    """Train a throwaway copy of ``model`` on a handful of clips and report accuracy.

    The standard first test when a run sits at chance. A correct pipeline drives a
    few dozen *unaugmented* clips to near-100% training accuracy in a couple of
    hundred steps - it only has to memorise them. If this stays at chance, the
    fault is upstream of the hyperparameters: features, labels, or the model.

    ``dataset`` must be batched, unaugmented and repeatable; it is consumed for
    ``steps`` batches. ``model`` is cloned, so the caller's weights are untouched.
    """
    clone = keras.models.clone_model(model)
    num_outputs = int(clone.output_shape[-1])
    clone.compile(
        optimizer=keras.optimizers.Adam(learning_rate=learning_rate),
        # No label smoothing here: this test wants the model to memorise, and
        # smoothing would stop it reaching the near-1.0 that makes the verdict
        # readable.
        loss=(
            keras.losses.BinaryCrossentropy()
            if num_outputs == 1
            else keras.losses.SparseCategoricalCrossentropy()
        ),
        metrics=[
            keras.metrics.BinaryAccuracy(name="accuracy")
            if num_outputs == 1
            else keras.metrics.SparseCategoricalAccuracy(name="accuracy")
        ],
    )
    history = clone.fit(
        dataset.repeat(),
        epochs=1,
        steps_per_epoch=int(steps),
        verbose=verbose,
        callbacks=[],
    )
    # Keras averages a metric over every step of the epoch, so this includes the
    # steps at the very start when the model still knew nothing. It describes the
    # run, not the model the run produced - never judge the outcome by it.
    running_accuracy = float(history.history["accuracy"][-1])

    # The model the run produced, in inference mode: this is the verdict.
    evaluated = clone.evaluate(dataset, verbose=0, return_dict=True)

    # Same clips in training mode, so BatchNorm uses batch statistics instead of
    # its moving averages. Run last: a training=True pass updates those averages.
    # Dropout is active here too, so a few points below the inference number is
    # normal; a collapse to chance in *inference* while this stays high is not.
    correct = total = 0
    for features, labels in dataset:
        outputs = clone(features, training=True)
        if num_outputs == 1:
            predicted = tf.cast(tf.reshape(outputs, [-1]) >= 0.5, tf.int32)
        else:
            predicted = tf.argmax(outputs, axis=-1, output_type=tf.int32)
        labels = tf.cast(tf.reshape(labels, [-1]), tf.int32)
        correct += int(tf.reduce_sum(tf.cast(tf.equal(predicted, labels), tf.int32)))
        total += int(tf.size(labels))

    return {
        "running_accuracy": running_accuracy,
        "inference_accuracy": float(evaluated.get("accuracy", float("nan"))),
        "inference_loss": float(evaluated.get("loss", float("nan"))),
        "train_mode_accuracy": float(correct) / float(total) if total else float("nan"),
        "steps": float(steps),
    }


def sanity_overfit_report(result: Dict[str, float], num_classes: int) -> str:
    """Verdict for :func:`sanity_overfit`, written for the notebook output."""
    chance = 1.0 / float(max(num_classes, 2))
    inference = result["inference_accuracy"]
    train_mode = result["train_mode_accuracy"]
    lines = [
        f"steps             : {int(result['steps'])}",
        f"final accuracy    : {inference:.4f} (chance {chance:.4f})  <- the verdict",
        f"train-mode accuracy: {train_mode:.4f} (BatchNorm on batch statistics)",
        f"accuracy over run : {result['running_accuracy']:.4f} (averaged across all "
        f"{int(result['steps'])} steps, so it starts at chance - ignore it)",
    ]
    if inference >= 0.9:
        lines.append(
            "\nPASS - the pipeline can learn. Features, labels and topology are fine, so "
            "a real run stuck at chance is a data-quantity or schedule problem: more "
            "recordings and speakers, more optimiser steps (smaller training.batch_size, "
            "more training.epochs), lighter augmentation."
        )
    elif train_mode >= 0.9:
        lines.append(
            "\nFAIL - it memorised the clips in training mode but not in inference mode. "
            "That is BatchNorm: the moving statistics never converged. Lower "
            "model.bn_momentum (0.9 -> 0.8) or raise the number of steps per epoch."
        )
    elif max(inference, train_mode) <= 1.25 * chance:
        lines.append(
            "\nFAIL - the model cannot even memorise a handful of clips. The problem is "
            "the data or the front-end, not the schedule: check that features are finite "
            "and vary between classes, that class_index matches the folder, and that the "
            "learning rate is not zero."
        )
    else:
        lines.append(
            "\nPARTIAL - it is learning but has not finished memorising. Raise `steps` "
            "and re-run before reading anything into it."
        )
    return "\n".join(lines)


def load_trained_model(path: PathLike) -> keras.Model:
    """Load a ``.keras`` checkpoint, including this module's custom objects."""
    custom_objects = {
        "WarmupCosineDecay": WarmupCosineDecay,
        "SparseCategoricalCrossentropyWithSmoothing": (
            SparseCategoricalCrossentropyWithSmoothing
        ),
    }
    if SparseMacroF1 is not None:
        custom_objects["SparseMacroF1"] = SparseMacroF1
    return keras.models.load_model(str(path), custom_objects=custom_objects, compile=False)

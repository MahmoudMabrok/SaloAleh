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
from typing import Dict, List, Optional, Union

import numpy as np
import tensorflow as tf

try:  # Keras 3 ships standalone and is what TensorFlow >= 2.16 uses
    import keras
except ImportError:  # pragma: no cover - TensorFlow 2.15 and older
    from tensorflow import keras

from .config import Config, TrainingConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "SparseCategoricalCrossentropyWithSmoothing",
    "Trainer",
    "TrainingArtifacts",
    "WarmupCosineDecay",
    "configure_mixed_precision",
    "sanity_overfit",
    "sanity_overfit_report",
    "set_global_seed",
]


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


def build_loss(num_classes: int, config: TrainingConfig) -> keras.losses.Loss:
    if config.label_smoothing > 0.0:
        return SparseCategoricalCrossentropyWithSmoothing(
            num_classes=num_classes, label_smoothing=config.label_smoothing
        )
    return keras.losses.SparseCategoricalCrossentropy()


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

    @property
    def chance_level(self) -> Optional[float]:
        """Accuracy a constant prediction reaches on a balanced split."""
        if not self.num_classes or self.num_classes < 2:
            return None
        return 1.0 / float(self.num_classes)

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
        if self.resumed:
            notes.append(
                "this run RESUMED an earlier one (BackupAndRestore). The weights, the "
                "optimiser state and the history above carry over from that run, so a "
                "config change you made since then did not start from scratch. Use a new "
                "RUN_NAME or Trainer.reset_run() for a clean comparison."
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
            metrics=[
                keras.metrics.SparseCategoricalAccuracy(name="accuracy"),
                keras.metrics.SparseTopKCategoricalAccuracy(k=3, name="top3_accuracy"),
            ],
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
    ) -> TrainingArtifacts:
        if not self._compiled:
            self.compile()

        resumed = self.is_resuming
        if resumed:
            self._reject_incompatible_resume()
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
        )

    def _reject_incompatible_resume(self) -> None:
        """Refuse to restore a backup trained on a different class vocabulary.

        Changing ``classes.include_phrases`` changes the width of the model's
        output, so restoring the old optimiser state fails deep inside Keras with
        a shape error that says nothing about the cause.
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
        if before == now:
            return
        raise ValueError(
            f"run '{self.run_name}' was trained on classes.include_phrases={before}, "
            f"but the config now says {now}. The model's output width changed, so the "
            f"backup cannot be restored. Set FRESH_START = True (or pick a new RUN_NAME) "
            f"and re-run 02_preprocessing so the manifest matches."
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
    clone.compile(
        optimizer=keras.optimizers.Adam(learning_rate=learning_rate),
        loss=keras.losses.SparseCategoricalCrossentropy(),
        metrics=[keras.metrics.SparseCategoricalAccuracy(name="accuracy")],
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
        predicted = tf.argmax(clone(features, training=True), axis=-1, output_type=tf.int32)
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
    return keras.models.load_model(
        str(path),
        custom_objects={
            "WarmupCosineDecay": WarmupCosineDecay,
            "SparseCategoricalCrossentropyWithSmoothing": (
                SparseCategoricalCrossentropyWithSmoothing
            ),
        },
        compile=False,
    )

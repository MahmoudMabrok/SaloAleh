"""One-vs-rest experiment: N specialised binary models vs. one multi-class model.

The proposal this module settles is "train one model per dhikr instead of one
model over all of them". It is a reasonable-sounding idea, and the only way to
answer it is to train both and score them on the *same* clips.

Everything here is built to keep that comparison honest:

* both approaches read the same ``processed/manifest.csv``, so they see the same
  train / val / test split - the binary models are never handed clips the
  multi-class model was evaluated on;
* the binary models get the same architecture, the same augmentation, the same
  optimiser and the same epoch budget, so a difference is the *approach* and not
  a hyperparameter;
* the evaluation dataset is built once and every model runs over those same
  tensors, so the comparison is per-clip rather than per-run.

Three questions get separate answers, because they can disagree:

1. **Per-phrase detection** - for each phrase, how well does each approach
   separate "this phrase" from everything else? Measured with ROC-AUC and
   average precision, which are threshold-free: neither approach gets to win by
   having a better-tuned threshold.
2. **Discrimination** - restricted to clips that really are a phrase, how often
   does each approach name the *right* one? This is where nested phrases decide
   the question (``سبحان الله`` ⊂ ``سبحان الله وبحمده`` ⊂
   ``سبحان الله العظيم وبحمده``): the multi-class model is trained to separate
   them, the binary models never see the distinction as a label.
3. **Rejection** - on ``unknown`` clips, how often does each approach stay quiet?
   The multi-class model has an ``unknown`` output; a committee of binaries has
   only a threshold.

A committee of binary models predicts by ``argmax`` over the per-model positive
scores. That is the deployment shape the proposal implies, and it is scored
exactly as the multi-class model's ``argmax`` over the same phrases.
"""

from __future__ import annotations

import json
import logging
import math
from dataclasses import asdict, dataclass, field, replace
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

import numpy as np
from sklearn.metrics import average_precision_score, roc_auc_score

from .config import Config
from .dataset import (
    ManifestRecord,
    class_names_from_manifest,
    compute_class_weights,
    filter_split,
    make_tf_dataset,
)
from .metrics import predict_dataset, wilson_interval

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]
ProgressFn = Optional[Callable[[str, int, int], None]]

__all__ = [
    "ComparisonReport",
    "DiscriminationResult",
    "OneVsRestModel",
    "PhraseComparison",
    "RejectionResult",
    "binary_class_names",
    "binary_records",
    "compare_one_vs_rest",
    "phrase_labels",
    "train_one_vs_rest",
]

# Class indices inside a binary run. 1 is the phrase so that ``y_prob[:, 1]`` is
# the positive score, which is what every metric below consumes.
REST_INDEX = 0
POSITIVE_INDEX = 1
REST_LABEL = "rest"

# A difference smaller than this in AUC is not a result on a split of a few dozen
# clips - it is one or two recordings landing differently.
NEGLIGIBLE_AUC_DELTA = 0.02


# ---------------------------------------------------------------------------
# Relabelling
# ---------------------------------------------------------------------------
def binary_class_names(positive_label: str) -> List[str]:
    """Class list for a one-vs-rest run, ordered by class index."""
    return [REST_LABEL, positive_label]


def binary_records(
    records: Sequence[ManifestRecord], positive_label: str
) -> List[ManifestRecord]:
    """Copy ``records`` with every label collapsed to "is / is not this phrase".

    The clips, the paths and the splits are untouched - only ``class_index``
    moves. That is the whole point: the binary model must be trained on exactly
    the recordings the multi-class model was trained on, or the comparison
    measures the split rather than the approach.

    Every non-positive clip becomes a negative, ``unknown`` included. Leaving
    ``unknown`` out would build a detector that has never been shown a non-dhikr
    sound, which is not a fair stand-in for a deployable model.
    """
    if not any(record.label == positive_label for record in records):
        raise ValueError(f"no records carry the label '{positive_label}'")
    return [
        replace(
            record,
            class_index=POSITIVE_INDEX if record.label == positive_label else REST_INDEX,
        )
        for record in records
    ]


def phrase_labels(class_names: Sequence[str], unknown_class: str = "unknown") -> List[str]:
    """The class names that are phrases, i.e. everything but ``unknown``."""
    return [name for name in class_names if name != unknown_class]


# ---------------------------------------------------------------------------
# Training one binary model
# ---------------------------------------------------------------------------
@dataclass
class OneVsRestModel:
    """A trained binary detector plus what its run reported."""

    label: str
    run_name: str
    model: object = field(repr=False)
    epochs_completed: int
    best_val_accuracy: Optional[float]
    train_positives: int
    train_negatives: int


def train_one_vs_rest(
    config: Config,
    records: Sequence[ManifestRecord],
    positive_label: str,
    extractor,
    augmentor=None,
    run_name: Optional[str] = None,
    epochs: Optional[int] = None,
    fresh: bool = True,
    verbose: int = 0,
) -> OneVsRestModel:
    """Train one "is this ``positive_label``?" detector on the manifest splits.

    ``epochs`` defaults to ``training.epochs`` on purpose. Giving the binary
    models a different budget than the multi-class model had would make the
    comparison meaningless, so override it only to shorten *both* sides.

    ``fresh`` wipes the run directory first. A one-vs-rest run reuses the
    training machinery, which resumes by default - and silently resuming a
    binary run left over from a previous experiment would poison the result with
    weights trained on a different phrase.
    """
    # TensorFlow is imported here rather than at module scope so that reading a
    # saved report does not pull in a 600 MB dependency, matching the lazy import
    # in dataset.make_tf_dataset.
    from .models import build_model
    from .trainer import Trainer, set_global_seed

    # Override the budget in the config rather than only in `fit`: the cosine
    # schedule sizes itself from `training.epochs`, so passing a shorter budget to
    # `fit` alone would leave the learning rate decaying towards an epoch the run
    # never reaches.
    if epochs is not None:
        config = config.with_overrides({"training.epochs": int(epochs)})

    relabelled = binary_records(records, positive_label)
    train_records = filter_split(relabelled, "train")
    val_records = filter_split(relabelled, "val")
    if not train_records or not val_records:
        raise ValueError(
            "a one-vs-rest run needs a non-empty train and val split - "
            "re-run preprocessing"
        )

    positives = sum(1 for record in train_records if record.class_index == POSITIVE_INDEX)
    negatives = len(train_records) - positives
    if positives == 0:
        raise ValueError(f"'{positive_label}' has no clips in the training split")
    if augmentor is None:
        LOGGER.warning(
            "training '%s' without an augmentor - the multi-class model was almost "
            "certainly trained with one, so this comparison is not like for like",
            positive_label,
        )

    # Same seed for every binary run and for the multi-class run, so the only
    # thing that differs between them is the labelling.
    set_global_seed(config.seed)

    train_dataset = make_tf_dataset(
        train_records, config, extractor, training=True, augmentor=augmentor
    )
    val_dataset = make_tf_dataset(val_records, config, extractor, training=False)

    model = build_model(config.input_shape, 2, config.model)
    trainer = Trainer(
        config=config,
        model=model,
        num_classes=2,
        steps_per_epoch=math.ceil(len(train_records) / config.training.batch_size),
        run_name=run_name or f"ovr_{positive_label}",
    )
    if fresh:
        trainer.reset_run()
    trainer.compile()

    # 1 positive against N-1 negatives is a heavy imbalance; without weights the
    # model scores well by never firing, which would understate one-vs-rest for
    # the wrong reason.
    class_weight = compute_class_weights(
        [record.class_index for record in train_records], 2
    )
    artifacts = trainer.fit(
        train_dataset,
        val_dataset,
        class_weight=class_weight,
        verbose=verbose,
        val_size=len(val_records),
    )
    LOGGER.info(
        "one-vs-rest '%s': %d epochs, best val_accuracy %s",
        positive_label,
        artifacts.epochs_completed,
        artifacts.best_value(),
    )
    return OneVsRestModel(
        label=positive_label,
        run_name=str(trainer.run_name),
        model=trainer.load_best(),
        epochs_completed=artifacts.epochs_completed,
        best_val_accuracy=artifacts.best_value(),
        train_positives=positives,
        train_negatives=negatives,
    )


# ---------------------------------------------------------------------------
# Results
# ---------------------------------------------------------------------------
def _jsonable(value):
    """Replace non-finite floats with null so the report is valid JSON.

    AUC is genuinely undefined for a phrase with no clips in the split, and
    ``json.dumps`` would happily write a bare ``NaN`` - which Python reads back
    but every other JSON parser rejects.
    """
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, float) and not np.isfinite(value):
        return None
    return value


def _safe_auc(truth: np.ndarray, scores: np.ndarray) -> float:
    """ROC-AUC, or NaN when the split has only one class (AUC is undefined)."""
    positives = int(truth.sum())
    if positives == 0 or positives == truth.size:
        return float("nan")
    return float(roc_auc_score(truth, scores))


def _safe_ap(truth: np.ndarray, scores: np.ndarray) -> float:
    positives = int(truth.sum())
    if positives == 0 or positives == truth.size:
        return float("nan")
    return float(average_precision_score(truth, scores))


@dataclass
class PhraseComparison:
    """Per-phrase detection quality, one row per phrase.

    Both columns answer the same question - "how well does this approach
    separate this phrase from everything else?" - so they are directly
    comparable. The multi-class score is its softmax output for that phrase; the
    binary score is its positive output. Threshold-free by design.
    """

    label: str
    support: int
    negatives: int
    auc_multiclass: float
    auc_binary: float
    ap_multiclass: float
    ap_binary: float
    binary_epochs: int = 0
    binary_val_accuracy: Optional[float] = None

    @property
    def auc_delta(self) -> float:
        """Binary minus multi-class. Positive means one-vs-rest won."""
        return self.auc_binary - self.auc_multiclass

    @property
    def ap_delta(self) -> float:
        return self.ap_binary - self.ap_multiclass


@dataclass
class DiscriminationResult:
    """Naming the right phrase, on clips that really are a phrase.

    ``unknown`` clips are excluded: this measures telling phrases *apart*, which
    is the half of the problem the nested prefixes make hard and the half a
    binary detector is never trained on.
    """

    num_clips: int
    num_phrases: int
    multiclass_correct: int
    committee_correct: int
    multiclass_confusions: List[Tuple[str, str, int]] = field(default_factory=list)
    committee_confusions: List[Tuple[str, str, int]] = field(default_factory=list)

    @property
    def multiclass_accuracy(self) -> float:
        return self.multiclass_correct / self.num_clips if self.num_clips else float("nan")

    @property
    def committee_accuracy(self) -> float:
        return self.committee_correct / self.num_clips if self.num_clips else float("nan")

    def intervals(self) -> Dict[str, Tuple[float, float]]:
        return {
            "multiclass": wilson_interval(self.multiclass_accuracy, self.num_clips),
            "committee": wilson_interval(self.committee_accuracy, self.num_clips),
        }


@dataclass
class RejectionResult:
    """Staying quiet on non-dhikr audio, at one shared threshold.

    Both approaches are read the same way: a clip is *accepted* when the highest
    phrase score clears the threshold. For the multi-class model that is the
    highest phrase softmax (its ``unknown`` output is deliberately not consulted,
    so the two sides are scored by the same rule); for the committee it is the
    highest binary positive score.

    This is one operating point, not a matched one - the two approaches produce
    differently-calibrated scores, so read the accept rates as a pair rather than
    either number alone.
    """

    threshold: float
    unknown_clips: int
    multiclass_unknown_accepted: int
    committee_unknown_accepted: int
    phrase_clips: int
    multiclass_phrase_accepted: int
    committee_phrase_accepted: int

    def _rate(self, count: int, total: int) -> float:
        return count / total if total else float("nan")

    @property
    def multiclass_false_accept(self) -> float:
        return self._rate(self.multiclass_unknown_accepted, self.unknown_clips)

    @property
    def committee_false_accept(self) -> float:
        return self._rate(self.committee_unknown_accepted, self.unknown_clips)

    @property
    def multiclass_recall(self) -> float:
        return self._rate(self.multiclass_phrase_accepted, self.phrase_clips)

    @property
    def committee_recall(self) -> float:
        return self._rate(self.committee_phrase_accepted, self.phrase_clips)


@dataclass
class ComparisonReport:
    """Everything the experiment measured, plus a plain-language verdict."""

    eval_split: str
    num_eval_clips: int
    class_names: List[str]
    phrases: List[str]
    per_phrase: List[PhraseComparison]
    discrimination: Optional[DiscriminationResult] = None
    rejection: Optional[RejectionResult] = None
    epochs: Optional[int] = None

    # -- aggregates ---------------------------------------------------------
    def _mean(self, attribute: str) -> float:
        values = [
            getattr(item, attribute)
            for item in self.per_phrase
            if np.isfinite(getattr(item, attribute))
        ]
        return float(np.mean(values)) if values else float("nan")

    @property
    def mean_auc_multiclass(self) -> float:
        return self._mean("auc_multiclass")

    @property
    def mean_auc_binary(self) -> float:
        return self._mean("auc_binary")

    def to_dataframe(self):
        import pandas as pd  # lazily imported: only notebooks need pandas

        return pd.DataFrame(
            [
                {
                    "phrase": item.label,
                    "clips": item.support,
                    "AUC multiclass": round(item.auc_multiclass, 4),
                    "AUC binary": round(item.auc_binary, 4),
                    "AUC delta": round(item.auc_delta, 4),
                    "AP multiclass": round(item.ap_multiclass, 4),
                    "AP binary": round(item.ap_binary, 4),
                    "AP delta": round(item.ap_delta, 4),
                }
                for item in self.per_phrase
            ]
        )

    # -- verdict ------------------------------------------------------------
    def verdict(self) -> List[str]:
        """What the numbers say, written so a null result reads as a null result."""
        notes: List[str] = []

        deltas = [item.auc_delta for item in self.per_phrase if np.isfinite(item.auc_delta)]
        if deltas:
            mean_delta = float(np.mean(deltas))
            wins = sum(1 for delta in deltas if delta > NEGLIGIBLE_AUC_DELTA)
            losses = sum(1 for delta in deltas if delta < -NEGLIGIBLE_AUC_DELTA)
            if abs(mean_delta) <= NEGLIGIBLE_AUC_DELTA and not (wins or losses):
                notes.append(
                    f"per-phrase detection is a tie: mean AUC difference "
                    f"{mean_delta:+.4f}, and no phrase moved by more than "
                    f"{NEGLIGIBLE_AUC_DELTA:.2f}. Training {len(deltas)} separate models "
                    f"bought nothing on the task they were each specialised for."
                )
            else:
                notes.append(
                    f"per-phrase detection: mean AUC difference {mean_delta:+.4f} "
                    f"(binary minus multi-class); {wins} phrase(s) better under "
                    f"one-vs-rest, {losses} worse, "
                    f"{len(deltas) - wins - losses} unchanged."
                )

        if self.discrimination and self.discrimination.num_clips:
            discrimination = self.discrimination
            gap = discrimination.multiclass_accuracy - discrimination.committee_accuracy
            low_m, high_m = discrimination.intervals()["multiclass"]
            low_c, high_c = discrimination.intervals()["committee"]
            overlap = low_m <= high_c and low_c <= high_m
            notes.append(
                f"telling the phrases apart: multi-class {discrimination.multiclass_accuracy:.4f} "
                f"vs. committee-of-binaries {discrimination.committee_accuracy:.4f} on "
                f"{discrimination.num_clips} phrase clips ({gap:+.4f} for multi-class)."
                + (
                    " The 95% intervals overlap, so this split cannot separate the two - "
                    "it is not evidence either way."
                    if overlap
                    else " The 95% intervals do not overlap."
                )
            )

        if self.rejection and self.rejection.unknown_clips:
            rejection = self.rejection
            notes.append(
                f"rejecting non-dhikr audio at threshold {rejection.threshold:.2f}: "
                f"multi-class fires on {rejection.multiclass_false_accept:.1%} of "
                f"{rejection.unknown_clips} unknown clips, the committee on "
                f"{rejection.committee_false_accept:.1%} - while accepting "
                f"{rejection.multiclass_recall:.1%} and {rejection.committee_recall:.1%} "
                f"of real phrase clips respectively. Compare the pairs, not the "
                f"false-accept rates alone: a model that never fires has none."
            )

        smallest = min((item.support for item in self.per_phrase), default=0)
        if smallest and smallest < 20:
            notes.append(
                f"the smallest phrase has {smallest} clips in the {self.eval_split} split, "
                f"so its AUC moves in visible steps and its interval covers most of the "
                f"range. Treat every number above as coarse: this experiment can rule out "
                f"a large effect, not measure a small one. Collect more recordings before "
                f"reading a few points either way as a result."
            )
        return notes

    def summary(self) -> str:
        lines = [
            f"split            : {self.eval_split} ({self.num_eval_clips} clips)",
            f"phrases compared : {len(self.phrases)} ({', '.join(self.phrases)})",
            f"models trained   : 1 multi-class + {len(self.per_phrase)} one-vs-rest",
            f"mean AUC         : multi-class {self.mean_auc_multiclass:.4f} | "
            f"one-vs-rest {self.mean_auc_binary:.4f}",
        ]
        if self.discrimination and self.discrimination.num_clips:
            lines.append(
                f"phrase accuracy  : multi-class "
                f"{self.discrimination.multiclass_accuracy:.4f} | committee "
                f"{self.discrimination.committee_accuracy:.4f} "
                f"({self.discrimination.num_clips} phrase clips)"
            )
        for note in self.verdict():
            lines.append(f"\n!! {note}")
        return "\n".join(lines)

    # -- persistence --------------------------------------------------------
    def to_dict(self) -> Dict[str, object]:
        payload: Dict[str, object] = {
            "eval_split": self.eval_split,
            "num_eval_clips": self.num_eval_clips,
            "class_names": list(self.class_names),
            "phrases": list(self.phrases),
            "epochs": self.epochs,
            "mean_auc_multiclass": self.mean_auc_multiclass,
            "mean_auc_binary": self.mean_auc_binary,
            "per_phrase": [
                {**asdict(item), "auc_delta": item.auc_delta, "ap_delta": item.ap_delta}
                for item in self.per_phrase
            ],
            "verdict": self.verdict(),
        }
        if self.discrimination:
            payload["discrimination"] = {
                **asdict(self.discrimination),
                "multiclass_accuracy": self.discrimination.multiclass_accuracy,
                "committee_accuracy": self.discrimination.committee_accuracy,
            }
        if self.rejection:
            payload["rejection"] = {
                **asdict(self.rejection),
                "multiclass_false_accept": self.rejection.multiclass_false_accept,
                "committee_false_accept": self.rejection.committee_false_accept,
                "multiclass_recall": self.rejection.multiclass_recall,
                "committee_recall": self.rejection.committee_recall,
            }
        return payload

    def save(self, directory: PathLike, name: str = "one_vs_rest") -> Path:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)
        destination = target / f"{name}.json"
        destination.write_text(
            json.dumps(_jsonable(self.to_dict()), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        LOGGER.info("one-vs-rest comparison written to %s", destination)
        return destination


# ---------------------------------------------------------------------------
# The experiment
# ---------------------------------------------------------------------------
def _confusions(
    true_labels: np.ndarray, predicted_labels: np.ndarray, top_k: int = 10
) -> List[Tuple[str, str, int]]:
    """Most frequent ``(true, predicted)`` mistakes, most frequent first."""
    counts: Dict[Tuple[str, str], int] = {}
    for true_label, predicted_label in zip(true_labels, predicted_labels):
        if true_label == predicted_label:
            continue
        key = (str(true_label), str(predicted_label))
        counts[key] = counts.get(key, 0) + 1
    ranked = sorted(counts.items(), key=lambda item: item[1], reverse=True)
    return [(true, predicted, count) for (true, predicted), count in ranked[:top_k]]


def compare_one_vs_rest(
    config: Config,
    records: Sequence[ManifestRecord],
    multiclass_model,
    extractor,
    augmentor=None,
    phrases: Optional[Sequence[str]] = None,
    eval_split: Optional[str] = None,
    epochs: Optional[int] = None,
    fresh: bool = True,
    run_prefix: str = "ovr",
    threshold: Optional[float] = None,
    verbose: int = 0,
    progress: ProgressFn = None,
) -> Tuple[ComparisonReport, List[OneVsRestModel]]:
    """Train one binary model per phrase and score it against ``multiclass_model``.

    ``multiclass_model`` is the already-trained model from section 03 - the
    experiment never retrains it, so it is compared exactly as it would ship.

    Returns the report and the trained detectors, so the notebook can keep
    probing them without paying for the training twice.
    """
    unknown = config.paths.unknown_class
    class_names = class_names_from_manifest(records)
    targets = list(phrases) if phrases else phrase_labels(class_names, unknown)
    missing = [label for label in targets if label not in class_names]
    if missing:
        raise ValueError(
            f"{', '.join(missing)} are not classes in the manifest "
            f"(it has {', '.join(class_names)})"
        )
    if not targets:
        raise ValueError("no phrases to compare - the manifest holds only 'unknown'")

    split_name = eval_split or config.evaluation.split
    eval_records = filter_split(records, split_name)
    if not eval_records:
        raise ValueError(f"the '{split_name}' split is empty - re-run preprocessing")

    # Built once, with no shuffling and no augmentation, and reused for every
    # model: each one sees byte-identical tensors, so a difference in the numbers
    # below cannot come from the input pipeline.
    eval_dataset = make_tf_dataset(
        eval_records,
        config,
        extractor,
        training=False,
        batch_size=config.evaluation.batch_size,
        shuffle=False,
    )

    truth, multiclass_probabilities = predict_dataset(multiclass_model, eval_dataset)
    if multiclass_probabilities.shape[1] != len(class_names):
        raise ValueError(
            f"the multi-class model has {multiclass_probabilities.shape[1]} outputs but "
            f"the manifest declares {len(class_names)} classes - it was trained on a "
            f"different class vocabulary, so the two are not comparable"
        )

    detectors: List[OneVsRestModel] = []
    binary_scores: Dict[str, np.ndarray] = {}
    comparisons: List[PhraseComparison] = []

    for position, label in enumerate(targets, start=1):
        if progress:
            progress(label, position, len(targets))
        LOGGER.info("training one-vs-rest detector %d/%d for '%s'", position, len(targets), label)
        detector = train_one_vs_rest(
            config,
            records,
            label,
            extractor,
            augmentor=augmentor,
            run_name=f"{run_prefix}_{label}",
            epochs=epochs,
            fresh=fresh,
            verbose=verbose,
        )
        detectors.append(detector)

        _, probabilities = predict_dataset(detector.model, eval_dataset)
        scores = probabilities[:, POSITIVE_INDEX].astype(np.float32)
        binary_scores[label] = scores

        positive = (truth == class_names.index(label)).astype(np.int32)
        multiclass_scores = multiclass_probabilities[:, class_names.index(label)]
        comparisons.append(
            PhraseComparison(
                label=label,
                support=int(positive.sum()),
                negatives=int(positive.size - positive.sum()),
                auc_multiclass=_safe_auc(positive, multiclass_scores),
                auc_binary=_safe_auc(positive, scores),
                ap_multiclass=_safe_ap(positive, multiclass_scores),
                ap_binary=_safe_ap(positive, scores),
                binary_epochs=detector.epochs_completed,
                binary_val_accuracy=detector.best_val_accuracy,
            )
        )

    report = ComparisonReport(
        eval_split=split_name,
        num_eval_clips=len(eval_records),
        class_names=class_names,
        phrases=targets,
        per_phrase=comparisons,
        discrimination=_discrimination(
            truth, multiclass_probabilities, binary_scores, class_names, targets
        ),
        rejection=_rejection(
            truth,
            multiclass_probabilities,
            binary_scores,
            class_names,
            targets,
            unknown,
            config.evaluation.confidence_threshold if threshold is None else threshold,
        ),
        epochs=epochs or config.training.epochs,
    )
    return report, detectors


def _discrimination(
    truth: np.ndarray,
    multiclass_probabilities: np.ndarray,
    binary_scores: Dict[str, np.ndarray],
    class_names: Sequence[str],
    targets: Sequence[str],
) -> Optional[DiscriminationResult]:
    """Score both approaches at naming the phrase, on phrase clips only."""
    if len(targets) < 2:
        # With one phrase there is nothing to tell apart, and a committee of one
        # always answers with its only member.
        return None

    indices = [list(class_names).index(label) for label in targets]
    mask = np.isin(truth, indices)
    if not mask.any():
        return None

    true_labels = np.array([class_names[index] for index in truth[mask]])

    # The multi-class model restricted to the phrases under test, so both sides
    # choose from the same set of answers and `unknown` cannot absorb a mistake.
    restricted = multiclass_probabilities[mask][:, indices]
    multiclass_labels = np.array([targets[index] for index in restricted.argmax(axis=1)])

    stacked = np.stack([binary_scores[label][mask] for label in targets], axis=1)
    committee_labels = np.array([targets[index] for index in stacked.argmax(axis=1)])

    return DiscriminationResult(
        num_clips=int(mask.sum()),
        num_phrases=len(targets),
        multiclass_correct=int((multiclass_labels == true_labels).sum()),
        committee_correct=int((committee_labels == true_labels).sum()),
        multiclass_confusions=_confusions(true_labels, multiclass_labels),
        committee_confusions=_confusions(true_labels, committee_labels),
    )


def _rejection(
    truth: np.ndarray,
    multiclass_probabilities: np.ndarray,
    binary_scores: Dict[str, np.ndarray],
    class_names: Sequence[str],
    targets: Sequence[str],
    unknown_class: str,
    threshold: float,
) -> Optional[RejectionResult]:
    """How often each approach fires, on unknown clips and on real phrases."""
    if unknown_class not in class_names:
        return None

    indices = [list(class_names).index(label) for label in targets]
    unknown_mask = truth == list(class_names).index(unknown_class)
    phrase_mask = np.isin(truth, indices)

    # Same rule for both sides: "accepted" means the best phrase score cleared
    # the threshold. The multi-class model's `unknown` output is not consulted,
    # so neither approach is scored by a mechanism the other lacks.
    multiclass_best = multiclass_probabilities[:, indices].max(axis=1)
    committee_best = np.stack([binary_scores[label] for label in targets], axis=1).max(axis=1)

    return RejectionResult(
        threshold=float(threshold),
        unknown_clips=int(unknown_mask.sum()),
        multiclass_unknown_accepted=int((multiclass_best[unknown_mask] >= threshold).sum()),
        committee_unknown_accepted=int((committee_best[unknown_mask] >= threshold).sum()),
        phrase_clips=int(phrase_mask.sum()),
        multiclass_phrase_accepted=int((multiclass_best[phrase_mask] >= threshold).sum()),
        committee_phrase_accepted=int((committee_best[phrase_mask] >= threshold).sum()),
    )

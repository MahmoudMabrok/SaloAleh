"""Evaluation metrics and error analysis.

:func:`evaluate_model` runs the model over a dataset once and returns an
:class:`EvaluationResult` that carries the raw predictions, so every downstream
figure and table is computed from the same forward pass.
"""

from __future__ import annotations

import csv
import json
import logging
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple, Union

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    auc,
    confusion_matrix,
    precision_recall_fscore_support,
    roc_curve,
)

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "ClassMetrics",
    "ErrorCase",
    "EvaluationResult",
    "NegativeTypeMetrics",
    "evaluate_model",
    "predict_dataset",
    "wilson_interval",
]


def _jsonable(value):
    """Replace non-finite floats with null so the report is valid JSON.

    A rate is genuinely undefined when its denominator is empty - a class with no
    negatives has no false-positive rate - and ``json.dumps`` would write a bare
    ``NaN``, which Python reads back and every other JSON parser rejects.
    """
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, (np.floating, np.integer)):
        return _jsonable(value.item())
    if isinstance(value, float) and not np.isfinite(value):
        return None
    return value


def wilson_interval(proportion: float, count: int, z: float = 1.96) -> Tuple[float, float]:
    """Wilson score interval for ``proportion`` measured on ``count`` samples.

    A split of a few dozen clips cannot measure a model: a proportion over ``n``
    samples only takes values in steps of ``1/n``, and its interval spans most of
    the range. Reporting the interval next to the point estimate keeps a number
    like "0.10 on 10 clips" from being read as a result.
    """
    n = float(count)
    if n <= 0:
        return (float("nan"), float("nan"))
    p = float(proportion)
    denominator = 1.0 + z * z / n
    center = (p + z * z / (2.0 * n)) / denominator
    half = z * np.sqrt(p * (1.0 - p) / n + z * z / (4.0 * n * n)) / denominator
    return (float(max(center - half, 0.0)), float(min(center + half, 1.0)))


@dataclass
class ClassMetrics:
    label: str
    support: int
    precision: float
    recall: float
    f1: float
    accuracy: float
    false_positives: int
    false_negatives: int


@dataclass
class ErrorCase:
    """One misclassified clip."""

    path: str
    true_label: str
    predicted_label: str
    confidence: float
    true_class_probability: float


@dataclass
class NegativeTypeMetrics:
    """False positives from one kind of negative audio.

    ``hard_negative`` is the row that matters: those are the near-miss phrases
    (``سبحان الله`` against ``سبحان الله وبحمده``) the counter must not fire on,
    and a model can look excellent overall while failing exactly here.
    """

    negative_type: str
    clips: int
    accepted: int
    predicted_as: Dict[str, int] = field(default_factory=dict)
    highest_confidence: float = 0.0

    @property
    def false_positive_rate(self) -> float:
        return self.accepted / self.clips if self.clips else float("nan")

    def interval(self) -> Tuple[float, float]:
        return wilson_interval(self.false_positive_rate, self.clips)


@dataclass
class EvaluationResult:
    """Predictions plus every metric derived from them."""

    class_names: List[str]
    y_true: np.ndarray
    y_pred: np.ndarray
    y_prob: np.ndarray
    paths: List[str] = field(default_factory=list)
    confidence_threshold: float = 0.5
    # Per-clip negative category (``hard_negative``, ``noise``, ...) for unknown
    # clips, empty for target phrases. Carried from the manifest so the error
    # analysis can say *what kind* of audio the model fires on.
    negative_types: List[str] = field(default_factory=list)
    unknown_class: str = "unknown"

    # -- headline numbers ---------------------------------------------------
    @property
    def num_samples(self) -> int:
        return int(self.y_true.size)

    @property
    def accuracy(self) -> float:
        return float(accuracy_score(self.y_true, self.y_pred))

    @property
    def confidence(self) -> np.ndarray:
        return self.y_prob.max(axis=1)

    def accuracy_interval(self, z: float = 1.96) -> Tuple[float, float]:
        """95% Wilson score interval for the accuracy."""
        return wilson_interval(self.accuracy, self.num_samples, z)

    def prediction_distribution(self) -> Dict[str, int]:
        """How many clips were predicted as each class, most-predicted first."""
        counts = np.bincount(self.y_pred, minlength=len(self.class_names))
        order = np.argsort(counts)[::-1]
        return {self.class_names[index]: int(counts[index]) for index in order}

    @property
    def collapsed_to(self) -> Optional[str]:
        """The single class every clip was predicted as, when that happened.

        A collapsed model scores exactly ``1 / num_classes`` on a balanced split,
        which reads as "chance" but has a very different cause from noisy
        predictions spread over all classes.
        """
        unique = np.unique(self.y_pred)
        if unique.size != 1 or self.num_samples == 0:
            return None
        return self.class_names[int(unique[0])]

    def averaged(self, average: str = "macro") -> Dict[str, float]:
        precision, recall, f1, _ = precision_recall_fscore_support(
            self.y_true,
            self.y_pred,
            average=average,
            labels=list(range(len(self.class_names))),
            zero_division=0,
        )
        return {"precision": float(precision), "recall": float(recall), "f1": float(f1)}

    @property
    def confusion_matrix(self) -> np.ndarray:
        return confusion_matrix(
            self.y_true, self.y_pred, labels=list(range(len(self.class_names)))
        )

    def per_class(self) -> List[ClassMetrics]:
        matrix = self.confusion_matrix
        precision, recall, f1, support = precision_recall_fscore_support(
            self.y_true,
            self.y_pred,
            average=None,
            labels=list(range(len(self.class_names))),
            zero_division=0,
        )
        results: List[ClassMetrics] = []
        for index, label in enumerate(self.class_names):
            true_positive = int(matrix[index, index])
            false_negative = int(matrix[index, :].sum() - true_positive)
            false_positive = int(matrix[:, index].sum() - true_positive)
            results.append(
                ClassMetrics(
                    label=label,
                    support=int(support[index]),
                    precision=float(precision[index]),
                    recall=float(recall[index]),
                    f1=float(f1[index]),
                    accuracy=float(true_positive / support[index]) if support[index] else 0.0,
                    false_positives=false_positive,
                    false_negatives=false_negative,
                )
            )
        return results

    # -- error analysis -----------------------------------------------------
    def _cases(self, mask: np.ndarray, limit: int) -> List[ErrorCase]:
        cases: List[ErrorCase] = []
        for position in np.flatnonzero(mask)[:limit]:
            true_index = int(self.y_true[position])
            predicted_index = int(self.y_pred[position])
            cases.append(
                ErrorCase(
                    path=self.paths[position] if position < len(self.paths) else "",
                    true_label=self.class_names[true_index],
                    predicted_label=self.class_names[predicted_index],
                    confidence=float(self.y_prob[position, predicted_index]),
                    true_class_probability=float(self.y_prob[position, true_index]),
                )
            )
        return cases

    def false_positives(self, label: str, limit: int = 12) -> List[ErrorCase]:
        """Clips of another class that were predicted as ``label``."""
        index = self.class_names.index(label)
        mask = (self.y_pred == index) & (self.y_true != index)
        return self._cases(mask, limit)

    def false_negatives(self, label: str, limit: int = 12) -> List[ErrorCase]:
        """Clips of ``label`` that were predicted as something else."""
        index = self.class_names.index(label)
        mask = (self.y_true == index) & (self.y_pred != index)
        return self._cases(mask, limit)

    def all_errors(self, limit: Optional[int] = None) -> List[ErrorCase]:
        mask = self.y_true != self.y_pred
        return self._cases(mask, limit if limit is not None else int(mask.sum()))

    def top_confusions(self, top_k: int = 15) -> List[Tuple[str, str, int]]:
        """Most frequent ``(true, predicted)`` mistakes."""
        matrix = self.confusion_matrix
        pairs: List[Tuple[str, str, int]] = []
        for true_index in range(len(self.class_names)):
            for predicted_index in range(len(self.class_names)):
                if true_index == predicted_index:
                    continue
                count = int(matrix[true_index, predicted_index])
                if count:
                    pairs.append(
                        (
                            self.class_names[true_index],
                            self.class_names[predicted_index],
                            count,
                        )
                    )
        pairs.sort(key=lambda item: item[2], reverse=True)
        return pairs[:top_k]

    # -- the confusions that decide this product ---------------------------
    @property
    def target_indices(self) -> List[int]:
        return [
            index
            for index, name in enumerate(self.class_names)
            if name != self.unknown_class
        ]

    @property
    def unknown_index(self) -> Optional[int]:
        if self.unknown_class not in self.class_names:
            return None
        return self.class_names.index(self.unknown_class)

    def false_positive_rate(self, label: str) -> float:
        """Share of clips that are *not* ``label`` which were predicted as it.

        Per-class precision hides this when the classes are unbalanced; the
        false-positive rate is what turns into activations on the phone.
        """
        index = self.class_names.index(label)
        negatives = self.y_true != index
        if not negatives.any():
            return float("nan")
        return float(((self.y_pred == index) & negatives).sum() / negatives.sum())

    def directional_confusions(self) -> Dict[str, object]:
        """The three confusions that matter here, named rather than ranked.

        * **unknown -> target**: firing at audio that is not a dhikr. Every one of
          these is a false count in the app - the expensive direction.
        * **target -> unknown**: staying quiet on a real dhikr. A miss; the user
          sees it and says it again.
        * **target A -> target B**: counting the wrong phrase. The nested phrases
          (``006`` ⊂ ``007``) live here, and a top-line accuracy can look fine
          while this is most of the error.
        """
        matrix = self.confusion_matrix
        targets = self.target_indices
        unknown = self.unknown_index

        unknown_to_target = 0
        target_to_unknown = 0
        if unknown is not None:
            unknown_to_target = int(sum(matrix[unknown, index] for index in targets))
            target_to_unknown = int(sum(matrix[index, unknown] for index in targets))

        cross: List[Dict[str, object]] = []
        for true_index in targets:
            for predicted_index in targets:
                if true_index == predicted_index:
                    continue
                count = int(matrix[true_index, predicted_index])
                if count:
                    cross.append(
                        {
                            "true": self.class_names[true_index],
                            "predicted": self.class_names[predicted_index],
                            "count": count,
                        }
                    )
        cross.sort(key=lambda item: item["count"], reverse=True)

        unknown_support = (
            int((self.y_true == unknown).sum()) if unknown is not None else 0
        )
        target_support = int(np.isin(self.y_true, targets).sum())
        return {
            "unknown_to_target": unknown_to_target,
            "unknown_to_target_rate": (
                unknown_to_target / unknown_support if unknown_support else float("nan")
            ),
            "target_to_unknown": target_to_unknown,
            "target_to_unknown_rate": (
                target_to_unknown / target_support if target_support else float("nan")
            ),
            "target_to_target": cross,
            "target_to_target_total": int(sum(item["count"] for item in cross)),
        }

    def by_negative_type(
        self, threshold: Optional[float] = None
    ) -> List[NegativeTypeMetrics]:
        """False positives grouped by the kind of negative audio.

        This is what turns "the model has some false positives" into "the model
        fires on partial phrases and on nothing else", which is a data-collection
        instruction rather than a mystery.
        """
        if not self.negative_types or len(self.negative_types) != self.num_samples:
            return []
        limit = self.confidence_threshold if threshold is None else threshold
        targets = self.target_indices
        if not targets:
            return []

        scores = self.y_prob[:, targets]
        best = scores.max(axis=1)
        chosen = np.array([targets[index] for index in scores.argmax(axis=1)])
        accepted = best >= limit

        grouped: Dict[str, List[int]] = {}
        for position, category in enumerate(self.negative_types):
            if not category:
                continue
            grouped.setdefault(category, []).append(position)

        results: List[NegativeTypeMetrics] = []
        for category, positions in sorted(grouped.items()):
            indices = np.asarray(positions)
            fired = indices[accepted[indices]]
            predicted: Dict[str, int] = {}
            for position in fired:
                name = self.class_names[int(chosen[position])]
                predicted[name] = predicted.get(name, 0) + 1
            results.append(
                NegativeTypeMetrics(
                    negative_type=category,
                    clips=int(indices.size),
                    accepted=int(fired.size),
                    predicted_as=dict(sorted(predicted.items())),
                    highest_confidence=float(best[indices].max()) if indices.size else 0.0,
                )
            )
        results.sort(key=lambda item: (-item.false_positive_rate, item.negative_type))
        return results

    def rejection_stats(self, threshold: Optional[float] = None) -> Dict[str, float]:
        """How the model behaves when low-confidence predictions are rejected.

        On-device this is the "did anyone actually say a dhikr?" gate.
        """
        limit = self.confidence_threshold if threshold is None else threshold
        confidence = self.confidence
        accepted = confidence >= limit
        correct = self.y_true == self.y_pred
        accepted_count = int(accepted.sum())
        return {
            "threshold": float(limit),
            "accept_rate": float(accepted.mean()) if confidence.size else 0.0,
            "accuracy_on_accepted": (
                float(correct[accepted].mean()) if accepted_count else 0.0
            ),
            "errors_rejected": int((~accepted & ~correct).sum()),
            "correct_rejected": int((~accepted & correct).sum()),
        }

    # -- ROC ----------------------------------------------------------------
    def roc_curves(self) -> Dict[str, Dict[str, object]]:
        """One-vs-rest ROC per class plus the macro average AUC."""
        curves: Dict[str, Dict[str, object]] = {}
        aucs: List[float] = []
        for index, label in enumerate(self.class_names):
            binary_true = (self.y_true == index).astype(np.int32)
            if binary_true.sum() == 0 or binary_true.sum() == binary_true.size:
                continue  # AUC is undefined for a class with no negatives/positives
            false_positive_rate, true_positive_rate, _ = roc_curve(
                binary_true, self.y_prob[:, index]
            )
            area = float(auc(false_positive_rate, true_positive_rate))
            aucs.append(area)
            curves[label] = {
                "fpr": false_positive_rate.tolist(),
                "tpr": true_positive_rate.tolist(),
                "auc": area,
            }
        if aucs:
            curves["__macro__"] = {"auc": float(np.mean(aucs))}
        return curves

    # -- reporting ----------------------------------------------------------
    def to_dict(self) -> Dict[str, object]:
        return {
            "num_samples": self.num_samples,
            "accuracy": self.accuracy,
            "macro": self.averaged("macro"),
            "weighted": self.averaged("weighted"),
            "rejection": self.rejection_stats(),
            "per_class": [asdict(item) for item in self.per_class()],
            "top_confusions": [
                {"true": true, "predicted": predicted, "count": count}
                for true, predicted, count in self.top_confusions()
            ],
            "directional_confusions": self.directional_confusions(),
            "false_positive_rate": {
                label: self.false_positive_rate(label) for label in self.class_names
            },
            "by_negative_type": [
                {
                    **asdict(item),
                    "false_positive_rate": item.false_positive_rate,
                }
                for item in self.by_negative_type()
            ],
            "class_names": list(self.class_names),
        }

    def summary(self) -> str:
        macro = self.averaged("macro")
        weighted = self.averaged("weighted")
        low, high = self.accuracy_interval()
        lines = [
            f"samples   : {self.num_samples}",
            f"accuracy  : {self.accuracy:.4f} (95% CI {low:.3f}-{high:.3f})",
            f"macro     : P {macro['precision']:.4f} | R {macro['recall']:.4f} | "
            f"F1 {macro['f1']:.4f}",
            f"weighted  : P {weighted['precision']:.4f} | R {weighted['recall']:.4f} | "
            f"F1 {weighted['f1']:.4f}",
        ]
        per_class = self.num_samples / max(len(self.class_names), 1)
        if per_class < 10:
            lines.append(
                f"\n!! {self.num_samples} clips over {len(self.class_names)} classes is "
                f"{per_class:.1f} per class. Accuracy here only moves in steps of "
                f"{1.0 / max(self.num_samples, 1):.2f} and the interval above covers most of "
                f"the range - this split cannot tell a good model from a bad one. Collect "
                f"more recordings before reading anything into it."
            )
        directional = self.directional_confusions()
        if self.unknown_index is not None:
            lines.append(
                f"\nunknown -> target : {directional['unknown_to_target']} clips "
                f"({directional['unknown_to_target_rate']:.1%} of unknown) - these "
                f"become false counts in the app"
            )
            lines.append(
                f"target -> unknown : {directional['target_to_unknown']} clips "
                f"({directional['target_to_unknown_rate']:.1%} of dhikr) - these are "
                f"misses"
            )
        if directional["target_to_target"]:
            worst = directional["target_to_target"][0]
            lines.append(
                f"phrase confusion  : {directional['target_to_target_total']} clip(s) "
                f"named as the wrong phrase, worst {worst['true']} -> "
                f"{worst['predicted']} ({worst['count']})"
            )

        for item in self.by_negative_type():
            low, high = item.interval()
            lines.append(
                f"  {item.negative_type:<16}{item.accepted:>4}/{item.clips:<4} "
                f"accepted as a dhikr ({item.false_positive_rate:.1%}, "
                f"95% CI {low:.1%}-{high:.1%})"
            )

        collapsed = self.collapsed_to
        if collapsed is not None:
            lines.append(
                f"\n!! every clip was predicted as '{collapsed}'. The model output does not "
                f"depend on its input, so this accuracy is just that class's share of the "
                f"split - not a partially working model."
            )
        return "\n".join(lines)

    def to_dataframe(self):
        import pandas as pd  # lazily imported: only notebooks need pandas

        return pd.DataFrame([asdict(item) for item in self.per_class()])

    def save(self, directory: PathLike, name: str = "evaluation") -> Dict[str, Path]:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)

        json_path = target / f"{name}.json"
        json_path.write_text(
            json.dumps(_jsonable(self.to_dict()), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )

        csv_path = target / f"{name}_per_class.csv"
        rows = [asdict(item) for item in self.per_class()]
        with open(csv_path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()) if rows else ["label"])
            writer.writeheader()
            writer.writerows(rows)

        errors_path = target / f"{name}_errors.csv"
        error_rows = [asdict(case) for case in self.all_errors()]
        with open(errors_path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=list(error_rows[0].keys())
                if error_rows
                else ["path", "true_label", "predicted_label", "confidence"],
            )
            writer.writeheader()
            writer.writerows(error_rows)

        matrix_path = target / f"{name}_confusion_matrix.csv"
        with open(matrix_path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["true\\predicted", *self.class_names])
            for label, row in zip(self.class_names, self.confusion_matrix):
                writer.writerow([label, *[int(value) for value in row]])

        LOGGER.info("evaluation artefacts written to %s", target)
        return {
            "json": json_path,
            "per_class": csv_path,
            "errors": errors_path,
            "confusion_matrix": matrix_path,
        }


def predict_dataset(model, dataset) -> Tuple[np.ndarray, np.ndarray]:
    """Run the model over a batched ``(features, label)`` dataset once."""
    probabilities: List[np.ndarray] = []
    labels: List[np.ndarray] = []
    for batch_features, batch_labels in dataset:
        probabilities.append(np.asarray(model(batch_features, training=False)))
        labels.append(np.asarray(batch_labels))
    if not probabilities:
        raise ValueError("the evaluation dataset yielded no batches")
    return (
        np.concatenate(labels).astype(np.int32).ravel(),
        np.concatenate(probabilities).astype(np.float32),
    )


def evaluate_model(
    model,
    dataset,
    class_names: Sequence[str],
    paths: Optional[Sequence[str]] = None,
    confidence_threshold: float = 0.5,
    negative_types: Optional[Sequence[str]] = None,
    unknown_class: str = "unknown",
) -> EvaluationResult:
    """Predict and wrap the result.

    ``paths`` and ``negative_types`` must line up with the dataset order, so
    build the evaluation dataset with ``shuffle=False`` (the default for
    non-training splits). ``negative_types`` comes straight from the manifest and
    is what lets the report attribute false positives to a kind of audio.
    """
    y_true, y_prob = predict_dataset(model, dataset)
    y_pred = y_prob.argmax(axis=1).astype(np.int32)
    ordered_paths = list(paths or [])
    if ordered_paths and len(ordered_paths) != y_true.size:
        LOGGER.warning(
            "%d paths supplied for %d predictions - dropping paths from error analysis",
            len(ordered_paths),
            y_true.size,
        )
        ordered_paths = []
    categories = list(negative_types or [])
    if categories and len(categories) != y_true.size:
        LOGGER.warning(
            "%d negative types supplied for %d predictions - dropping the "
            "per-category breakdown",
            len(categories),
            y_true.size,
        )
        categories = []
    return EvaluationResult(
        class_names=list(class_names),
        y_true=y_true,
        y_pred=y_pred,
        y_prob=y_prob,
        paths=ordered_paths,
        confidence_threshold=confidence_threshold,
        negative_types=categories,
        unknown_class=unknown_class,
    )

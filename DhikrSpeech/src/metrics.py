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
    "evaluate_model",
    "predict_dataset",
]


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
class EvaluationResult:
    """Predictions plus every metric derived from them."""

    class_names: List[str]
    y_true: np.ndarray
    y_pred: np.ndarray
    y_prob: np.ndarray
    paths: List[str] = field(default_factory=list)
    confidence_threshold: float = 0.5

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
            "class_names": list(self.class_names),
        }

    def summary(self) -> str:
        macro = self.averaged("macro")
        weighted = self.averaged("weighted")
        lines = [
            f"samples   : {self.num_samples}",
            f"accuracy  : {self.accuracy:.4f}",
            f"macro     : P {macro['precision']:.4f} | R {macro['recall']:.4f} | "
            f"F1 {macro['f1']:.4f}",
            f"weighted  : P {weighted['precision']:.4f} | R {weighted['recall']:.4f} | "
            f"F1 {weighted['f1']:.4f}",
        ]
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
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
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
) -> EvaluationResult:
    """Predict and wrap the result.

    ``paths`` must line up with the dataset order, so build the evaluation
    dataset with ``shuffle=False`` (the default for non-training splits).
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
    return EvaluationResult(
        class_names=list(class_names),
        y_true=y_true,
        y_pred=y_pred,
        y_prob=y_prob,
        paths=ordered_paths,
        confidence_threshold=confidence_threshold,
    )

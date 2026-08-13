"""Matplotlib figures for the notebooks.

Every function returns a ``Figure`` so the caller decides whether to display it,
save it, or both; :func:`save_figure` writes one to the reports folder.

Note on Arabic: matplotlib does not shape or reorder Arabic text, so plots are
labelled with class folder ids (``001``, ``unknown``) rather than phrase text.
The id -> phrase mapping is printed as a table in notebook 01 instead.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Dict, Mapping, Optional, Sequence, Union

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.figure import Figure

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "plot_architecture_comparison",
    "plot_class_distribution",
    "plot_confidence_distribution",
    "plot_confusion_matrix",
    "plot_detector_scores",
    "plot_duration_histogram",
    "plot_log_mel",
    "plot_negative_type_false_positives",
    "plot_per_class_metrics",
    "plot_roc_curves",
    "plot_score_timeline",
    "plot_speaker_distribution",
    "plot_threshold_sweep",
    "plot_training_history",
    "plot_waveform",
    "save_figure",
]

_GRID = {"alpha": 0.3, "linestyle": "--", "linewidth": 0.6}


def save_figure(figure: Figure, path: PathLike, dpi: int = 150) -> Path:
    """Write a figure to disk, creating parent directories."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(destination, dpi=dpi, bbox_inches="tight")
    LOGGER.info("figure saved to %s", destination)
    return destination


def plot_class_distribution(
    counts: Mapping[str, int],
    title: str = "Recordings per class",
    highlight_below: Optional[int] = None,
) -> Figure:
    """Bar chart of recordings per class, optionally flagging thin classes."""
    labels = list(counts.keys())
    values = [counts[label] for label in labels]
    figure, axis = plt.subplots(figsize=(max(6.0, len(labels) * 0.55), 4.0))

    colors = ["#2a9d8f"] * len(values)
    if highlight_below is not None:
        colors = ["#e76f51" if value < highlight_below else "#2a9d8f" for value in values]

    axis.bar(labels, values, color=colors)
    axis.set_title(title)
    axis.set_xlabel("class")
    axis.set_ylabel("recordings")
    axis.tick_params(axis="x", rotation=90)
    axis.grid(axis="y", **_GRID)

    if values:
        mean = float(np.mean(values))
        axis.axhline(mean, color="#264653", linewidth=1.0, label=f"mean {mean:.0f}")
        if highlight_below is not None:
            axis.axhline(
                highlight_below,
                color="#e76f51",
                linewidth=1.0,
                linestyle=":",
                label=f"minimum {highlight_below}",
            )
        axis.legend(loc="upper right", fontsize=8)

    figure.tight_layout()
    return figure


def plot_duration_histogram(
    durations: Sequence[float],
    bins: int = 40,
    min_duration: Optional[float] = None,
    max_duration: Optional[float] = None,
    title: str = "Recording duration",
) -> Figure:
    """Histogram of clip durations with the configured accept window marked."""
    figure, axis = plt.subplots(figsize=(7.0, 4.0))
    values = np.asarray(list(durations), dtype=np.float64)
    if values.size:
        axis.hist(values, bins=bins, color="#457b9d", edgecolor="white")
        axis.axvline(
            float(values.mean()),
            color="#264653",
            linewidth=1.2,
            label=f"mean {values.mean():.2f} s",
        )
    for bound, label in ((min_duration, "min"), (max_duration, "max")):
        if bound is not None:
            axis.axvline(
                bound, color="#e76f51", linestyle=":", linewidth=1.2, label=f"{label} {bound:g} s"
            )
    axis.set_title(title)
    axis.set_xlabel("seconds")
    axis.set_ylabel("recordings")
    axis.grid(axis="y", **_GRID)
    axis.legend(fontsize=8)
    figure.tight_layout()
    return figure


def plot_waveform(
    samples: np.ndarray, sample_rate: int, title: str = "waveform"
) -> Figure:
    figure, axis = plt.subplots(figsize=(8.0, 2.4))
    time_axis = np.arange(samples.size) / float(sample_rate)
    axis.plot(time_axis, samples, linewidth=0.6, color="#264653")
    axis.set_title(title)
    axis.set_xlabel("seconds")
    axis.set_ylabel("amplitude")
    axis.set_ylim(-1.05, 1.05)
    axis.grid(**_GRID)
    figure.tight_layout()
    return figure


def plot_log_mel(
    features: np.ndarray,
    hop_ms: float = 10.0,
    title: str = "log mel spectrogram",
    axis=None,
) -> Figure:
    """Render a ``(frames, n_mels)`` feature array."""
    created = axis is None
    if created:
        figure, axis = plt.subplots(figsize=(7.0, 3.0))
    else:
        figure = axis.get_figure()

    duration = features.shape[0] * hop_ms / 1000.0
    image = axis.imshow(
        features.T,
        origin="lower",
        aspect="auto",
        cmap="magma",
        extent=(0.0, duration, 0.0, features.shape[1]),
    )
    axis.set_title(title, fontsize=9)
    axis.set_xlabel("seconds")
    axis.set_ylabel("mel bin")
    if created:
        figure.colorbar(image, ax=axis, shrink=0.85)
        figure.tight_layout()
    return figure


def plot_feature_grid(
    features: Sequence[np.ndarray],
    titles: Sequence[str],
    hop_ms: float = 10.0,
    columns: int = 3,
) -> Figure:
    """Grid of log mel previews, one per supplied feature array."""
    count = len(features)
    if count == 0:
        raise ValueError("nothing to plot")
    columns = max(1, min(columns, count))
    rows = int(np.ceil(count / columns))
    figure, axes = plt.subplots(rows, columns, figsize=(4.2 * columns, 2.6 * rows))
    flat = np.atleast_1d(np.asarray(axes)).ravel()
    for position, axis in enumerate(flat):
        if position < count:
            plot_log_mel(features[position], hop_ms=hop_ms, title=titles[position], axis=axis)
        else:
            axis.axis("off")
    figure.tight_layout()
    return figure


def plot_training_history(
    history: Mapping[str, Sequence[float]], title: str = "training history"
) -> Figure:
    """Training curves, including detector metrics when present."""
    panels = [("loss", "val_loss", "loss"), ("accuracy", "val_accuracy", "accuracy")]
    for train_key, val_key, label in (
        ("target_f1", "val_target_f1", "target F1"),
        ("pr_auc", "val_pr_auc", "PR AUC"),
        ("precision", "val_precision", "target precision"),
        ("recall", "val_recall", "target recall"),
    ):
        if train_key in history or val_key in history:
            panels.append((train_key, val_key, label))
    has_lr = any(key in history for key in ("lr", "learning_rate"))
    total = len(panels) + (1 if has_lr else 0)

    figure, axes = plt.subplots(1, total, figsize=(5.2 * total, 3.8))
    axes = np.atleast_1d(axes)

    for axis, (train_key, val_key, label) in zip(axes, panels):
        if train_key in history:
            axis.plot(
                range(1, len(history[train_key]) + 1),
                history[train_key],
                label=f"train {label}",
                color="#2a9d8f",
            )
        if val_key in history:
            axis.plot(
                range(1, len(history[val_key]) + 1),
                history[val_key],
                label=f"val {label}",
                color="#e76f51",
            )
            best = int(np.argmin(history[val_key])) if label == "loss" else int(
                np.argmax(history[val_key])
            )
            axis.axvline(best + 1, color="#264653", linestyle=":", linewidth=1.0)
        axis.set_xlabel("epoch")
        axis.set_ylabel(label)
        axis.grid(**_GRID)
        axis.legend(fontsize=8)

    if has_lr:
        key = "lr" if "lr" in history else "learning_rate"
        axes[-1].plot(range(1, len(history[key]) + 1), history[key], color="#457b9d")
        axes[-1].set_xlabel("epoch")
        axes[-1].set_ylabel("learning rate")
        axes[-1].set_yscale("log")
        axes[-1].grid(**_GRID)

    figure.suptitle(title)
    figure.tight_layout()
    return figure


def plot_confusion_matrix(
    matrix: np.ndarray,
    class_names: Sequence[str],
    normalize: bool = True,
    title: str = "confusion matrix",
    annotate_threshold: int = 25,
) -> Figure:
    """Confusion matrix heatmap; cells are annotated for small class counts."""
    data = np.asarray(matrix, dtype=np.float64)
    if normalize:
        row_sums = data.sum(axis=1, keepdims=True)
        data = np.divide(data, row_sums, out=np.zeros_like(data), where=row_sums > 0)

    size = max(5.0, len(class_names) * 0.45)
    figure, axis = plt.subplots(figsize=(size, size * 0.9))
    image = axis.imshow(data, cmap="Blues", vmin=0.0, vmax=data.max() if data.size else 1.0)

    axis.set_xticks(range(len(class_names)))
    axis.set_yticks(range(len(class_names)))
    axis.set_xticklabels(class_names, rotation=90, fontsize=8)
    axis.set_yticklabels(class_names, fontsize=8)
    axis.set_xlabel("predicted")
    axis.set_ylabel("true")
    axis.set_title(title)

    if len(class_names) <= annotate_threshold:
        limit = data.max() if data.size else 1.0
        for row in range(data.shape[0]):
            for column in range(data.shape[1]):
                value = data[row, column]
                if value <= 0:
                    continue
                axis.text(
                    column,
                    row,
                    f"{value:.2f}" if normalize else f"{int(value)}",
                    ha="center",
                    va="center",
                    fontsize=7,
                    color="white" if value > limit * 0.6 else "#1d3557",
                )

    figure.colorbar(image, ax=axis, shrink=0.8)
    figure.tight_layout()
    return figure


def plot_per_class_metrics(
    per_class: Sequence,
    title: str = "per-class metrics",
) -> Figure:
    """Grouped precision / recall / F1 bars, one group per class."""
    labels = [item.label for item in per_class]
    precision = [item.precision for item in per_class]
    recall = [item.recall for item in per_class]
    f1_score = [item.f1 for item in per_class]

    positions = np.arange(len(labels))
    width = 0.27
    figure, axis = plt.subplots(figsize=(max(6.0, len(labels) * 0.65), 4.2))
    axis.bar(positions - width, precision, width, label="precision", color="#2a9d8f")
    axis.bar(positions, recall, width, label="recall", color="#457b9d")
    axis.bar(positions + width, f1_score, width, label="F1", color="#e9c46a")

    axis.set_xticks(positions)
    axis.set_xticklabels(labels, rotation=90, fontsize=8)
    axis.set_ylim(0.0, 1.05)
    axis.set_ylabel("score")
    axis.set_title(title)
    axis.grid(axis="y", **_GRID)
    axis.legend(fontsize=8)
    figure.tight_layout()
    return figure


def plot_roc_curves(
    curves: Dict[str, Dict[str, object]], title: str = "ROC (one-vs-rest)", max_classes: int = 20
) -> Figure:
    """One-vs-rest ROC curves, worst AUC first so problem classes stay visible."""
    entries = [
        (label, payload)
        for label, payload in curves.items()
        if label != "__macro__" and "fpr" in payload
    ]
    entries.sort(key=lambda item: float(item[1]["auc"]))

    figure, axis = plt.subplots(figsize=(5.6, 5.2))
    for label, payload in entries[:max_classes]:
        axis.plot(
            payload["fpr"],
            payload["tpr"],
            linewidth=1.0,
            label=f"{label} (AUC {float(payload['auc']):.3f})",
        )
    axis.plot([0, 1], [0, 1], color="#adb5bd", linestyle=":", linewidth=1.0)
    axis.set_xlabel("false positive rate")
    axis.set_ylabel("true positive rate")
    macro = curves.get("__macro__", {}).get("auc")
    axis.set_title(f"{title}\nmacro AUC {float(macro):.4f}" if macro else title)
    axis.grid(**_GRID)
    if entries:
        axis.legend(fontsize=6, loc="lower right")
    figure.tight_layout()
    return figure


def plot_confidence_distribution(
    confidence: np.ndarray,
    correct: np.ndarray,
    threshold: float = 0.5,
    title: str = "prediction confidence",
) -> Figure:
    """Confidence histogram split by correct / incorrect - picks the reject threshold."""
    figure, axis = plt.subplots(figsize=(7.0, 4.0))
    bins = np.linspace(0.0, 1.0, 41)
    axis.hist(
        confidence[correct.astype(bool)], bins=bins, alpha=0.75, label="correct", color="#2a9d8f"
    )
    axis.hist(
        confidence[~correct.astype(bool)],
        bins=bins,
        alpha=0.75,
        label="incorrect",
        color="#e76f51",
    )
    axis.axvline(
        threshold, color="#264653", linestyle=":", linewidth=1.2, label=f"threshold {threshold:g}"
    )
    axis.set_xlabel("max softmax probability")
    axis.set_ylabel("predictions")
    axis.set_title(title)
    axis.grid(axis="y", **_GRID)
    axis.legend(fontsize=8)
    figure.tight_layout()
    return figure


def plot_score_timeline(
    timeline,
    events: Sequence = (),
    truth: Sequence = (),
    activation: Optional[float] = None,
    release: Optional[float] = None,
    title: str = "streaming timeline",
) -> Figure:
    """``P(target)`` over a recording, with detections and ground truth.

    The single most useful picture in the project: a missed repetition, a phrase
    counted twice and a false activation on the TV all look completely different
    here, and identical in a table of accuracies.
    """
    figure, axis = plt.subplots(figsize=(12.0, 3.6))
    times = np.asarray(timeline.times, dtype=np.float32)
    scores = np.asarray(timeline.scores, dtype=np.float32)

    raw = getattr(timeline, "raw_scores", None)
    if raw is not None:
        axis.plot(times, np.asarray(raw), color="#adb5bd", linewidth=0.8, label="raw")
    axis.plot(times, scores, color="#264653", linewidth=1.3, label="P(target)")

    for index, (start, end) in enumerate(truth):
        axis.axvspan(
            start, end, color="#2a9d8f", alpha=0.18,
            label="annotated repetition" if index == 0 else None,
        )
    for index, event in enumerate(events):
        axis.axvline(
            event.time, color="#e76f51", linestyle="-", linewidth=1.4,
            label="detected event" if index == 0 else None,
        )
    if activation is not None:
        axis.axhline(activation, color="#e63946", linestyle="--", linewidth=1.0,
                     label=f"activation {activation:g}")
    if release is not None:
        axis.axhline(release, color="#f4a261", linestyle=":", linewidth=1.0,
                     label=f"release {release:g}")

    axis.set_xlabel("seconds")
    axis.set_ylabel("P(target)")
    axis.set_ylim(-0.02, 1.02)
    axis.set_xlim(float(times[0]) if times.size else 0.0, float(times[-1]) if times.size else 1.0)
    axis.set_title(title)
    axis.grid(**_GRID)
    axis.legend(fontsize=8, ncol=3, loc="upper right")

def plot_speaker_distribution(
    recordings_per_speaker: Mapping[str, int],
    title: str = "recordings per speaker",
    max_speakers: int = 40,
) -> Figure:
    """How lopsided the dataset is across voices.

    One speaker towering over the rest is the shape of a dataset that will score
    well on its own test split and disappoint on a stranger's phone.
    """
    items = sorted(recordings_per_speaker.items(), key=lambda item: item[1], reverse=True)
    shown = items[:max_speakers]
    labels = [name for name, _ in shown]
    values = [count for _, count in shown]

    figure, axis = plt.subplots(figsize=(max(6.0, len(labels) * 0.35), 4.0))
    axis.bar(labels, values, color="#457b9d")
    if values:
        mean = float(np.mean(list(recordings_per_speaker.values())))
        axis.axhline(mean, color="#264653", linewidth=1.0, label=f"mean {mean:.0f}")
        axis.legend(fontsize=8)
    axis.set_title(
        title
        + (f" (top {max_speakers} of {len(items)})" if len(items) > max_speakers else "")
    )
    axis.set_xlabel("speaker")
    axis.set_ylabel("recordings")
    axis.tick_params(axis="x", rotation=90, labelsize=7)
    axis.grid(axis="y", **_GRID)
    figure.tight_layout()
    return figure


    figure.tight_layout()
    return figure


def plot_threshold_sweep(
    rows: Sequence,
    budget: Optional[float] = None,
    chosen: Optional[float] = None,
    title: str = "threshold sweep",
) -> Figure:
    """Event recall/precision and FA/hour against the activation threshold.

    Two axes on purpose: the choice is a trade between the left one (how much is
    counted) and the right one (how much is counted wrongly), and the budget line
    is what actually decides it.
    """
    thresholds = [row.activation for row in rows]
    recall = [row.metrics.recall for row in rows]
    precision = [row.metrics.precision for row in rows]
    false_alarms = [row.metrics.false_activations_per_hour for row in rows]

    figure, axis = plt.subplots(figsize=(9.0, 4.2))
    axis.plot(thresholds, recall, color="#2a9d8f", marker="o", markersize=2.5, label="event recall")
    axis.plot(thresholds, precision, color="#457b9d", marker="o", markersize=2.5,
              label="event precision")
    axis.set_xlabel("activation threshold")
    axis.set_ylabel("event precision / recall")
    axis.set_ylim(-0.02, 1.02)
    axis.grid(**_GRID)

    right = axis.twinx()
    right.plot(thresholds, false_alarms, color="#e76f51", linewidth=1.4, label="FA / hour")
    right.set_ylabel("false activations per hour")
    if budget is not None:
        right.axhline(budget, color="#e63946", linestyle="--", linewidth=1.0,
                      label=f"budget {budget:g}/h")
    if chosen is not None:
        axis.axvline(chosen, color="#264653", linestyle=":", linewidth=1.4,
                     label=f"chosen {chosen:g}")

    handles, labels = axis.get_legend_handles_labels()
    extra_handles, extra_labels = right.get_legend_handles_labels()
    axis.legend(handles + extra_handles, labels + extra_labels, fontsize=8, loc="center left")
    axis.set_title(title)
    figure.tight_layout()
    return figure


def plot_detector_scores(
    scores: np.ndarray,
    y_true: np.ndarray,
    negative_types: Optional[Sequence[str]] = None,
    threshold: float = 0.5,
    title: str = "P(target) by clip type",
) -> Figure:
    """Score histogram: target clips against each kind of negative.

    Overlap on the right of the threshold is where false counts come from, and
    which negative category is doing the overlapping is the actionable part - it
    names the recordings to go and collect.
    """
    scores = np.asarray(scores, dtype=np.float32)
    y_true = np.asarray(y_true, dtype=np.int32)
    bins = np.linspace(0.0, 1.0, 41)

    figure, axis = plt.subplots(figsize=(9.0, 4.2))
    axis.hist(scores[y_true == 1], bins=bins, alpha=0.8, color="#2a9d8f", label="target")

    if negative_types is not None and len(negative_types) != scores.size:
        LOGGER.warning(
            "%d negative types for %d clips - falling back to one negative histogram; "
            "the per-category breakdown needs one entry per clip",
            len(negative_types),
            scores.size,
        )
        negative_types = None

    if negative_types:
        types = np.asarray(negative_types)
        palette = ["#e76f51", "#e9c46a", "#457b9d", "#8d99ae", "#6d597a", "#b56576"]
        for index, name in enumerate(sorted(set(types[y_true != 1]))):
            mask = (types == name) & (y_true != 1)
            axis.hist(
                scores[mask], bins=bins, histtype="step", linewidth=1.4,
                color=palette[index % len(palette)], label=name,
            )
    else:
        axis.hist(scores[y_true != 1], bins=bins, alpha=0.6, color="#e76f51", label="not target")

    axis.axvline(threshold, color="#264653", linestyle=":", linewidth=1.4,
                 label=f"threshold {threshold:g}")
    axis.set_xlabel("P(target)")
    axis.set_ylabel("clips")
    axis.set_yscale("symlog")
    axis.set_title(title)
    axis.grid(axis="y", **_GRID)
    axis.legend(fontsize=8)
    figure.tight_layout()
    return figure


def plot_architecture_comparison(rows: Sequence[Mapping], title: str = "architecture comparison") -> Figure:
    """INT8 size, FA/hour, event F1 and latency for each architecture."""
    labels = [str(row["architecture"]) for row in rows]
    panels = [
        ("int8_kb", "INT8 size (KB)", "#457b9d"),
        ("fa_per_hour", "false activations / hour", "#e76f51"),
        ("event_f1", "event F1", "#2a9d8f"),
        ("latency_ms", "latency (ms, this machine)", "#6d597a"),
    ]
    figure, axes = plt.subplots(1, len(panels), figsize=(4.0 * len(panels), 3.6))
    for axis, (key, label, colour) in zip(np.atleast_1d(axes), panels):
        values = [float(row.get(key) or 0.0) for row in rows]
        axis.bar(labels, values, color=colour)
        axis.set_title(label, fontsize=10)
        axis.tick_params(axis="x", rotation=20, labelsize=8)
        axis.grid(axis="y", **_GRID)
    figure.suptitle(title)
def plot_negative_type_false_positives(
    rows: Sequence,
    title: str = "false positives by negative audio type",
    limit: Optional[float] = None,
) -> Figure:
    """Which kind of audio the model fires on.

    A flat bar chart here is a model with a general problem; one tall bar is a
    data-collection instruction.
    """
    labels = [item.negative_type for item in rows]
    values = [item.false_positive_rate for item in rows]
    counts = [item.clips for item in rows]

    figure, axis = plt.subplots(figsize=(max(6.0, len(labels) * 1.1), 4.0))
    bars = axis.bar(labels, values, color="#e76f51")
    for bar, count in zip(bars, counts):
        axis.text(
            bar.get_x() + bar.get_width() / 2.0,
            bar.get_height(),
            f"n={count}",
            ha="center",
            va="bottom",
            fontsize=7,
            color="#264653",
        )
    if limit is not None:
        axis.axhline(
            limit, color="#264653", linestyle="--", linewidth=1.0, label=f"limit {limit:g}"
        )
        axis.legend(fontsize=8)
    axis.set_ylabel("accepted as a dhikr")
    axis.set_title(title)
    axis.tick_params(axis="x", rotation=20)
    axis.grid(axis="y", **_GRID)
    figure.tight_layout()
    return figure


def plot_benchmark(results: Sequence, title: str = "exported model comparison") -> Figure:
    """Size and latency of each exported variant side by side."""
    labels = [item.name for item in results]
    sizes = [item.size_kb / 1024.0 for item in results]
    latencies = [item.mean_latency_ms for item in results]

    figure, (left, right) = plt.subplots(1, 2, figsize=(10.0, 4.0))
    left.bar(labels, sizes, color="#457b9d")
    left.set_title("model size")
    left.set_ylabel("MB")
    left.tick_params(axis="x", rotation=20)
    left.grid(axis="y", **_GRID)

    right.bar(labels, latencies, color="#2a9d8f")
    right.set_title("mean inference time (this machine)")
    right.set_ylabel("ms")
    right.tick_params(axis="x", rotation=20)
    right.grid(axis="y", **_GRID)

    figure.suptitle(title)
    figure.tight_layout()
    return figure

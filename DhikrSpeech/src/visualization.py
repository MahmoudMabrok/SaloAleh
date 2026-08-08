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
    "plot_class_distribution",
    "plot_confidence_distribution",
    "plot_confusion_matrix",
    "plot_duration_histogram",
    "plot_log_mel",
    "plot_negative_type_false_positives",
    "plot_per_class_metrics",
    "plot_roc_curves",
    "plot_speaker_distribution",
    "plot_streaming_timeline",
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
    """Loss, accuracy and (when logged) learning rate over epochs."""
    panels = [("loss", "val_loss", "loss"), ("accuracy", "val_accuracy", "accuracy")]
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
            best = (
                int(np.argmin(history[val_key]))
                if label == "loss"
                else int(np.argmax(history[val_key]))
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


def plot_streaming_timeline(
    scan,
    events: Sequence = (),
    truth: Sequence = (),
    detector=None,
    title: str = "streaming predictions",
    ignore: Sequence[str] = ("unknown",),
) -> Figure:
    """The whole streaming story for one recording, on one time axis.

    Top: every class's probability over time, with the activation and release
    thresholds drawn in - this is where you see whether the hysteresis band is in
    the right place. Bottom: what was annotated against what was detected, so a
    miss, a false activation and a duplicate are visually distinct rather than
    three numbers in a table.
    """
    figure, (top, bottom) = plt.subplots(
        2, 1, figsize=(11.0, 5.4), gridspec_kw={"height_ratios": [3, 1]}, sharex=True
    )
    ignored = {name.lower() for name in ignore}
    palette = plt.get_cmap("tab10")
    for index, label in enumerate(scan.labels):
        dimmed = label.lower() in ignored
        top.plot(
            scan.times,
            scan.probabilities[:, index],
            label=label,
            color="#adb5bd" if dimmed else palette(index % 10),
            linewidth=0.9 if not dimmed else 0.7,
            alpha=0.5 if dimmed else 1.0,
        )
    if detector is not None:
        top.axhline(
            detector.confidence_threshold,
            color="#e63946",
            linestyle="--",
            linewidth=1.0,
            label=f"activation {detector.confidence_threshold:g}",
        )
        top.axhline(
            detector.release_threshold,
            color="#f4a261",
            linestyle=":",
            linewidth=1.0,
            label=f"release {detector.release_threshold:g}",
        )
    for event in events:
        top.axvspan(event.start, max(event.end, event.start + 0.05), color="#2a9d8f", alpha=0.18)
        top.axvline(event.trigger_time, color="#2a9d8f", linewidth=1.2)
    top.set_ylim(0.0, 1.02)
    top.set_ylabel("probability")
    top.set_title(title)
    top.grid(**_GRID)
    top.legend(fontsize=7, ncol=3, loc="upper right")

    for annotation in truth:
        bottom.axvspan(annotation.start, annotation.end, ymin=0.55, ymax=0.95,
                       color="#264653", alpha=0.6)
    for event in events:
        bottom.axvspan(event.start, max(event.end, event.start + 0.05), ymin=0.05,
                       ymax=0.45, color="#2a9d8f", alpha=0.7)
    bottom.set_yticks([0.25, 0.75])
    bottom.set_yticklabels(["detected", "annotated"], fontsize=8)
    bottom.set_ylim(0.0, 1.0)
    bottom.set_xlabel("seconds")
    bottom.set_xlim(0.0, max(float(scan.times[-1]) + scan.window_seconds, 1.0))
    bottom.grid(axis="x", **_GRID)

    figure.tight_layout()
    return figure


def plot_threshold_sweep(
    points: Sequence,
    target_false_activations_per_hour: Optional[float] = None,
    chosen: Optional[float] = None,
    title: str = "threshold sweep",
) -> Figure:
    """Recall and false activations per hour against the threshold.

    The two curves are the whole trade-off, and the budget line is what decides
    it: the operating point is the leftmost threshold whose FA/hour curve is under
    that line, not the peak of anything.
    """
    thresholds = [point.threshold for point in points]
    recalls = [point.recall for point in points]
    false_rates = [point.false_activations_per_hour for point in points]

    figure, (left, right) = plt.subplots(1, 2, figsize=(11.0, 4.0))
    left.plot(thresholds, recalls, marker="o", color="#2a9d8f", label="event recall")
    left.plot(
        thresholds,
        [point.precision for point in points],
        marker="s",
        color="#457b9d",
        label="event precision",
    )
    left.set_xlabel("threshold")
    left.set_ylabel("rate")
    left.set_ylim(0.0, 1.05)
    left.grid(**_GRID)
    left.legend(fontsize=8)

    right.plot(thresholds, false_rates, marker="o", color="#e76f51")
    right.set_xlabel("threshold")
    right.set_ylabel("false activations / hour")
    right.set_yscale("symlog", linthresh=0.1)
    if target_false_activations_per_hour is not None:
        right.axhline(
            target_false_activations_per_hour,
            color="#264653",
            linestyle="--",
            linewidth=1.0,
            label=f"budget {target_false_activations_per_hour:g}/h",
        )
        right.legend(fontsize=8)
    right.grid(**_GRID)

    for axis in (left, right):
        if chosen is not None:
            axis.axvline(chosen, color="#8d99ae", linestyle=":", linewidth=1.2)

    figure.suptitle(title)
    figure.tight_layout()
    return figure


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

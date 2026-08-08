"""Dataset quality report - is there enough data, and enough *variety*, to train?

The pipeline runs happily on a few dozen clips. That is a feature: the plumbing
can be verified before the recordings exist. The failure mode is reading the
numbers such a run produces as if they described a model anyone can ship.

This module answers the question the accuracy cannot: how many recordings, from
how many people, of what kind, and where the gaps are. Everything here is
advisory - nothing blocks a run - but the recommendations are specific enough to
act on, and the report says plainly when a dataset is prototype-scale.
"""

from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Union

import numpy as np

from .config import ClassesConfig, QualityConfig
from .speakers import SpeakerReport

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = ["ClassQuality", "DatasetQualityReport", "dataset_quality"]


@dataclass
class ClassQuality:
    """Everything measurable about one class folder."""

    label: str
    is_target: bool
    clips: int
    speakers: int
    total_duration: float
    mean_duration: float
    median_duration: float
    min_duration: float
    max_duration: float
    clips_per_speaker: Dict[str, int] = field(default_factory=dict)

    def row(self) -> Dict[str, object]:
        return {
            "class": self.label,
            "target": self.is_target,
            "clips": self.clips,
            "speakers": self.speakers,
            "total (min)": round(self.total_duration / 60.0, 2),
            "mean (s)": round(self.mean_duration, 2),
            "median (s)": round(self.median_duration, 2),
            "min (s)": round(self.min_duration, 2),
            "max (s)": round(self.max_duration, 2),
        }


@dataclass
class DatasetQualityReport:
    """Per-class statistics, negative categories and what to collect next."""

    per_class: List[ClassQuality]
    negative_counts: Dict[str, int]
    speakers: SpeakerReport
    config: QualityConfig
    unknown_class: str = "unknown"
    expected_negative_types: List[str] = field(default_factory=list)

    # -- aggregates ---------------------------------------------------------
    @property
    def targets(self) -> List[ClassQuality]:
        return [item for item in self.per_class if item.is_target]

    @property
    def target_clips(self) -> int:
        return sum(item.clips for item in self.targets)

    @property
    def unknown_clips(self) -> int:
        return sum(item.clips for item in self.per_class if not item.is_target)

    @property
    def total_clips(self) -> int:
        return sum(item.clips for item in self.per_class)

    @property
    def total_duration(self) -> float:
        return sum(item.total_duration for item in self.per_class)

    @property
    def imbalance(self) -> float:
        """Largest class / smallest class, over the target phrases."""
        counts = [item.clips for item in self.targets if item.clips]
        if len(counts) < 2:
            return 1.0
        return max(counts) / float(min(counts))

    @property
    def scale(self) -> str:
        """``prototype`` | ``usable`` | ``production`` - the honest headline.

        Deliberately driven by the *weakest* target phrase and by the speaker
        count, not by the total: a dataset is only as good as its thinnest class,
        and 500 recordings from three people is still three people.
        """
        if not self.targets:
            return "prototype"
        smallest = min(item.clips for item in self.targets)
        speakers = self.speakers.num_speakers if self.speakers.known else 0
        if (
            smallest >= self.config.recommended_recordings_per_class
            and speakers >= self.config.recommended_speakers
        ):
            return "production"
        if (
            smallest >= self.config.prototype_recordings_per_class
            and speakers >= self.config.prototype_speakers
        ):
            return "usable"
        return "prototype"

    # -- recommendations ----------------------------------------------------
    def warnings(self) -> List[str]:
        notes: List[str] = []
        config = self.config

        thin = [
            item
            for item in self.targets
            if item.clips < config.prototype_recordings_per_class
        ]
        if thin:
            detail = ", ".join(f"{item.label} ({item.clips})" for item in thin)
            notes.append(
                f"target phrase(s) below {config.prototype_recordings_per_class} "
                f"recordings: {detail}. Prototype scale - enough to verify the "
                f"pipeline, not enough to measure a model. Aim for "
                f"{config.recommended_recordings_per_class}+ per phrase for a first "
                f"model worth putting in the app."
            )

        if self.speakers.known and self.speakers.num_speakers < config.prototype_speakers:
            notes.append(
                f"{self.speakers.num_speakers} speaker(s), below the "
                f"{config.prototype_speakers} a prototype needs and well below the "
                f"{config.recommended_speakers} a shipped model needs. Speaker "
                f"variety is the single highest-value thing to collect next: ten "
                f"people saying a phrase twice teaches more than one person saying "
                f"it twenty times."
            )

        if self.target_clips:
            ratio = self.unknown_clips / float(self.target_clips)
            if ratio < config.min_unknown_ratio:
                notes.append(
                    f"{self.unknown_clips} unknown clips against {self.target_clips} "
                    f"target clips (ratio {ratio:.2f}, recommended "
                    f"{config.min_unknown_ratio:g}). Negatives are what keep the "
                    f"false-activation rate down; with too few of them the model has "
                    f"barely been shown what *not* to count, and the FA/hour figure "
                    f"cannot be trusted."
                )

        if self.imbalance > config.max_class_imbalance:
            biggest = max(self.targets, key=lambda item: item.clips)
            smallest = min(self.targets, key=lambda item: item.clips)
            notes.append(
                f"class imbalance {self.imbalance:.1f}x ({biggest.label}: "
                f"{biggest.clips} vs {smallest.label}: {smallest.clips}), above "
                f"{config.max_class_imbalance:g}x. Class weights compensate in the "
                f"loss, but the thin class still has fewer *distinct* recordings to "
                f"learn from - collect for it rather than reweighting harder."
            )

        missing = [
            name
            for name in self.expected_negative_types
            if name != self.unknown_class and not self.negative_counts.get(name)
        ]
        if missing:
            notes.append(
                f"no negatives of type(s): {', '.join(missing)}. Each one is a "
                f"failure mode nothing is measuring - `hard_negative` above all "
                f"(the near-miss phrases the counter must not fire on)."
            )

        thin_categories = [
            f"{name} ({count})"
            for name, count in self.negative_counts.items()
            if 0 < count < config.min_negative_category
        ]
        if thin_categories:
            notes.append(
                f"negative categories with under {config.min_negative_category} "
                f"clips: {', '.join(thin_categories)}. Their false-positive rates "
                f"will be anecdotes rather than measurements."
            )

        notes.extend(self.speakers.warnings(config.prototype_speakers))
        return notes

    # -- reporting ----------------------------------------------------------
    def to_dataframe(self):
        import pandas as pd  # lazily imported: only notebooks need pandas

        return pd.DataFrame([item.row() for item in self.per_class])

    def to_dict(self) -> Dict[str, object]:
        return {
            "scale": self.scale,
            "total_clips": self.total_clips,
            "target_clips": self.target_clips,
            "unknown_clips": self.unknown_clips,
            "total_duration": self.total_duration,
            "imbalance": self.imbalance,
            "per_class": [asdict(item) for item in self.per_class],
            "negative_counts": dict(self.negative_counts),
            "speakers": self.speakers.to_dict(),
            "warnings": self.warnings(),
        }

    def save(self, directory: PathLike, name: str = "dataset_quality") -> Path:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)
        destination = target / f"{name}.json"
        destination.write_text(
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
        )
        LOGGER.info("dataset quality report written to %s", destination)
        return destination

    def summary(self) -> str:
        headline = {
            "prototype": "PROTOTYPE - enough to verify the pipeline, not to measure a model",
            "usable": "USABLE - enough for a first real model; keep collecting",
            "production": "PRODUCTION SCALE - by dataset size and speaker count",
        }[self.scale]
        lines = [
            f"scale            : {headline}",
            f"clips            : {self.total_clips} "
            f"({self.target_clips} target, {self.unknown_clips} unknown)",
            f"audio            : {self.total_duration / 60.0:.1f} minutes",
            f"speakers         : "
            + (
                f"{self.speakers.num_speakers} (source: {self.speakers.source})"
                if self.speakers.known
                else "unknown - no speaker information"
            ),
            "",
            f"{'class':<14}{'clips':>7}{'speakers':>10}{'minutes':>10}{'mean s':>9}",
        ]
        for item in self.per_class:
            lines.append(
                f"{item.label:<14}{item.clips:>7}{item.speakers:>10}"
                f"{item.total_duration / 60.0:>10.1f}{item.mean_duration:>9.2f}"
            )
        if self.negative_counts:
            lines.append("")
            lines.append("negative categories:")
            for name, count in self.negative_counts.items():
                lines.append(f"  {name:<20}{count:>6}")
        for note in self.warnings():
            lines.append(f"\n!! {note}")
        return "\n".join(lines)


def dataset_quality(
    index,
    speakers: SpeakerReport,
    config: QualityConfig,
    durations: Optional[Dict[str, float]] = None,
    classes: Optional[ClassesConfig] = None,
    unknown_class: str = "unknown",
) -> DatasetQualityReport:
    """Build the report from an indexed dataset.

    ``durations`` maps a recording path to its length in seconds - normally
    ``{item.path: item.duration for item in validation_report.file_stats}``. When
    it is absent the counts and speaker figures are still exact and the duration
    columns read zero, so this works before a deep validation pass has run.
    """
    lengths = durations or {}
    per_class: List[ClassQuality] = []

    for label in index.class_names:
        samples = [sample for sample in index.samples if sample.label == label]
        values = np.asarray(
            [
                lengths.get(str(sample.path), 0.0)
                for sample in samples
                if lengths.get(str(sample.path), 0.0) > 0.0
            ],
            dtype=np.float64,
        )
        speaker_counts: Dict[str, int] = {}
        for sample in samples:
            if sample.speaker:
                speaker_counts[sample.speaker] = speaker_counts.get(sample.speaker, 0) + 1
        per_class.append(
            ClassQuality(
                label=label,
                is_target=label != unknown_class,
                clips=len(samples),
                speakers=len(speaker_counts),
                total_duration=float(values.sum()) if values.size else 0.0,
                mean_duration=float(values.mean()) if values.size else 0.0,
                median_duration=float(np.median(values)) if values.size else 0.0,
                min_duration=float(values.min()) if values.size else 0.0,
                max_duration=float(values.max()) if values.size else 0.0,
                clips_per_speaker=dict(sorted(speaker_counts.items())),
            )
        )

    expected: Sequence[str] = classes.negative_types if classes else []
    return DatasetQualityReport(
        per_class=per_class,
        negative_counts=index.negative_counts(),
        speakers=speakers,
        config=config,
        unknown_class=unknown_class,
        expected_negative_types=list(expected),
    )

"""Single-target dataset mapping: one dhikr against everything else.

This is the production shape. Android loads one model per dhikr, so a model's
whole job is::

    TARGET     the selected phrase, spoken completely
    UNKNOWN    anything else

"Anything else" is not one thing, and that is the point of this module. A model
that has only ever seen room tone as a negative will happily fire on
``سبحان الله وبحمده`` when its target is ``سبحان الله العظيم وبحمده``. So every
negative carries a :data:`NEGATIVE_TYPES` category through the manifest - all of
them train as ``unknown``, but they are *evaluated separately*, because "the
model is 99% accurate" and "the model cannot tell a complete phrase from its
first three words" are both true of the same run.

Layout, all optional except the target folder itself::

    dataset/007/**                     the target                -> TARGET
    dataset/006/**  dataset/001/**     other dhikr               -> other_dhikr
    dataset/unknown/**                 flat filler               -> unknown
    dataset/unknown/normal_speech/     ordinary Arabic speech    -> normal_speech
    dataset/unknown/hard_negative/     near-misses of a phrase   -> hard_negative
    dataset/unknown/partial_phrase/    incomplete utterances     -> partial_phrase
    dataset/unknown/other_dhikr/       recorded as negatives     -> other_dhikr
    dataset/unknown/noise/             room, street, TV          -> noise

The category is the first subfolder under ``unknown/``. It is derived once by
:func:`src.dataset.scan_dataset`, which this module calls rather than walking the
tree a second time - so a target scan sees exactly the folders, the categories
and the speaker ids that a multi-class scan does.

Untagged files in ``unknown/hard_negative/`` are shared across targets. A
collector can instead scope a near-miss to one detector by ending its filename
with ``_hard_negative_<target_id>``. For example,
``unknown_spABC_01_000_hard_negative_006.wav`` is a hard negative for 006 and is
excluded from every other target's dataset.
"""

from __future__ import annotations

import logging
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .config import Config, NegativeSamplingConfig, TargetConfig
from .dataset import DatasetIndex, ManifestRecord, Phrase, Sample, scan_dataset

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "NEGATIVE_TYPES",
    "OTHER_DHIKR",
    "TARGET_INDEX",
    "TARGET_LABEL",
    "TargetDatasetReport",
    "BinarySplitDistribution",
    "UNKNOWN_INDEX",
    "UNKNOWN_LABEL",
    "build_target_report",
    "binary_split_distribution",
    "negative_breakdown",
    "recommend_clip_seconds",
    "sample_negatives",
    "scan_target_dataset",
    "target_scoped_hard_negative_id",
    "target_class_names",
    "target_negative_paths",
    "validate_binary_label_mapping",
]

# Class indices inside a single-target run. 1 is the target so that
# `probabilities[:, 1]` is P(target) under a 2-output softmax, and the sigmoid
# head's single output means the same thing.
UNKNOWN_INDEX = 0
TARGET_INDEX = 1
TARGET_LABEL = "target"
UNKNOWN_LABEL = "unknown"

# Categories a negative can carry. They all train as UNKNOWN; they exist so that
# evaluation can say *what* produced a false detection. `src.dataset` derives the
# value from the subfolder name, so this list is what the reports iterate over
# and order by - most-dangerous first.
NEGATIVE_TYPES: Tuple[str, ...] = (
    "hard_negative",
    "partial_phrase",
    "other_dhikr",
    "normal_speech",
    "general_speech",
    "background_audio",
    "noise",
    "silence",
    "unknown",
)


# What a clip from another phrase folder is called once it becomes a negative.
OTHER_DHIKR = "other_dhikr"
HARD_NEGATIVE = "hard_negative"
TARGET_SCOPED_HARD_NEGATIVE = re.compile(
    r"(?:^|_)hard_negative_(?P<target>\d{1,3})(?=_|$)", re.IGNORECASE
)


@dataclass(frozen=True)
class BinarySplitDistribution:
    target: int
    unknown: int

    @property
    def total(self) -> int:
        return self.target + self.unknown

    @property
    def majority_baseline(self) -> float:
        return max(self.target, self.unknown) / self.total if self.total else float("nan")


def binary_split_distribution(records: Sequence[ManifestRecord]) -> BinarySplitDistribution:
    """TARGET/UNKNOWN counts and constant-majority accuracy for one split."""
    validate_binary_label_mapping(records)
    return BinarySplitDistribution(
        target=sum(record.class_index == TARGET_INDEX for record in records),
        unknown=sum(record.class_index == UNKNOWN_INDEX for record in records),
    )


def validate_binary_label_mapping(
    records: Sequence[ManifestRecord], samples_per_class: int = 3, require_both: bool = False
) -> Dict[int, str]:
    """Verify the exact labels fed to tf.data are UNKNOWN=0 and TARGET=1.

    The optional sample walk intentionally checks concrete records from each
    class, rather than trusting only a vocabulary declaration. ``make_tf_dataset``
    consumes these same ``class_index`` values directly.
    """
    expected = {UNKNOWN_INDEX: UNKNOWN_LABEL, TARGET_INDEX: TARGET_LABEL}
    seen: Dict[int, int] = {UNKNOWN_INDEX: 0, TARGET_INDEX: 0}
    for record in records:
        if record.class_index not in expected:
            raise ValueError(
                f"invalid binary label {record.class_index} for {record.path}; expected only 0/1"
            )
        expected_label = expected[record.class_index]
        if record.label.lower() != expected_label:
            raise ValueError(
                f"invalid binary label mapping for {record.path}: class_index "
                f"{record.class_index} must be '{expected_label}', got '{record.label}'"
            )
        if seen[record.class_index] < max(samples_per_class, 0):
            # Counting these proves concrete rows in each class were inspected.
            seen[record.class_index] += 1
    if require_both:
        missing = [expected[index].upper() for index, count in seen.items() if count == 0]
        if missing:
            raise ValueError(
                "binary label sanity check needs both classes; missing " + ", ".join(missing)
            )
    return {UNKNOWN_INDEX: "UNKNOWN", TARGET_INDEX: "TARGET"}


def target_scoped_hard_negative_id(path: PathLike) -> Optional[int]:
    """Target named by ``*_hard_negative_<id>`` or ``None`` when untagged."""
    match = TARGET_SCOPED_HARD_NEGATIVE.search(Path(path).stem)
    return int(match.group("target")) if match else None


def target_class_names() -> List[str]:
    """Class list in class-index order: ``["unknown", "target"]``."""
    return [UNKNOWN_LABEL, TARGET_LABEL]


def target_negative_paths(
    dataset_dir: PathLike,
    target: TargetConfig,
    unknown_class: str = UNKNOWN_LABEL,
) -> List[Path]:
    """Top-level folders that supply negatives for one target.

    The binary dataset has no single ``negatives`` directory. When automatic
    other-dhikr negatives are enabled, every visible dataset folder except the
    active target contributes negatives; ``unknown/`` always contributes them.
    A missing ``unknown/`` path is still returned so setup/status UIs can report
    it as missing instead of silently omitting the expected negative source.
    """
    if not target.enabled:
        raise ValueError("target_negative_paths needs target.phrase_id to be set")

    root = Path(dataset_dir)
    folders: Dict[str, Path] = {}
    if root.is_dir():
        for entry in root.iterdir():
            if not entry.is_dir() or entry.name.startswith("."):
                continue
            if entry.name == target.folder:
                continue
            if entry.name != unknown_class and not target.auto_other_dhikr_negatives:
                continue
            folders[entry.name] = entry

    folders.setdefault(unknown_class, root / unknown_class)

    def sort_key(path: Path) -> Tuple[int, Union[int, str]]:
        return (0, int(path.name)) if path.name.isdigit() else (1, path.name)

    return sorted(folders.values(), key=sort_key)


def scan_target_dataset(
    dataset_dir: PathLike,
    phrases: Sequence[Phrase],
    target: TargetConfig,
    unknown_class: str = "unknown",
    extensions: Sequence[str] = (".wav",),
    speaker_resolver=None,
) -> DatasetIndex:
    """Index the dataset as TARGET vs UNKNOWN for ``target.phrase_id``.

    The walk itself is :func:`src.dataset.scan_dataset` - one implementation of
    "what is in this dataset", so a target scan and a multi-class scan agree
    about folders, negative categories and speaker ids by construction. This
    function only *relabels* the result:

    * the target's own folder becomes ``TARGET``;
    * every other phrase folder becomes ``UNKNOWN`` / ``other_dhikr``;
    * everything under ``unknown/`` keeps the category the scan derived.

    ``rel_dir`` is left as the source folder, so conditioned clips stay cached
    per folder and the shared negative pool is written once for every target.
    """
    if not target.enabled:
        raise ValueError("scan_target_dataset needs target.phrase_id to be set")

    folder = target.folder or ""
    index = scan_dataset(
        dataset_dir,
        phrases,
        unknown_class=unknown_class,
        extensions=extensions,
        classes=None,
        speaker_resolver=speaker_resolver,
    )
    if folder not in index.class_names:
        available = ", ".join(index.class_names)
        raise FileNotFoundError(
            f"target phrase {folder} has no folder under {dataset_dir} "
            f"(dataset holds: {available or 'nothing'})"
        )

    phrase_text = {phrase.id: phrase.text for phrase in phrases}
    target_text = phrase_text.get(int(target.phrase_id or 0), folder)

    samples: List[Sample] = []
    for sample in index.samples:
        if sample.label == folder:
            samples.append(
                replace(
                    sample,
                    label=TARGET_LABEL,
                    class_index=TARGET_INDEX,
                    phrase_id=int(target.phrase_id) if target.phrase_id else None,
                    text=target_text,
                    negative_type=None,
                )
            )
            continue

        if sample.label != unknown_class:
            # Another phrase folder. These are the negatives that matter most and
            # cost nothing - they are already in the dataset.
            if not target.auto_other_dhikr_negatives:
                continue
            negative_type = OTHER_DHIKR
        else:
            scoped_target = target_scoped_hard_negative_id(sample.path)
            if scoped_target is not None and scoped_target != int(target.phrase_id or 0):
                # A near-miss collected for another detector may be this target's
                # complete phrase. Exclude it instead of teaching the model that
                # its own target is UNKNOWN.
                continue
            negative_type = (
                HARD_NEGATIVE
                if scoped_target is not None
                else sample.negative_type or unknown_class
            )

        samples.append(
            replace(
                sample,
                label=UNKNOWN_LABEL,
                class_index=UNKNOWN_INDEX,
                phrase_id=None,
                text=UNKNOWN_LABEL,
                negative_type=negative_type,
            )
        )

    positives = sum(1 for sample in samples if sample.class_index == TARGET_INDEX)
    if not positives:
        raise FileNotFoundError(f"target folder {folder} contains no audio files")

    LOGGER.info(
        "target %s: %d positive / %d negative clips (%s)",
        folder,
        positives,
        len(samples) - positives,
        ", ".join(f"{name} {count}" for name, count in sorted(negative_breakdown(samples).items()))
        or "no negatives",
    )
    if len(samples) == positives:
        LOGGER.warning(
            "target %s has no negatives at all. A detector trained only on its own "
            "phrase will fire on everything - add other phrase folders or an "
            "`unknown/` tree before training.",
            folder,
        )

    return DatasetIndex(
        samples=samples,
        class_names=target_class_names(),
        phrases=phrase_text,
        root=index.root,
        target_id=int(target.phrase_id) if target.phrase_id else None,
        target_text=target_text,
        speaker_source=index.speaker_source,
    )


def negative_breakdown(samples: Sequence[Sample]) -> Dict[str, int]:
    """``{negative_type: clips}`` over the negatives in ``samples``."""
    counter: Counter = Counter()
    for sample in samples:
        if sample.class_index != TARGET_INDEX and sample.negative_type:
            counter[sample.negative_type] += 1
    return dict(counter)


# ---------------------------------------------------------------------------
# Negative sampling
# ---------------------------------------------------------------------------
def sample_negatives(
    records: Sequence[ManifestRecord],
    config: NegativeSamplingConfig,
    seed: int,
    split: str = "train",
) -> List[ManifestRecord]:
    """Cut the negatives of one split down to ``ratio x positives``, by weight.

    Records outside ``split`` pass through untouched, so the caller can hand the
    whole manifest in and get a manifest back. Sampling is *without replacement*
    and deterministic in ``seed``: the same run twice trains on the same clips.

    The weights bias which negatives survive the cut, they do not duplicate
    anything - a hard negative at weight 4.0 is four times as likely to be kept
    as room tone at 1.0, and every negative still has a chance, so the pool is
    not silently reduced to its hard core.
    """
    if not config.enabled:
        return list(records)

    others = [record for record in records if record.split != split]
    positives = [
        record
        for record in records
        if record.split == split and record.class_index == TARGET_INDEX
    ]
    negatives = [
        record
        for record in records
        if record.split == split and record.class_index != TARGET_INDEX
    ]
    if not positives or not negatives:
        return list(records)

    budget = int(round(len(positives) * config.ratio))
    if budget >= len(negatives):
        LOGGER.info(
            "negative sampling: keeping all %d negatives (budget %d at ratio %.2f)",
            len(negatives),
            budget,
            config.ratio,
        )
        return list(records)
    budget = max(budget, 1)

    weights = np.array(
        [max(config.weight_for(record.negative_type), 0.0) for record in negatives],
        dtype=np.float64,
    )
    if weights.sum() <= 0.0:
        weights = np.ones_like(weights)
    # Efraimidis-Spirakis: one exponential key per item gives a weighted sample
    # without replacement in a single pass, which keeps this deterministic and
    # cheap on a pool of any size.
    rng = np.random.default_rng(seed)
    keys = rng.exponential(size=weights.size) / np.maximum(weights, 1e-12)
    keys[weights <= 0.0] = np.inf
    chosen = np.argsort(keys, kind="stable")[:budget]
    kept = [negatives[int(index)] for index in sorted(chosen)]

    dropped = len(negatives) - len(kept)
    LOGGER.info(
        "negative sampling: %d of %d negatives kept for '%s' (%d positives, ratio "
        "%.2f); %d dropped this run",
        len(kept),
        len(negatives),
        split,
        len(positives),
        config.ratio,
        dropped,
    )
    return others + positives + kept


# ---------------------------------------------------------------------------
# Window length
# ---------------------------------------------------------------------------
def recommend_clip_seconds(
    durations: Sequence[float],
    percentile: float = 95.0,
    margin_seconds: float = 0.35,
    round_to: float = 0.1,
) -> Dict[str, float]:
    """Suggest a window length from the target's utterance durations.

    A window has to hold the *complete* phrase plus enough margin that a slightly
    slow speaker is not cropped - a cropped positive is indistinguishable from a
    partial phrase, which is exactly the distinction the model must learn. The
    recommendation is the requested percentile of the durations plus
    ``margin_seconds``, rounded up.

    Reported, never applied: changing ``audio.clip_seconds`` invalidates every
    cached clip and every trained checkpoint, so it stays a human decision
    (requirement 15).
    """
    values = np.asarray([float(value) for value in durations], dtype=np.float64)
    values = values[np.isfinite(values) & (values > 0.0)]
    if values.size == 0:
        return {}
    high = float(np.percentile(values, percentile))
    recommended = high + float(margin_seconds)
    if round_to > 0:
        recommended = float(np.ceil(recommended / round_to) * round_to)
    return {
        "clips": float(values.size),
        "min": float(values.min()),
        "median": float(np.median(values)),
        "percentile": float(percentile),
        "percentile_seconds": high,
        "max": float(values.max()),
        "margin_seconds": float(margin_seconds),
        "recommended_clip_seconds": round(recommended, 3),
    }


# ---------------------------------------------------------------------------
# Dataset report
# ---------------------------------------------------------------------------
@dataclass
class TargetDatasetReport:
    """What one target actually has to train on, and whether that is enough."""

    target_id: Optional[int]
    target_text: str
    positive_clips: int
    positive_speakers: int
    positive_seconds: float
    negative_clips: int
    negative_speakers: int
    negative_seconds: float
    by_negative_type: Dict[str, Dict[str, float]] = field(default_factory=dict)
    speakers_known: bool = True
    window_recommendation: Dict[str, float] = field(default_factory=dict)
    min_positive_clips: int = 100
    min_positive_speakers: int = 10
    recommended_positive_clips: int = 200
    recommended_positive_speakers: int = 20

    @property
    def hard_negative_clips(self) -> int:
        return int(self.by_negative_type.get("hard_negative", {}).get("clips", 0))

    @property
    def negative_ratio(self) -> float:
        return self.negative_clips / self.positive_clips if self.positive_clips else 0.0

    def recommendations(self) -> List[str]:
        """Plain-language gaps, ordered by how much they cost.

        Deliberately never blocking: a 40-clip prototype is a legitimate thing to
        run, it just must not be reported as a result (requirement 9).
        """
        notes: List[str] = []
        if self.positive_clips < self.min_positive_clips:
            notes.append(
                f"PROTOTYPE ONLY - {self.positive_clips} positive recordings, below the "
                f"{self.min_positive_clips} this project treats as the minimum. Train "
                f"freely, but the numbers this produces describe these clips, not the "
                f"phrase."
            )
        elif self.positive_clips < self.recommended_positive_clips:
            notes.append(
                f"{self.positive_clips} positive recordings clears the "
                f"{self.min_positive_clips} minimum; "
                f"{self.recommended_positive_clips}-500 is where a real-world model "
                f"starts."
            )
        if not self.speakers_known:
            notes.append(
                "speaker ids could not be resolved, so speaker counts are unknown and "
                "EVALUATION IS NOT SPEAKER-INDEPENDENT. Set split.speaker_regex or "
                "split.speaker_metadata."
            )
        elif self.positive_speakers < self.min_positive_speakers:
            notes.append(
                f"only {self.positive_speakers} distinct speakers said the target. "
                f"Speaker diversity beats clip count by a wide margin: 10 speakers x 20 "
                f"clips teaches far more than 1 speaker x 200. Aim for "
                f"{self.recommended_positive_speakers}+."
            )
        if not self.hard_negative_clips:
            notes.append(
                "no hard negatives. Without recordings of the target's prefixes and "
                "near-misses ('سبحان الله', 'سبحان الله العظيم', ...) the model learns "
                "to fire on the opening words, which is the single most common cause of "
                "false counts. Collect them under unknown/hard_negative/."
            )
        if not self.negative_clips:
            notes.append(
                "no negatives at all - this cannot train a detector, only a model that "
                "says yes."
            )
        elif self.negative_ratio < 1.0:
            notes.append(
                f"negatives outnumber positives {self.negative_ratio:.1f}:1. Production "
                f"audio is overwhelmingly non-target; a pool at least 2-3x the positives, "
                f"drawn from the shared negative set, matches that better."
            )
        return notes

    def to_dict(self) -> Dict[str, object]:
        return {
            "target_id": self.target_id,
            "target_text": self.target_text,
            "positive": {
                "clips": self.positive_clips,
                "speakers": self.positive_speakers if self.speakers_known else None,
                "seconds": round(self.positive_seconds, 2),
            },
            "negative": {
                "clips": self.negative_clips,
                "speakers": self.negative_speakers if self.speakers_known else None,
                "seconds": round(self.negative_seconds, 2),
                "by_type": self.by_negative_type,
            },
            "speakers_known": self.speakers_known,
            "window_recommendation": self.window_recommendation,
            "recommendations": self.recommendations(),
        }

    def summary(self) -> str:
        header = f"{self.target_id:03d}" if self.target_id else "?"
        speakers = str(self.positive_speakers) if self.speakers_known else "unknown"
        negative_speakers = str(self.negative_speakers) if self.speakers_known else "unknown"
        lines = [
            f"TARGET {header}  {self.target_text}",
            "",
            f"positives : {self.positive_clips} clips | {speakers} speakers | "
            f"{self.positive_seconds / 60.0:.1f} min",
            f"negatives : {self.negative_clips} clips | {negative_speakers} speakers | "
            f"{self.negative_seconds / 60.0:.1f} min "
            f"({self.negative_ratio:.1f}x positives)",
        ]
        for name in NEGATIVE_TYPES:
            entry = self.by_negative_type.get(name)
            if not entry:
                continue
            lines.append(
                f"  {name:<16} {int(entry['clips']):5d} clips | "
                f"{entry['seconds'] / 60.0:6.1f} min"
            )
        if self.window_recommendation:
            window = self.window_recommendation
            lines.append("")
            lines.append(
                f"window    : positives run {window['min']:.2f}-{window['max']:.2f} s "
                f"(median {window['median']:.2f}); p{window['percentile']:.0f} + "
                f"{window['margin_seconds']:.2f} s margin suggests "
                f"audio.clip_seconds = {window['recommended_clip_seconds']:.2f} "
                f"(reported only - not applied)"
            )
        for note in self.recommendations():
            lines.append(f"\n!! {note}")
        return "\n".join(lines)


def build_target_report(
    index: DatasetIndex,
    durations: Optional[Dict[str, float]] = None,
    config: Optional[Config] = None,
) -> TargetDatasetReport:
    """Assemble :class:`TargetDatasetReport` from an index.

    ``durations`` is keyed by ``str(sample.path)`` and is optional, so this works
    straight after a scan and gets sharper once the validation pass has measured
    the files. Speaker ids come off the samples, which the scan resolved.
    """
    durations = durations or {}
    readiness = config.readiness if config else None

    positive_seconds = negative_seconds = 0.0
    positive_speakers: set = set()
    negative_speakers: set = set()
    by_type: Dict[str, Dict[str, float]] = defaultdict(
        lambda: {"clips": 0.0, "seconds": 0.0, "speakers": 0.0}
    )
    speakers_seen_by_type: Dict[str, set] = defaultdict(set)
    positive_durations: List[float] = []
    resolved_any = False

    for sample in index.samples:
        key = str(sample.path)
        duration = float(durations.get(key, 0.0))
        speaker = sample.speaker
        if speaker:
            resolved_any = True
        if sample.class_index == TARGET_INDEX:
            positive_seconds += duration
            if duration > 0:
                positive_durations.append(duration)
            if speaker:
                positive_speakers.add(speaker)
        else:
            negative_seconds += duration
            if speaker:
                negative_speakers.add(speaker)
            name = sample.negative_type or "unknown"
            by_type[name]["clips"] += 1
            by_type[name]["seconds"] += duration
            if speaker:
                speakers_seen_by_type[name].add(speaker)

    for name, entry in by_type.items():
        entry["speakers"] = float(len(speakers_seen_by_type[name]))

    positives = sum(1 for sample in index.samples if sample.class_index == TARGET_INDEX)
    return TargetDatasetReport(
        target_id=index.target_id,
        target_text=index.target_text or "",
        positive_clips=positives,
        positive_speakers=len(positive_speakers),
        positive_seconds=positive_seconds,
        negative_clips=len(index.samples) - positives,
        negative_speakers=len(negative_speakers),
        negative_seconds=negative_seconds,
        by_negative_type={name: dict(entry) for name, entry in sorted(by_type.items())},
        speakers_known=resolved_any,
        window_recommendation=recommend_clip_seconds(positive_durations)
        if positive_durations
        else {},
        min_positive_clips=readiness.min_positive_clips if readiness else 100,
        min_positive_speakers=readiness.min_positive_speakers if readiness else 10,
        recommended_positive_clips=readiness.recommended_positive_clips if readiness else 200,
        recommended_positive_speakers=readiness.recommended_positive_speakers
        if readiness
        else 20,
    )

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
    dataset/unknown/**                 filler                    -> unknown
    dataset/negatives/general_speech/  ordinary Arabic speech    -> general_speech
    dataset/negatives/partial_phrase/  incomplete utterances     -> partial_phrase
    dataset/negatives/noise/           room tone, street, TV     -> noise
    dataset/negatives/silence/                                   -> silence
    dataset/negatives/shared/**        the shared pool           -> by subfolder
    dataset/negatives/hard/007/**      designed to fool 007      -> hard_negative
    dataset/negatives/hard/006/**      designed to fool 006      -> excluded

The last line is deliberate: a hard negative built to fool target 006
(``سبحان الله وبحمده``) is quite likely a recording of 007's complete phrase, and
labelling that as a negative for 007 would teach the model to reject its own
target. ``target.include_other_hard_negatives`` overrides it for datasets where
that cannot happen.
"""

from __future__ import annotations

import logging
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .config import Config, NegativeSamplingConfig, TargetConfig
from .dataset import DatasetIndex, ManifestRecord, Phrase, Sample

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "HARD_DIRNAME",
    "NEGATIVE_TYPES",
    "SHARED_DIRNAME",
    "TARGET_INDEX",
    "TARGET_LABEL",
    "TargetDatasetReport",
    "UNKNOWN_INDEX",
    "UNKNOWN_LABEL",
    "build_target_report",
    "classify_negative_path",
    "negative_breakdown",
    "recommend_clip_seconds",
    "sample_negatives",
    "scan_target_dataset",
    "target_class_names",
]

# Class indices inside a single-target run. 1 is the target so that
# `probabilities[:, 1]` is P(target) under a 2-output softmax, and the sigmoid
# head's single output means the same thing.
UNKNOWN_INDEX = 0
TARGET_INDEX = 1
TARGET_LABEL = "target"
UNKNOWN_LABEL = "unknown"

HARD_DIRNAME = "hard"
SHARED_DIRNAME = "shared"

# Every category trains as UNKNOWN; they exist so that evaluation can say *what*
# produced a false detection. Ordered most- to least-dangerous, which is the
# order the dataset report prints them in.
NEGATIVE_TYPES: Tuple[str, ...] = (
    "hard_negative",
    "partial_phrase",
    "other_dhikr",
    "general_speech",
    "background_audio",
    "noise",
    "silence",
    "unknown",
)

# Folder names accepted for each category, so a dataset can spell them the
# obvious way without a config entry per synonym.
_CATEGORY_ALIASES: Dict[str, str] = {
    "hard": "hard_negative",
    "hard_negative": "hard_negative",
    "hard_negatives": "hard_negative",
    "partial": "partial_phrase",
    "partial_phrase": "partial_phrase",
    "partial_phrases": "partial_phrase",
    "incomplete": "partial_phrase",
    "other_dhikr": "other_dhikr",
    "other_dhkr": "other_dhikr",
    "speech": "general_speech",
    "general_speech": "general_speech",
    "conversation": "general_speech",
    "quran": "background_audio",
    "recitation": "background_audio",
    "tv": "background_audio",
    "background": "background_audio",
    "background_audio": "background_audio",
    "noise": "noise",
    "street": "noise",
    "room": "noise",
    "silence": "silence",
    "unknown": "unknown",
}


def target_class_names() -> List[str]:
    """Class list in class-index order: ``["unknown", "target"]``."""
    return [UNKNOWN_LABEL, TARGET_LABEL]


def classify_negative_path(
    relative: PathLike, default: str = "unknown"
) -> Tuple[str, Optional[str]]:
    """Category of a negative from its path under ``dataset/negatives``.

    Returns ``(negative_type, hard_target)`` where ``hard_target`` is the
    zero-padded id a ``hard/<id>/`` folder belongs to, and ``None`` for every
    other category. The first recognised path segment wins, so
    ``negatives/shared/noise/street/x.wav`` is ``noise`` and an unrecognised
    folder falls back to ``default`` rather than being dropped.
    """
    parts = [part for part in Path(relative).parts if part not in (".", "/")]
    for position, part in enumerate(parts):
        name = part.strip().lower()
        if name == SHARED_DIRNAME:
            continue
        if name in (HARD_DIRNAME, "hard_negative", "hard_negatives"):
            following = parts[position + 1] if position + 1 < len(parts) else ""
            token = following.strip()
            hard_target = f"{int(token):03d}" if token.isdigit() else None
            return "hard_negative", hard_target
        category = _CATEGORY_ALIASES.get(name)
        if category:
            return category, None
    return default, None


def _audio_files(directory: Path, suffixes: set) -> List[Path]:
    return sorted(
        path
        for path in directory.rglob("*")
        if path.is_file() and path.suffix.lower() in suffixes
    )


def _phrase_folders(root: Path) -> List[Path]:
    return sorted(
        (
            entry
            for entry in root.iterdir()
            if entry.is_dir() and entry.name.isdigit()
        ),
        key=lambda entry: int(entry.name),
    )


def scan_target_dataset(
    dataset_dir: PathLike,
    phrases: Sequence[Phrase],
    target: TargetConfig,
    unknown_class: str = "unknown",
    extensions: Sequence[str] = (".wav",),
) -> DatasetIndex:
    """Index the dataset as TARGET vs UNKNOWN for ``target.phrase_id``.

    Every sample keeps the folder it came from in ``rel_dir`` (so conditioned
    clips are cached per source folder and shared between targets) and its
    :data:`NEGATIVE_TYPES` category in ``negative_type`` (``None`` for the
    target's own clips).
    """
    if not target.enabled:
        raise ValueError("scan_target_dataset needs target.phrase_id to be set")

    root = Path(dataset_dir)
    if not root.is_dir():
        raise FileNotFoundError(f"dataset directory not found: {root}")

    folder = target.folder or ""
    target_dir = root / folder
    if not target_dir.is_dir():
        available = ", ".join(sorted(entry.name for entry in root.iterdir() if entry.is_dir()))
        raise FileNotFoundError(
            f"target phrase {folder} has no folder at {target_dir} "
            f"(dataset holds: {available or 'nothing'})"
        )

    suffixes = {ext.lower() for ext in extensions}
    phrase_text = {phrase.id: phrase.text for phrase in phrases}
    target_text = phrase_text.get(int(target.phrase_id or 0), folder)

    samples: List[Sample] = []

    for path in _audio_files(target_dir, suffixes):
        samples.append(
            Sample(
                path=path,
                label=TARGET_LABEL,
                class_index=TARGET_INDEX,
                phrase_id=int(target.phrase_id) if target.phrase_id else None,
                text=target_text,
                rel_dir=folder,
                negative_type=None,
            )
        )
    if not samples:
        raise FileNotFoundError(f"target folder {target_dir} contains no audio files")

    def add_negatives(files: Sequence[Path], negative_type: str, rel_dir: str) -> None:
        for path in files:
            samples.append(
                Sample(
                    path=path,
                    label=UNKNOWN_LABEL,
                    class_index=UNKNOWN_INDEX,
                    phrase_id=None,
                    text=UNKNOWN_LABEL,
                    rel_dir=rel_dir,
                    negative_type=negative_type,
                )
            )

    # Other phrase folders. These are the negatives that matter most and cost
    # nothing to collect, because they are already in the dataset.
    if target.auto_other_dhikr_negatives:
        for directory in _phrase_folders(root):
            if directory.name == folder:
                continue
            add_negatives(_audio_files(directory, suffixes), "other_dhikr", directory.name)

    # The legacy `unknown` folder.
    unknown_dir = root / unknown_class
    if unknown_dir.is_dir():
        add_negatives(_audio_files(unknown_dir, suffixes), "unknown", unknown_class)

    # The categorised negative tree, shared across every target.
    negatives_root = root / target.negatives_dir
    skipped_hard: Counter = Counter()
    if negatives_root.is_dir():
        for path in _audio_files(negatives_root, suffixes):
            relative = path.parent.relative_to(negatives_root)
            negative_type, hard_target = classify_negative_path(relative)
            if negative_type == "hard_negative" and hard_target is not None:
                if hard_target != folder and not target.include_other_hard_negatives:
                    skipped_hard[hard_target] += 1
                    continue
            add_negatives(
                [path],
                negative_type,
                str(Path(target.negatives_dir) / relative),
            )

    if skipped_hard:
        LOGGER.info(
            "skipped %d hard negative(s) belonging to other targets (%s); they may "
            "contain this target's phrase. Set target.include_other_hard_negatives "
            "to use them anyway.",
            sum(skipped_hard.values()),
            ", ".join(f"{name}x{count}" for name, count in sorted(skipped_hard.items())),
        )

    positives = sum(1 for sample in samples if sample.class_index == TARGET_INDEX)
    LOGGER.info(
        "target %s: %d positive / %d negative clips (%s)",
        folder,
        positives,
        len(samples) - positives,
        ", ".join(
            f"{name} {count}"
            for name, count in sorted(negative_breakdown(samples).items())
        )
        or "no negatives",
    )
    if len(samples) == positives:
        LOGGER.warning(
            "target %s has no negatives at all. A detector trained only on its own "
            "phrase will fire on everything - add other phrase folders, an `unknown` "
            "folder or a `negatives/` tree before training.",
            folder,
        )

    return DatasetIndex(
        samples=samples,
        class_names=target_class_names(),
        phrases=phrase_text,
        root=root,
        target_id=int(target.phrase_id) if target.phrase_id else None,
        target_text=target_text,
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
                "false counts. Collect them under negatives/hard/<target>/."
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
    speakers: Optional[Dict[str, Optional[str]]] = None,
    config: Optional[Config] = None,
) -> TargetDatasetReport:
    """Assemble :class:`TargetDatasetReport` from an index.

    ``durations`` and ``speakers`` are keyed by ``str(sample.path)``; both are
    optional, so this works straight after a scan and gets sharper once the
    validation pass has measured the files.
    """
    durations = durations or {}
    speakers = speakers or {}
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
        speaker = speakers.get(key)
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

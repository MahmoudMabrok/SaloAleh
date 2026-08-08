"""Speaker identity and speaker-safe train / val / test splits.

A keyword spotter that has heard the same voice in training and in validation
reports a number that has nothing to do with how it will behave on a stranger's
phone. With a few hundred clips from a handful of volunteers this is not a small
effect - it is usually the whole difference between the printed accuracy and the
device.

So splitting here is by *speaker*, not by clip, and the split is checked
afterwards rather than assumed: :func:`verify_speaker_isolation` intersects the
three splits and :func:`assign_speaker_splits` refuses to hand back a manifest
that leaks (requirement 7). The isolation is global - a speaker's target
recordings and their *other-dhikr* recordings land in the same split, so a voice
cannot enter training through the negative pool and be evaluated on through the
positive one (requirement 8).

Speaker ids come from one of two places, and neither is guessed:

``split.speaker_metadata``
    JSON mapping, keyed by file name or by any path suffix::

        {"spk03_007_012.wav": "spk03", "007/spk03_007_012.wav": "spk03"}

``split.speaker_regex``
    matched against the file name, then against the whole path. A named group
    ``speaker`` wins, else group 1, else the whole match. For files named
    ``spk03_007_012.wav`` that is ``^(?P<speaker>[^_]+)_``.
"""

from __future__ import annotations

import json
import logging
import re
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Set, Union

import numpy as np

from .config import SplitConfig
from .dataset import ManifestRecord, Sample

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "SpeakerIsolationReport",
    "SpeakerLeakageError",
    "SpeakerStats",
    "assign_speaker_splits",
    "attach_speakers",
    "load_speaker_metadata",
    "resolve_speaker",
    "resolve_speakers",
    "speaker_stats",
    "verify_speaker_isolation",
]

SPLITS = ("train", "val", "test")


class SpeakerLeakageError(RuntimeError):
    """Raised when one speaker appears in more than one split."""


# ---------------------------------------------------------------------------
# Resolving speaker ids
# ---------------------------------------------------------------------------
def load_speaker_metadata(path: PathLike) -> Dict[str, str]:
    """Read a ``{file: speaker}`` JSON mapping.

    Accepts a bare mapping or ``{"speakers": {...}}``, and a list of
    ``{"file": ..., "speaker": ...}`` entries, because all three are things a
    collection tool plausibly writes.
    """
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(payload, dict) and "speakers" in payload:
        payload = payload["speakers"]
    mapping: Dict[str, str] = {}
    if isinstance(payload, dict):
        for key, value in payload.items():
            speaker = str(value).strip()
            if speaker:
                mapping[str(key).replace("\\", "/")] = speaker
    elif isinstance(payload, list):
        for entry in payload:
            if not isinstance(entry, dict):
                continue
            key = entry.get("file") or entry.get("path")
            speaker = entry.get("speaker") or entry.get("speaker_id")
            if key and speaker:
                mapping[str(key).replace("\\", "/")] = str(speaker).strip()
    else:
        raise ValueError(f"unsupported speaker metadata format in {path}")
    return mapping


def _metadata_lookup(path: Path, metadata: Dict[str, str]) -> Optional[str]:
    """Match the file name first, then progressively longer path suffixes."""
    if not metadata:
        return None
    text = str(path).replace("\\", "/")
    if text in metadata:
        return metadata[text]
    parts = Path(text).parts
    for depth in range(1, len(parts) + 1):
        suffix = "/".join(parts[-depth:])
        if suffix in metadata:
            return metadata[suffix]
    return None


def resolve_speaker(
    path: PathLike,
    pattern: Optional[re.Pattern] = None,
    metadata: Optional[Dict[str, str]] = None,
) -> Optional[str]:
    """Speaker id for one recording, or ``None`` when it cannot be resolved."""
    candidate = Path(path)
    found = _metadata_lookup(candidate, metadata or {})
    if found:
        return found
    if pattern is None:
        return None
    for text in (candidate.name, str(candidate).replace("\\", "/")):
        match = pattern.search(text)
        if not match:
            continue
        if "speaker" in (match.groupdict() or {}) and match.group("speaker"):
            return str(match.group("speaker"))
        if match.groups():
            return str(match.group(1))
        return str(match.group(0))
    return None


def resolve_speakers(
    items: Sequence[Union[Sample, ManifestRecord, str, Path]],
    config: SplitConfig,
    metadata_root: Optional[PathLike] = None,
) -> Dict[str, Optional[str]]:
    """``{path: speaker}`` for every item, warning loudly when identity is lost.

    The warning is the point of the "unknown" case. Training still runs - a
    prototype dataset with no naming convention is a legitimate thing to
    experiment on - but every number it produces is measured across speakers that
    may sit on both sides of the split, and nothing downstream should describe it
    as speaker-independent.
    """
    pattern = re.compile(config.speaker_regex) if config.speaker_regex else None
    metadata: Dict[str, str] = {}
    if config.speaker_metadata:
        location = Path(config.speaker_metadata)
        if not location.is_absolute() and metadata_root:
            location = Path(metadata_root) / location
        if location.is_file():
            metadata = load_speaker_metadata(location)
            LOGGER.info("loaded %d speaker ids from %s", len(metadata), location)
        else:
            LOGGER.warning("split.speaker_metadata points at %s, which does not exist", location)

    resolved: Dict[str, Optional[str]] = {}
    for item in items:
        if isinstance(item, Sample):
            key = str(item.path)
        elif isinstance(item, ManifestRecord):
            key = item.source_path or item.path
            if item.speaker:
                resolved[key] = item.speaker
                continue
        else:
            key = str(item)
        resolved[key] = resolve_speaker(key, pattern, metadata)

    missing = sum(1 for value in resolved.values() if not value)
    if missing:
        level = LOGGER.warning if config.speaker_safe else LOGGER.info
        level(
            "%d of %d recordings have no speaker id (split.speaker_regex=%r, "
            "split.speaker_metadata=%r). EVALUATION IS NOT SPEAKER-INDEPENDENT for "
            "those clips: the same voice can sit in train and in val, and the "
            "reported accuracy will be optimistic.",
            missing,
            len(resolved),
            config.speaker_regex,
            config.speaker_metadata,
        )
    return resolved


def attach_speakers(
    records: Sequence[ManifestRecord],
    speakers: Dict[str, Optional[str]],
) -> List[ManifestRecord]:
    """Copy of ``records`` with ``speaker`` filled in from a resolved mapping."""
    updated: List[ManifestRecord] = []
    for record in records:
        speaker = record.speaker or speakers.get(record.source_path) or speakers.get(record.path)
        updated.append(ManifestRecord(**{**asdict(record), "speaker": speaker}))
    return updated


# ---------------------------------------------------------------------------
# Statistics
# ---------------------------------------------------------------------------
@dataclass
class SpeakerStats:
    """Who is in the dataset and where they ended up."""

    total_recordings: int
    resolved_recordings: int
    positive_speakers: int
    negative_speakers: int
    shared_speakers: int
    speakers_per_split: Dict[str, int] = field(default_factory=dict)
    clips_per_split: Dict[str, int] = field(default_factory=dict)
    positives_per_split: Dict[str, int] = field(default_factory=dict)

    @property
    def all_resolved(self) -> bool:
        return self.total_recordings > 0 and self.resolved_recordings == self.total_recordings

    @property
    def any_resolved(self) -> bool:
        return self.resolved_recordings > 0

    def to_dict(self) -> Dict[str, object]:
        return asdict(self)

    def summary(self) -> str:
        lines = [
            f"recordings       : {self.total_recordings} "
            f"({self.resolved_recordings} with a speaker id)",
            f"positive speakers: {self.positive_speakers}",
            f"negative speakers: {self.negative_speakers} "
            f"({self.shared_speakers} also recorded the target)",
        ]
        for split in SPLITS:
            if split in self.clips_per_split:
                lines.append(
                    f"{split:<17}: {self.clips_per_split.get(split, 0):5d} clips | "
                    f"{self.positives_per_split.get(split, 0):5d} positive | "
                    f"{self.speakers_per_split.get(split, 0):4d} speakers"
                )
        if not self.any_resolved:
            lines.append(
                "\n!! no speaker ids at all - EVALUATION IS NOT SPEAKER-INDEPENDENT."
            )
        elif not self.all_resolved:
            lines.append(
                f"\n!! {self.total_recordings - self.resolved_recordings} recordings have "
                f"no speaker id; those clips are split individually and may leak."
            )
        return "\n".join(lines)


def _positive_indices(records: Sequence[ManifestRecord]) -> Set[int]:
    """Class index of the positive class, for weighting the split quotas.

    Single-target manifests label the target class 1; a multi-class manifest has
    no single positive, so every clip counts equally.
    """
    labels = {record.label for record in records}
    if "target" in labels:
        return {1}
    return set()


def speaker_stats(records: Sequence[ManifestRecord]) -> SpeakerStats:
    positive = _positive_indices(records)
    positive_speakers: Set[str] = set()
    negative_speakers: Set[str] = set()
    per_split: Dict[str, Set[str]] = defaultdict(set)
    clips: Counter = Counter()
    positives: Counter = Counter()
    resolved = 0

    for record in records:
        is_positive = record.class_index in positive if positive else True
        clips[record.split] += 1
        if record.class_index in positive:
            positives[record.split] += 1
        if not record.speaker:
            continue
        resolved += 1
        per_split[record.split].add(record.speaker)
        if is_positive and positive:
            positive_speakers.add(record.speaker)
        elif positive:
            negative_speakers.add(record.speaker)
        else:
            positive_speakers.add(record.speaker)

    return SpeakerStats(
        total_recordings=len(records),
        resolved_recordings=resolved,
        positive_speakers=len(positive_speakers),
        negative_speakers=len(negative_speakers),
        shared_speakers=len(positive_speakers & negative_speakers),
        speakers_per_split={split: len(names) for split, names in sorted(per_split.items())},
        clips_per_split=dict(sorted(clips.items())),
        positives_per_split=dict(sorted(positives.items())),
    )


# ---------------------------------------------------------------------------
# Leakage
# ---------------------------------------------------------------------------
@dataclass
class SpeakerIsolationReport:
    """The three pairwise intersections, stated explicitly."""

    train_val: List[str] = field(default_factory=list)
    train_test: List[str] = field(default_factory=list)
    val_test: List[str] = field(default_factory=list)
    speakers_per_split: Dict[str, int] = field(default_factory=dict)
    unresolved: int = 0

    @property
    def leaked(self) -> List[str]:
        return sorted(set(self.train_val) | set(self.train_test) | set(self.val_test))

    @property
    def is_clean(self) -> bool:
        return not self.leaked

    def to_dict(self) -> Dict[str, object]:
        return {
            "train_val": list(self.train_val),
            "train_test": list(self.train_test),
            "val_test": list(self.val_test),
            "leaked_speakers": self.leaked,
            "speakers_per_split": dict(self.speakers_per_split),
            "unresolved_recordings": self.unresolved,
            "clean": self.is_clean,
        }

    def summary(self) -> str:
        lines = [
            f"train n val : {len(self.train_val)} speaker(s)"
            + (f" - {', '.join(self.train_val[:8])}" if self.train_val else " - empty"),
            f"train n test: {len(self.train_test)} speaker(s)"
            + (f" - {', '.join(self.train_test[:8])}" if self.train_test else " - empty"),
            f"val n test  : {len(self.val_test)} speaker(s)"
            + (f" - {', '.join(self.val_test[:8])}" if self.val_test else " - empty"),
        ]
        lines.append("PASS - no speaker crosses a split." if self.is_clean else "FAIL - speaker leakage.")
        if self.unresolved:
            lines.append(
                f"!! {self.unresolved} recordings have no speaker id and were split "
                f"individually - this check cannot see them. EVALUATION IS NOT "
                f"SPEAKER-INDEPENDENT."
            )
        return "\n".join(lines)


def verify_speaker_isolation(records: Sequence[ManifestRecord]) -> SpeakerIsolationReport:
    """Intersect the speaker sets of the three splits."""
    per_split: Dict[str, Set[str]] = {split: set() for split in SPLITS}
    unresolved = 0
    for record in records:
        if not record.speaker:
            unresolved += 1
            continue
        per_split.setdefault(record.split, set()).add(record.speaker)

    return SpeakerIsolationReport(
        train_val=sorted(per_split["train"] & per_split["val"]),
        train_test=sorted(per_split["train"] & per_split["test"]),
        val_test=sorted(per_split["val"] & per_split["test"]),
        speakers_per_split={split: len(names) for split, names in per_split.items()},
        unresolved=unresolved,
    )


# ---------------------------------------------------------------------------
# Splitting
# ---------------------------------------------------------------------------
def _group_key(record: ManifestRecord, fallback_pattern: Optional[re.Pattern]) -> str:
    """Speaker id when known; otherwise a per-recording group so nothing merges."""
    if record.speaker:
        return f"spk:{record.speaker}"
    if fallback_pattern is not None:
        match = fallback_pattern.search(Path(record.path).name)
        if match:
            return f"grp:{match.group(0)}"
    return f"file:{record.path}"


def assign_speaker_splits(
    records: Sequence[ManifestRecord],
    config: SplitConfig,
    seed: int,
    strict: Optional[bool] = None,
) -> List[ManifestRecord]:
    """Assign ``train`` / ``val`` / ``test`` with every speaker kept whole.

    Quotas are counted in *positive* clips when the manifest has a target class,
    because that is the scarce resource: a split that holds 15% of the clips but
    two positives cannot measure recall. Speakers are placed largest-first into
    whichever split is furthest below its quota, which keeps the ratios close
    while never dividing a voice.

    ``strict`` (default ``split.fail_on_leakage``) makes leakage - and, when
    ``split.require_speaker_ids`` is set, an unresolved speaker - an error rather
    than a warning. That is the requirement-7 behaviour: preprocessing fails
    rather than producing a manifest whose numbers cannot be trusted.
    """
    if not records:
        return []

    strict = config.fail_on_leakage if strict is None else strict
    updated = [ManifestRecord(**asdict(record)) for record in records]

    unresolved = sum(1 for record in updated if not record.speaker)
    if unresolved and config.require_speaker_ids:
        raise SpeakerLeakageError(
            f"{unresolved} of {len(updated)} recordings have no speaker id and "
            f"split.require_speaker_ids is set. Configure split.speaker_regex or "
            f"split.speaker_metadata, or clear the flag to split them individually."
        )
    if unresolved:
        LOGGER.warning(
            "%d of %d recordings have no speaker id; they are split individually, so "
            "EVALUATION IS NOT SPEAKER-INDEPENDENT for them.",
            unresolved,
            len(updated),
        )

    fallback = re.compile(config.group_regex) if config.group_regex else None
    groups: Dict[str, List[int]] = defaultdict(list)
    for position, record in enumerate(updated):
        groups[_group_key(record, fallback)].append(position)

    positive = _positive_indices(updated)

    def weight_of(positions: Sequence[int]) -> float:
        if positive:
            count = sum(1 for index in positions if updated[index].class_index in positive)
            # Negative-only speakers still have to go somewhere; a small weight
            # keeps them spread out without letting them fill a quota measured in
            # positives.
            return float(count) if count else 0.05
        return float(len(positions))

    total_weight = sum(weight_of(positions) for positions in groups.values())
    quotas = {
        "test": total_weight * config.test_ratio,
        "val": total_weight * config.val_ratio,
        "train": total_weight * max(1.0 - config.test_ratio - config.val_ratio, 0.0),
    }

    rng = np.random.default_rng(seed)
    names = sorted(groups)
    order = rng.permutation(len(names))
    shuffled = [names[int(index)] for index in order]
    # Largest first: a big speaker placed late can only overshoot whatever quota
    # is left, and with a handful of speakers that is the difference between a
    # 15% validation split and a 40% one.
    shuffled.sort(key=lambda name: weight_of(groups[name]), reverse=True)

    assigned: Dict[str, float] = {split: 0.0 for split in SPLITS}
    for name in shuffled:
        weight = weight_of(groups[name])
        candidates = [split for split in SPLITS if quotas[split] > 0.0]
        if not candidates:
            candidates = ["train"]
        chosen = max(candidates, key=lambda split: (quotas[split] - assigned[split], split == "train"))
        assigned[chosen] += weight
        for index in groups[name]:
            updated[index].split = chosen

    _rescue_empty_splits(updated, groups, quotas, positive)

    report = verify_speaker_isolation(updated)
    if not report.is_clean:
        message = (
            "speaker leakage after splitting: "
            + "; ".join(
                f"{pair} -> {', '.join(names)}"
                for pair, names in (
                    ("train n val", report.train_val),
                    ("train n test", report.train_test),
                    ("val n test", report.val_test),
                )
                if names
            )
        )
        if strict:
            raise SpeakerLeakageError(message)
        LOGGER.warning("%s (split.fail_on_leakage is off)", message)

    stats = speaker_stats(updated)
    LOGGER.info("speaker-safe split:\n%s", stats.summary())
    return updated


def _rescue_empty_splits(
    records: List[ManifestRecord],
    groups: Dict[str, List[int]],
    quotas: Dict[str, float],
    positive: Set[int],
) -> None:
    """Move one whole speaker into a split that ended up with no positives.

    With five speakers and a 15% validation ratio the greedy pass can legitimately
    leave val or test empty. An empty evaluation split is worse than a lopsided
    one - it silently turns "val_accuracy" into a number over zero clips - so one
    speaker is moved across, taken from train and smallest first so the training
    split loses as little as possible.
    """

    def positives_in(split: str) -> int:
        if not positive:
            return sum(1 for record in records if record.split == split)
        return sum(
            1 for record in records if record.split == split and record.class_index in positive
        )

    for split in ("val", "test"):
        if quotas.get(split, 0.0) <= 0.0 or positives_in(split):
            continue
        donors = [
            name
            for name, positions in groups.items()
            if records[positions[0]].split == "train"
            and any(
                (not positive and True) or records[index].class_index in positive
                for index in positions
            )
        ]
        if len(donors) <= 1:
            LOGGER.warning(
                "the '%s' split has no positive clips and train cannot spare a speaker; "
                "this dataset is too small to evaluate on held-out voices.",
                split,
            )
            continue
        donors.sort(key=lambda name: (len(groups[name]), name))
        chosen = donors[0]
        for index in groups[chosen]:
            records[index].split = split
        LOGGER.warning(
            "the '%s' split had no positive clips; moved speaker group '%s' (%d clips) "
            "into it. The ratios are approximate at this dataset size.",
            split,
            chosen,
            len(groups[chosen]),
        )

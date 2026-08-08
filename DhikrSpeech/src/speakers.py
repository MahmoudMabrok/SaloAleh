"""Speaker identity: where it comes from, and proving the splits never share it.

This is the most consequential thing the pipeline can know about the dataset.
A model that has heard a voice during training recognises *that voice saying the
phrase* far more easily than a stranger saying it, so a split that puts the same
speaker in train and test reports an accuracy nobody can ship against. The app is
installed by people the model has never heard.

Three ways to know who spoke a recording, in order of preference:

1. **Explicit metadata** - ``speakers.csv`` next to ``phrases.json``, two columns
   (``file,speaker``). Nothing to infer, nothing to get wrong.
2. **A per-speaker subfolder** - ``dataset/006/ali/*.wav``.
3. **A filename convention** - ``speaker01_001.wav``, matched by a configurable
   regex. Convenient, and the easiest to be quietly wrong about, which is why
   :func:`resolve_speakers` reports how many files the pattern actually matched.

When none of them applies the resolver says so loudly and the evaluation is
explicitly *not* speaker-independent. It never silently pretends otherwise.
"""

from __future__ import annotations

import csv
import logging
import re
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple, Union

from .config import SpeakerConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "SpeakerAssignment",
    "SpeakerLeak",
    "SpeakerReport",
    "SpeakerResolver",
    "load_speaker_metadata",
    "speaker_leaks",
    "speaker_report",
    "verify_speaker_disjoint",
]

# What ``resolve``/``describe`` report as the origin of an id.
SOURCE_METADATA = "metadata"
SOURCE_FILENAME = "filename"
SOURCE_PARENT = "parent"
SOURCE_NONE = "none"

SPLITS = ("train", "val", "test")


# ---------------------------------------------------------------------------
# Metadata file
# ---------------------------------------------------------------------------
def _normalise_key(value: str) -> str:
    """Match a metadata row to a file however the row spells the path."""
    return str(value).strip().replace("\\", "/").lstrip("./").lower()


def load_speaker_metadata(path: PathLike) -> Dict[str, str]:
    """Read ``speakers.csv`` into ``{normalised file key: speaker id}``.

    The file is a two-column CSV with a header::

        file,speaker
        006/ali_001.wav,ali
        006/ali_002.wav,ali
        007/fatima_001.wav,fatima

    ``file`` may be a bare name, a path relative to ``dataset/`` or an absolute
    path - every form is indexed, so the same file matches whichever way the
    manifest happens to spell it. A missing file is not an error: the caller
    falls back to the next source.
    """
    source = Path(path)
    if not source.is_file():
        return {}

    mapping: Dict[str, str] = {}
    with open(source, "r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fields = {name.strip().lower(): name for name in (reader.fieldnames or [])}
        file_field = fields.get("file") or fields.get("path") or fields.get("filename")
        speaker_field = fields.get("speaker") or fields.get("speaker_id")
        if not file_field or not speaker_field:
            LOGGER.warning(
                "%s needs 'file' and 'speaker' columns (found: %s) - ignoring it",
                source,
                ", ".join(reader.fieldnames or []) or "nothing",
            )
            return {}
        for row in reader:
            raw = (row.get(file_field) or "").strip()
            speaker = (row.get(speaker_field) or "").strip()
            if not raw or not speaker:
                continue
            key = _normalise_key(raw)
            mapping[key] = speaker
            # Index the bare name too, so a manifest that stores only the file
            # name still matches a row that spells out a path.
            mapping.setdefault(_normalise_key(Path(raw).name), speaker)
    LOGGER.info("loaded %d speaker rows from %s", len(mapping), source)
    return mapping


# ---------------------------------------------------------------------------
# Resolver
# ---------------------------------------------------------------------------
@dataclass
class SpeakerAssignment:
    """One recording's speaker, and where the id came from."""

    path: str
    speaker: Optional[str]
    source: str


@dataclass
class SpeakerResolver:
    """Turns a recording path into a speaker id, per ``split.speaker``.

    ``dataset_root`` is only used to compute the path a metadata row would spell,
    so a resolver still works on paths from outside it (the manifest stores
    ``<label>/<name>.wav``).
    """

    config: SpeakerConfig
    metadata: Dict[str, str] = field(default_factory=dict)
    dataset_root: Optional[Path] = None
    _pattern: Optional[re.Pattern] = field(default=None, init=False, repr=False)
    # Decided by `describe`/`resolve_all` under `source: auto`; until then `auto`
    # tries every source in order for each file.
    resolved_source: Optional[str] = field(default=None, init=False)

    def __post_init__(self) -> None:
        if self.config.regex:
            try:
                self._pattern = re.compile(self.config.regex)
            except re.error as exc:
                raise ValueError(
                    f"split.speaker.regex is not a valid regular expression: {exc}"
                ) from exc

    # -- individual sources -------------------------------------------------
    def _from_metadata(self, path: PathLike) -> Optional[str]:
        if not self.metadata:
            return None
        candidate = Path(path)
        keys = [_normalise_key(str(candidate)), _normalise_key(candidate.name)]
        if self.dataset_root is not None:
            try:
                keys.insert(0, _normalise_key(str(candidate.relative_to(self.dataset_root))))
            except ValueError:
                pass
        # `<label>/<name>.wav` - how the manifest spells a processed clip.
        if candidate.parent.name:
            keys.append(_normalise_key(f"{candidate.parent.name}/{candidate.name}"))
        for key in keys:
            speaker = self.metadata.get(key)
            if speaker:
                return speaker
        return None

    def _from_filename(self, path: PathLike) -> Optional[str]:
        if self._pattern is None:
            return None
        match = self._pattern.search(Path(path).name)
        if not match:
            return None
        if "speaker" in (match.groupdict() or {}):
            value = match.group("speaker")
        elif match.groups():
            value = match.group(1)
        else:
            value = match.group(0)
        value = (value or "").strip()
        return value or None

    def _from_parent(
        self, path: PathLike, blocked: Sequence[str] = ()
    ) -> Optional[str]:
        """The folder a recording sits in *inside* its class folder.

        ``dataset/006/ali/x.wav`` -> ``ali``. ``blocked`` holds folder names that
        are structure rather than people - the class folder itself and the
        negative category (``unknown/hard_negative/x.wav`` is a category, not a
        speaker called "hard_negative"). ``dataset/006/x.wav`` resolves to nothing.
        """
        parent = Path(path).parent.name
        if not parent or parent in set(blocked):
            return None
        if self.dataset_root is not None and Path(path).parent == Path(self.dataset_root):
            return None
        return parent

    # -- public API ---------------------------------------------------------
    def resolve(
        self, path: PathLike, blocked: Sequence[str] = ()
    ) -> SpeakerAssignment:
        """The speaker of one recording, following the configured source."""
        source = self.resolved_source or self.config.source
        text = str(path)

        if source == SOURCE_NONE:
            return SpeakerAssignment(text, None, SOURCE_NONE)
        if source == SOURCE_METADATA:
            return SpeakerAssignment(text, self._from_metadata(path), SOURCE_METADATA)
        if source == SOURCE_FILENAME:
            return SpeakerAssignment(text, self._from_filename(path), SOURCE_FILENAME)
        if source == SOURCE_PARENT:
            return SpeakerAssignment(text, self._from_parent(path, blocked), SOURCE_PARENT)

        # auto: metadata, then filename, then folder.
        for name, getter in (
            (SOURCE_METADATA, lambda: self._from_metadata(path)),
            (SOURCE_FILENAME, lambda: self._from_filename(path)),
            (SOURCE_PARENT, lambda: self._from_parent(path, blocked)),
        ):
            speaker = getter()
            if speaker:
                return SpeakerAssignment(text, speaker, name)
        return SpeakerAssignment(text, None, SOURCE_NONE)

    def resolve_all(
        self, items: Sequence[Tuple[PathLike, Sequence[str]]]
    ) -> List[SpeakerAssignment]:
        """Resolve a whole dataset, deciding the ``auto`` source once.

        Deciding per file would let a dataset end up with ids from three different
        conventions, where ``ali`` from a folder and ``ali`` from a filename may or
        may not be the same person. So under ``auto`` the source that covers
        enough of the dataset is chosen once and applied to every file.

        ``items`` is ``(path, blocked_folder_names)``.
        """
        if self.config.source != "auto":
            self.resolved_source = self.config.source
            return [self.resolve(path, blocked) for path, blocked in items]

        total = len(items)
        if total == 0:
            self.resolved_source = SOURCE_NONE
            return []

        candidates = (
            (SOURCE_METADATA, lambda path, blocked: self._from_metadata(path)),
            (SOURCE_FILENAME, lambda path, blocked: self._from_filename(path)),
            (SOURCE_PARENT, self._from_parent),
        )
        for name, getter in candidates:
            resolved = [getter(path, blocked) for path, blocked in items]
            matched = sum(1 for value in resolved if value)
            ratio = matched / float(total)
            if ratio >= self.config.auto_match_ratio:
                self.resolved_source = name
                LOGGER.info(
                    "speaker ids taken from %s (%d/%d files, %.0f%%)",
                    name,
                    matched,
                    total,
                    ratio * 100.0,
                )
                return [
                    SpeakerAssignment(str(path), value, name if value else SOURCE_NONE)
                    for (path, _), value in zip(items, resolved)
                ]
            if matched:
                LOGGER.info(
                    "speaker source '%s' matched only %d/%d files (%.0f%%, need %.0f%%)",
                    name,
                    matched,
                    total,
                    ratio * 100.0,
                    self.config.auto_match_ratio * 100.0,
                )

        self.resolved_source = SOURCE_NONE
        return [SpeakerAssignment(str(path), None, SOURCE_NONE) for path, _ in items]


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------
@dataclass
class SpeakerLeak:
    """One speaker found in more than one split."""

    speaker: str
    splits: List[str]
    counts: Dict[str, int]

    def summary(self) -> str:
        where = ", ".join(f"{split}={self.counts.get(split, 0)}" for split in self.splits)
        return f"{self.speaker}: {where}"


@dataclass
class SpeakerReport:
    """Who is in the dataset, and whether the splits keep them apart."""

    source: str
    total_recordings: int
    assigned: int
    speakers: List[str]
    recordings_per_speaker: Dict[str, int]
    speakers_per_class: Dict[str, List[str]]
    recordings_per_class_speaker: Dict[str, Dict[str, int]] = field(default_factory=dict)
    speakers_per_split: Dict[str, List[str]] = field(default_factory=dict)
    leaks: List[SpeakerLeak] = field(default_factory=list)

    @property
    def num_speakers(self) -> int:
        return len(self.speakers)

    @property
    def unassigned(self) -> int:
        return self.total_recordings - self.assigned

    @property
    def known(self) -> bool:
        """True when the dataset carries usable speaker information at all."""
        return self.source != SOURCE_NONE and self.assigned > 0

    @property
    def is_disjoint(self) -> bool:
        return not self.leaks

    def coverage(self) -> float:
        return self.assigned / float(self.total_recordings) if self.total_recordings else 0.0

    def to_dict(self) -> Dict[str, object]:
        payload = asdict(self)
        payload.update(
            {
                "num_speakers": self.num_speakers,
                "unassigned": self.unassigned,
                "coverage": self.coverage(),
                "is_disjoint": self.is_disjoint,
                "known": self.known,
            }
        )
        return payload

    def warnings(self, min_speakers: int = 10) -> List[str]:
        notes: List[str] = []
        if not self.known:
            notes.append(
                "NO SPEAKER INFORMATION. The split is random, so the same voice can "
                "appear in train and test - and a model recognises a voice it has "
                "heard far more easily than a stranger. Every accuracy below is "
                "therefore NOT speaker-independent and says nothing about how the "
                "app behaves for a new user. Fix it with a speakers.csv, a "
                "per-speaker subfolder (dataset/006/ali/*.wav), or a filename "
                "convention (ali_001.wav) plus split.speaker.regex."
            )
            return notes
        if self.unassigned:
            notes.append(
                f"{self.unassigned} of {self.total_recordings} recordings have no "
                f"speaker id, so they are split individually and can leak across "
                f"splits. Give them one before reading the test numbers."
            )
        if self.num_speakers < min_speakers:
            notes.append(
                f"{self.num_speakers} speaker(s), below the recommended "
                f"{min_speakers}. The split is speaker-safe but not speaker-diverse: "
                f"the test set measures generalisation to {self.num_speakers} "
                f"voices' worth of variation, which is optimistic for a public app. "
                f"More speakers beats more recordings per speaker."
            )
        counts = list(self.recordings_per_speaker.values())
        if counts and max(counts) > 0.5 * self.assigned and self.num_speakers > 1:
            dominant = max(self.recordings_per_speaker, key=self.recordings_per_speaker.get)
            notes.append(
                f"speaker '{dominant}' contributes "
                f"{self.recordings_per_speaker[dominant]} of {self.assigned} "
                f"recordings - the model will mostly learn that voice."
            )
        thin = [
            label
            for label, speakers in self.speakers_per_class.items()
            if len(speakers) < 2
        ]
        if thin:
            notes.append(
                f"class(es) {', '.join(sorted(thin))} come from fewer than two "
                f"speakers, so the model cannot tell the phrase from the voice."
            )
        if self.leaks:
            notes.append(
                f"SPEAKER LEAKAGE: {len(self.leaks)} speaker(s) appear in more than "
                f"one split ({', '.join(leak.speaker for leak in self.leaks[:5])}"
                f"{' ...' if len(self.leaks) > 5 else ''}). The evaluation is "
                f"invalid until this is fixed."
            )
        return notes

    def summary(self, min_speakers: int = 10) -> str:
        lines = [
            f"speaker source   : {self.source}",
            f"recordings       : {self.total_recordings} "
            f"({self.assigned} with a speaker id, {self.unassigned} without)",
            f"speakers         : {self.num_speakers}",
        ]
        if self.recordings_per_speaker:
            counts = sorted(self.recordings_per_speaker.values())
            lines.append(
                f"per speaker      : min {counts[0]} | median "
                f"{counts[len(counts) // 2]} | max {counts[-1]}"
            )
        for label, speakers in sorted(self.speakers_per_class.items()):
            lines.append(f"  {label:<12} {len(speakers)} speaker(s)")
        if self.speakers_per_split:
            for split in SPLITS:
                if split in self.speakers_per_split:
                    lines.append(
                        f"  {split:<12} {len(self.speakers_per_split[split])} speaker(s)"
                    )
            lines.append(
                "  disjoint     : "
                + ("PASS - no speaker appears in two splits" if self.is_disjoint else "FAIL")
            )
            for leak in self.leaks[:10]:
                lines.append(f"    leak: {leak.summary()}")
        for note in self.warnings(min_speakers):
            lines.append(f"\n!! {note}")
        return "\n".join(lines)


def speaker_leaks(
    assignments: Iterable[Tuple[Optional[str], str]]
) -> List[SpeakerLeak]:
    """Speakers present in more than one split.

    ``assignments`` is ``(speaker, split)`` pairs. Recordings without a speaker id
    are skipped - they cannot be proven to leak, which is exactly why the report
    counts them separately.
    """
    per_speaker: Dict[str, Counter] = defaultdict(Counter)
    for speaker, split in assignments:
        if not speaker or not split:
            continue
        per_speaker[speaker][split] += 1

    leaks: List[SpeakerLeak] = []
    for speaker in sorted(per_speaker):
        counts = per_speaker[speaker]
        if len(counts) > 1:
            leaks.append(
                SpeakerLeak(
                    speaker=speaker,
                    splits=sorted(counts),
                    counts={split: int(count) for split, count in sorted(counts.items())},
                )
            )
    return leaks


def speaker_report(
    items: Sequence[Tuple[Optional[str], str, str]], source: str
) -> SpeakerReport:
    """Build a :class:`SpeakerReport` from ``(speaker, label, split)`` triples.

    ``split`` may be empty for a dataset that has not been split yet; the split
    half of the report is then simply absent rather than wrong.
    """
    per_speaker: Counter = Counter()
    per_class: Dict[str, set] = defaultdict(set)
    per_class_speaker: Dict[str, Counter] = defaultdict(Counter)
    per_split: Dict[str, set] = defaultdict(set)
    assigned = 0

    for speaker, label, split in items:
        if speaker:
            assigned += 1
            per_speaker[speaker] += 1
            per_class[label].add(speaker)
            per_class_speaker[label][speaker] += 1
            if split:
                per_split[split].add(speaker)

    return SpeakerReport(
        source=source,
        total_recordings=len(items),
        assigned=assigned,
        speakers=sorted(per_speaker),
        recordings_per_speaker={name: int(per_speaker[name]) for name in sorted(per_speaker)},
        speakers_per_class={label: sorted(names) for label, names in sorted(per_class.items())},
        recordings_per_class_speaker={
            label: {name: int(count) for name, count in sorted(counter.items())}
            for label, counter in sorted(per_class_speaker.items())
        },
        speakers_per_split={split: sorted(names) for split, names in sorted(per_split.items())},
        leaks=speaker_leaks((speaker, split) for speaker, _, split in items),
    )


def verify_speaker_disjoint(
    report: SpeakerReport, require_disjoint: bool = True
) -> SpeakerReport:
    """Fail the run when speakers were configured and the splits share one.

    A leak is not a warning-level problem: everything measured downstream - the
    test accuracy, the calibrated thresholds, the readiness verdict - is computed
    from a split that quietly answers a different question. So when speaker
    information exists and ``require_disjoint`` is on, this raises. When there is
    no speaker information there is nothing to verify, and the loud warning in
    :meth:`SpeakerReport.warnings` is what the caller gets instead.
    """
    if not report.leaks:
        return report
    if not require_disjoint:
        LOGGER.warning(
            "%d speaker(s) appear in more than one split and "
            "split.speaker.require_disjoint is off - the evaluation is not "
            "speaker-independent",
            len(report.leaks),
        )
        return report
    detail = "\n".join(f"  {leak.summary()}" for leak in report.leaks[:10])
    more = "" if len(report.leaks) <= 10 else f"\n  ... and {len(report.leaks) - 10} more"
    raise ValueError(
        f"speaker leakage: {len(report.leaks)} speaker(s) appear in more than one "
        f"split:\n{detail}{more}\n"
        "Every number measured on these splits describes recognising a known voice, "
        "not a new user. Re-run the split (assign_splits groups by speaker when "
        "speaker ids exist), fix the speaker ids, or set "
        "split.speaker.require_disjoint: false to accept the consequence knowingly."
    )

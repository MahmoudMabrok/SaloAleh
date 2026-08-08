"""Dataset discovery, validation, preprocessing, splitting and input pipeline.

The layout on Drive is::

    Dhikr Speech Dataset/dataset/001/*.wav      # one folder per phrase id
    Dhikr Speech Dataset/dataset/unknown/*.wav  # filler / out-of-vocabulary audio
    Dhikr Speech Dataset/phrases.json           # [{"id": 1, "text": "..."}, ...]

Notebook 01 scans and validates, notebook 02 writes conditioned 16 kHz mono
PCM16 copies plus ``processed/manifest.csv``. Everything downstream reads that
manifest, so training, evaluation and export always see the same splits.
"""

from __future__ import annotations

import csv
import json
import logging
import re
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple, Union

import numpy as np

from .augmentation import spec_augment
from .audio import (
    AudioFileInfo,
    content_hash,
    load_audio,
    prepare_clip,
    probe_audio,
    read_wav,
    rms_dbfs,
    write_wav,
)
from .config import AudioConfig, ClassesConfig, Config, SplitConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]
ProgressFn = Optional[Callable[[int, int], None]]

__all__ = [
    "DatasetIndex",
    "DatasetIssue",
    "DatasetStatistics",
    "ManifestRecord",
    "Phrase",
    "PreprocessSummary",
    "Sample",
    "ValidationReport",
    "assign_splits",
    "class_names_from_manifest",
    "compute_class_weights",
    "filter_split",
    "load_manifest",
    "load_phrases",
    "make_tf_dataset",
    "preprocess_dataset",
    "save_manifest",
    "scan_dataset",
    "split_counts",
    "validate_dataset",
]

MANIFEST_FIELDS = [
    "path",
    "source_path",
    "label",
    "class_index",
    "phrase_id",
    "text",
    "split",
    "duration",
    # Single-target mode. `negative_type` is empty for the target's own clips and
    # for legacy multi-class manifests; `speaker` is empty when speaker identity
    # could not be resolved. Both are optional on read, so a manifest written
    # before this change still loads.
    "negative_type",
    "speaker",
]

ISSUE_KINDS = (
    "corrupted",
    "empty",
    "silent",
    "stereo",
    "sample_rate",
    "too_short",
    "too_long",
    "duplicate",
)


# ---------------------------------------------------------------------------
# Phrases and samples
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class Phrase:
    """One entry of ``phrases.json``."""

    id: int
    text: str

    @property
    def folder(self) -> str:
        """Canonical zero-padded folder name, e.g. id 1 -> ``001``."""
        return f"{self.id:03d}"


def load_phrases(path: PathLike) -> List[Phrase]:
    """Read ``phrases.json``. Accepts a list or an ``{"phrases": [...]}`` wrapper."""
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        payload = payload.get("phrases", [])
    phrases: List[Phrase] = []
    seen: set = set()
    for entry in payload:
        identifier = int(entry["id"])
        if identifier in seen:
            raise ValueError(f"duplicate phrase id {identifier} in {path}")
        seen.add(identifier)
        phrases.append(Phrase(id=identifier, text=str(entry.get("text", "")).strip()))
    return sorted(phrases, key=lambda phrase: phrase.id)


@dataclass(frozen=True)
class Sample:
    """A single recording on disk.

    ``rel_dir`` is the sample's folder *as it exists in the dataset* (``007``,
    ``negatives/hard/007``, ...), which is not the same as ``label`` in
    single-target mode where every negative is labelled ``unknown``. Conditioned
    clips are cached under ``rel_dir``, so the shared negative pool is
    preprocessed once and reused by every target rather than re-written per run.

    ``negative_type`` is the :data:`src.targets.NEGATIVE_TYPES` category of a
    negative and ``None`` for the target's own clips.
    """

    path: Path
    label: str
    class_index: int
    phrase_id: Optional[int]
    text: str
    rel_dir: str = ""
    negative_type: Optional[str] = None

    @property
    def cache_dir(self) -> str:
        return self.rel_dir or self.label


@dataclass
class DatasetIndex:
    """All recordings found under ``dataset/`` plus the class vocabulary."""

    samples: List[Sample]
    class_names: List[str]
    phrases: Dict[int, str] = field(default_factory=dict)
    root: Optional[Path] = None
    # Set in single-target mode; None for a multi-class index.
    target_id: Optional[int] = None
    target_text: str = ""

    @property
    def num_classes(self) -> int:
        return len(self.class_names)

    def counts(self) -> Dict[str, int]:
        counter = Counter(sample.label for sample in self.samples)
        return {name: counter.get(name, 0) for name in self.class_names}

    def label_text(self, label: str) -> str:
        for sample in self.samples:
            if sample.label == label:
                return sample.text
        return label

    def by_class(self) -> Dict[str, List[Sample]]:
        grouped: Dict[str, List[Sample]] = defaultdict(list)
        for sample in self.samples:
            grouped[sample.label].append(sample)
        return dict(grouped)

    def __len__(self) -> int:
        return len(self.samples)


def _sort_key(label: str) -> Tuple[int, object]:
    """Numeric folders first in numeric order, named folders (``unknown``) last."""
    return (0, int(label)) if label.isdigit() else (1, label)


def scan_dataset(
    dataset_dir: PathLike,
    phrases: Sequence[Phrase],
    unknown_class: str = "unknown",
    extensions: Sequence[str] = (".wav",),
    classes: Optional[ClassesConfig] = None,
) -> DatasetIndex:
    """Index every recording under ``dataset_dir``, one folder per class.

    ``classes`` restricts the vocabulary to the configured phrase ids. This is
    the single point where the class list is decided, so the selection flows into
    the manifest, the class indices, the model's output width and ``labels.txt``
    without anything downstream needing to know about it.
    """
    root = Path(dataset_dir)
    if not root.is_dir():
        raise FileNotFoundError(f"dataset directory not found: {root}")

    phrase_text = {phrase.id: phrase.text for phrase in phrases}
    suffixes = {ext.lower() for ext in extensions}

    class_dirs = sorted(
        (entry for entry in root.iterdir() if entry.is_dir() and not entry.name.startswith(".")),
        key=lambda entry: _sort_key(entry.name),
    )
    if not class_dirs:
        raise FileNotFoundError(f"no class folders inside {root}")

    if classes is not None and classes.enabled:
        found = {entry.name for entry in class_dirs}
        class_dirs = [
            entry for entry in class_dirs if classes.selects(entry.name, unknown_class)
        ]
        # `unknown` alone is not a vocabulary: check the selected phrases exist,
        # rather than that anything at all survived the filter.
        if not [entry for entry in class_dirs if entry.name != unknown_class]:
            raise FileNotFoundError(
                f"classes.include_phrases selects {classes.folders}, none of which exist "
                f"under {root} (found: {', '.join(sorted(found)) or 'nothing'})"
            )
        absent = [name for name in (classes.folders or []) if name not in found]
        if absent:
            LOGGER.warning(
                "classes.include_phrases asks for %s, which have no dataset folder",
                ", ".join(absent),
            )
        LOGGER.info(
            "class selection active: %d of %d folders (%s)",
            len(class_dirs),
            len(found),
            ", ".join(entry.name for entry in class_dirs),
        )

    class_names = [entry.name for entry in class_dirs]
    class_to_index = {name: index for index, name in enumerate(class_names)}

    samples: List[Sample] = []
    for directory in class_dirs:
        label = directory.name
        phrase_id: Optional[int] = int(label) if label.isdigit() else None
        if phrase_id is not None and phrase_id not in phrase_text:
            LOGGER.warning("folder '%s' has no matching id in phrases.json", label)
        text = phrase_text.get(phrase_id, unknown_class if label == unknown_class else label)
        files = sorted(
            path
            for path in directory.rglob("*")
            if path.is_file() and path.suffix.lower() in suffixes
        )
        if not files:
            LOGGER.warning("class folder '%s' contains no audio files", label)
        for path in files:
            samples.append(
                Sample(
                    path=path,
                    label=label,
                    class_index=class_to_index[label],
                    phrase_id=phrase_id,
                    text=text,
                    rel_dir=label,
                )
            )

    wanted = set(phrase.folder for phrase in phrases)
    if classes is not None and classes.enabled:
        # Folders excluded on purpose are not "missing"; only report gaps in the
        # selection, which scan_dataset has already warned about above.
        wanted &= set(classes.folders or [])
    missing = sorted(wanted - set(class_names))
    if missing:
        LOGGER.warning("phrases without a dataset folder: %s", ", ".join(missing))

    LOGGER.info("indexed %d recordings across %d classes", len(samples), len(class_names))
    return DatasetIndex(samples=samples, class_names=class_names, phrases=phrase_text, root=root)


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------
@dataclass
class FileStats:
    """Per-file measurements collected during validation."""

    path: str
    label: str
    ok: bool
    duration: float
    sample_rate: int
    channels: int
    subtype: str
    rms_dbfs: float = float("nan")
    content_hash: str = ""


@dataclass
class DatasetIssue:
    path: str
    label: str
    kind: str
    detail: str


@dataclass
class DatasetStatistics:
    total_files: int
    total_classes: int
    total_duration: float
    mean_duration: float
    min_duration: float
    max_duration: float
    median_duration: float
    per_class: Dict[str, int]

    def to_dict(self) -> Dict[str, object]:
        return asdict(self)


@dataclass
class ValidationReport:
    """Outcome of :func:`validate_dataset`."""

    stats: DatasetStatistics
    file_stats: List[FileStats]
    issues: List[DatasetIssue]
    deep: bool

    @property
    def is_clean(self) -> bool:
        return not self.issues

    def issues_by_kind(self) -> Dict[str, int]:
        counter = Counter(issue.kind for issue in self.issues)
        return {kind: counter.get(kind, 0) for kind in ISSUE_KINDS if counter.get(kind, 0)}

    def issues_of(self, kind: str) -> List[DatasetIssue]:
        return [issue for issue in self.issues if issue.kind == kind]

    def unusable_paths(self) -> List[str]:
        """Files that must not enter preprocessing."""
        blocking = {"corrupted", "empty", "silent"}
        return sorted({issue.path for issue in self.issues if issue.kind in blocking})

    def to_dataframe(self):
        import pandas as pd  # imported lazily: only notebooks need pandas

        return pd.DataFrame([asdict(item) for item in self.file_stats])

    def issues_dataframe(self):
        import pandas as pd

        return pd.DataFrame([asdict(item) for item in self.issues])

    def summary(self) -> str:
        lines = [
            f"files          : {self.stats.total_files}",
            f"classes        : {self.stats.total_classes}",
            f"total duration : {self.stats.total_duration / 60.0:.1f} min",
            f"duration       : mean {self.stats.mean_duration:.2f} s | "
            f"median {self.stats.median_duration:.2f} s | "
            f"min {self.stats.min_duration:.2f} s | max {self.stats.max_duration:.2f} s",
        ]
        issues = self.issues_by_kind()
        if issues:
            lines.append("issues         : " + ", ".join(f"{k}={v}" for k, v in issues.items()))
        else:
            lines.append("issues         : none")
        return "\n".join(lines)

    def save(self, directory: PathLike, name: str = "validation_report") -> Dict[str, Path]:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)
        json_path = target / f"{name}.json"
        payload = {
            "statistics": self.stats.to_dict(),
            "deep": self.deep,
            "issue_counts": self.issues_by_kind(),
            "issues": [asdict(issue) for issue in self.issues],
        }
        json_path.write_text(
            json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
        )

        csv_path = target / f"{name}_files.csv"
        with open(csv_path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(asdict(self.file_stats[0]).keys())
                                    if self.file_stats else ["path"])
            writer.writeheader()
            for item in self.file_stats:
                writer.writerow(asdict(item))
        LOGGER.info("validation report written to %s", target)
        return {"json": json_path, "csv": csv_path}


def validate_dataset(
    index: DatasetIndex,
    config: AudioConfig,
    deep: bool = True,
    progress: ProgressFn = None,
) -> ValidationReport:
    """Check every recording and collect statistics.

    ``deep=False`` only reads file headers (fast). ``deep=True`` also decodes each
    file, which is what silence and duplicate detection need.
    """
    file_stats: List[FileStats] = []
    issues: List[DatasetIssue] = []
    hashes: Dict[str, List[str]] = defaultdict(list)
    durations: List[float] = []
    total = len(index.samples)

    for position, sample in enumerate(index.samples, start=1):
        info: AudioFileInfo = probe_audio(sample.path)
        path_text = str(sample.path)

        if not info.ok:
            issues.append(DatasetIssue(path_text, sample.label, "corrupted", info.error))
            file_stats.append(
                FileStats(path_text, sample.label, False, 0.0, 0, 0, "", float("nan"), "")
            )
            if progress:
                progress(position, total)
            continue

        stats = FileStats(
            path=path_text,
            label=sample.label,
            ok=True,
            duration=info.duration,
            sample_rate=info.sample_rate,
            channels=info.channels,
            subtype=info.subtype,
        )

        if info.frames == 0 or info.duration <= 0.0:
            issues.append(DatasetIssue(path_text, sample.label, "empty", "zero-length file"))
        else:
            durations.append(info.duration)
            if info.duration < config.min_duration:
                issues.append(
                    DatasetIssue(
                        path_text, sample.label, "too_short", f"{info.duration:.3f} s"
                    )
                )
            elif info.duration > config.max_duration:
                issues.append(
                    DatasetIssue(path_text, sample.label, "too_long", f"{info.duration:.3f} s")
                )

        if info.channels > 1:
            issues.append(
                DatasetIssue(path_text, sample.label, "stereo", f"{info.channels} channels")
            )
        if info.sample_rate != config.sample_rate:
            issues.append(
                DatasetIssue(
                    path_text,
                    sample.label,
                    "sample_rate",
                    f"{info.sample_rate} Hz (expected {config.sample_rate} Hz)",
                )
            )

        if deep and info.frames > 0:
            try:
                samples = load_audio(sample.path, config.sample_rate)
                level = rms_dbfs(samples)
                stats.rms_dbfs = level
                digest = content_hash(samples)
                stats.content_hash = digest
                hashes[digest].append(path_text)
                if not np.isfinite(level) or level < config.silence_dbfs:
                    issues.append(
                        DatasetIssue(
                            path_text,
                            sample.label,
                            "silent",
                            f"RMS {level:.1f} dBFS < {config.silence_dbfs:.1f} dBFS",
                        )
                    )
            except Exception as exc:  # noqa: BLE001
                issues.append(
                    DatasetIssue(
                        path_text, sample.label, "corrupted", f"decode failed: {exc}"
                    )
                )
                stats.ok = False

        file_stats.append(stats)
        if progress:
            progress(position, total)

    for digest, paths in hashes.items():
        if len(paths) > 1:
            keeper = paths[0]
            for duplicate in paths[1:]:
                label = next(
                    (item.label for item in file_stats if item.path == duplicate), ""
                )
                issues.append(
                    DatasetIssue(duplicate, label, "duplicate", f"same audio as {keeper}")
                )

    duration_array = np.asarray(durations, dtype=np.float64)
    statistics = DatasetStatistics(
        total_files=total,
        total_classes=index.num_classes,
        total_duration=float(duration_array.sum()) if duration_array.size else 0.0,
        mean_duration=float(duration_array.mean()) if duration_array.size else 0.0,
        min_duration=float(duration_array.min()) if duration_array.size else 0.0,
        max_duration=float(duration_array.max()) if duration_array.size else 0.0,
        median_duration=float(np.median(duration_array)) if duration_array.size else 0.0,
        per_class=index.counts(),
    )
    return ValidationReport(
        stats=statistics, file_stats=file_stats, issues=issues, deep=deep
    )


# ---------------------------------------------------------------------------
# Manifest
# ---------------------------------------------------------------------------
@dataclass
class ManifestRecord:
    """One conditioned clip; the unit every downstream notebook works with."""

    path: str
    source_path: str
    label: str
    class_index: int
    phrase_id: Optional[int]
    text: str
    split: str
    duration: float
    negative_type: Optional[str] = None
    speaker: Optional[str] = None

    def resolve(self, root: PathLike) -> Path:
        candidate = Path(self.path)
        return candidate if candidate.is_absolute() else Path(root) / candidate


def save_manifest(records: Sequence[ManifestRecord], path: PathLike) -> Path:
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with open(destination, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=MANIFEST_FIELDS)
        writer.writeheader()
        for record in records:
            row = asdict(record)
            row["phrase_id"] = "" if record.phrase_id is None else record.phrase_id
            row["negative_type"] = record.negative_type or ""
            row["speaker"] = record.speaker or ""
            writer.writerow(row)
    LOGGER.info("manifest with %d rows written to %s", len(records), destination)
    return destination


def load_manifest(path: PathLike) -> List[ManifestRecord]:
    """Read a manifest, tolerating ones written before a column existed."""
    source = Path(path)
    if not source.is_file():
        raise FileNotFoundError(
            f"manifest not found at {source} - run the preprocessing stage first"
        )
    records: List[ManifestRecord] = []
    with open(source, "r", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            records.append(
                ManifestRecord(
                    path=row["path"],
                    source_path=row["source_path"],
                    label=row["label"],
                    class_index=int(row["class_index"]),
                    phrase_id=int(row["phrase_id"]) if row["phrase_id"] else None,
                    text=row["text"],
                    split=row["split"],
                    duration=float(row["duration"]),
                    negative_type=(row.get("negative_type") or "").strip() or None,
                    speaker=(row.get("speaker") or "").strip() or None,
                )
            )
    return records


def class_names_from_manifest(records: Sequence[ManifestRecord]) -> List[str]:
    """Class list ordered by class index, exactly as the model was trained."""
    mapping: Dict[int, str] = {}
    for record in records:
        existing = mapping.setdefault(record.class_index, record.label)
        if existing != record.label:
            raise ValueError(
                f"class index {record.class_index} maps to both "
                f"'{existing}' and '{record.label}'"
            )
    if mapping and sorted(mapping) != list(range(len(mapping))):
        raise ValueError("class indices in the manifest are not contiguous from 0")
    return [mapping[index] for index in sorted(mapping)]


def filter_split(records: Sequence[ManifestRecord], split: str) -> List[ManifestRecord]:
    return [record for record in records if record.split == split]


def split_counts(records: Sequence[ManifestRecord]) -> Dict[str, int]:
    counter = Counter(record.split for record in records)
    return {split: counter[split] for split in sorted(counter)}


# ---------------------------------------------------------------------------
# Preprocessing
# ---------------------------------------------------------------------------
@dataclass
class PreprocessSummary:
    written: int
    skipped: int
    failed: int
    failures: List[Tuple[str, str]] = field(default_factory=list)

    def summary(self) -> str:
        text = f"written {self.written} | skipped {self.skipped} | failed {self.failed}"
        if self.failures:
            head = "\n".join(f"  {path}: {error}" for path, error in self.failures[:10])
            text = f"{text}\n{head}"
        return text


def preprocess_dataset(
    index: DatasetIndex,
    config: Config,
    exclude: Optional[Iterable[str]] = None,
    overwrite: bool = False,
    progress: ProgressFn = None,
    speakers: Optional[Dict[str, Optional[str]]] = None,
    destination_root: Optional[PathLike] = None,
) -> Tuple[List[ManifestRecord], PreprocessSummary]:
    """Write conditioned copies of every recording and build manifest records.

    Output is 16 kHz mono PCM16, silence trimmed, loudness normalised and fitted
    to ``audio.clip_seconds`` - the exact tensor the model sees at inference.

    In single-target mode ``destination_root`` should be
    ``config.clip_cache_path()``: clips are then keyed by the *source* folder and
    by the audio geometry, so the shared negative pool is conditioned once and
    every target that uses the same window reuses those files instead of writing
    its own copy (requirement 32). ``speakers`` maps ``str(sample.path)`` to a
    speaker id and is carried into the manifest.
    """
    audio_config = config.audio
    destination_root = Path(destination_root or config.paths.processed_path)
    destination_root.mkdir(parents=True, exist_ok=True)
    speaker_map = speakers or {}

    blocked = set(exclude or ())
    records: List[ManifestRecord] = []
    written = skipped = failed = 0
    failures: List[Tuple[str, str]] = []
    total = len(index.samples)

    for position, sample in enumerate(index.samples, start=1):
        source_text = str(sample.path)
        if source_text in blocked:
            skipped += 1
            if progress:
                progress(position, total)
            continue

        relative = Path(sample.cache_dir) / f"{sample.path.stem}.wav"
        target = destination_root / relative
        try:
            if target.exists() and not overwrite:
                skipped += 1
            else:
                raw = load_audio(sample.path, audio_config.sample_rate)
                clip = prepare_clip(raw, audio_config, fixed_length=True)
                write_wav(target, clip, audio_config.sample_rate)
                written += 1
            records.append(
                ManifestRecord(
                    path=str(relative),
                    source_path=source_text,
                    label=sample.label,
                    class_index=sample.class_index,
                    phrase_id=sample.phrase_id,
                    text=sample.text,
                    split="",
                    duration=audio_config.clip_seconds,
                    negative_type=sample.negative_type,
                    speaker=speaker_map.get(source_text),
                )
            )
        except Exception as exc:  # noqa: BLE001
            failed += 1
            failures.append((source_text, f"{type(exc).__name__}: {exc}"))
            LOGGER.warning("preprocessing failed for %s: %s", source_text, exc)
        if progress:
            progress(position, total)

    return records, PreprocessSummary(
        written=written, skipped=skipped, failed=failed, failures=failures
    )


# ---------------------------------------------------------------------------
# Splitting
# ---------------------------------------------------------------------------
def _group_of(record: ManifestRecord, pattern: re.Pattern) -> str:
    match = pattern.search(Path(record.path).name)
    return match.group(0) if match else Path(record.path).stem


def assign_splits(
    records: Sequence[ManifestRecord], config: SplitConfig, seed: int
) -> List[ManifestRecord]:
    """Label every record ``train`` / ``val`` / ``test`` in place-safe fashion.

    Stratified per class by default. When ``split.group_regex`` is set, whole
    groups (for example one speaker or one recording session) move together,
    which prevents leakage at the cost of exact per-class ratios.
    """
    rng = np.random.default_rng(seed)
    updated = [ManifestRecord(**asdict(record)) for record in records]

    if config.group_regex:
        pattern = re.compile(config.group_regex)
        groups: Dict[str, List[int]] = defaultdict(list)
        for position, record in enumerate(updated):
            groups[_group_of(record, pattern)].append(position)
        names = sorted(groups)
        rng.shuffle(names)
        total = len(updated)
        test_target = int(round(total * config.test_ratio))
        val_target = int(round(total * config.val_ratio))
        assigned = 0
        for name in names:
            positions = groups[name]
            if assigned < test_target:
                split = "test"
            elif assigned < test_target + val_target:
                split = "val"
            else:
                split = "train"
            for position in positions:
                updated[position].split = split
            assigned += len(positions)
        return updated

    by_class: Dict[int, List[int]] = defaultdict(list)
    for position, record in enumerate(updated):
        by_class[record.class_index].append(position)

    for class_index in sorted(by_class):
        positions = np.array(by_class[class_index])
        rng.shuffle(positions)
        count = positions.size
        test_size = int(np.floor(count * config.test_ratio))
        val_size = int(np.floor(count * config.val_ratio))
        # Never starve the training split for tiny classes.
        if count >= 3:
            val_size = max(val_size, 1)
            if config.test_ratio > 0.0:
                test_size = max(test_size, 1)
        while count - val_size - test_size < 1 and (val_size > 0 or test_size > 0):
            if test_size >= val_size and test_size > 0:
                test_size -= 1
            elif val_size > 0:
                val_size -= 1

        for offset, position in enumerate(positions):
            if offset < test_size:
                updated[position].split = "test"
            elif offset < test_size + val_size:
                updated[position].split = "val"
            else:
                updated[position].split = "train"

    return updated


def compute_class_weights(
    class_indices: Sequence[int], num_classes: int
) -> Dict[int, float]:
    """Balanced class weights: ``n_samples / (n_classes * count)``."""
    counts = Counter(int(index) for index in class_indices)
    total = sum(counts.values())
    if total == 0:
        raise ValueError("cannot compute class weights from an empty split")
    weights: Dict[int, float] = {}
    for index in range(num_classes):
        count = counts.get(index, 0)
        weights[index] = float(total / (num_classes * count)) if count else 1.0
    return weights


# ---------------------------------------------------------------------------
# tf.data input pipeline
# ---------------------------------------------------------------------------
def make_tf_dataset(
    records: Sequence[ManifestRecord],
    config: Config,
    extractor,
    training: bool,
    augmentor=None,
    processed_root: Optional[PathLike] = None,
    batch_size: Optional[int] = None,
    shuffle: Optional[bool] = None,
    drop_remainder: bool = False,
):
    """Build a ``tf.data.Dataset`` of ``(features, label)``.

    ``features`` is ``(frames, n_mels, 1)`` float32. Decoding is cached before
    augmentation, so every epoch re-augments the same cached audio.
    """
    import tensorflow as tf  # imported lazily: notebooks 01/02 do not need TF

    if not records:
        raise ValueError("cannot build a dataset from zero records")

    root = Path(processed_root or config.paths.processed_path)
    paths = np.array([str(record.resolve(root)) for record in records])
    labels = np.array([record.class_index for record in records], dtype=np.int32)

    clip_samples = config.audio.clip_samples
    frames, n_mels, _ = config.input_shape
    apply_augmentation = bool(training and augmentor is not None and config.augmentation.enabled)

    def _read(path_bytes: bytes) -> np.ndarray:
        samples, sample_rate = read_wav(path_bytes.decode("utf-8"))
        if sample_rate != config.audio.sample_rate:
            # Manifest clips are written at the configured rate; a mismatch means
            # the file was replaced by hand, so condition it again rather than fail.
            samples = load_audio(path_bytes.decode("utf-8"), config.audio.sample_rate)
        if samples.size != clip_samples:
            samples = prepare_clip(samples, config.audio, fixed_length=True)
        return samples.astype(np.float32)

    def _featurize(samples: np.ndarray, seed: np.int64) -> np.ndarray:
        rng = np.random.default_rng(int(seed))
        clip = np.asarray(samples, dtype=np.float32)
        if apply_augmentation:
            clip = augmentor(clip, rng)
        features = extractor(clip)
        if apply_augmentation:
            features = spec_augment(features, config.augmentation, rng)
        return np.ascontiguousarray(features[..., np.newaxis], dtype=np.float32)

    dataset = tf.data.Dataset.from_tensor_slices((paths, labels))

    should_shuffle = training if shuffle is None else shuffle
    if should_shuffle:
        buffer = min(config.training.shuffle_buffer, len(records))
        dataset = dataset.shuffle(buffer, seed=config.seed, reshuffle_each_iteration=True)

    def _load_map(path, label):
        audio = tf.numpy_function(_read, [path], tf.float32, name="read_clip")
        audio.set_shape([clip_samples])
        return audio, label

    dataset = dataset.map(_load_map, num_parallel_calls=tf.data.AUTOTUNE)
    if config.training.cache:
        dataset = dataset.cache()

    def _feature_map(audio, label):
        seed = tf.random.uniform([], maxval=np.iinfo(np.int32).max, dtype=tf.int32)
        features = tf.numpy_function(
            _featurize, [audio, tf.cast(seed, tf.int64)], tf.float32, name="featurize"
        )
        features.set_shape([frames, n_mels, 1])
        return features, label

    dataset = dataset.map(_feature_map, num_parallel_calls=tf.data.AUTOTUNE)

    size = batch_size or config.training.batch_size
    if training and not drop_remainder and len(records) > size:
        # A trailing batch of one sample gives BatchNorm zero variance, which
        # produces a garbage gradient step and poisons the moving statistics.
        # Dropping it costs one clip per epoch and only when the split lands
        # exactly on that remainder.
        if len(records) % size == 1:
            drop_remainder = True
            LOGGER.info(
                "dropping the trailing 1-sample batch (%d records / batch %d)",
                len(records),
                size,
            )
    dataset = dataset.batch(size, drop_remainder=drop_remainder)
    if config.training.prefetch:
        dataset = dataset.prefetch(tf.data.AUTOTUNE)
    return dataset

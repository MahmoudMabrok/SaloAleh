"""Give pre-token recordings a speaker id, from the collector's metadata sheet.

Every filename the SpeechCollector writes today carries a ``sp<8 hex>`` device
token, so ``split.group_regex`` can keep one voice out of both train and
validation. Recordings uploaded before that token shipped carry nothing, and a
file with no speaker is its own group - which is precisely how the same voice
ends up on both sides of a split and the reported validation accuracy stops
describing a stranger installing the app.

Those recordings are not a lost cause. The collector's metadata spreadsheet
still records, per upload, the ``browser`` and ``platform`` it came from, and
that pair - normalised and hashed - is a usable stand-in for a device. This
module reads the sheet, works out which files on the mounted Drive are untagged,
and renames them into the shape a fresh upload would have had::

    006_20260803_183015_ab12cd.webm  ->  006_sp8d358495_20260803_183015_ab12cd.webm

What this buys, and what it does not:

* It **over-groups**, which is the safe direction. Two volunteers on the same
  Chrome/Android build collapse into one group and therefore land in the same
  split; one volunteer is never spread across two. Leakage can only go down. The
  cost is granularity, which is why :meth:`BackfillPlan.summary` prints the group
  sizes *before* anything is renamed: if one bucket holds most of the dataset,
  grouping by it buys almost nothing.
* A derived token deliberately reuses the real ``sp<8 hex>`` shape, so the
  pipeline honours it with no config change beyond turning the grouping on. The
  derivation is deterministic, so a derived token stays identifiable afterwards
  by recomputing it from its row.
* It never invents an identity. A row naming neither a browser nor a platform is
  left alone, keeping the one-group-per-file fallback rather than merging every
  unknown device into a single bucket.
* It never guesses at a filename. Only the exact shape the collector produces is
  rewritten; anything else is counted and skipped, because a mangled class prefix
  would relabel the recording.

Matching a sheet row to a file tolerates a token already being there, so the
whole thing is idempotent and safe to re-run: the sheet keeps the pre-rename name
forever (nothing writes back to it), and a row still finds its file after the
rename. :func:`write_speakers_csv` is the non-destructive alternative - the same
grouping as a ``speakers.csv``, which the resolver prefers over filenames anyway,
with nothing on Drive touched.
"""

from __future__ import annotations

import csv
import hashlib
import json
import logging
import re
from collections import defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple, Union

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "BackfillPlan",
    "BackfillResult",
    "MetadataRow",
    "PlannedRename",
    "SpeakerGroup",
    "apply_backfill",
    "derive_speaker_token",
    "device_fingerprint",
    "filename_with_speaker_token",
    "has_speaker_token",
    "load_metadata_rows",
    "plan_backfill",
    "strip_speaker_token",
    "write_speakers_csv",
]

# The token the pipeline already matches (`split.speaker.filename_patterns`).
SPEAKER_TOKEN = re.compile(r"(?:^|_)(sp[0-9a-f]{8})_")

# The shape the collector produces without a token:
# `{class}_{yyyyMMdd}_{HHmmss}_{6 hex}{extension}`. Anything else is left alone.
UNTAGGED_FILENAME = re.compile(r"^(?P<prefix>[^_]+)_(?P<tail>\d{8}_\d{6}_[0-9a-f]{6}\.[A-Za-z0-9]+)$")

# The sheet columns this module needs. The collector writes more; the rest are
# ignored so an added column never breaks the read.
REQUIRED_COLUMNS = ("filename", "browser", "platform")

# Rows whose filename says the upload is background noise rather than speech.
# Noise is not a class and is never speaker-split, but the collector stamps a
# token on it like anything else, so it is renamed for consistency.
DEFAULT_REPORT_NAME = "speaker_backfill"


# ---------------------------------------------------------------------------
# Token derivation
# ---------------------------------------------------------------------------
def device_fingerprint(browser: object, platform: object) -> str:
    """The string a token is derived from, or ``""`` when there is no signal.

    Case and stray whitespace are normalised so one device reported two ways
    still hashes into one group, and the two fields are joined with a separator
    so ``("ab", "c")`` and ``("a", "bc")`` cannot collide.
    """
    parts = []
    for value in (browser, platform):
        text = "" if value is None else str(value)
        parts.append(re.sub(r"\s+", " ", text.strip().lower()))
    return "|".join(parts) if any(parts) else ""


def derive_speaker_token(browser: object, platform: object) -> str:
    """``sp`` + the first 8 hex characters of SHA-256 over the fingerprint.

    Returns ``""`` when the row names neither a browser nor a platform: a row
    with no signal keeps the pipeline's one-group-per-file fallback instead of
    joining a catch-all bucket that would merge every unknown device.
    """
    fingerprint = device_fingerprint(browser, platform)
    if not fingerprint:
        return ""
    digest = hashlib.sha256(fingerprint.encode("utf-8")).hexdigest()
    return "sp" + digest[:8]


def has_speaker_token(filename: object) -> bool:
    """Whether a filename already names a speaker, derived or real."""
    return bool(SPEAKER_TOKEN.search(str(filename or "")))


def strip_speaker_token(filename: object) -> str:
    """The filename as it was before any token was inserted.

    This is what makes the backfill re-runnable against a sheet nothing writes
    back to: a row recorded as ``006_2026…webm`` still matches the renamed
    ``006_sp8d358495_2026…webm`` on disk.
    """
    return SPEAKER_TOKEN.sub(lambda match: match.group(0)[: -len(match.group(1)) - 1], str(filename or ""))


def filename_with_speaker_token(filename: object, token: str) -> Optional[str]:
    """The filename with ``token`` in the slot the collector puts it in.

    ``None`` when the name is not the untagged collector shape or the token is
    malformed - a mangled class prefix would relabel the recording, which is a
    worse outcome than leaving one file ungrouped.
    """
    match = UNTAGGED_FILENAME.match(str(filename or ""))
    if not match or not re.fullmatch(r"sp[0-9a-f]{8}", str(token or "")):
        return None
    return f"{match.group('prefix')}_{token}_{match.group('tail')}"


# ---------------------------------------------------------------------------
# The metadata sheet
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class MetadataRow:
    """One upload as the collector's spreadsheet recorded it."""

    filename: str
    browser: str
    platform: str

    @property
    def token(self) -> str:
        return derive_speaker_token(self.browser, self.platform)

    @property
    def device(self) -> str:
        return device_fingerprint(self.browser, self.platform)


def _rows_from_records(records: Iterable[dict]) -> List[MetadataRow]:
    rows: List[MetadataRow] = []
    for record in records:
        lowered = {str(key).strip().lower(): value for key, value in record.items()}
        missing = [column for column in REQUIRED_COLUMNS if column not in lowered]
        if missing:
            raise ValueError(
                "the metadata sheet is missing the %s column(s); found: %s"
                % (", ".join(missing), ", ".join(sorted(lowered)) or "nothing")
            )
        filename = str(lowered["filename"] or "").strip()
        if not filename:
            continue
        rows.append(
            MetadataRow(
                filename=filename,
                browser=str(lowered["browser"] or "").strip(),
                platform=str(lowered["platform"] or "").strip(),
            )
        )
    return rows


def load_metadata_rows(
    source: object,
    *,
    worksheet: str = "samples",
) -> List[MetadataRow]:
    """Read the collector's metadata sheet from whichever form you have.

    ``source`` may be

    * a ``pandas.DataFrame`` (already loaded however you like);
    * a path to a CSV export of the sheet, or an ``http(s)`` CSV URL;
    * a Google Sheets id or URL - read through ``gspread``, which in Colab needs
      ``google.colab.auth.authenticate_user()`` first.

    Only ``filename``, ``browser`` and ``platform`` are read; extra columns are
    ignored, so a sheet that grows a column still loads.
    """
    if hasattr(source, "to_dict") and hasattr(source, "columns"):  # DataFrame
        return _rows_from_records(source.to_dict("records"))

    if isinstance(source, (list, tuple)):
        return _rows_from_records(source)

    text = str(source)
    if _looks_like_spreadsheet(text):
        return _rows_from_records(_read_google_sheet(text, worksheet))

    if text.startswith("http://") or text.startswith("https://"):
        import pandas as pd  # local: only this branch needs it

        return _rows_from_records(pd.read_csv(text).to_dict("records"))

    path = Path(text).expanduser()
    if not path.is_file():
        raise FileNotFoundError(f"metadata sheet not found: {path}")
    with open(path, "r", encoding="utf-8-sig", newline="") as handle:
        return _rows_from_records(list(csv.DictReader(handle)))


def _looks_like_spreadsheet(source: str) -> bool:
    """A Google Sheets URL, or a bare 44-character sheet id."""
    if "docs.google.com/spreadsheets" in source:
        return True
    return bool(re.fullmatch(r"[A-Za-z0-9_-]{30,60}", source)) and not Path(source).exists()


def _read_google_sheet(source: str, worksheet: str) -> List[dict]:
    try:
        import gspread
    except ImportError as error:  # pragma: no cover - depends on the runtime
        raise ImportError(
            "reading the sheet directly needs gspread (`pip install gspread`). "
            "Alternatively export the sheet to CSV and pass that path."
        ) from error

    client = _gspread_client(gspread)
    match = re.search(r"/spreadsheets/d/([A-Za-z0-9_-]+)", source)
    sheet_id = match.group(1) if match else source
    spreadsheet = client.open_by_key(sheet_id)
    try:
        sheet = spreadsheet.worksheet(worksheet)
    except Exception:  # pragma: no cover - depends on the sheet
        sheet = spreadsheet.sheet1
        LOGGER.warning("worksheet %r not found; using %r", worksheet, sheet.title)
    return sheet.get_all_records()


def _gspread_client(gspread):  # pragma: no cover - depends on the runtime
    """Colab's authenticated client when available, otherwise gspread's own."""
    try:
        from google.auth import default
        from google.colab import auth

        auth.authenticate_user()
        credentials, _ = default()
        return gspread.authorize(credentials)
    except ImportError:
        return gspread.service_account()


# ---------------------------------------------------------------------------
# Planning
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class PlannedRename:
    """One file, the name it will take, and the group that name puts it in."""

    path: Path
    current_name: str
    new_name: str
    token: str
    device: str

    @property
    def target(self) -> Path:
        return self.path.with_name(self.new_name)


@dataclass(frozen=True)
class SpeakerGroup:
    """A derived speaker and how much of the dataset it would swallow."""

    token: str
    device: str
    clips: int


@dataclass
class BackfillPlan:
    """What :func:`apply_backfill` would do, before anything is touched."""

    renames: List[PlannedRename] = field(default_factory=list)
    groups: List[SpeakerGroup] = field(default_factory=list)
    rows: int = 0
    already_tagged: int = 0
    no_metadata: int = 0
    unrecognised: int = 0
    missing_on_disk: int = 0
    duplicate_names: List[str] = field(default_factory=list)
    untracked_files: List[str] = field(default_factory=list)

    @property
    def largest_group_share(self) -> float:
        """Fraction of the renames the biggest derived group would take.

        The number that decides whether this is worth doing: near ``1.0`` means
        one bucket holds nearly everything and grouping by it buys almost no
        speaker independence.
        """
        planned = len(self.renames)
        if not planned or not self.groups:
            return 0.0
        return max(group.clips for group in self.groups) / planned

    def to_dataframe(self):
        """The planned renames as a DataFrame, for display in a notebook."""
        import pandas as pd

        return pd.DataFrame(
            [
                {
                    "class": rename.path.parent.name,
                    "current": rename.current_name,
                    "new": rename.new_name,
                    "speaker": rename.token,
                    "device": rename.device,
                }
                for rename in self.renames
            ]
        )

    def groups_dataframe(self):
        import pandas as pd

        return pd.DataFrame([asdict(group) for group in self.groups])

    def summary(self) -> str:
        lines = [
            "Speaker-token backfill plan",
            "=" * 60,
            f"sheet rows            : {self.rows}",
            f"already tagged        : {self.already_tagged}",
            f"to rename             : {len(self.renames)}",
            f"derived speakers      : {len(self.groups)}",
            f"no browser/platform   : {self.no_metadata}",
            f"unrecognised filename : {self.unrecognised}",
            f"not found on disk     : {self.missing_on_disk}",
        ]
        if self.duplicate_names:
            lines.append(
                f"ambiguous names       : {len(self.duplicate_names)} "
                "(same filename in two folders - skipped)"
            )
        if self.untracked_files:
            lines.append(
                f"files with no row     : {len(self.untracked_files)} "
                "(on disk but not in the sheet - left alone)"
            )
        if self.groups:
            lines.append("")
            lines.append("Largest derived speakers:")
            for group in self.groups[:15]:
                share = group.clips / max(1, len(self.renames))
                lines.append(f"  {group.token}  {group.clips:5d} clips  {share:5.1%}  {group.device}")
            share = self.largest_group_share
            lines.append("")
            if share >= 0.6:
                lines.append(
                    f"NOTE: the largest group is {share:.0%} of the renamed clips. Grouping by it "
                    "will barely change the split - browser and platform are too coarse to "
                    "separate these volunteers."
                )
            elif len(self.groups) < 5:
                lines.append(
                    f"NOTE: only {len(self.groups)} derived speakers. The split will be "
                    "speaker-safe but not speaker-diverse."
                )
            else:
                lines.append(
                    "These groups over-merge (two volunteers on the same browser and platform "
                    "become one speaker), which is the safe direction: leakage can only go down."
                )
        return "\n".join(lines)


def _index_files(roots: Sequence[Path]) -> Tuple[Dict[str, List[Path]], List[Path]]:
    """Every audio file under ``roots``, keyed by its untagged filename.

    Keying on the *untagged* form is what lets a sheet row find its file after a
    previous run renamed it, without anything ever writing back to the sheet.
    """
    index: Dict[str, List[Path]] = defaultdict(list)
    everything: List[Path] = []
    for root in roots:
        root = Path(root)
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.name.startswith("."):
                continue
            everything.append(path)
            index[strip_speaker_token(path.name)].append(path)
    return index, everything


def plan_backfill(
    rows: Sequence[MetadataRow],
    roots: Union[PathLike, Sequence[PathLike]],
) -> BackfillPlan:
    """Work out every rename without performing any of them.

    ``roots`` is the folder (or folders) the recordings live in - normally
    ``paths.dataset_path`` and ``paths.noise_path``, since the sheet covers both.
    """
    if isinstance(roots, (str, Path)):
        roots = [roots]
    index, everything = _index_files([Path(root) for root in roots])

    plan = BackfillPlan(rows=len(rows))
    grouped: Dict[str, int] = defaultdict(int)
    devices: Dict[str, str] = {}
    matched: set = set()

    for row in rows:
        key = strip_speaker_token(row.filename)
        candidates = index.get(key, [])
        if not candidates:
            plan.missing_on_disk += 1
            continue
        if len(candidates) > 1:
            plan.duplicate_names.append(row.filename)
            continue

        path = candidates[0]
        matched.add(path)
        if has_speaker_token(path.name):
            plan.already_tagged += 1
            continue

        token = row.token
        if not token:
            plan.no_metadata += 1
            continue

        new_name = filename_with_speaker_token(path.name, token)
        if new_name is None:
            plan.unrecognised += 1
            LOGGER.debug("unrecognised filename shape, left as is: %s", path.name)
            continue

        plan.renames.append(
            PlannedRename(
                path=path,
                current_name=path.name,
                new_name=new_name,
                token=token,
                device=row.device,
            )
        )
        grouped[token] += 1
        devices[token] = row.device

    plan.untracked_files = [str(path.name) for path in everything if path not in matched]
    plan.groups = sorted(
        (SpeakerGroup(token=token, device=devices[token], clips=clips) for token, clips in grouped.items()),
        key=lambda group: (-group.clips, group.token),
    )
    return plan


# ---------------------------------------------------------------------------
# Applying
# ---------------------------------------------------------------------------
@dataclass
class BackfillResult:
    """What actually happened on disk."""

    renamed: int = 0
    skipped_existing: int = 0
    failed: List[str] = field(default_factory=list)
    mapping: List[Dict[str, str]] = field(default_factory=list)
    dry_run: bool = True

    def summary(self) -> str:
        head = "Would rename" if self.dry_run else "Renamed"
        lines = [f"{head} {self.renamed} file(s)."]
        if self.skipped_existing:
            lines.append(f"{self.skipped_existing} skipped: a file of that name already exists.")
        if self.failed:
            lines.append(f"{len(self.failed)} failed:")
            lines.extend(f"  {message}" for message in self.failed[:10])
        return "\n".join(lines)

    def save(self, directory: PathLike, name: str = DEFAULT_REPORT_NAME) -> List[Path]:
        """Write the old-name -> new-name mapping next to the other reports.

        Nothing writes back to the collector's spreadsheet, so this file is the
        record of what the rename did.
        """
        directory = Path(directory)
        directory.mkdir(parents=True, exist_ok=True)
        csv_path = directory / f"{name}.csv"
        json_path = directory / f"{name}.json"
        with open(csv_path, "w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=["current", "new", "speaker", "device"])
            writer.writeheader()
            writer.writerows(self.mapping)
        with open(json_path, "w", encoding="utf-8") as handle:
            json.dump(
                {"dry_run": self.dry_run, "renamed": self.renamed, "failed": self.failed,
                 "mapping": self.mapping},
                handle,
                ensure_ascii=False,
                indent=2,
            )
        return [csv_path, json_path]


def apply_backfill(plan: BackfillPlan, dry_run: bool = True) -> BackfillResult:
    """Perform the plan's renames. ``dry_run=True`` (the default) touches nothing.

    Safe to re-run: a file that already carries a token was never planned, and a
    target that somehow exists is skipped rather than overwritten - losing a
    recording to a name collision would be far worse than leaving it ungrouped.
    """
    result = BackfillResult(dry_run=dry_run)
    for rename in plan.renames:
        target = rename.target
        if target.exists():
            result.skipped_existing += 1
            LOGGER.warning("not renaming %s: %s already exists", rename.current_name, target.name)
            continue
        if not dry_run:
            try:
                rename.path.rename(target)
            except OSError as error:
                result.failed.append(f"{rename.current_name}: {error}")
                continue
        result.renamed += 1
        result.mapping.append(
            {
                "current": rename.current_name,
                "new": rename.new_name,
                "speaker": rename.token,
                "device": rename.device,
            }
        )
    return result


def write_speakers_csv(plan: BackfillPlan, path: PathLike, rows: Sequence[MetadataRow] = ()) -> Path:
    """The same grouping as a ``speakers.csv``, renaming nothing.

    The resolver prefers explicit metadata over a filename pattern, so this is
    the non-destructive way to get the identical split: every planned rename
    becomes a ``file,speaker`` line under the name the file has *now*. Pass
    ``rows`` as well to carry over files that already hold a real token, so one
    file covers the whole dataset.

    The trade-off against renaming: a token in the filename travels with the file
    (copy the dataset, re-download it, and the grouping survives), while this CSV
    goes stale the moment recordings are added.
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    entries: Dict[str, str] = {}
    for row in rows:
        match = SPEAKER_TOKEN.search(row.filename)
        if match:
            entries[row.filename] = match.group(1)
    for rename in plan.renames:
        entries[rename.current_name] = rename.token
    with open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["file", "speaker"])
        for filename, speaker in sorted(entries.items()):
            writer.writerow([filename, speaker])
    LOGGER.info("wrote %d speaker rows to %s", len(entries), path)
    return path

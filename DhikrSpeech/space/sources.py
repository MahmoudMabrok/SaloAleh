"""Fetching exported models from a shared folder instead of the Space repo.

Exports are produced on Google Drive and are not in git, so the Space would
otherwise start empty every time. This module pulls them from wherever they
already live:

===========================  ==================================================
source                       example
===========================  ==================================================
Google Drive folder          https://drive.google.com/drive/folders/<id>
Google Drive file            https://drive.google.com/file/d/<id>/view
Hugging Face repo            hf://user/dhikrspeech-models  (or just user/repo)
direct file URL              https://example.com/dhikr_int8.tflite
local path                   /mnt/exports
===========================  ==================================================

A folder is worth more than a single file here: ``labels.txt`` and
``model_meta.json`` have to travel with the ``.tflite``, and fetching the folder
is what keeps them together. A lone ``.tflite`` loads with positional class
names and the front-end guessed from ``config.yaml``.
"""

from __future__ import annotations

import logging
import os
import re
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional
from urllib.parse import parse_qs, urlparse

LOGGER = logging.getLogger(__name__)

__all__ = [
    "FetchResult",
    "SourceError",
    "configured_source",
    "fetch",
    "remote_cache_dir",
]

# Files worth pulling out of a shared folder. Anything else (checkpoints, logs,
# the raw dataset) would only bloat an ephemeral Space disk.
WANTED_SUFFIXES = (".tflite", ".txt", ".json", ".keras", ".h5", ".pb", ".index", ".data-00000-of-00001")

# Hosts a *visitor* is allowed to make the Space fetch from. The Space owner is
# not restricted - DHIKR_MODEL_SOURCE and model_source.txt are theirs to set -
# but the paste-a-link field is reachable by anyone who can open a public Space,
# and an unrestricted fetcher there turns it into an open proxy.
ALLOWED_HOSTS = frozenset(
    {
        "drive.google.com",
        "docs.google.com",
        "huggingface.co",
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
    }
)

MAX_DOWNLOAD_BYTES = int(os.environ.get("DHIKR_MAX_DOWNLOAD_BYTES", str(512 * 1024 * 1024)))

_DRIVE_FOLDER = re.compile(r"drive\.google\.com/drive/folders/([\w-]+)")
_DRIVE_FILE = re.compile(r"drive\.google\.com/file/d/([\w-]+)")
_HF_REPO = re.compile(r"^[\w.-]+/[\w.-]+$")


class SourceError(RuntimeError):
    """A shared source could not be fetched, with a message worth showing."""


@dataclass
class FetchResult:
    directory: Path
    kind: str
    source: str
    files: List[str] = field(default_factory=list)

    def summary(self) -> str:
        if not self.files:
            return f"Nothing usable found at {self.source}"
        return f"Fetched {len(self.files)} file(s) from {self.kind}: " + ", ".join(
            sorted(self.files)[:8]
        ) + ("…" if len(self.files) > 8 else "")


def remote_cache_dir() -> Path:
    """Where fetched models live. Ephemeral - a Space restart clears it."""
    return Path(os.environ.get("DHIKR_REMOTE_DIR", Path(tempfile.gettempdir()) / "dhikrspeech-remote"))


def configured_source(space_dir: Optional[Path] = None) -> Optional[str]:
    """The shared folder to pull on startup, if one is configured.

    ``DHIKR_MODEL_SOURCE`` wins so a Space can be repointed from its settings
    without a commit; ``model_source.txt`` is the committed default, which is
    what makes a freshly deployed Space come up with a model already loaded.
    """
    from_env = (os.environ.get("DHIKR_MODEL_SOURCE") or "").strip()
    if from_env:
        return from_env
    default_file = (space_dir or Path(__file__).resolve().parent) / "model_source.txt"
    if default_file.exists():
        for line in default_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                return line
    return None


# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
def _check_host(source: str) -> None:
    host = (urlparse(source).hostname or "").lower()
    if host not in ALLOWED_HOSTS:
        raise SourceError(
            f"'{host or source}' is not an allowed source. This field accepts "
            + ", ".join(sorted(ALLOWED_HOSTS))
            + ". The Space owner can point anywhere via DHIKR_MODEL_SOURCE."
        )


def fetch(
    source: str, destination: Optional[Path] = None, restrict_hosts: bool = False
) -> FetchResult:
    """Fetch ``source`` into ``destination`` and report what landed."""
    source = (source or "").strip()
    if not source:
        raise SourceError("No source given.")

    local = Path(source).expanduser()
    if local.exists():
        # Used in place - copying a local export would only duplicate it.
        return FetchResult(
            directory=local if local.is_dir() else local.parent,
            kind="local path",
            source=str(local),
            files=[item.name for item in _usable_files(local if local.is_dir() else local.parent)],
        )

    if restrict_hosts and "://" in source:
        _check_host(source)

    target = Path(destination or remote_cache_dir())
    target.mkdir(parents=True, exist_ok=True)

    folder_match = _DRIVE_FOLDER.search(source)
    if folder_match:
        return _fetch_drive_folder(source, target)

    file_match = _DRIVE_FILE.search(source)
    drive_id = file_match.group(1) if file_match else _drive_id_param(source)
    if drive_id:
        return _fetch_drive_file(drive_id, source, target)

    if source.startswith("hf://") or _HF_REPO.match(source):
        return _fetch_hf(source, target)

    # A huggingface.co link is a whole repo unless it points at one file -
    # "copy download link" on the Hub gives a /resolve/ URL, and treating that
    # as a repo id would look up a repo that does not exist.
    if "huggingface.co/" in source and not _is_hf_file_url(source):
        return _fetch_hf(source, target)

    if source.startswith(("http://", "https://")):
        return _fetch_url(source, target)

    raise SourceError(
        f"Could not tell what '{source}' is. Give a Google Drive folder link, a "
        "Hugging Face repo id, a direct file URL, or a local path."
    )


def _is_hf_file_url(source: str) -> bool:
    """Whether a huggingface.co URL points at one file rather than a repo.

    ``/resolve/`` is what the Hub's "copy download link" produces and ``/blob/``
    what the file browser shows; both name a single file. Anything else on the
    host is a repo to snapshot.
    """
    if "/resolve/" in source or "/blob/" in source:
        return True
    return Path(urlparse(source).path).suffix.lower() in WANTED_SUFFIXES


def _drive_id_param(source: str) -> Optional[str]:
    if "drive.google.com" not in source and "docs.google.com" not in source:
        return None
    values = parse_qs(urlparse(source).query).get("id")
    return values[0] if values else None


def _usable_files(folder: Path) -> List[Path]:
    return [
        item
        for item in folder.rglob("*")
        if item.is_file() and item.suffix.lower() in WANTED_SUFFIXES
    ]


# ---------------------------------------------------------------------------
# Google Drive
# ---------------------------------------------------------------------------
def _import_gdown():
    try:
        import gdown  # type: ignore

        return gdown
    except ImportError as exc:  # pragma: no cover - environment problem
        raise SourceError(
            "Google Drive support needs the 'gdown' package; add it to "
            "requirements.txt."
        ) from exc


def _wanted_from_drive(relative_path: str) -> bool:
    """Whether a file in a shared folder is worth pulling into the Space.

    A training folder holds far more than an export. Fetching only what the app
    can actually use keeps startup quick and - because Drive throttles per file -
    materially reduces the chance of tripping its rate limit. SavedModel content
    is skipped unless asked for: the Space runs LiteRT, so it could not load one
    anyway without TensorFlow added to requirements.

    The export root also keeps a ``history/`` subfolder of dated snapshots of
    every published model (see ``archive_export`` in src/export.py). Pointing a
    Space at the root should load the latest model, not every archived one, so
    ``history/`` is skipped wholesale here; to load an old model, point the Space
    straight at its ``history/<name>/`` subfolder instead.
    """
    parts = Path(relative_path).parts
    if any(part == "history" for part in parts[:-1]):
        return False
    if any(part == "saved_model" or part.endswith(".savedmodel") for part in parts[:-1]):
        if os.environ.get("DHIKR_FETCH_SAVEDMODEL", "").strip().lower() not in ("1", "true", "yes"):
            return False
    return Path(relative_path).suffix.lower() in WANTED_SUFFIXES


def _fetch_drive_folder(source: str, target: Path) -> FetchResult:
    gdown = _import_gdown()
    LOGGER.info("fetching Drive folder %s", source)

    options = dict(url=source, output=str(target), quiet=True, use_cookies=False)
    # gdown 5 caps a folder at 50 files unless remaining_ok is set; gdown 6
    # removed both the cap and the argument, and rejects it. Ask the installed
    # version which it is rather than pinning one.
    import inspect

    if "remaining_ok" in inspect.signature(gdown.download_folder).parameters:
        options["remaining_ok"] = True

    # List first, then fetch file by file. gdown's own folder download aborts the
    # whole folder on the first file Drive refuses, and Drive refuses files
    # routinely once a link has seen traffic - so an all-or-nothing fetch means
    # one throttled file costs the entire export. Listing is a single metadata
    # call and keeps working when downloads are being throttled.
    try:
        listing = gdown.download_folder(skip_download=True, **options)
    except Exception as exc:  # noqa: BLE001
        raise SourceError(_drive_hint(exc)) from exc
    if not listing:
        raise SourceError(
            f"Nothing was listed at {source}. Check the folder is shared as "
            "'Anyone with the link' and is not empty."
        )

    wanted = [item for item in listing if _wanted_from_drive(item.path)]
    if not wanted:
        raise SourceError(
            f"That Drive folder holds {len(listing)} file(s), none of them an export. "
            "It needs dhikr_*.tflite plus labels.txt and model_metadata.json."
        )

    downloaded: List[str] = []
    failed: List[str] = []
    for item in wanted:
        destination = Path(item.local_path)
        if destination.exists() and destination.stat().st_size > 0:
            downloaded.append(destination.name)
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        for attempt in range(2):
            try:
                gdown.download(id=item.id, output=str(destination), quiet=True, use_cookies=False)
                downloaded.append(destination.name)
                break
            except Exception as exc:  # noqa: BLE001 - one file must not sink the rest
                if attempt:
                    LOGGER.warning("Drive refused %s: %s", item.path, exc)
                    failed.append(Path(item.path).name)
                    destination.unlink(missing_ok=True)

    if not any(name.endswith(".tflite") for name in downloaded):
        raise SourceError(
            "Drive served none of the model files"
            + (f" (refused: {', '.join(sorted(set(failed))[:6])})" if failed else "")
            + ". This is usually rate limiting rather than a permissions problem - "
            "it clears on its own. A Hugging Face model repo is the more reliable "
            "home for a public Space."
        )

    result = FetchResult(
        directory=target, kind="Google Drive folder", source=source, files=downloaded
    )
    if failed:
        # Partial is still useful: the model runs, just with degraded labelling.
        result.files.append(f"[{len(failed)} refused: {', '.join(sorted(set(failed))[:4])}]")
    return result


def _fetch_drive_file(file_id: str, source: str, target: Path) -> FetchResult:
    gdown = _import_gdown()
    try:
        written = gdown.download(id=file_id, output=str(target) + "/", quiet=True, use_cookies=False)
    except Exception as exc:  # noqa: BLE001
        raise SourceError(_drive_hint(exc)) from exc
    if not written:
        raise SourceError(_drive_hint(None))
    return FetchResult(
        directory=target, kind="Google Drive file", source=source,
        files=[Path(written).name],
    )


def _drive_hint(exc: Optional[Exception]) -> str:
    detail = f" ({type(exc).__name__}: {str(exc).strip().splitlines()[0][:160]})" if exc else ""
    return (
        "Google Drive refused the download" + detail + ". Check that the folder is "
        "shared as 'Anyone with the link'; Drive also rate-limits heavily downloaded "
        "files. A Hugging Face model repo is the more reliable home for a Space."
    )


# ---------------------------------------------------------------------------
# Hugging Face Hub
# ---------------------------------------------------------------------------
def _fetch_hf(source: str, target: Path) -> FetchResult:
    try:
        from huggingface_hub import snapshot_download  # type: ignore
    except ImportError as exc:  # pragma: no cover
        raise SourceError("Hugging Face support needs the 'huggingface_hub' package.") from exc

    repo_type = "model"
    repo_id = source
    for prefix in ("hf://", "https://huggingface.co/", "http://huggingface.co/"):
        if repo_id.startswith(prefix):
            repo_id = repo_id[len(prefix):]
    for kind in ("datasets", "spaces"):
        if repo_id.startswith(f"{kind}/"):
            repo_type = kind.rstrip("s")
            repo_id = repo_id[len(kind) + 1:]
    repo_id = repo_id.split("/tree/")[0].strip("/")

    LOGGER.info("fetching Hugging Face %s %s", repo_type, repo_id)
    try:
        downloaded = snapshot_download(
            repo_id=repo_id,
            repo_type=repo_type,
            local_dir=str(target / repo_id.replace("/", "__")),
            allow_patterns=[f"*{suffix}" for suffix in WANTED_SUFFIXES],
            token=os.environ.get("HF_TOKEN") or None,
        )
    except Exception as exc:  # noqa: BLE001
        raise SourceError(
            f"Could not fetch '{repo_id}' from the Hugging Face Hub "
            f"({type(exc).__name__}: {str(exc).strip().splitlines()[0][:160]}). "
            "A private repo needs an HF_TOKEN secret on the Space."
        ) from exc

    files = _usable_files(Path(downloaded))
    if not files:
        raise SourceError(f"'{repo_id}' holds no model files.")
    return FetchResult(
        directory=Path(downloaded), kind=f"Hugging Face {repo_type}", source=repo_id,
        files=[item.name for item in files],
    )


# ---------------------------------------------------------------------------
# Plain URL
# ---------------------------------------------------------------------------
def _fetch_url(source: str, target: Path) -> FetchResult:
    import urllib.request

    # A /blob/ link renders the Hub's file viewer; /resolve/ serves the bytes.
    if "huggingface.co/" in source and "/blob/" in source:
        source = source.replace("/blob/", "/resolve/", 1)

    name = Path(urlparse(source).path).name or "download.tflite"
    if Path(name).suffix.lower() not in WANTED_SUFFIXES:
        raise SourceError(
            f"'{name}' is not a model or sidecar file "
            f"({', '.join(WANTED_SUFFIXES)} only)."
        )
    destination = target / name
    LOGGER.info("downloading %s", source)
    try:
        request = urllib.request.Request(source, headers={"User-Agent": "DhikrSpeech-Space"})
        with urllib.request.urlopen(request, timeout=60) as response:  # noqa: S310 - host-checked above
            declared = int(response.headers.get("Content-Length") or 0)
            if declared > MAX_DOWNLOAD_BYTES:
                raise SourceError(
                    f"{name} is {declared / 1e6:.0f} MB, over the "
                    f"{MAX_DOWNLOAD_BYTES / 1e6:.0f} MB limit."
                )
            written = 0
            with open(destination, "wb") as handle:
                while chunk := response.read(1 << 20):
                    written += len(chunk)
                    if written > MAX_DOWNLOAD_BYTES:
                        handle.close()
                        destination.unlink(missing_ok=True)
                        raise SourceError(
                            f"{name} exceeded the "
                            f"{MAX_DOWNLOAD_BYTES / 1e6:.0f} MB download limit."
                        )
                    handle.write(chunk)
    except SourceError:
        raise
    except Exception as exc:  # noqa: BLE001
        destination.unlink(missing_ok=True)
        raise SourceError(
            f"Could not download {source} "
            f"({type(exc).__name__}: {str(exc).strip().splitlines()[0][:160]})"
        ) from exc
    return FetchResult(directory=target, kind="direct URL", source=source, files=[name])


def clear_cache() -> None:
    """Drop everything fetched so far, so a re-fetch starts clean."""
    cache = remote_cache_dir()
    if cache.exists():
        shutil.rmtree(cache, ignore_errors=True)

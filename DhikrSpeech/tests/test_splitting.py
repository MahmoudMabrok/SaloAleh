"""Tests for speaker resolution, speaker-safe splitting and leakage detection.

The failure this guards against is silent: a leaked speaker does not crash
anything, it just inflates every number the run reports. So the tests here assert
the *absence* of overlap, and that the pipeline refuses to continue when the
overlap exists.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import SplitConfig
from src.dataset import ManifestRecord
from src.splitting import (
    SpeakerLeakageError,
    assign_speaker_splits,
    attach_speakers,
    load_speaker_metadata,
    resolve_speaker,
    resolve_speakers,
    speaker_stats,
    verify_speaker_isolation,
)

SPEAKER_REGEX = r"^(?P<speaker>spk\d+)"


def record(
    speaker: str,
    index: int,
    positive: bool = True,
    split: str = "",
    negative_type=None,
) -> ManifestRecord:
    label = "target" if positive else "unknown"
    return ManifestRecord(
        path=f"{label}/{speaker}_{index:03d}.wav",
        source_path=f"/raw/{speaker}_{index:03d}.wav",
        label=label,
        class_index=1 if positive else 0,
        phrase_id=7 if positive else None,
        text="",
        split=split,
        duration=2.0,
        negative_type=negative_type,
        speaker=speaker,
    )


def corpus(speakers: int = 10, per_speaker: int = 8):
    records = []
    for speaker_index in range(speakers):
        speaker = f"spk{speaker_index:02d}"
        for clip in range(per_speaker):
            records.append(record(speaker, clip))
            records.append(record(speaker, clip, positive=False, negative_type="other_dhikr"))
    return records


# ---------------------------------------------------------------------------
# Resolving ids
# ---------------------------------------------------------------------------
def test_regex_named_group_wins() -> None:
    pattern = re.compile(SPEAKER_REGEX)
    assert resolve_speaker("dataset/007/spk03_007_012.wav", pattern) == "spk03"


def test_regex_falls_back_to_group_one_then_whole_match() -> None:
    assert resolve_speaker("spk03_1.wav", re.compile(r"^([a-z]+\d+)_")) == "spk03"
    assert resolve_speaker("spk03_1.wav", re.compile(r"^[a-z]+\d+")) == "spk03"


def test_unmatched_filename_resolves_to_none() -> None:
    assert resolve_speaker("recording-1.wav", re.compile(SPEAKER_REGEX)) is None


def test_metadata_overrides_the_regex(tmp_path: Path) -> None:
    metadata = tmp_path / "speakers.json"
    metadata.write_text(json.dumps({"spk03_1.wav": "volunteer-A"}), encoding="utf-8")
    config = SplitConfig(speaker_regex=SPEAKER_REGEX, speaker_metadata=str(metadata))
    resolved = resolve_speakers(["/data/007/spk03_1.wav", "/data/007/spk04_1.wav"], config)
    assert resolved["/data/007/spk03_1.wav"] == "volunteer-A"
    assert resolved["/data/007/spk04_1.wav"] == "spk04"


def test_metadata_matches_a_path_suffix(tmp_path: Path) -> None:
    metadata = tmp_path / "speakers.json"
    metadata.write_text(json.dumps({"007/a.wav": "vol-1"}), encoding="utf-8")
    assert load_speaker_metadata(metadata) == {"007/a.wav": "vol-1"}
    config = SplitConfig(speaker_metadata=str(metadata))
    assert resolve_speakers(["/dataset/007/a.wav"], config)["/dataset/007/a.wav"] == "vol-1"


def test_metadata_accepts_a_list_of_entries(tmp_path: Path) -> None:
    metadata = tmp_path / "speakers.json"
    metadata.write_text(
        json.dumps([{"file": "a.wav", "speaker": "vol-1"}]), encoding="utf-8"
    )
    assert load_speaker_metadata(metadata) == {"a.wav": "vol-1"}


def test_missing_speakers_warn_loudly(caplog) -> None:
    resolve_speakers(["/data/a.wav", "/data/b.wav"], SplitConfig(speaker_regex=SPEAKER_REGEX))
    assert "NOT SPEAKER-INDEPENDENT" in caplog.text


def test_attach_speakers_fills_the_manifest() -> None:
    plain = [
        ManifestRecord("007/a.wav", "/raw/spk1_a.wav", "target", 1, 7, "", "", 2.0),
    ]
    updated = attach_speakers(plain, {"/raw/spk1_a.wav": "spk1"})
    assert updated[0].speaker == "spk1"


# ---------------------------------------------------------------------------
# Splitting
# ---------------------------------------------------------------------------
def test_no_speaker_crosses_a_split() -> None:
    split = assign_speaker_splits(corpus(), SplitConfig(), seed=1337)
    report = verify_speaker_isolation(split)
    assert report.train_val == []
    assert report.train_test == []
    assert report.val_test == []
    assert report.is_clean


def test_isolation_is_global_across_positives_and_negatives() -> None:
    """A voice must not enter training through the negative pool and be
    evaluated on through the positive one (requirement 8)."""
    split = assign_speaker_splits(corpus(), SplitConfig(), seed=5)
    placement = {}
    for item in split:
        placement.setdefault(item.speaker, set()).add(item.split)
    assert all(len(splits) == 1 for splits in placement.values())


def test_every_split_is_populated() -> None:
    split = assign_speaker_splits(corpus(), SplitConfig(), seed=11)
    stats = speaker_stats(split)
    for name in ("train", "val", "test"):
        assert stats.clips_per_split.get(name, 0) > 0
        assert stats.positives_per_split.get(name, 0) > 0


def test_ratios_are_approximately_honoured() -> None:
    split = assign_speaker_splits(corpus(speakers=20, per_speaker=10), SplitConfig(), seed=3)
    stats = speaker_stats(split)
    total = sum(stats.positives_per_split.values())
    assert 0.05 <= stats.positives_per_split["val"] / total <= 0.30
    assert 0.02 <= stats.positives_per_split["test"] / total <= 0.25


def test_splitting_is_deterministic() -> None:
    records = corpus()
    first = assign_speaker_splits(records, SplitConfig(), seed=42)
    again = assign_speaker_splits(records, SplitConfig(), seed=42)
    assert [r.split for r in first] == [r.split for r in again]


def test_a_tiny_dataset_still_fills_val_and_test() -> None:
    split = assign_speaker_splits(corpus(speakers=4, per_speaker=3), SplitConfig(), seed=9)
    stats = speaker_stats(split)
    assert stats.positives_per_split.get("val", 0) > 0
    assert stats.positives_per_split.get("test", 0) > 0


def test_records_without_speakers_are_split_individually(caplog) -> None:
    records = [
        ManifestRecord(f"007/{i}.wav", f"/raw/{i}.wav", "target", 1, 7, "", "", 2.0)
        for i in range(20)
    ]
    split = assign_speaker_splits(records, SplitConfig(), seed=1)
    assert {r.split for r in split} == {"train", "val", "test"}
    assert "NOT SPEAKER-INDEPENDENT" in caplog.text


def test_require_speaker_ids_fails_when_they_are_missing() -> None:
    records = [
        ManifestRecord(f"007/{i}.wav", f"/raw/{i}.wav", "target", 1, 7, "", "", 2.0)
        for i in range(10)
    ]
    with pytest.raises(SpeakerLeakageError, match="no speaker id"):
        assign_speaker_splits(records, SplitConfig(require_speaker_ids=True), seed=1)


# ---------------------------------------------------------------------------
# Leakage detection
# ---------------------------------------------------------------------------
def test_leakage_is_detected_and_named() -> None:
    leaked = [
        record("spk01", 1, split="train"),
        record("spk01", 2, split="val"),
        record("spk02", 1, split="train"),
        record("spk03", 1, split="test"),
        record("spk02", 2, split="test"),
    ]
    report = verify_speaker_isolation(leaked)
    assert report.train_val == ["spk01"]
    assert report.train_test == ["spk02"]
    assert not report.is_clean
    assert "FAIL" in report.summary()


def test_clean_split_reports_pass() -> None:
    clean = [record("spk01", 1, split="train"), record("spk02", 1, split="val")]
    report = verify_speaker_isolation(clean)
    assert report.is_clean
    assert "PASS" in report.summary()


def test_unresolved_recordings_are_reported_not_hidden() -> None:
    mixed = [
        record("spk01", 1, split="train"),
        ManifestRecord("007/x.wav", "/raw/x.wav", "target", 1, 7, "", "val", 2.0),
    ]
    report = verify_speaker_isolation(mixed)
    assert report.unresolved == 1
    assert "NOT SPEAKER-INDEPENDENT" in report.summary()


def test_stats_count_shared_speakers() -> None:
    records = [
        record("spk01", 1, split="train"),
        record("spk01", 2, positive=False, split="train", negative_type="other_dhikr"),
        record("spk02", 1, positive=False, split="train", negative_type="noise"),
    ]
    stats = speaker_stats(records)
    assert stats.positive_speakers == 1
    assert stats.negative_speakers == 2
    assert stats.shared_speakers == 1
    assert stats.all_resolved

"""Tests for reusing validation results in the dataset issues inspector."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.dataset import (  # noqa: E402
    DatasetIssue,
    DatasetStatistics,
    FileStats,
    ValidationReport,
)


def validation_report() -> ValidationReport:
    stats = DatasetStatistics(3, 1, 4.5, 1.5, 1.0, 2.0, 1.5, {"001": 3})
    files = [
        FileStats("/data/a.wav", "001", True, 1.0, 48_000, 2, "PCM_16", -20.0, "same"),
        FileStats("/data/b.wav", "001", True, 1.0, 16_000, 1, "PCM_16", -20.0, "same"),
        FileStats("/data/broken.wav", "001", False, 0.0, 0, 0, ""),
    ]
    issues = [
        DatasetIssue("/data/a.wav", "001", "stereo", "2 channels"),
        DatasetIssue("/data/a.wav", "001", "sample_rate", "48000 Hz"),
        DatasetIssue("/data/b.wav", "001", "duplicate", "same audio as /data/a.wav"),
        DatasetIssue("/data/broken.wav", "001", "corrupted", "bad header"),
    ]
    return ValidationReport(stats, files, issues, deep=True)


def test_issue_records_join_existing_metadata_without_reopening_files() -> None:
    records = validation_report().issue_records(["stereo", "corrupted"])

    assert [record["issue"] for record in records] == ["stereo", "corrupted"]
    assert records[0]["file"] == "/data/a.wav"
    assert records[0]["sample_rate"] == 48_000
    assert records[0]["channels"] == 2
    assert records[0]["duration"] == 1.0
    assert records[0]["readable"] is True
    assert records[1]["sample_rate"] is None
    assert records[1]["readable"] is False


def test_duplicate_groups_include_all_copies_for_side_by_side_review() -> None:
    groups = validation_report().duplicate_groups()

    assert len(groups) == 1
    assert [record["file"] for record in groups[0]] == ["/data/a.wav", "/data/b.wav"]
    assert {record["duplicate_group"] for record in groups[0]} == {"same"}


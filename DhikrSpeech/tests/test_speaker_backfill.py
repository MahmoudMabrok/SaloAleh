"""Tests for backfilling speaker tokens onto pre-token recordings.

This is a bulk rename over someone's only copy of a dataset, driven by a
spreadsheet nobody re-reads afterwards, so the two things that must be pinned
down are that it renames exactly the files it should and that running it twice
changes nothing the second time. Both are checked here against a real temporary
tree - no Drive, no network, no TensorFlow.

The derivation is also checked against a fixed vector, because a token that
shifts between versions would silently split one speaker into two groups.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.speaker_backfill import (
    MetadataRow,
    apply_backfill,
    derive_speaker_token,
    device_fingerprint,
    filename_with_speaker_token,
    has_speaker_token,
    load_metadata_rows,
    plan_backfill,
    strip_speaker_token,
    write_speakers_csv,
)


def row(filename: str, browser: str = "Chrome 120", platform: str = "Android") -> MetadataRow:
    return MetadataRow(filename=filename, browser=browser, platform=platform)


@pytest.fixture()
def dataset(tmp_path: Path) -> Path:
    root = tmp_path / "dataset"
    for name in [
        "006/006_20260101_000000_aaaaaa.webm",
        "006/006_20260102_000000_bbbbbb.webm",
        "007/007_20260103_000000_cccccc.webm",
        "006/006_sp11112222_20260104_000000_dddddd.webm",
        "unknown/unknown_20260105_000000_eeeeee.webm",
    ]:
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"audio")
    return root


# ---------------------------------------------------------------------------
# Token derivation
# ---------------------------------------------------------------------------
def test_token_is_the_shape_the_pipeline_already_matches():
    # `split.speaker.filename_patterns` starts with sp[0-9a-f]{8}. A derived
    # token that did not fit it would need a config change, and a half-matching
    # dataset is worse than none.
    token = derive_speaker_token("Chrome 120", "Android")
    assert len(token) == 10
    assert token.startswith("sp")
    assert all(character in "0123456789abcdef" for character in token[2:])


def test_token_is_the_documented_hash_and_does_not_drift():
    # Pinned by construction: a change to the derivation would regroup an
    # already-renamed dataset, splitting one speaker across two ids.
    expected = "sp" + hashlib.sha256("chrome 120|android".encode("utf-8")).hexdigest()[:8]
    assert derive_speaker_token("Chrome 120", "Android") == expected


def test_case_and_whitespace_do_not_split_one_device_in_two():
    assert derive_speaker_token("Chrome 120", "Android") == derive_speaker_token("  chrome   120 ", "ANDROID")


def test_different_devices_get_different_tokens():
    assert derive_speaker_token("Chrome 120", "Android") != derive_speaker_token("Safari 17", "iPhone")


def test_fields_are_joined_with_a_separator():
    # Without one, ("ab", "c") and ("a", "bc") would be the same fingerprint and
    # two unrelated devices would silently merge.
    assert derive_speaker_token("ab", "c") != derive_speaker_token("a", "bc")
    assert device_fingerprint("Chrome", "Android") == "chrome|android"


def test_a_row_with_no_signal_derives_no_token():
    # Grouping every unknown device together would be one enormous speaker; the
    # per-file fallback the pipeline already has is the honest answer.
    assert derive_speaker_token("", "") == ""
    assert derive_speaker_token(None, None) == ""
    assert device_fingerprint("", "") == ""
    # One known field is still a usable group.
    assert derive_speaker_token("", "Android").startswith("sp")


# ---------------------------------------------------------------------------
# Filenames
# ---------------------------------------------------------------------------
def test_the_token_goes_between_the_class_prefix_and_the_timestamp():
    assert (
        filename_with_speaker_token("006_20260101_000000_abcdef.webm", "sp3f9a2c41")
        == "006_sp3f9a2c41_20260101_000000_abcdef.webm"
    )
    assert (
        filename_with_speaker_token("unknown_20260101_000000_abcdef.webm", "sp3f9a2c41")
        == "unknown_sp3f9a2c41_20260101_000000_abcdef.webm"
    )


@pytest.mark.parametrize(
    "filename, token",
    [
        ("006_sp3f9a2c41_20260101_000000_abcdef.webm", "spdeadbeef"),  # already tagged
        ("holiday-photo.jpg", "sp3f9a2c41"),                          # not a recording
        ("006_2026_abcdef.webm", "sp3f9a2c41"),                       # not the collector shape
        ("006_20260101_000000_abcdef.webm", "nope"),                  # malformed token
        ("", "sp3f9a2c41"),
    ],
)
def test_a_name_that_is_not_the_collector_shape_is_never_rewritten(filename, token):
    # A mangled class prefix relabels the recording, which is worse than leaving
    # one file ungrouped.
    assert filename_with_speaker_token(filename, token) is None


def test_the_hex_suffix_is_not_mistaken_for_a_speaker_token():
    # The 6-hex suffix is hex too. A loose check here would report every file as
    # already tagged and the backfill would quietly do nothing.
    assert has_speaker_token("006_sp3f9a2c41_20260101_000000_abcdef.webm")
    assert not has_speaker_token("006_20260101_000000_5ba1e5.webm")


def test_stripping_a_token_recovers_the_name_the_sheet_recorded():
    # This is what lets a row find its file after a rename, with nothing ever
    # written back to the spreadsheet.
    assert (
        strip_speaker_token("006_sp3f9a2c41_20260101_000000_abcdef.webm")
        == "006_20260101_000000_abcdef.webm"
    )
    assert strip_speaker_token("006_20260101_000000_abcdef.webm") == "006_20260101_000000_abcdef.webm"


# ---------------------------------------------------------------------------
# Planning
# ---------------------------------------------------------------------------
def test_the_plan_counts_every_outcome(dataset: Path):
    plan = plan_backfill(
        [
            row("006_20260101_000000_aaaaaa.webm"),
            row("006_20260102_000000_bbbbbb.webm"),
            row("007_20260103_000000_cccccc.webm", browser="Safari 17", platform="iPhone"),
            row("006_sp11112222_20260104_000000_dddddd.webm"),
            row("unknown_20260105_000000_eeeeee.webm", browser="", platform=""),
            row("006_20260106_000000_deleted.webm"),
        ],
        dataset,
    )

    assert len(plan.renames) == 3
    assert plan.already_tagged == 1
    assert plan.no_metadata == 1
    assert plan.missing_on_disk == 1
    assert {group.clips for group in plan.groups} == {2, 1}
    # Two Chrome/Android rows are one speaker; the iPhone row is another.
    assert len(plan.groups) == 2
    assert plan.groups[0].clips == 2  # largest first
    assert plan.largest_group_share == pytest.approx(2 / 3)


def test_the_plan_touches_nothing(dataset: Path):
    before = sorted(path.name for path in dataset.rglob("*") if path.is_file())
    plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    assert sorted(path.name for path in dataset.rglob("*") if path.is_file()) == before


def test_a_file_with_no_sheet_row_is_reported_and_left_alone(dataset: Path):
    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    assert "007_20260103_000000_cccccc.webm" in plan.untracked_files
    assert all(rename.current_name == "006_20260101_000000_aaaaaa.webm" for rename in plan.renames)


def test_the_same_filename_in_two_folders_is_skipped_not_guessed(tmp_path: Path):
    root = tmp_path / "dataset"
    for folder in ("006", "007"):
        path = root / folder / "006_20260101_000000_aaaaaa.webm"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"audio")

    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], root)
    assert plan.renames == []
    assert plan.duplicate_names == ["006_20260101_000000_aaaaaa.webm"]


def test_several_roots_are_searched(tmp_path: Path):
    # The sheet covers noise uploads too, and noise lives beside dataset/.
    dataset_root = tmp_path / "dataset"
    noise_root = tmp_path / "noise"
    (dataset_root / "006").mkdir(parents=True)
    noise_root.mkdir()
    (dataset_root / "006" / "006_20260101_000000_aaaaaa.webm").write_bytes(b"audio")
    (noise_root / "noise_20260101_000000_bbbbbb.webm").write_bytes(b"audio")

    plan = plan_backfill(
        [row("006_20260101_000000_aaaaaa.webm"), row("noise_20260101_000000_bbbbbb.webm")],
        [dataset_root, noise_root],
    )
    assert len(plan.renames) == 2


# ---------------------------------------------------------------------------
# Applying
# ---------------------------------------------------------------------------
def test_a_dry_run_renames_nothing(dataset: Path):
    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    result = apply_backfill(plan, dry_run=True)

    assert result.renamed == 1
    assert result.dry_run is True
    assert (dataset / "006" / "006_20260101_000000_aaaaaa.webm").is_file()


def test_applying_renames_the_files_on_disk(dataset: Path):
    rows = [
        row("006_20260101_000000_aaaaaa.webm"),
        row("006_20260102_000000_bbbbbb.webm"),
        row("007_20260103_000000_cccccc.webm", browser="Safari 17", platform="iPhone"),
    ]
    result = apply_backfill(plan_backfill(rows, dataset), dry_run=False)
    assert result.renamed == 3

    names = sorted(path.name for path in dataset.rglob("*.webm"))
    assert all(has_speaker_token(name) for name in names if "eeeeee" not in name)
    token = derive_speaker_token("Chrome 120", "Android")
    assert (dataset / "006" / f"006_{token}_20260101_000000_aaaaaa.webm").is_file()
    # The class folder is untouched, so the clip keeps its label.
    assert (dataset / "006" / f"006_{token}_20260102_000000_bbbbbb.webm").is_file()


def test_running_it_twice_changes_nothing_the_second_time(dataset: Path):
    # The sheet is never written back to, so the second run reads the ORIGINAL
    # filenames against files that have since been renamed. That is the case
    # this has to survive.
    rows = [
        row("006_20260101_000000_aaaaaa.webm"),
        row("006_20260102_000000_bbbbbb.webm"),
    ]
    apply_backfill(plan_backfill(rows, dataset), dry_run=False)
    after_first = sorted(path.name for path in dataset.rglob("*") if path.is_file())

    second = plan_backfill(rows, dataset)
    assert second.renames == []
    assert second.already_tagged == 2
    apply_backfill(second, dry_run=False)
    assert sorted(path.name for path in dataset.rglob("*") if path.is_file()) == after_first


def test_a_tagged_copy_next_to_the_untagged_one_is_ambiguous_and_planned_away(dataset: Path):
    # Both files strip to the same key, so the row cannot say which one it means.
    # Planning refuses rather than picking one.
    token = derive_speaker_token("Chrome 120", "Android")
    (dataset / "006" / f"006_{token}_20260101_000000_aaaaaa.webm").write_bytes(b"other")

    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    assert plan.renames == []
    assert plan.duplicate_names == ["006_20260101_000000_aaaaaa.webm"]

    apply_backfill(plan, dry_run=False)
    # Neither recording was lost.
    assert (dataset / "006" / "006_20260101_000000_aaaaaa.webm").read_bytes() == b"audio"
    assert (dataset / "006" / f"006_{token}_20260101_000000_aaaaaa.webm").read_bytes() == b"other"


def test_an_existing_target_is_skipped_rather_than_overwritten(dataset: Path):
    # The second guard: the target appeared between planning and applying.
    # Overwriting would destroy a recording, which is far worse than leaving one
    # file ungrouped.
    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    assert len(plan.renames) == 1
    plan.renames[0].target.write_bytes(b"other")

    result = apply_backfill(plan, dry_run=False)
    assert result.renamed == 0
    assert result.skipped_existing == 1
    assert (dataset / "006" / "006_20260101_000000_aaaaaa.webm").read_bytes() == b"audio"
    assert plan.renames[0].target.read_bytes() == b"other"


def test_the_mapping_is_saved_because_the_sheet_is_not_updated(dataset: Path, tmp_path: Path):
    plan = plan_backfill([row("006_20260101_000000_aaaaaa.webm")], dataset)
    result = apply_backfill(plan, dry_run=False)
    written = result.save(tmp_path / "reports")

    assert all(path.is_file() for path in written)
    body = (tmp_path / "reports" / "speaker_backfill.csv").read_text(encoding="utf-8")
    assert "006_20260101_000000_aaaaaa.webm" in body
    assert derive_speaker_token("Chrome 120", "Android") in body


# ---------------------------------------------------------------------------
# The non-destructive alternative
# ---------------------------------------------------------------------------
def test_speakers_csv_carries_the_same_grouping_without_renaming(dataset: Path, tmp_path: Path):
    rows = [
        row("006_20260101_000000_aaaaaa.webm"),
        row("006_20260102_000000_bbbbbb.webm"),
        row("006_sp11112222_20260104_000000_dddddd.webm"),
    ]
    plan = plan_backfill(rows, dataset)
    path = write_speakers_csv(plan, tmp_path / "speakers.csv", rows=rows)

    lines = path.read_text(encoding="utf-8").strip().splitlines()
    assert lines[0] == "file,speaker"
    token = derive_speaker_token("Chrome 120", "Android")
    assert f"006_20260101_000000_aaaaaa.webm,{token}" in lines
    # A file that already carries a real token keeps it, so one file covers all.
    assert "006_sp11112222_20260104_000000_dddddd.webm,sp11112222" in lines
    # Nothing on disk moved.
    assert (dataset / "006" / "006_20260101_000000_aaaaaa.webm").is_file()


# ---------------------------------------------------------------------------
# Loading the sheet
# ---------------------------------------------------------------------------
def test_a_csv_export_of_the_sheet_loads(tmp_path: Path):
    path = tmp_path / "samples.csv"
    path.write_text(
        "sample_id,phrase_id,filename,browser,platform,drive_url\n"
        "a,6,006_20260101_000000_aaaaaa.webm,Chrome 120,Android,x\n"
        "b,6,,Chrome 120,Android,x\n",
        encoding="utf-8",
    )
    rows = load_metadata_rows(path)
    assert len(rows) == 1  # the row with no filename is dropped
    assert rows[0].filename == "006_20260101_000000_aaaaaa.webm"
    assert rows[0].token == derive_speaker_token("Chrome 120", "Android")


def test_a_sheet_without_the_needed_columns_fails_loudly(tmp_path: Path):
    path = tmp_path / "samples.csv"
    path.write_text("sample_id,filename\na,x.webm\n", encoding="utf-8")
    with pytest.raises(ValueError, match="browser"):
        load_metadata_rows(path)


def test_extra_columns_are_ignored(tmp_path: Path):
    path = tmp_path / "samples.csv"
    path.write_text(
        "filename,browser,platform,something_new\n006_20260101_000000_aaaaaa.webm,C,A,42\n",
        encoding="utf-8",
    )
    assert len(load_metadata_rows(path)) == 1

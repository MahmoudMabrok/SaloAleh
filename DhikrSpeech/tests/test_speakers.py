"""Tests for speaker resolution, speaker-grouped splitting and leakage detection.

Speaker leakage is the failure this project can least afford to get wrong and the
one nothing else would catch: nothing crashes, no metric looks odd, the reported
accuracy is simply answering a different question than the one anyone asked. So
the resolver, the grouped split and the leakage check are pinned down here on
hand-built records whose right answer is known by construction - no dataset, no
audio, no TensorFlow.
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import SpeakerConfig, SplitConfig
from src.dataset import ManifestRecord, assign_splits, manifest_speaker_report, verify_manifest_splits
from src.speakers import (
    SOURCE_FILENAME,
    SOURCE_METADATA,
    SOURCE_NONE,
    SpeakerResolver,
    load_speaker_metadata,
    speaker_leaks,
    speaker_report,
    verify_speaker_disjoint,
)


def record(
    label: str,
    class_index: int,
    name: str,
    speaker: str = "",
    split: str = "",
) -> ManifestRecord:
    return ManifestRecord(
        path=f"{label}/{name}.wav",
        source_path=f"/raw/{label}/{name}.wav",
        label=label,
        class_index=class_index,
        phrase_id=int(label) if label.isdigit() else None,
        text=label,
        split=split,
        duration=2.0,
        speaker=speaker,
    )


# ---------------------------------------------------------------------------
# Resolution
# ---------------------------------------------------------------------------
def test_filename_regex_extracts_the_speaker_token():
    resolver = SpeakerResolver(SpeakerConfig(source="filename"))
    assert resolver.resolve("dataset/006/speaker01_003.wav").speaker == "speaker01"
    assert resolver.resolve("dataset/006/ali-7.wav").speaker == "ali"


def test_filename_regex_without_a_match_yields_no_speaker():
    resolver = SpeakerResolver(SpeakerConfig(source="filename"))
    assignment = resolver.resolve("dataset/006/recording.wav")
    assert assignment.speaker is None


def test_named_group_wins_over_group_one():
    resolver = SpeakerResolver(
        SpeakerConfig(source="filename", regex=r"(\d+)_(?P<speaker>[a-z]+)")
    )
    assert resolver.resolve("006/12_ali.wav").speaker == "ali"


def test_the_collector_device_token_is_the_speaker_not_the_class_id():
    """SpeechCollector names uploads `<class>_sp<8 hex>_<timestamp>_<suffix>`.

    A pattern that simply took the leading token would return the *class id*, so
    every recording of a phrase would be one "speaker" and the split would move
    whole classes into one split with nothing looking wrong. This is the case
    that has to keep working.
    """
    resolver = SpeakerResolver(SpeakerConfig(source="filename"))
    assignment = resolver.resolve(
        "dataset/006/006_spa1b2c3d4_20260808T120000_ab12.webm", blocked=("006", "")
    )
    assert assignment.speaker == "spa1b2c3d4"


def test_a_collector_file_without_a_token_has_no_speaker():
    """Uploads that predate the device token fall back to being their own group,
    which is honest - not to the class id, which would be a lie."""
    resolver = SpeakerResolver(SpeakerConfig(source="filename"))
    assignment = resolver.resolve(
        "dataset/007/007_20260101T090000_old1.webm", blocked=("007", "")
    )
    assert assignment.speaker is None


def test_a_hand_named_file_still_resolves_by_its_leading_token():
    resolver = SpeakerResolver(SpeakerConfig(source="filename"))
    assert resolver.resolve("006/ali_001.wav", blocked=("006", "")).speaker == "ali"


def test_parent_folder_source_ignores_class_and_category_folders():
    resolver = SpeakerResolver(SpeakerConfig(source="parent"))
    assert resolver.resolve("dataset/006/ali/x.wav", blocked=("006", "")).speaker == "ali"
    # A recording sitting directly in its class folder has no speaker folder.
    assert resolver.resolve("dataset/006/x.wav", blocked=("006", "")).speaker is None
    # A negative category is structure, not a person.
    assert (
        resolver.resolve(
            "dataset/unknown/hard_negative/x.wav", blocked=("unknown", "hard_negative")
        ).speaker
        is None
    )


def test_metadata_matches_bare_names_and_relative_paths(tmp_path):
    csv_path = tmp_path / "speakers.csv"
    csv_path.write_text(
        "file,speaker\n006/one.wav,ali\ntwo.wav,fatima\n", encoding="utf-8"
    )
    metadata = load_speaker_metadata(csv_path)
    resolver = SpeakerResolver(SpeakerConfig(source="metadata"), metadata=metadata)
    assert resolver.resolve("/data/dataset/006/one.wav").speaker == "ali"
    assert resolver.resolve("/data/dataset/007/two.wav").speaker == "fatima"
    assert resolver.resolve("/data/dataset/007/three.wav").speaker is None


def test_metadata_without_the_expected_columns_is_ignored(tmp_path):
    csv_path = tmp_path / "speakers.csv"
    csv_path.write_text("a,b\n1,2\n", encoding="utf-8")
    assert load_speaker_metadata(csv_path) == {}


def test_missing_metadata_file_is_not_an_error(tmp_path):
    assert load_speaker_metadata(tmp_path / "absent.csv") == {}


def test_auto_picks_one_source_for_the_whole_dataset(tmp_path):
    """`auto` must not mix conventions: `ali` from a folder and `ali` from a
    filename are not knowably the same person."""
    resolver = SpeakerResolver(SpeakerConfig(source="auto"))
    items = [(f"006/ali_{index}.wav", ("006", "")) for index in range(5)]
    items += [("006/nomatch.wav", ("006", ""))]
    assignments = resolver.resolve_all(items)
    assert resolver.resolved_source == SOURCE_FILENAME
    assert [item.speaker for item in assignments[:5]] == ["ali"] * 5
    assert assignments[-1].speaker is None


def test_auto_falls_back_to_none_when_nothing_matches_at_all():
    resolver = SpeakerResolver(SpeakerConfig(source="auto"))
    assignments = resolver.resolve_all(
        [(f"006/rec{index}.wav", ("006", "")) for index in range(5)]
    )
    assert resolver.resolved_source == SOURCE_NONE
    assert all(item.speaker is None for item in assignments)


def test_auto_uses_partial_coverage_rather_than_discarding_it(caplog):
    """A dataset part-way through a collector rollout: newer files carry the
    device token, older ones do not. Grouping what can be grouped is strictly
    better than splitting all of it at random - but it is worth a warning."""
    items = [
        (f"006/006_spa1b2c3d4_{index}_x.webm", ("006", "")) for index in range(2)
    ]
    items += [(f"006/006_{index}_old.webm", ("006", "")) for index in range(5)]
    resolver = SpeakerResolver(SpeakerConfig(source="auto"))
    with caplog.at_level(logging.WARNING, logger="src.speakers"):
        assignments = resolver.resolve_all(items)
    assert resolver.resolved_source == SOURCE_FILENAME
    assert [item.speaker for item in assignments[:2]] == ["spa1b2c3d4"] * 2
    assert all(item.speaker is None for item in assignments[2:])
    assert "cover only" in caplog.text


def test_auto_prefers_metadata_over_the_filename_convention():
    resolver = SpeakerResolver(
        SpeakerConfig(source="auto"), metadata={"a_1.wav": "real", "a_2.wav": "real"}
    )
    assignments = resolver.resolve_all(
        [("006/a_1.wav", ("006", "")), ("006/a_2.wav", ("006", ""))]
    )
    assert resolver.resolved_source == SOURCE_METADATA
    assert [item.speaker for item in assignments] == ["real", "real"]


# ---------------------------------------------------------------------------
# Leakage
# ---------------------------------------------------------------------------
def test_speaker_leaks_finds_a_speaker_in_two_splits():
    leaks = speaker_leaks(
        [("ali", "train"), ("ali", "test"), ("fatima", "train"), ("fatima", "train")]
    )
    assert [leak.speaker for leak in leaks] == ["ali"]
    assert leaks[0].counts == {"test": 1, "train": 1}


def test_recordings_without_a_speaker_never_count_as_a_leak():
    assert speaker_leaks([(None, "train"), (None, "test"), ("", "val")]) == []


def test_verify_speaker_disjoint_raises_on_leakage():
    report = speaker_report(
        [("ali", "006", "train"), ("ali", "006", "test")], SOURCE_FILENAME
    )
    assert not report.is_disjoint
    with pytest.raises(ValueError, match="speaker leakage"):
        verify_speaker_disjoint(report)


def test_verify_speaker_disjoint_can_be_downgraded_to_a_warning(caplog):
    report = speaker_report(
        [("ali", "006", "train"), ("ali", "006", "test")], SOURCE_FILENAME
    )
    verify_speaker_disjoint(report, require_disjoint=False)  # must not raise
    assert "require_disjoint is off" in caplog.text


def test_report_without_speakers_says_so_loudly():
    report = speaker_report([(None, "006", "train"), (None, "007", "test")], SOURCE_NONE)
    assert not report.known
    assert report.is_disjoint  # nothing to leak - but that is not a pass
    assert any("NOT speaker-independent" in note for note in report.warnings())


def test_report_counts_speakers_per_class_and_split():
    report = speaker_report(
        [
            ("ali", "006", "train"),
            ("fatima", "006", "val"),
            ("ali", "007", "train"),
        ],
        SOURCE_FILENAME,
    )
    assert report.num_speakers == 2
    assert report.speakers_per_class["006"] == ["ali", "fatima"]
    assert report.speakers_per_split == {"train": ["ali"], "val": ["fatima"]}
    assert report.recordings_per_speaker == {"ali": 2, "fatima": 1}


# ---------------------------------------------------------------------------
# Splitting
# ---------------------------------------------------------------------------
def _speaker_dataset(speakers: int = 8, per_class: int = 3) -> list:
    records = []
    for index in range(speakers):
        for label, class_index in (("006", 0), ("007", 1), ("unknown", 2)):
            for number in range(per_class):
                records.append(
                    record(label, class_index, f"sp{index}_{number}", f"sp{index}")
                )
    return records


def test_split_never_puts_one_speaker_in_two_splits():
    records = assign_splits(_speaker_dataset(), SplitConfig(), seed=1337)
    report = manifest_speaker_report(records)
    assert report.is_disjoint
    assert set(report.speakers_per_split) == {"train", "val", "test"}


def test_split_is_deterministic_for_a_seed():
    first = assign_splits(_speaker_dataset(), SplitConfig(), seed=7)
    second = assign_splits(_speaker_dataset(), SplitConfig(), seed=7)
    assert [item.split for item in first] == [item.split for item in second]


def test_split_keeps_every_class_in_train():
    records = assign_splits(_speaker_dataset(speakers=3), SplitConfig(), seed=3)
    trained = {item.class_index for item in records if item.split == "train"}
    assert trained == {0, 1, 2}


def test_records_without_a_speaker_are_split_individually():
    """They cannot be grouped, and pretending they can is how a leak gets in."""
    records = [record("006", 0, f"rec{index}") for index in range(10)]
    records += [record("007", 1, f"rec{index}", "ali") for index in range(10)]
    updated = assign_splits(records, SplitConfig(), seed=1)
    assert manifest_speaker_report(updated).is_disjoint
    # 'ali' owns every 007 clip, so all of them share one split.
    ali_splits = {item.split for item in updated if item.speaker == "ali"}
    assert len(ali_splits) == 1


def test_legacy_group_regex_still_groups_when_no_speakers_are_present():
    records = [record("006", 0, f"s{index // 4}_{index}") for index in range(12)]
    records += [record("007", 1, f"s{index // 4}_{index}") for index in range(12)]
    config = SplitConfig(group_regex=r"^s\d+")
    updated = assign_splits(records, config, seed=5)
    groups = {}
    for item in updated:
        groups.setdefault(Path(item.path).name.split("_")[0], set()).add(item.split)
    assert all(len(splits) == 1 for splits in groups.values())


def test_verify_manifest_splits_raises_on_a_leaking_manifest():
    records = [
        record("006", 0, "a", "ali", "train"),
        record("006", 0, "b", "ali", "test"),
    ]
    with pytest.raises(ValueError, match="speaker leakage"):
        verify_manifest_splits(records, SplitConfig())


def test_verify_manifest_splits_passes_without_speaker_information():
    records = [record("006", 0, "a", "", "train"), record("006", 0, "b", "", "test")]
    report = verify_manifest_splits(records, SplitConfig())
    assert not report.known

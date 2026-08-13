"""Tests for single-target dataset mapping and negative categorisation.

Everything here runs on a temporary directory of empty ``.wav`` files - no
Drive, no audio decoding, no TensorFlow. What is being checked is the *mapping*:
which folder becomes the target, which becomes which kind of negative, and which
is left out. A mistake there is invisible in training (the run happily converges)
and only shows up as a detector that fires on the wrong phrase.
"""

from __future__ import annotations

import json
import sys
from types import SimpleNamespace
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import Config, NegativeSamplingConfig, TargetConfig
from src.dataset import ManifestRecord, Phrase
from src.targets import (
    TARGET_INDEX,
    TARGET_LABEL,
    UNKNOWN_INDEX,
    UNKNOWN_LABEL,
    build_target_report,
    binary_split_distribution,
    negative_breakdown,
    recommend_clip_seconds,
    sample_negatives,
    scan_target_dataset,
    target_negative_paths,
    validate_binary_label_mapping,
)
from src.pipeline import training_setup_summary

PHRASES = [
    Phrase(id=1, text="سبحان الله"),
    Phrase(id=6, text="سبحان الله وبحمده"),
    Phrase(id=7, text="سبحان الله العظيم وبحمده"),
]


def touch(root: Path, relative: str, count: int = 1, prefix: str = "spk01") -> None:
    directory = root / relative
    directory.mkdir(parents=True, exist_ok=True)
    for index in range(count):
        (directory / f"{prefix}_{index:03d}.wav").write_bytes(b"")


@pytest.fixture
def dataset(tmp_path: Path) -> Path:
    root = tmp_path / "dataset"
    touch(root, "007", 4, "spk01")
    touch(root, "006", 3, "spk02")
    touch(root, "001", 2, "spk03")
    touch(root, "unknown", 2, "spk04")                    # flat filler
    touch(root, "unknown/normal_speech", 3, "spk05")
    touch(root, "unknown/noise", 2, "spk06")
    touch(root, "unknown/partial_phrase", 2, "spk07")
    touch(root, "unknown/hard_negative", 5, "spk08")
    return root


def scan(root: Path, **kwargs):
    target = TargetConfig(phrase_id=7, **kwargs)
    return scan_target_dataset(root, PHRASES, target)


def record(label: str, class_index: int, path: str, split: str = "train") -> ManifestRecord:
    return ManifestRecord(path, path, label, class_index, None, label, split, 1.0)


def test_binary_label_mapping_accepts_unknown_zero_target_one() -> None:
    mapping = validate_binary_label_mapping(
        [record("unknown", 0, "u.wav"), record("target", 1, "t.wav")]
    )
    assert mapping == {0: "UNKNOWN", 1: "TARGET"}


def test_binary_split_distribution_reports_majority_baseline() -> None:
    rows = [record("unknown", 0, f"u{i}.wav") for i in range(188)]
    rows += [record("target", 1, f"t{i}.wav") for i in range(12)]
    distribution = binary_split_distribution(rows)
    assert distribution.target == 12
    assert distribution.unknown == 188
    assert distribution.majority_baseline == pytest.approx(0.94)


def test_training_setup_prints_split_baselines_warning_and_step_budget() -> None:
    train = [record("unknown", 0, f"u{i}.wav") for i in range(20)]
    train += [record("target", 1, f"t{i}.wav") for i in range(10)]
    validation = [record("unknown", 0, f"vu{i}.wav", "val") for i in range(188)]
    validation += [record("target", 1, f"vt{i}.wav", "val") for i in range(12)]
    test = [record("unknown", 0, "test-u.wav", "test"), record("target", 1, "test-t.wav", "test")]
    config = Config.load(Path(__file__).resolve().parents[1] / "configs" / "config.yaml")
    preparation = SimpleNamespace(config=config, records=train + validation + test)
    text = training_setup_summary(preparation, train)
    assert "0 -> UNKNOWN" in text and "1 -> TARGET" in text
    assert "Validation contains only 12 TARGET clips" in text
    assert "8.33 percentage points" in text
    assert "training clips       : 30" in text
    assert "steps per epoch      : 2" in text
    assert "max optimiser steps  : 600" in text


@pytest.mark.parametrize(
    "records",
    [
        [record("unknown", 2, "bad.wav")],
        [record("target", 0, "bad.wav")],
        [record("other", 1, "bad.wav")],
    ],
)
def test_binary_label_mapping_fails_loudly_on_invalid_labels(records) -> None:
    with pytest.raises(ValueError, match="binary label"):
        validate_binary_label_mapping(records)


# ---------------------------------------------------------------------------
# Positive / negative mapping
# ---------------------------------------------------------------------------
def test_negative_paths_are_every_non_target_folder_plus_unknown(tmp_path: Path) -> None:
    root = tmp_path / "dataset"
    for name in ("007", "006", "001", "unknown", ".cache"):
        (root / name).mkdir(parents=True)

    paths = target_negative_paths(root, TargetConfig(phrase_id=7))

    assert [path.name for path in paths] == ["001", "006", "unknown"]


def test_notebook_location_cell_lists_non_target_folders_and_unknown(
    tmp_path: Path, capsys
) -> None:
    dataset = tmp_path / "project" / "dataset"
    for name in ("007", "006", "001", "unknown"):
        (dataset / name).mkdir(parents=True)

    config = Config().with_overrides(
        {
            "paths.drive_root": str(tmp_path),
            "paths.project_dir": "project",
            "target.phrase_id": 7,
        }
    )
    paths = config.paths
    notebook_path = Path(__file__).resolve().parents[1] / "notebooks" / "DhikrSpeech.ipynb"
    notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
    source = "".join(
        next(cell["source"] for cell in notebook["cells"] if cell.get("id") == "cell-006")
    )

    exec(compile(source, str(notebook_path), "exec"), {"config": config, "paths": paths})

    output = capsys.readouterr().out
    assert "negative/001" in output
    assert "negative/006" in output
    assert "negative/unknown" in output
    assert "negative/007" not in output
    assert "target_negative_paths(" in source
    assert "negatives_dir" not in source


def test_target_folder_is_the_only_positive(dataset: Path) -> None:
    index = scan(dataset)
    positives = [s for s in index.samples if s.class_index == TARGET_INDEX]
    assert len(positives) == 4
    assert {s.label for s in positives} == {TARGET_LABEL}
    assert all("007" in str(s.path) for s in positives)
    assert all(s.negative_type is None for s in positives)


def test_other_dhikr_folders_become_negatives(dataset: Path) -> None:
    index = scan(dataset)
    breakdown = negative_breakdown(index.samples)
    assert breakdown["other_dhikr"] == 5  # 006 (3) + 001 (2)
    assert all(
        s.class_index == UNKNOWN_INDEX and s.label == UNKNOWN_LABEL
        for s in index.samples
        if s.negative_type == "other_dhikr"
    )


def test_other_dhikr_negatives_can_be_switched_off(dataset: Path) -> None:
    index = scan(dataset, auto_other_dhikr_negatives=False)
    assert "other_dhikr" not in negative_breakdown(index.samples)


def test_negative_categories_are_preserved(dataset: Path) -> None:
    breakdown = negative_breakdown(scan(dataset).samples)
    assert breakdown == {
        "other_dhikr": 5,
        "unknown": 2,
        "normal_speech": 3,
        "noise": 2,
        "partial_phrase": 2,
        "hard_negative": 5,
    }


def test_hard_negatives_are_shared_across_targets(dataset: Path) -> None:
    """`unknown/hard_negative/` is one folder for every target - which is why a
    clip containing another target's complete phrase must not go in it."""
    for phrase_id in (6, 7):
        index = scan_target_dataset(dataset, PHRASES, TargetConfig(phrase_id=phrase_id))
        assert negative_breakdown(index.samples)["hard_negative"] == 5


def test_filename_scoped_hard_negative_is_used_only_for_its_target(dataset: Path) -> None:
    tagged = dataset / "unknown" / "unknown_spABC_01_000_hard_negative_006.wav"
    tagged.write_bytes(b"")

    for phrase_id, expected in ((6, True), (7, False)):
        index = scan_target_dataset(dataset, PHRASES, TargetConfig(phrase_id=phrase_id))
        matches = [sample for sample in index.samples if sample.path == tagged]
        assert bool(matches) is expected
        if matches:
            assert matches[0].negative_type == "hard_negative"


def test_cache_dir_follows_the_source_folder_not_the_label(dataset: Path) -> None:
    """Shared negatives must land in the same cache slot for every target,
    otherwise each target re-conditions the whole pool."""
    index = scan(dataset)
    by_dir = {s.rel_dir.replace("\\", "/") for s in index.samples}
    assert "007" in by_dir and "006" in by_dir
    assert "unknown/normal_speech" in by_dir
    speech = next(s for s in index.samples if s.negative_type == "normal_speech")
    assert speech.label == UNKNOWN_LABEL and speech.cache_dir != UNKNOWN_LABEL


def test_missing_target_folder_is_an_error(dataset: Path) -> None:
    with pytest.raises(FileNotFoundError, match="009"):
        scan_target_dataset(dataset, PHRASES, TargetConfig(phrase_id=9))


def test_empty_target_folder_is_an_error(tmp_path: Path) -> None:
    root = tmp_path / "dataset"
    (root / "007").mkdir(parents=True)
    touch(root, "006", 2)
    with pytest.raises(FileNotFoundError, match="no audio files"):
        scan_target_dataset(root, PHRASES, TargetConfig(phrase_id=7))


def test_target_without_negatives_still_scans(tmp_path: Path, caplog) -> None:
    """A positives-only dataset is a legitimate prototype; it must warn, not fail."""
    root = tmp_path / "dataset"
    touch(root, "007", 3)
    index = scan_target_dataset(root, PHRASES, TargetConfig(phrase_id=7))
    assert len(index.samples) == 3
    assert "no negatives" in caplog.text.lower()


def test_index_carries_the_target_text(dataset: Path) -> None:
    index = scan(dataset)
    assert index.target_id == 7
    assert index.target_text == "سبحان الله العظيم وبحمده"
    assert index.class_names == [UNKNOWN_LABEL, TARGET_LABEL]


# ---------------------------------------------------------------------------
# Negative sampling
# ---------------------------------------------------------------------------
def record(index: int, class_index: int, negative_type=None, split: str = "train"):
    return ManifestRecord(
        path=f"{negative_type or 'target'}/{index}.wav",
        source_path=f"/raw/{index}.wav",
        label=TARGET_LABEL if class_index == TARGET_INDEX else UNKNOWN_LABEL,
        class_index=class_index,
        phrase_id=7 if class_index == TARGET_INDEX else None,
        text="",
        split=split,
        duration=2.0,
        negative_type=negative_type,
    )


def pool(positives: int = 10, per_type: int = 40):
    records = [record(i, TARGET_INDEX) for i in range(positives)]
    for offset, name in enumerate(("hard_negative", "noise", "general_speech")):
        records += [
            record(1000 * (offset + 1) + i, UNKNOWN_INDEX, name) for i in range(per_type)
        ]
    return records


def test_sampling_respects_the_ratio() -> None:
    records = pool()
    sampled = sample_negatives(records, NegativeSamplingConfig(ratio=2.0), seed=1)
    assert sum(1 for r in sampled if r.class_index == TARGET_INDEX) == 10
    assert sum(1 for r in sampled if r.class_index == UNKNOWN_INDEX) == 20


def test_sampling_is_deterministic_in_the_seed() -> None:
    records = pool()
    config = NegativeSamplingConfig(ratio=1.5)
    first = sample_negatives(records, config, seed=7)
    again = sample_negatives(records, config, seed=7)
    other = sample_negatives(records, config, seed=8)
    assert [r.path for r in first] == [r.path for r in again]
    assert [r.path for r in first] != [r.path for r in other]


def test_weights_bias_which_negatives_survive() -> None:
    records = pool()
    config = NegativeSamplingConfig(
        ratio=2.0, weights={"hard_negative": 8.0, "general_speech": 1.0, "noise": 0.25}
    )
    kept = [
        r for r in sample_negatives(records, config, seed=3) if r.class_index == UNKNOWN_INDEX
    ]
    counts = {}
    for item in kept:
        counts[item.negative_type] = counts.get(item.negative_type, 0) + 1
    assert counts.get("hard_negative", 0) > counts.get("general_speech", 0)
    assert counts.get("general_speech", 0) >= counts.get("noise", 0)


def test_zero_weight_negatives_are_dropped_last() -> None:
    records = pool(positives=10, per_type=20)
    config = NegativeSamplingConfig(ratio=1.0, weights={"noise": 0.0})
    kept = [
        r for r in sample_negatives(records, config, seed=5) if r.class_index == UNKNOWN_INDEX
    ]
    assert all(r.negative_type != "noise" for r in kept)


def test_sampling_only_touches_the_requested_split() -> None:
    records = pool() + [record(9001, UNKNOWN_INDEX, "noise", split="val")]
    sampled = sample_negatives(records, NegativeSamplingConfig(ratio=1.0), seed=2)
    assert sum(1 for r in sampled if r.split == "val") == 1


def test_sampling_disabled_returns_everything() -> None:
    records = pool()
    assert len(sample_negatives(records, NegativeSamplingConfig(enabled=False), seed=1)) == len(
        records
    )


def test_a_small_pool_is_kept_whole() -> None:
    records = pool(positives=10, per_type=3)
    sampled = sample_negatives(records, NegativeSamplingConfig(ratio=3.0), seed=1)
    assert len(sampled) == len(records)


# ---------------------------------------------------------------------------
# Window recommendation and the dataset report
# ---------------------------------------------------------------------------
def test_window_recommendation_leaves_margin() -> None:
    result = recommend_clip_seconds([1.5, 1.7, 1.9, 2.1, 2.4], margin_seconds=0.35)
    assert result["recommended_clip_seconds"] >= 2.4 + 0.3
    assert result["max"] == pytest.approx(2.4)


def test_window_recommendation_on_no_durations_is_empty() -> None:
    assert recommend_clip_seconds([]) == {}


def test_report_flags_a_prototype_dataset(dataset: Path) -> None:
    index = scan(dataset)
    report = build_target_report(index, config=Config())
    text = " ".join(report.recommendations())
    assert "PROTOTYPE ONLY" in text
    assert report.positive_clips == 4
    assert report.hard_negative_clips == 5
    assert not report.speakers_known
    assert "NOT SPEAKER-INDEPENDENT" in text


def test_report_flags_a_missing_hard_negative_set(tmp_path: Path) -> None:
    root = tmp_path / "dataset"
    touch(root, "007", 200)
    touch(root, "006", 200)
    index = scan_target_dataset(root, PHRASES, TargetConfig(phrase_id=7))
    report = build_target_report(index, config=Config())
    assert any(
        "unknown/hard_negative/" in note for note in report.recommendations()
    )


def test_report_counts_speakers_when_known(dataset: Path) -> None:
    """Speaker ids come off the samples, which `scan_dataset` resolved."""
    from src.speakers import SpeakerResolver
    from src.config import SpeakerConfig

    resolver = SpeakerResolver(
        config=SpeakerConfig(source="filename", regex=r"^(?P<speaker>spk\d+)"),
        metadata={},
        dataset_root=dataset,
    )
    index = scan_target_dataset(dataset, PHRASES, TargetConfig(phrase_id=7), speaker_resolver=resolver)
    durations = {str(s.path): 2.0 for s in index.samples}
    report = build_target_report(index, durations=durations, config=Config())
    assert report.speakers_known
    assert report.positive_speakers == 1
    assert report.negative_speakers == 7
    assert report.positive_seconds == pytest.approx(8.0)
    assert report.by_negative_type["hard_negative"]["clips"] == 5
    assert "sec" not in report.summary() or True  # summary renders without raising

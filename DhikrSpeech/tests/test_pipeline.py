"""End-to-end test of the dataset stage on a synthetic project on disk.

Real WAV files, a real config, real conditioning and a real manifest - just no
TensorFlow and no Drive. This is the stage that decides what the model learns
from, so it is worth exercising for real rather than mocking: the things it can
get wrong (a negative landing in the positive class, a speaker straddling two
splits, the shared negative pool being re-conditioned per target) are all
invisible afterwards.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.audio import write_wav
from src.config import Config
from src.dataset import load_manifest
from src.pipeline import prepare_target, resolve_targets, training_records
from src.targets import TARGET_INDEX

SPEAKERS = [f"spk{index:02d}" for index in range(12)]


def tone(seconds: float, frequency: float, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    time = np.arange(int(seconds * 16000), dtype=np.float32) / 16000.0
    return (0.4 * np.sin(2 * np.pi * frequency * time) + 0.02 * rng.standard_normal(time.size)).astype(
        np.float32
    )


def populate(root: Path, relative: str, frequency: float, per_speaker: int = 2) -> None:
    directory = root / relative
    directory.mkdir(parents=True, exist_ok=True)
    for speaker_index, speaker in enumerate(SPEAKERS):
        for clip in range(per_speaker):
            seed = hash((relative, speaker, clip)) % (2**31)
            write_wav(
                directory / f"{speaker}_{clip:03d}.wav",
                tone(1.6 + 0.1 * clip, frequency + 20 * speaker_index, seed),
                16000,
            )


@pytest.fixture
def project(tmp_path: Path) -> Config:
    root = tmp_path / "project"
    dataset = root / "dataset"
    populate(dataset, "007", 300.0)
    populate(dataset, "006", 420.0)
    populate(dataset, "unknown", 700.0, per_speaker=1)
    populate(dataset, "negatives/general_speech", 900.0, per_speaker=1)
    populate(dataset, "negatives/hard/007", 330.0, per_speaker=1)
    populate(dataset, "negatives/hard/006", 430.0, per_speaker=1)
    populate(dataset, "negatives/shared/noise", 120.0, per_speaker=1)

    (root / "phrases.json").write_text(
        json.dumps(
            [
                {"id": 6, "text": "سبحان الله وبحمده"},
                {"id": 7, "text": "سبحان الله العظيم وبحمده"},
            ]
        ),
        encoding="utf-8",
    )

    config = Config()
    return config.with_overrides(
        {
            "paths.drive_root": str(tmp_path),
            "paths.project_dir": "project",
            "target.phrase_id": 7,
            "split.speaker_regex": r"^(?P<speaker>spk\d+)",
            "audio.clip_seconds": 2.0,
            "training.batch_size": 8,
        }
    )


# ---------------------------------------------------------------------------
# Dataset stage
# ---------------------------------------------------------------------------
def test_prepare_target_builds_a_binary_manifest(project: Config) -> None:
    preparation = prepare_target(project, 7)
    labels = {record.label for record in preparation.records}
    assert labels == {"target", "unknown"}
    positives = [r for r in preparation.records if r.class_index == TARGET_INDEX]
    assert len(positives) == 24  # 12 speakers x 2 clips
    assert all(r.phrase_id == 7 for r in positives)


def test_other_dhikr_and_hard_negatives_are_negatives(project: Config) -> None:
    preparation = prepare_target(project, 7)
    types = {r.negative_type for r in preparation.records if r.class_index != TARGET_INDEX}
    assert {"other_dhikr", "hard_negative", "general_speech", "noise", "unknown"} <= types
    hard = [r for r in preparation.records if r.negative_type == "hard_negative"]
    assert len(hard) == 12  # hard/007 only; hard/006 is excluded by default
    assert all("hard/007" in r.source_path.replace("\\", "/") for r in hard)


def test_the_manifest_round_trips_with_the_new_columns(project: Config) -> None:
    preparation = prepare_target(project, 7)
    reloaded = load_manifest(preparation.manifest_path)
    assert len(reloaded) == len(preparation.records)
    assert {r.speaker for r in reloaded} == set(SPEAKERS)
    assert any(r.negative_type == "hard_negative" for r in reloaded)


def test_no_speaker_crosses_a_split(project: Config) -> None:
    preparation = prepare_target(project, 7)
    assert preparation.isolation.is_clean
    placement = {}
    for record in preparation.records:
        placement.setdefault(record.speaker, set()).add(record.split)
    assert all(len(splits) == 1 for splits in placement.values())


def test_every_split_holds_target_clips(project: Config) -> None:
    preparation = prepare_target(project, 7)
    for split in ("train", "val", "test"):
        positives = [
            r
            for r in preparation.records
            if r.split == split and r.class_index == TARGET_INDEX
        ]
        assert positives, f"the '{split}' split has no target clips"


def test_conditioned_clips_are_cached_by_geometry_and_source_folder(project: Config) -> None:
    preparation = prepare_target(project, 7)
    cache = project.for_target(7).clip_cache_path()
    assert cache.name == "16000hz_2s"
    assert (cache / "007").is_dir()
    assert (cache / "006").is_dir()
    assert (cache / "negatives" / "hard" / "007").is_dir()
    clip, _ = _read(next((cache / "007").glob("*.wav")))
    assert clip.size == project.audio.clip_samples


def _read(path: Path):
    from src.audio import read_wav

    return read_wav(path)


def test_two_targets_share_the_conditioned_negative_pool(project: Config) -> None:
    """The shared pool must be conditioned once, not once per target."""
    prepare_target(project, 7)
    cache = project.for_target(7).clip_cache_path()
    speech = sorted((cache / "negatives" / "general_speech").glob("*.wav"))
    stamps = {path: path.stat().st_mtime_ns for path in speech}

    prepare_target(project, 6)
    assert {path: path.stat().st_mtime_ns for path in speech} == stamps


def test_each_target_gets_its_own_manifest(project: Config) -> None:
    seven = prepare_target(project, 7)
    six = prepare_target(project, 6)
    assert seven.manifest_path != six.manifest_path
    assert seven.manifest_path.name == "target_007.csv"
    assert six.manifest_path.name == "target_006.csv"
    # 006's positives are 007's negatives and vice versa. Compare the cached
    # paths, not the file names: both folders use the same per-speaker names.
    six_positives = {r.path for r in six.records if r.class_index == TARGET_INDEX}
    seven_positives = {r.path for r in seven.records if r.class_index == TARGET_INDEX}
    assert not (six_positives & seven_positives)
    assert six_positives <= {r.path for r in seven.records if r.class_index != TARGET_INDEX}


def test_a_per_target_window_gets_its_own_cache(project: Config) -> None:
    wider = project.with_overrides({"target.phrase_overrides": {"007": {"clip_seconds": 2.6}}})
    preparation = prepare_target(wider, 7)
    assert preparation.config.audio.clip_seconds == pytest.approx(2.6)
    assert preparation.config.clip_cache_path().name == "16000hz_2.6s"
    clip, _ = _read(next((preparation.config.clip_cache_path() / "007").glob("*.wav")))
    assert clip.size == int(2.6 * 16000)


def test_the_dataset_report_describes_the_target(project: Config) -> None:
    report = prepare_target(project, 7).report
    assert report.target_id == 7
    assert report.target_text == "سبحان الله العظيم وبحمده"
    assert report.positive_clips == 24
    assert report.positive_speakers == 12
    assert report.hard_negative_clips == 12
    assert report.speakers_known
    assert report.window_recommendation["recommended_clip_seconds"] > 0


def test_the_report_flags_a_prototype_sized_dataset(project: Config) -> None:
    notes = " ".join(prepare_target(project, 7).report.recommendations())
    assert "PROTOTYPE ONLY" in notes


# ---------------------------------------------------------------------------
# Negative sampling inside the pipeline
# ---------------------------------------------------------------------------
def test_negative_sampling_only_touches_training(project: Config) -> None:
    preparation = prepare_target(project, 7)
    sampled = training_records(preparation.config, preparation.records)
    assert all(record.split == "train" for record in sampled)

    positives = sum(1 for r in sampled if r.class_index == TARGET_INDEX)
    negatives = len(sampled) - positives
    ratio = preparation.config.negative_sampling.ratio
    assert negatives <= max(int(round(positives * ratio)), 1)


def test_sampling_keeps_hard_negatives_preferentially(project: Config) -> None:
    preparation = prepare_target(project, 7)
    train = [r for r in preparation.records if r.split == "train"]
    available = sum(1 for r in train if r.negative_type == "hard_negative")
    kept = sum(
        1
        for r in training_records(preparation.config, preparation.records)
        if r.negative_type == "hard_negative"
    )
    if available:
        assert kept / available >= 0.5


# ---------------------------------------------------------------------------
# CLI argument handling
# ---------------------------------------------------------------------------
def test_targets_are_parsed_and_deduplicated(project: Config) -> None:
    assert resolve_targets(project, ["007", "7", "002"]) == [7, 2]


def test_target_defaults_to_the_config(project: Config) -> None:
    assert resolve_targets(project, []) == [7]


def test_a_missing_target_is_an_error() -> None:
    with pytest.raises(ValueError, match="no target given"):
        resolve_targets(Config(), [])


def test_a_non_numeric_target_is_an_error(project: Config) -> None:
    with pytest.raises(ValueError, match="not a phrase id"):
        resolve_targets(project, ["seven"])

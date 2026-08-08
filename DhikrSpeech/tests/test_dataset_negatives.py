"""Tests for negative categories, recursive indexing and the manifest columns.

The `unknown` folder is where the false-activation rate is decided, so how it is
indexed matters more than its size suggests. Three things have to hold and none
of them fails loudly if it breaks: every file under `unknown/` trains as one
class whatever subfolder it is in, the subfolder survives into the manifest so
evaluation can attribute a false positive to it, and two files with the same name
in different categories stay two files after preprocessing.

Real (tiny) WAVs are written here rather than mocked - the indexing walks the
filesystem and the preprocessing writes files, and a fake for either would test
the fake.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import Config
from src.dataset import (
    MANIFEST_FIELDS,
    ManifestRecord,
    build_speaker_resolver,
    load_manifest,
    load_phrases,
    negative_type_counts,
    preprocess_dataset,
    save_manifest,
    scan_dataset,
)


def write_clip(path: Path, seconds: float = 1.2, seed: int = 0) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(seed)
    samples = (0.2 * rng.standard_normal(int(16000 * seconds))).astype("float32")
    sf.write(path, samples, 16000, subtype="PCM_16")


@pytest.fixture
def project(tmp_path):
    """A dataset with nested negatives and two same-named files in it."""
    dataset = tmp_path / "dataset"
    for speaker in ("ali", "fatima"):
        for number in range(2):
            write_clip(dataset / "006" / f"{speaker}_{number}.wav", seed=hash(speaker) % 100)
            write_clip(dataset / "007" / f"{speaker}_{number}.wav", seed=1)
    write_clip(dataset / "unknown" / "hard_negative" / "ali_0.wav", seed=2)
    write_clip(dataset / "unknown" / "noise" / "ali_0.wav", seed=3)  # same name!
    write_clip(dataset / "unknown" / "partial_phrase" / "fatima_0.wav", seed=4)
    write_clip(dataset / "unknown" / "fatima_9.wav", seed=5)  # flat, no category
    (tmp_path / "phrases.json").write_text(
        json.dumps([{"id": 6, "text": "a"}, {"id": 7, "text": "b"}]), encoding="utf-8"
    )

    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "."}
    )
    config.classes.include_phrases = [6, 7]
    config.paths.ensure_dirs()
    return config


def index_of(config):
    return scan_dataset(
        config.paths.dataset_path,
        load_phrases(config.paths.phrases_path),
        unknown_class=config.paths.unknown_class,
        extensions=config.audio.file_extensions,
        classes=config.classes,
        speaker_resolver=build_speaker_resolver(config),
    )


# ---------------------------------------------------------------------------
# Indexing
# ---------------------------------------------------------------------------
def test_every_file_under_unknown_trains_as_one_class(project):
    index = index_of(project)
    assert index.class_names == ["006", "007", "unknown"]
    unknown = [sample for sample in index.samples if sample.label == "unknown"]
    assert len(unknown) == 4
    assert {sample.class_index for sample in unknown} == {2}


def test_the_subfolder_is_kept_as_the_negative_type(project):
    index = index_of(project)
    assert index.negative_counts() == {
        "hard_negative": 1,
        "noise": 1,
        "partial_phrase": 1,
        "unknown": 1,
    }


def test_a_flat_unknown_folder_still_works(tmp_path):
    """The layout every existing dataset uses must keep working unchanged."""
    dataset = tmp_path / "dataset"
    write_clip(dataset / "006" / "a.wav")
    write_clip(dataset / "unknown" / "b.wav")
    (tmp_path / "phrases.json").write_text(
        json.dumps([{"id": 6, "text": "a"}]), encoding="utf-8"
    )
    config = Config().with_overrides(
        {"paths.drive_root": str(tmp_path), "paths.project_dir": "."}
    )
    index = index_of(config)
    assert index.negative_counts() == {"unknown": 1}
    assert all(sample.negative_type is None for sample in index.samples if sample.label == "006")


def test_target_phrases_have_no_negative_type(project):
    index = index_of(project)
    assert all(
        sample.negative_type is None
        for sample in index.samples
        if sample.label in ("006", "007")
    )


def test_a_category_folder_is_not_mistaken_for_a_speaker(project):
    """`unknown/hard_negative/ali_0.wav` is a category, not a speaker called
    "hard_negative" - the filename still decides."""
    index = index_of(project)
    negatives = {
        sample.negative_type: sample.speaker
        for sample in index.samples
        if sample.label == "unknown"
    }
    assert negatives["hard_negative"] == "ali"
    assert negatives["noise"] == "ali"
    assert negatives["partial_phrase"] == "fatima"


# ---------------------------------------------------------------------------
# Preprocessing
# ---------------------------------------------------------------------------
def test_same_named_files_in_two_categories_are_both_kept(project):
    """Without the subfolder in the output path these two overwrite each other."""
    records, summary = preprocess_dataset(index_of(project), project)
    assert summary.failed == 0
    written = {record.path for record in records}
    assert "unknown/hard_negative/ali_0.wav" in written
    assert "unknown/noise/ali_0.wav" in written
    assert len(records) == len(index_of(project).samples)
    for record in records:
        assert record.resolve(project.paths.processed_path).is_file()


def test_the_manifest_carries_speaker_and_negative_type(project, tmp_path):
    records, _ = preprocess_dataset(index_of(project), project)
    path = save_manifest(records, tmp_path / "manifest.csv")
    loaded = load_manifest(path)

    assert "speaker" in MANIFEST_FIELDS and "negative_type" in MANIFEST_FIELDS
    assert negative_type_counts(loaded) == {
        "hard_negative": 1,
        "noise": 1,
        "partial_phrase": 1,
        "unknown": 1,
    }
    assert {record.speaker for record in loaded} == {"ali", "fatima"}


# ---------------------------------------------------------------------------
# Backward compatibility
# ---------------------------------------------------------------------------
def test_a_manifest_without_the_new_columns_still_loads(tmp_path, caplog):
    """Datasets processed before this change must not break the notebook."""
    legacy = tmp_path / "manifest.csv"
    legacy.write_text(
        "path,source_path,label,class_index,phrase_id,text,split,duration\n"
        "006/a.wav,/raw/006/a.wav,006,0,6,a,train,2.0\n",
        encoding="utf-8",
    )
    records = load_manifest(legacy)
    assert len(records) == 1
    assert records[0].speaker is None
    assert records[0].negative_type is None
    assert "predates the speaker/negative_type columns" in caplog.text


def test_a_manifest_round_trips(tmp_path):
    record = ManifestRecord(
        path="unknown/hard_negative/ali_0.wav",
        source_path="/raw/unknown/hard_negative/ali_0.wav",
        label="unknown",
        class_index=2,
        phrase_id=None,
        text="unknown",
        split="train",
        duration=2.0,
        speaker="ali",
        negative_type="hard_negative",
    )
    path = save_manifest([record], tmp_path / "manifest.csv")
    assert load_manifest(path) == [record]

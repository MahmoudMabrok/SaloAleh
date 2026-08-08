"""Tests for the dataset quality report.

The report's job is to stop a prototype dataset being mistaken for a production
one, so what is worth pinning down is that its headline follows the *weakest*
part of the dataset rather than the total, and that each warning fires on the
condition it names.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import ClassesConfig, QualityConfig
from src.quality import ClassQuality, DatasetQualityReport
from src.speakers import SOURCE_FILENAME, SOURCE_NONE, speaker_report


def speakers(count: int, known: bool = True):
    if not known:
        return speaker_report([(None, "006", "train")] * 10, SOURCE_NONE)
    return speaker_report(
        [(f"sp{index}", "006", "train") for index in range(count)], SOURCE_FILENAME
    )


def report(
    *,
    target_clips=200,
    unknown_clips=150,
    speaker_count=20,
    negatives=None,
    second_target=None,
    known_speakers=True,
):
    classes = [
        ClassQuality("006", True, target_clips, speaker_count, 400.0, 2.0, 2.0, 1.0, 3.0)
    ]
    if second_target is not None:
        classes.append(
            ClassQuality("007", True, second_target, speaker_count, 300.0, 2.0, 2.0, 1.0, 3.0)
        )
    classes.append(
        ClassQuality("unknown", False, unknown_clips, speaker_count, 300.0, 2.0, 2.0, 1.0, 3.0)
    )
    return DatasetQualityReport(
        per_class=classes,
        negative_counts=negatives if negatives is not None else {"hard_negative": 60},
        speakers=speakers(speaker_count, known_speakers),
        config=QualityConfig(),
        expected_negative_types=list(ClassesConfig().negative_types),
    )


def warned(quality, needle):
    return any(needle in note for note in quality.warnings())


# ---------------------------------------------------------------------------
# Scale
# ---------------------------------------------------------------------------
def test_a_healthy_dataset_reads_production_scale():
    assert report().scale == "production"


def test_the_thinnest_phrase_decides_the_scale():
    """500 recordings of one phrase and 40 of another is not a 500-clip dataset."""
    assert report(target_clips=500, second_target=40).scale == "prototype"


def test_many_recordings_from_few_speakers_is_not_production_scale():
    """500 recordings from three people is still three people."""
    assert report(target_clips=500, speaker_count=3).scale == "prototype"


def test_prototype_scale_is_reported_as_such():
    quality = report(target_clips=35, speaker_count=2)
    assert quality.scale == "prototype"
    assert "PROTOTYPE" in quality.summary()


# ---------------------------------------------------------------------------
# Warnings
# ---------------------------------------------------------------------------
def test_thin_classes_are_named_with_their_counts():
    quality = report(target_clips=35)
    assert warned(quality, "006 (35)")


def test_too_few_speakers_warns_about_speaker_variety():
    assert warned(report(speaker_count=4), "highest-value thing to collect")


def test_too_few_negatives_warns_about_the_false_activation_rate():
    quality = report(target_clips=200, unknown_clips=10)
    assert warned(quality, "FA/hour figure")


def test_class_imbalance_is_flagged_with_the_ratio():
    quality = report(target_clips=400, second_target=50)
    assert quality.imbalance == pytest.approx(8.0)
    assert warned(quality, "class imbalance")


def test_a_missing_negative_category_is_named():
    quality = report(negatives={"noise": 40})
    assert warned(quality, "hard_negative")


def test_a_thin_negative_category_is_called_an_anecdote():
    quality = report(negatives={"hard_negative": 3})
    assert warned(quality, "anecdotes rather than measurements")


def test_no_speaker_information_produces_the_loud_warning():
    quality = report(known_speakers=False)
    assert warned(quality, "NOT speaker-independent")


# ---------------------------------------------------------------------------
# Aggregates and persistence
# ---------------------------------------------------------------------------
def test_target_and_unknown_clips_are_counted_separately():
    quality = report(target_clips=100, second_target=80, unknown_clips=90)
    assert quality.target_clips == 180
    assert quality.unknown_clips == 90
    assert quality.total_clips == 270


def test_the_report_saves_json(tmp_path):
    import json

    path = report().save(tmp_path)
    payload = json.loads(path.read_text(encoding="utf-8"))
    assert payload["scale"] == "production"
    assert payload["per_class"]
    assert "warnings" in payload

"""Tests for the production-readiness verdict.

This report exists to stop one specific mistake: reading a high clip accuracy as
a finished model. So the properties worth pinning down are not the individual
checks but the *shape of the verdict* - that a check nobody measured never counts
as a pass, that clip accuracy alone can never produce READY, and that a real
failure outranks an unmeasured one.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import QualityConfig, ReadinessConfig
from src.metrics import EvaluationResult
from src.quality import ClassQuality, DatasetQualityReport
from src.readiness import EXPERIMENTAL, NOT_READY, READY, build_readiness_report
from src.speakers import SOURCE_FILENAME, SOURCE_NONE, speaker_report
from src.streaming_eval import CalibrationResult, StreamingMetrics


def speakers(count=12, leaking=False):
    items = [(f"sp{index}", "006", "train") for index in range(count)]
    items.append(("sp0", "006", "test") if leaking else (f"sp{count}", "006", "test"))
    return speaker_report(items, SOURCE_FILENAME)


def quality(clips=200, hard_negatives=50):
    return DatasetQualityReport(
        per_class=[
            ClassQuality("006", True, clips, 12, 400.0, 2.0, 2.0, 1.0, 3.0),
            ClassQuality("unknown", False, clips, 12, 400.0, 2.0, 2.0, 1.0, 3.0),
        ],
        negative_counts={"hard_negative": hard_negatives} if hard_negatives else {},
        speakers=speakers(),
        config=QualityConfig(),
    )


def clip_result(hard_negative_accepted=0):
    names = ["006", "unknown"]
    y_true = np.array([0] * 10 + [1] * 10)
    y_prob = np.zeros((20, 2), dtype=np.float32)
    y_prob[:10] = (0.95, 0.05)
    y_prob[10:] = (0.05, 0.95)
    for position in range(hard_negative_accepted):
        y_prob[10 + position] = (0.95, 0.05)
    return EvaluationResult(
        class_names=names,
        y_true=y_true,
        y_pred=y_prob.argmax(axis=1).astype(np.int32),
        y_prob=y_prob,
        negative_types=[""] * 10 + ["hard_negative"] * 10,
    )


def streaming_metrics(false_positives=0, expected=50, true_positives=49, hours=1.0):
    return StreamingMetrics(
        expected=expected,
        detected=true_positives + false_positives,
        true_positives=true_positives,
        false_positives=false_positives,
        false_negatives=expected - true_positives,
        duplicates=0,
        duration_seconds=hours * 3600.0,
        negative_duration_seconds=hours * 1800.0,
        negative_false_positives=false_positives,
    )


def calibration(satisfied=True, threshold=0.85, reason=""):
    return CalibrationResult(
        threshold=threshold,
        satisfied=satisfied,
        policy="min_threshold_within_budget",
        target_false_activations_per_hour=0.5,
        reason=reason,
    )


def stress(false_events=0, hours=1.0):
    from src.streaming_eval import NegativeStressResult

    return NegativeStressResult(
        files=3,
        duration_seconds=hours * 3600.0,
        windows=14400,
        false_events=false_events,
    )


def full_report(**overrides):
    arguments = dict(
        speakers=speakers(),
        quality=quality(),
        clip_result=clip_result(),
        streaming=streaming_metrics(),
        stress=stress(),
        calibration=calibration(),
        tflite_accuracy_drop=0.005,
        tflite_extra_false_activations=0.0,
    )
    arguments.update(overrides)
    return build_readiness_report(ReadinessConfig(), **arguments)


# ---------------------------------------------------------------------------
# The verdict
# ---------------------------------------------------------------------------
def test_everything_measured_and_passing_reaches_ready():
    report = full_report()
    assert report.failures == []
    assert report.unmeasured == []
    assert report.status == READY


def test_clip_accuracy_alone_never_reaches_ready():
    """The mistake this whole report exists to prevent."""
    report = build_readiness_report(
        ReadinessConfig(), speakers=speakers(), quality=quality(), clip_result=clip_result()
    )
    assert report.status == EXPERIMENTAL
    assert any("streaming" in check.name for check in report.unmeasured)


def test_no_streaming_evaluation_is_experimental_not_ready():
    report = full_report(streaming=None, stress=None, calibration=None)
    assert report.status == EXPERIMENTAL


def test_a_measured_failure_outranks_an_unmeasured_check():
    report = full_report(streaming=None, stress=None, calibration=None, speakers=speakers(count=2))
    assert report.status == NOT_READY


def test_speaker_leakage_fails():
    report = full_report(speakers=speakers(leaking=True))
    assert report.status == NOT_READY
    assert any(check.name == "speaker leakage" for check in report.failures)


def test_unknown_speakers_cannot_pass_the_leakage_check():
    """No speaker information means leakage is unruled-out, not ruled out."""
    report = full_report(speakers=speaker_report([(None, "006", "train")], SOURCE_NONE))
    leakage = next(check for check in report.checks if check.name == "speaker leakage")
    assert not leakage.measured
    assert report.status != READY


def test_false_activations_over_budget_fails():
    report = full_report(streaming=streaming_metrics(false_positives=3))
    assert report.status == NOT_READY
    assert any("false activations" in check.name for check in report.failures)


def test_too_little_streaming_audio_is_unmeasured_rather_than_passed():
    """Three minutes of audio cannot measure a budget of 0.5 events per hour."""
    report = full_report(streaming=streaming_metrics(hours=0.05), stress=None)
    names = {check.name for check in report.unmeasured}
    assert "event recall" in names and "false activations / hour" in names
    assert report.status == EXPERIMENTAL


def test_hard_negative_false_positives_fail():
    report = full_report(clip_result=clip_result(hard_negative_accepted=3))
    assert report.status == NOT_READY
    assert any("hard-negative" in check.name for check in report.failures)


def test_a_calibration_that_found_no_viable_threshold_fails():
    report = full_report(
        calibration=calibration(satisfied=False, threshold=None, reason="NO threshold")
    )
    assert report.status == NOT_READY


def test_a_threshold_that_meets_the_budget_by_never_firing_fails():
    report = full_report(
        calibration=calibration(satisfied=True, reason="recall there is only 3.0%")
    )
    assert report.status == NOT_READY


def test_int8_adding_false_activations_fails():
    report = full_report(tflite_extra_false_activations=0.4)
    assert report.status == NOT_READY
    assert any("INT8 extra" in check.name for check in report.failures)


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------
def test_every_check_prints_the_threshold_it_was_compared_against():
    text = full_report().summary()
    assert "limit" in text
    assert "Status: " in text
    assert "PRODUCTION READINESS" in text


def test_recommended_thresholds_are_carried_into_the_report():
    report = full_report(per_class_thresholds={"006": 0.82, "007": 0.9})
    assert report.recommended_thresholds["006"] == 0.82
    assert report.recommended_thresholds["global"] == 0.85
    assert "0.82" in report.summary()


def test_the_report_saves_valid_json(tmp_path):
    path = full_report(streaming=streaming_metrics(hours=0.0)).save(tmp_path)
    import json

    payload = json.loads(path.read_text(encoding="utf-8"))
    assert payload["status"] in (READY, EXPERIMENTAL, NOT_READY)
    assert payload["checks"]

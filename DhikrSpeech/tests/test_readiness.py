"""Tests for the per-target readiness verdict.

The property that matters most is negative: a target must not reach
``READY FOR DEVICE TEST`` because its clip accuracy was high. Anything
unmeasured caps the status at ``EXPERIMENTAL``.
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.config import ReadinessConfig
from src.readiness import EXPERIMENTAL, NOT_READY, READY, assess_readiness
from src.splitting import SpeakerIsolationReport
from src.streaming_eval import CalibrationResult, EventMetrics, NegativeClipReport, ThresholdRow
from src.target_export import QuantizationReport, VariantComparison
from src.targets import TargetDatasetReport


def dataset(**kwargs) -> TargetDatasetReport:
    defaults = dict(
        target_id=7,
        target_text="سبحان الله العظيم وبحمده",
        positive_clips=350,
        positive_speakers=24,
        positive_seconds=700.0,
        negative_clips=900,
        negative_speakers=40,
        negative_seconds=1800.0,
        by_negative_type={"hard_negative": {"clips": 120.0, "seconds": 240.0}},
        speakers_known=True,
    )
    defaults.update(kwargs)
    return TargetDatasetReport(**defaults)


def streaming(**kwargs) -> EventMetrics:
    defaults = dict(
        expected=100,
        detected=99,
        correct=98,
        missed=2,
        false_events=1,
        duplicates=0,
        duration_seconds=5 * 3600.0,
    )
    defaults.update(kwargs)
    return EventMetrics(**defaults)


def hard_negatives(rate: float = 0.007) -> NegativeClipReport:
    clips = 1000
    false_positives = int(round(rate * clips))
    return NegativeClipReport(
        threshold=0.8,
        total=clips,
        false_positives=false_positives,
        by_type={
            "hard_negative": {
                "clips": float(clips),
                "false_positives": float(false_positives),
                "false_positive_rate": rate,
                "max_score": 0.6,
            }
        },
    )


def calibration(satisfied: bool = True) -> CalibrationResult:
    row = ThresholdRow(activation=0.85, release=0.51, metrics=streaming())
    return CalibrationResult(
        rows=[row],
        activation=0.85 if satisfied else None,
        release=0.51 if satisfied else None,
        target_fa_per_hour=0.5,
        reason="" if satisfied else "No threshold stays inside the budget.",
    )


def quantization(accepted: bool = True) -> QuantizationReport:
    return QuantizationReport(
        comparisons=[
            VariantComparison(
                variant="int8",
                drifts=[],
                reference_events=streaming(),
                candidate_events=streaming(false_events=1 if accepted else 40),
                tolerance=0.05,
                max_fa_increase=0.10,
                size_bytes=44_000,
            )
        ]
    )


def full(**overrides):
    kwargs = dict(
        config=ReadinessConfig(),
        target_id=7,
        target_text="سبحان الله العظيم وبحمده",
        dataset=dataset(),
        isolation=SpeakerIsolationReport(),
        streaming=streaming(),
        hard_negatives=hard_negatives(),
        quantization=quantization(),
        calibration=calibration(),
    )
    kwargs.update(overrides)
    return assess_readiness(**kwargs)


# ---------------------------------------------------------------------------
# The happy path, and the ways out of it
# ---------------------------------------------------------------------------
def test_a_fully_measured_target_is_ready() -> None:
    report = full()
    assert report.status == READY
    assert not report.failures and not report.unmeasured
    assert "READY FOR DEVICE TEST" in report.summary()


def test_a_target_with_no_streaming_evaluation_is_never_ready() -> None:
    """The central rule: clip accuracy cannot buy a release."""
    report = full(streaming=None)
    assert report.status == EXPERIMENTAL
    names = {check.name for check in report.unmeasured}
    assert "false activations/hour" in names


def test_too_many_false_activations_is_not_ready() -> None:
    report = full(streaming=streaming(false_events=20))
    assert report.status == NOT_READY
    assert "false activations/hour" in {check.name for check in report.failures}


def test_low_event_recall_is_not_ready() -> None:
    report = full(streaming=streaming(expected=100, detected=60, correct=60, missed=40))
    assert report.status == NOT_READY
    assert "event recall" in {check.name for check in report.failures}


def test_speaker_leakage_is_not_ready() -> None:
    leaking = SpeakerIsolationReport(train_val=["spk01"], train_test=[])
    report = full(isolation=leaking)
    assert report.status == NOT_READY
    assert "speaker leakage" in {check.name for check in report.failures}


def test_unknown_speakers_cap_the_status() -> None:
    report = full(dataset=dataset(speakers_known=False))
    assert report.status == EXPERIMENTAL
    detail = next(c.detail for c in report.checks if c.name == "positive speakers")
    assert "NOT SPEAKER-INDEPENDENT" in detail


def test_too_few_speakers_is_not_ready() -> None:
    report = full(dataset=dataset(positive_speakers=4))
    assert report.status == NOT_READY
    assert "positive speakers" in {check.name for check in report.failures}


def test_a_prototype_dataset_is_not_ready() -> None:
    report = full(dataset=dataset(positive_clips=40, positive_speakers=3))
    assert report.status == NOT_READY


def test_missing_hard_negatives_only_warn() -> None:
    report = full(dataset=dataset(by_negative_type={}))
    assert report.status == EXPERIMENTAL
    assert "hard negatives" in {check.name for check in report.unmeasured}


def test_a_high_hard_negative_fp_rate_is_not_ready() -> None:
    report = full(hard_negatives=hard_negatives(rate=0.30))
    assert report.status == NOT_READY
    assert "hard-negative FP rate" in {check.name for check in report.failures}


def test_failed_calibration_is_not_ready() -> None:
    report = full(calibration=calibration(satisfied=False))
    assert report.status == NOT_READY
    assert "threshold calibration" in {check.name for check in report.failures}


def test_int8_that_adds_false_activations_is_not_ready() -> None:
    report = full(quantization=quantization(accepted=False))
    assert report.status == NOT_READY
    assert "INT8" in {check.name for check in report.failures}


def test_an_empty_assessment_is_experimental_not_ready() -> None:
    report = assess_readiness(ReadinessConfig(), target_id=7)
    assert report.status == EXPERIMENTAL
    assert len(report.unmeasured) == len(report.checks)


def test_the_report_serialises_and_reads() -> None:
    payload = full().to_dict()
    assert payload["status"] == READY
    assert payload["target_id"] == 7
    assert any(check["name"] == "event precision" for check in payload["checks"])


def test_criteria_are_configurable() -> None:
    """These are release policy, so a project may legitimately move them."""
    strict = ReadinessConfig(max_false_activations_per_hour=0.0)
    report = full(config=strict)
    assert report.status == NOT_READY

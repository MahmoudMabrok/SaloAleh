"""Per-target release readiness.

The question this answers is not "is the model accurate?" but "would I let this
count someone's dhikr?". Those are different questions, and the first one is
answerable from a validation split while the second is not.

So the status is derived from the *streaming* numbers and the dataset that
produced them, and a target with no streaming evaluation can never reach
``READY FOR DEVICE TEST`` no matter how high its clip accuracy is - the check
comes back ``unknown``, which caps the status at ``EXPERIMENTAL``. That is the
whole design (requirement 35).

    NOT READY               something measured is outside the release criteria
    EXPERIMENTAL            nothing has failed, but something is unmeasured or
                            the dataset is too small for the numbers to mean much
    READY FOR DEVICE TEST   every criterion measured and met - go and try it on a
                            phone, which is still the last word

The thresholds in :class:`src.config.ReadinessConfig` are project release
criteria, not scientific constants. They live in ``config.yaml`` so that raising
one is a visible, reviewable decision.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Dict, List, Optional

import numpy as np

from .config import ReadinessConfig
from .speakers import SpeakerReport
from .streaming_eval import (
    CalibrationResult,
    CountAccuracy,
    EventMetrics,
    NegativeClipReport,
)
from .targets import TargetDatasetReport
from .target_export import QuantizationReport

LOGGER = logging.getLogger(__name__)

__all__ = [
    "READY",
    "EXPERIMENTAL",
    "NOT_READY",
    "ReadinessCheck",
    "ReadinessReport",
    "assess_readiness",
]

NOT_READY = "NOT READY"
EXPERIMENTAL = "EXPERIMENTAL"
READY = "READY FOR DEVICE TEST"

PASS = "pass"
FAIL = "fail"
WARN = "warn"
UNKNOWN = "unknown"

_SEVERITY = {PASS: 0, WARN: 1, UNKNOWN: 1, FAIL: 2}
_MARKS = {PASS: "PASS", FAIL: "FAIL", WARN: "WARN", UNKNOWN: "  ? "}


@dataclass
class ReadinessCheck:
    """One criterion, its verdict and the measurement behind it."""

    name: str
    status: str
    detail: str
    value: Optional[float] = None
    threshold: Optional[float] = None
    group: str = ""

    def to_dict(self) -> Dict[str, object]:
        return {
            "name": self.name,
            "group": self.group,
            "status": self.status,
            "detail": self.detail,
            "value": None if self.value is None or not np.isfinite(self.value) else round(float(self.value), 4),
            "threshold": self.threshold,
        }


@dataclass
class ReadinessReport:
    """All criteria for one target, and the status they add up to."""

    target_id: Optional[int]
    target_text: str = ""
    checks: List[ReadinessCheck] = field(default_factory=list)

    @property
    def failures(self) -> List[ReadinessCheck]:
        return [check for check in self.checks if check.status == FAIL]

    @property
    def unmeasured(self) -> List[ReadinessCheck]:
        return [check for check in self.checks if check.status in (UNKNOWN, WARN)]

    @property
    def status(self) -> str:
        if any(check.status == FAIL for check in self.checks):
            return NOT_READY
        if any(check.status in (UNKNOWN, WARN) for check in self.checks):
            return EXPERIMENTAL
        return READY if self.checks else EXPERIMENTAL

    def to_dict(self) -> Dict[str, object]:
        return {
            "target_id": self.target_id,
            "target_text": self.target_text,
            "status": self.status,
            "failures": [check.name for check in self.failures],
            "unmeasured": [check.name for check in self.unmeasured],
            "checks": [check.to_dict() for check in self.checks],
        }

    def summary(self) -> str:
        header = f"{self.target_id:03d}" if self.target_id else "?"
        lines = [f"Target: {header}  {self.target_text}".rstrip(), ""]
        group = None
        for check in self.checks:
            if check.group != group:
                group = check.group
                lines.append(f"{group}:")
            lines.append(f"  [{_MARKS[check.status]}] {check.name:<28} {check.detail}")
        lines.append("")
        lines.append(f"STATUS: {self.status}")
        if self.status == NOT_READY:
            lines.append(
                "\nSomething measured is outside the release criteria - see the FAIL "
                "rows. Fixing these is a data job before it is a modelling job: the "
                "usual answer is more speakers and more hard negatives of the kind the "
                "streaming report named."
            )
        elif self.status == EXPERIMENTAL:
            lines.append(
                "\nNothing has failed, but something is unmeasured or the dataset is too "
                "small for these numbers to carry weight. A high validation accuracy is "
                "not a substitute for a streaming evaluation - collect the missing "
                "measurements (the '?' rows) before shipping."
            )
        else:
            lines.append(
                "\nEvery criterion was measured and met. That is a licence to test on a "
                "real device, not a release: on-device latency, microphone response and "
                "real rooms are still unmeasured here."
            )
        return "\n".join(lines)


def _check(
    name: str,
    group: str,
    value: Optional[float],
    threshold: Optional[float],
    passed: Optional[bool],
    detail: str,
) -> ReadinessCheck:
    if passed is None:
        status = UNKNOWN
    else:
        status = PASS if passed else FAIL
    return ReadinessCheck(
        name=name, status=status, detail=detail, value=value, threshold=threshold, group=group
    )


def assess_readiness(
    config: ReadinessConfig,
    target_id: Optional[int] = None,
    target_text: str = "",
    dataset: Optional[TargetDatasetReport] = None,
    isolation: Optional[SpeakerReport] = None,
    streaming: Optional[EventMetrics] = None,
    counts: Optional["CountAccuracy"] = None,
    hard_negatives: Optional[NegativeClipReport] = None,
    quantization: Optional[QuantizationReport] = None,
    calibration: Optional[CalibrationResult] = None,
) -> ReadinessReport:
    """Score one target against the release criteria.

    Every argument is optional and a missing one produces an ``unknown`` check
    rather than being skipped. That is deliberate: a criterion nobody measured is
    not a criterion that passed, and silently dropping it is how "READY" comes to
    mean "the parts we happened to run were fine".
    """
    checks: List[ReadinessCheck] = []

    # -- dataset ------------------------------------------------------------
    if dataset is None:
        checks.append(_check("positive recordings", "Dataset", None, None, None, "not measured"))
        checks.append(_check("positive speakers", "Dataset", None, None, None, "not measured"))
    else:
        checks.append(
            _check(
                "positive recordings",
                "Dataset",
                float(dataset.positive_clips),
                float(config.min_positive_clips),
                dataset.positive_clips >= config.min_positive_clips,
                f"{dataset.positive_clips} (minimum {config.min_positive_clips}, "
                f"{config.recommended_positive_clips}+ recommended)",
            )
        )
        if not dataset.speakers_known:
            checks.append(
                ReadinessCheck(
                    name="positive speakers",
                    status=UNKNOWN,
                    detail=(
                        "speaker ids unresolved - EVALUATION IS NOT SPEAKER-INDEPENDENT, "
                        "so every number below is optimistic"
                    ),
                    group="Dataset",
                )
            )
        else:
            checks.append(
                _check(
                    "positive speakers",
                    "Dataset",
                    float(dataset.positive_speakers),
                    float(config.min_positive_speakers),
                    dataset.positive_speakers >= config.min_positive_speakers,
                    f"{dataset.positive_speakers} (minimum {config.min_positive_speakers}, "
                    f"{config.recommended_positive_speakers}+ recommended)",
                )
            )
        checks.append(
            ReadinessCheck(
                name="hard negatives",
                status=PASS if dataset.hard_negative_clips else WARN,
                detail=(
                    f"{dataset.hard_negative_clips} clips"
                    if dataset.hard_negative_clips
                    else "none - the model has never been shown a near-miss of its own phrase"
                ),
                value=float(dataset.hard_negative_clips),
                group="Dataset",
            )
        )

    # -- speaker isolation --------------------------------------------------
    if isolation is None:
        checks.append(_check("speaker leakage", "Dataset", None, None, None, "not verified"))
    else:
        leaked = [leak.speaker for leak in isolation.leaks]
        if not isolation.known:
            checks.append(
                ReadinessCheck(
                    name="speaker leakage",
                    status=UNKNOWN,
                    detail=(
                        "no recording could be traced to a speaker, so the split cannot "
                        "be shown to be speaker-safe - EVALUATION IS NOT "
                        "SPEAKER-INDEPENDENT"
                    ),
                    group="Dataset",
                )
            )
        else:
            checks.append(
                _check(
                    "speaker leakage",
                    "Dataset",
                    float(len(leaked)),
                    float(config.max_speaker_leakage),
                    len(leaked) <= config.max_speaker_leakage,
                    "no speaker crosses a split"
                    if not leaked
                    else f"{len(leaked)} speaker(s) in more than one split: "
                    f"{', '.join(leaked[:5])}",
                )
            )

    # -- streaming ----------------------------------------------------------
    measured = streaming is not None and streaming.duration_seconds > 0
    if not measured:
        detail = "no streaming evaluation - clip accuracy cannot stand in for this"
        if counts is not None and counts.recordings:
            # Count-only sessions measure whether the counter reaches the right
            # number, which is what the user sees - but they cannot say whether a
            # detection was real, so precision and FA/hour stay unmeasured.
            checks.append(
                _check(
                    "count accuracy",
                    "Streaming",
                    counts.count_accuracy,
                    config.min_event_recall,
                    bool(np.isfinite(counts.count_accuracy))
                    and counts.count_accuracy >= config.min_event_recall,
                    f"{counts.count_accuracy:.1%} over {counts.recordings} counted "
                    f"session(s) ({counts.detected}/{counts.expected} counted)",
                )
            )
            detail = (
                "only count-only sessions - they say whether the counter reaches the "
                "right number, not whether it stays quiet on other audio"
            )
        for name in ("event precision", "event recall", "false activations/hour", "duplicate rate"):
            checks.append(_check(name, "Streaming", None, None, None, detail))
    else:
        checks.append(
            _check(
                "event precision",
                "Streaming",
                streaming.precision,
                config.min_event_precision,
                bool(np.isfinite(streaming.precision))
                and streaming.precision >= config.min_event_precision,
                f"{streaming.precision:.1%} (>= {config.min_event_precision:.0%})"
                if np.isfinite(streaming.precision)
                else "no events detected",
            )
        )
        checks.append(
            _check(
                "event recall",
                "Streaming",
                streaming.recall,
                config.min_event_recall,
                bool(np.isfinite(streaming.recall))
                and streaming.recall >= config.min_event_recall,
                f"{streaming.recall:.1%} (>= {config.min_event_recall:.0%})"
                if np.isfinite(streaming.recall)
                else "no annotated repetitions",
            )
        )
        fa = streaming.false_activations_per_hour
        checks.append(
            _check(
                "false activations/hour",
                "Streaming",
                fa,
                config.max_false_activations_per_hour,
                bool(np.isfinite(fa)) and fa <= config.max_false_activations_per_hour,
                f"{fa:.2f} over {streaming.duration_seconds / 3600.0:.2f} h "
                f"(<= {config.max_false_activations_per_hour:.2f})",
            )
        )
        duplicates = streaming.duplicate_rate
        checks.append(
            _check(
                "duplicate rate",
                "Streaming",
                duplicates,
                config.max_duplicate_rate,
                not np.isfinite(duplicates) or duplicates <= config.max_duplicate_rate,
                f"{duplicates:.1%} (<= {config.max_duplicate_rate:.0%})"
                if np.isfinite(duplicates)
                else "no annotated repetitions",
            )
        )

    # -- hard negatives -----------------------------------------------------
    if hard_negatives is None:
        checks.append(
            _check("hard-negative FP rate", "Hard negatives", None, None, None, "not measured")
        )
    else:
        rate = hard_negatives.hard_negative_fp_rate
        if not np.isfinite(rate):
            checks.append(
                ReadinessCheck(
                    name="hard-negative FP rate",
                    status=UNKNOWN,
                    detail="no hard-negative clips to measure on",
                    group="Hard negatives",
                )
            )
        else:
            checks.append(
                _check(
                    "hard-negative FP rate",
                    "Hard negatives",
                    rate,
                    config.max_hard_negative_fp_rate,
                    rate <= config.max_hard_negative_fp_rate,
                    f"{rate:.1%} of {hard_negatives.total} negative clips "
                    f"(<= {config.max_hard_negative_fp_rate:.0%})",
                )
            )

    # -- calibration --------------------------------------------------------
    if calibration is None:
        checks.append(
            _check("threshold calibration", "Calibration", None, None, None, "not run")
        )
    else:
        checks.append(
            _check(
                "threshold calibration",
                "Calibration",
                calibration.activation,
                calibration.target_fa_per_hour,
                calibration.satisfied,
                f"activation {calibration.activation:.2f} / release {calibration.release:.2f}"
                if calibration.satisfied
                else "no threshold meets the false-activation budget",
            )
        )

    # -- quantisation -------------------------------------------------------
    if quantization is None:
        checks.append(_check("INT8", "Quantisation", None, None, None, "not verified"))
    else:
        recommended = quantization.recommended()
        int8 = next(
            (item for item in quantization.comparisons if item.variant == "int8"), None
        )
        if int8 is None:
            checks.append(
                ReadinessCheck(
                    name="INT8",
                    status=UNKNOWN,
                    detail="no INT8 variant was exported",
                    group="Quantisation",
                )
            )
        else:
            checks.append(
                _check(
                    "INT8",
                    "Quantisation",
                    int8.max_delta,
                    config.max_int8_probability_drift,
                    int8.accepted,
                    f"drift {int8.max_delta:.4f}, FA/h "
                    + (
                        f"{int8.fa_per_hour_delta:+.2f}"
                        if np.isfinite(int8.fa_per_hour_delta)
                        else "n/a"
                    )
                    + (
                        ""
                        if int8.accepted
                        else " - " + "; ".join(int8.reasons()[:2])
                    ),
                )
            )
        if recommended is not None and recommended.variant != "int8":
            checks.append(
                ReadinessCheck(
                    name="production variant",
                    status=WARN,
                    detail=f"{recommended.variant} recommended instead of INT8",
                    group="Quantisation",
                )
            )

    checks.sort(key=lambda check: (check.group, -_SEVERITY[check.status]))
    return ReadinessReport(target_id=target_id, target_text=target_text, checks=checks)

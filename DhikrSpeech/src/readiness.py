"""Production readiness: one report, transparent checks, an honest verdict.

The failure this guards against is a specific and very easy one: a 96% test
accuracy on a few dozen clips reads like a finished model. It is not. It says
nothing about unseen speakers if the split leaked, nothing about false counting
because clips have no hours, and nothing about the phone because the quantised
model was never compared on the audio that matters.

So the verdict here is never derived from clip accuracy. Every check states the
measurement, the configured threshold it was compared against, and what to do
when it fails; the status is the worst of them:

``NOT READY``
    A check failed on something measured. Fix it.
``EXPERIMENTAL``
    Nothing measured has failed, but something that decides the answer was not
    measured at all - too few speakers, no streaming recordings, no hard
    negatives. This is the honest verdict for a prototype dataset, and the one
    this project's own dataset earns today.
``READY FOR DEVICE TEST``
    Every check passed on enough data to mean something. Not "ready to ship" -
    the next step is a real phone, in a real room, with a real user.
"""

from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Union

import numpy as np

from .config import ReadinessConfig

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "EXPERIMENTAL",
    "NOT_READY",
    "READY",
    "ReadinessCheck",
    "ReadinessReport",
    "build_readiness_report",
]

NOT_READY = "NOT READY"
EXPERIMENTAL = "EXPERIMENTAL"
READY = "READY FOR DEVICE TEST"

PASS = "PASS"
FAIL = "FAIL"
UNKNOWN = "NOT MEASURED"

_RANK = {PASS: 0, UNKNOWN: 1, FAIL: 2}


@dataclass
class ReadinessCheck:
    """One check: what was measured, against what, and what to do about it."""

    name: str
    status: str
    value: Optional[float] = None
    threshold: Optional[float] = None
    detail: str = ""
    advice: str = ""
    section: str = "general"

    @property
    def failed(self) -> bool:
        return self.status == FAIL

    @property
    def measured(self) -> bool:
        return self.status in (PASS, FAIL)

    def line(self) -> str:
        marker = {PASS: "PASS", FAIL: "FAIL", UNKNOWN: "  ? "}[self.status]
        value = "" if self.value is None else f"{self.value:.4g}"
        against = "" if self.threshold is None else f" (limit {self.threshold:g})"
        text = f"  [{marker}] {self.name:<34}{value}{against}"
        if self.detail:
            text += f"\n         {self.detail}"
        if self.status != PASS and self.advice:
            text += f"\n         -> {self.advice}"
        return text


def _check(
    name: str,
    section: str,
    value: Optional[float],
    threshold: Optional[float],
    ok: Optional[bool],
    detail: str = "",
    advice: str = "",
) -> ReadinessCheck:
    status = UNKNOWN if ok is None else (PASS if ok else FAIL)
    return ReadinessCheck(
        name=name,
        status=status,
        value=value,
        threshold=threshold,
        detail=detail,
        advice=advice,
        section=section,
    )


@dataclass
class ReadinessReport:
    """Every check, plus the verdict and the numbers behind it."""

    checks: List[ReadinessCheck] = field(default_factory=list)
    config: Optional[ReadinessConfig] = None
    headline: Dict[str, object] = field(default_factory=dict)
    recommended_thresholds: Dict[str, float] = field(default_factory=dict)

    @property
    def failures(self) -> List[ReadinessCheck]:
        return [check for check in self.checks if check.status == FAIL]

    @property
    def unmeasured(self) -> List[ReadinessCheck]:
        return [check for check in self.checks if check.status == UNKNOWN]

    @property
    def status(self) -> str:
        if self.failures:
            return NOT_READY
        if self.unmeasured:
            return EXPERIMENTAL
        return READY

    def to_dict(self) -> Dict[str, object]:
        return {
            "status": self.status,
            "headline": self.headline,
            "recommended_thresholds": dict(self.recommended_thresholds),
            "checks": [asdict(check) for check in self.checks],
            "thresholds": asdict(self.config) if self.config else {},
        }

    def save(self, directory: PathLike, name: str = "production_readiness") -> Path:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)
        destination = target / f"{name}.json"
        destination.write_text(
            json.dumps(_jsonable(self.to_dict()), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        LOGGER.info("readiness report written to %s", destination)
        return destination

    def summary(self) -> str:
        lines = ["PRODUCTION READINESS", "=" * 60]
        for section in ("Dataset", "Clip evaluation", "Streaming", "Hard negatives", "TFLite"):
            checks = [check for check in self.checks if check.section == section]
            if not checks:
                continue
            lines.append("")
            lines.append(section)
            lines.extend(check.line() for check in checks)

        if self.recommended_thresholds:
            lines.append("")
            lines.append("Recommended threshold:")
            for label, value in sorted(self.recommended_thresholds.items()):
                lines.append(f"  {label:<10}{value:.2f}")

        lines.append("")
        lines.append(f"Status: {self.status}")
        if self.status == NOT_READY:
            lines.append(
                f"  {len(self.failures)} check(s) failed. Each one above says what "
                f"to do; none of them is fixed by retuning the threshold."
            )
        elif self.status == EXPERIMENTAL:
            missing = ", ".join(check.name for check in self.unmeasured[:4])
            lines.append(
                f"  Nothing measured has failed, but {len(self.unmeasured)} check(s) "
                f"could not be measured at all ({missing}"
                f"{' ...' if len(self.unmeasured) > 4 else ''}). Insufficient "
                f"speaker/data diversity or missing streaming recordings - the "
                f"numbers above describe this dataset, not this model in the app."
            )
        else:
            lines.append(
                "  Every check passed on enough data to mean something. The next "
                "step is a real device in a real room, not a release: desktop "
                "latency is an estimate and no phone microphone has been in the loop."
            )
        return "\n".join(lines)


def _jsonable(value):
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, float) and not np.isfinite(value):
        return None
    if isinstance(value, (np.floating, np.integer)):
        return _jsonable(value.item())
    return value


def build_readiness_report(
    config: ReadinessConfig,
    speakers=None,
    quality=None,
    clip_result=None,
    streaming=None,
    stress=None,
    calibration=None,
    per_class_thresholds: Optional[Dict[str, float]] = None,
    tflite_verifications: Optional[Sequence] = None,
    tflite_accuracy_drop: Optional[float] = None,
    tflite_extra_false_activations: Optional[float] = None,
) -> ReadinessReport:
    """Assemble the report from whatever stages have actually run.

    Every argument is optional, and an absent one produces a ``NOT MEASURED``
    check rather than a silent pass - that asymmetry is the whole point. A model
    with no streaming evaluation has not been shown to count correctly; it has
    only failed to be shown wrong.
    """
    checks: List[ReadinessCheck] = []
    headline: Dict[str, object] = {}

    # -- dataset ------------------------------------------------------------
    if speakers is not None:
        leaks = len(speakers.leaks)
        checks.append(
            _check(
                "speaker leakage",
                "Dataset",
                float(leaks),
                float(config.max_speaker_leaks),
                leaks <= config.max_speaker_leaks if speakers.known else None,
                detail=(
                    ""
                    if speakers.known
                    else "no speaker information - leakage cannot be ruled out"
                ),
                advice=(
                    "re-run the split (assign_splits groups by speaker) or add "
                    "speaker ids: speakers.csv, a per-speaker subfolder, or a "
                    "filename convention plus split.speaker.regex"
                ),
            )
        )
        checks.append(
            _check(
                "speakers",
                "Dataset",
                float(speakers.num_speakers) if speakers.known else None,
                float(config.min_speakers),
                (speakers.num_speakers >= config.min_speakers) if speakers.known else None,
                advice=(
                    "more speakers is the highest-value thing to collect: the model "
                    "generalises across voices only as far as it has heard them"
                ),
            )
        )
        headline["speakers"] = speakers.num_speakers if speakers.known else None

    if quality is not None:
        targets = quality.targets
        smallest = min((item.clips for item in targets), default=0)
        checks.append(
            _check(
                "clips per target phrase",
                "Dataset",
                float(smallest),
                float(config.min_clips_per_target),
                smallest >= config.min_clips_per_target,
                detail=f"thinnest phrase of {len(targets)}",
                advice=(
                    "collect recordings for the thinnest phrase first - the model is "
                    "only as good as its weakest class"
                ),
            )
        )
        negatives = quality.negative_counts
        hard = int(negatives.get("hard_negative", 0))
        checks.append(
            _check(
                "hard negatives collected",
                "Dataset",
                float(hard),
                None,
                (hard > 0) if negatives else None,
                detail=(
                    "near-miss phrases are what the false-activation rate is made of"
                ),
                advice=(
                    "record the near misses: for 007, say 'سبحان الله', 'سبحان الله "
                    "العظيم', 'سبحان الله وبحمده' and put them in "
                    "dataset/unknown/hard_negative/"
                ),
            )
        )
        headline["target_clips"] = quality.target_clips
        headline["unknown_clips"] = quality.unknown_clips
        headline["scale"] = quality.scale

    # -- clip evaluation ----------------------------------------------------
    if clip_result is not None:
        macro = clip_result.averaged("macro")
        headline["clip_accuracy"] = clip_result.accuracy
        headline["macro_f1"] = macro["f1"]
        checks.append(
            ReadinessCheck(
                name="clip accuracy",
                status=PASS,
                value=float(clip_result.accuracy),
                detail=(
                    f"macro F1 {macro['f1']:.4f} on {clip_result.num_samples} clips - "
                    f"reported, never used to decide readiness"
                ),
                section="Clip evaluation",
            )
        )
        for item in clip_result.per_class():
            if item.label == clip_result.unknown_class:
                continue
            checks.append(
                ReadinessCheck(
                    name=f"{item.label} recall (clips)",
                    status=PASS,
                    value=float(item.recall),
                    detail=f"{item.support} clip(s) in the split",
                    section="Clip evaluation",
                )
            )

        by_type = {item.negative_type: item for item in clip_result.by_negative_type()}
        hard_negative = by_type.get("hard_negative")
        checks.append(
            _check(
                "hard-negative false-positive rate",
                "Hard negatives",
                float(hard_negative.false_positive_rate) if hard_negative else None,
                float(config.max_hard_negative_fp_rate),
                (
                    hard_negative.false_positive_rate <= config.max_hard_negative_fp_rate
                    if hard_negative
                    else None
                ),
                detail=(
                    f"{hard_negative.accepted}/{hard_negative.clips} near-miss clips "
                    f"accepted as a dhikr"
                    if hard_negative
                    else "no clips are labelled hard_negative"
                ),
                advice=(
                    "collect more near-miss recordings and retrain; a higher "
                    "threshold trades away real dhikr to hide this"
                ),
            )
        )
        if hard_negative:
            headline["hard_negative_fp_rate"] = hard_negative.false_positive_rate

    # -- streaming ----------------------------------------------------------
    if streaming is not None:
        headline["event_precision"] = streaming.precision
        headline["event_recall"] = streaming.recall
        headline["event_f1"] = streaming.f1
        headline["false_activations_per_hour"] = streaming.false_activations_per_hour
        headline["duplicate_rate"] = streaming.duplicate_rate

        minutes = streaming.duration_seconds / 60.0
        enough = minutes >= config.min_streaming_minutes
        checks.append(
            _check(
                "streaming audio evaluated",
                "Streaming",
                minutes,
                float(config.min_streaming_minutes),
                True if enough else None,
                detail=f"{minutes:.1f} minutes of long-form recordings",
                advice=(
                    "record more continuous audio - reciting sessions and, above "
                    "all, negative audio (conversation, TV, a quiet room). Below "
                    "the minimum, FA/hour moves in steps far larger than the budget"
                ),
            )
        )
        measurable = enough or None
        checks.append(
            _check(
                "event recall",
                "Streaming",
                float(streaming.recall),
                float(config.min_event_recall),
                (streaming.recall >= config.min_event_recall) if enough else measurable,
                detail=f"{streaming.true_positives}/{streaming.expected} dhikr counted",
                advice=(
                    "misses come from the activation threshold being too high for "
                    "quiet or fast speakers, or from too few speakers in training"
                ),
            )
        )
        checks.append(
            _check(
                "event precision",
                "Streaming",
                float(streaming.precision),
                float(config.min_event_precision),
                (streaming.precision >= config.min_event_precision) if enough else measurable,
                detail=(
                    f"{streaming.false_positives} false and {streaming.duplicates} "
                    f"duplicate detection(s)"
                ),
                advice=(
                    "duplicates are a detector problem (cooldown, hysteresis); false "
                    "positives are a model problem (hard negatives, more speakers)"
                ),
            )
        )
        checks.append(
            _check(
                "false activations / hour",
                "Streaming",
                float(streaming.false_activations_per_hour),
                float(config.max_false_activations_per_hour),
                (
                    streaming.false_activations_per_hour
                    <= config.max_false_activations_per_hour
                )
                if enough
                else measurable,
                detail="the number this feature lives or dies by",
                advice=(
                    "collect the audio it fires on as hard negatives and retrain - "
                    "raising the threshold past the calibrated point costs real dhikr"
                ),
            )
        )

    if stress is not None:
        checks.append(
            _check(
                "FA/hour on negative-only audio",
                "Streaming",
                float(stress.false_activations_per_hour),
                float(config.max_false_activations_per_hour),
                stress.false_activations_per_hour
                <= config.max_false_activations_per_hour,
                detail=(
                    f"{stress.false_events} event(s) in "
                    f"{stress.duration_seconds / 60.0:.1f} min of audio with no dhikr "
                    f"in it"
                ),
                advice=(
                    "listen to the highest-confidence moment in the stress report; "
                    "whatever it is, that is the recording to collect more of"
                ),
            )
        )
        headline["negative_fa_per_hour"] = stress.false_activations_per_hour
    elif streaming is None:
        checks.append(
            _check(
                "streaming evaluation",
                "Streaming",
                None,
                None,
                None,
                detail="no long-form recordings were evaluated",
                advice=(
                    "record a few sessions plus some negative-only audio and "
                    "annotate them (see 'Streaming test recordings' in README.md). "
                    "Without this the counter has never been measured as a counter"
                ),
            )
        )

    # -- calibration --------------------------------------------------------
    thresholds: Dict[str, float] = dict(per_class_thresholds or {})
    if calibration is not None:
        checks.append(
            _check(
                "threshold meets the FA budget",
                "Streaming",
                float(calibration.threshold) if calibration.threshold is not None else None,
                float(calibration.target_false_activations_per_hour),
                bool(calibration.satisfied) and not calibration.reason,
                detail=calibration.reason or "calibrated from streaming recordings",
                advice=(
                    "no threshold satisfies the budget - this is a data problem, not "
                    "a tuning one"
                ),
            )
        )
        if calibration.threshold is not None:
            thresholds.setdefault("global", float(calibration.threshold))

    # -- TFLite -------------------------------------------------------------
    verifications = list(tflite_verifications or [])
    if verifications:
        worst = min(verifications, key=lambda item: item.agreement)
        checks.append(
            _check(
                "TFLite agreement with Keras",
                "TFLite",
                float(worst.agreement),
                0.99,
                all(item.passed for item in verifications),
                detail=(
                    f"worst subset: {worst.subset} ({worst.disagreements} "
                    f"disagreement(s) of {worst.samples})"
                ),
                advice=(
                    "re-run INT8 calibration with representative features, or ship "
                    "dynamic_range instead - a quantised model that disagrees on "
                    "near-miss audio changes the false-activation rate"
                ),
            )
        )
        headline["tflite_agreement"] = worst.agreement

    checks.append(
        _check(
            "INT8 accuracy drop",
            "TFLite",
            tflite_accuracy_drop,
            float(config.max_tflite_accuracy_drop),
            (
                tflite_accuracy_drop <= config.max_tflite_accuracy_drop
                if tflite_accuracy_drop is not None
                else None
            ),
            advice="ship dynamic_range if INT8 costs more than the tolerance",
        )
    )
    checks.append(
        _check(
            "INT8 extra false activations / hour",
            "TFLite",
            tflite_extra_false_activations,
            float(config.max_tflite_extra_false_activations_per_hour),
            (
                tflite_extra_false_activations
                <= config.max_tflite_extra_false_activations_per_hour
                if tflite_extra_false_activations is not None
                else None
            ),
            detail=(
                "measured by running the streaming evaluation through the quantised "
                "model"
            ),
            advice=(
                "reject INT8 for production if quantisation adds false counts - the "
                "size saving is not worth it"
            ),
        )
    )

    return ReadinessReport(
        checks=checks,
        config=config,
        headline=headline,
        recommended_thresholds=thresholds,
    )

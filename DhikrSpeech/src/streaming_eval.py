"""Streaming evaluation: events, false activations per hour, threshold calibration.

Clip accuracy answers "given that someone said one of these, which one was it".
The counter in the app has to answer something harder: over an hour of open
microphone, how many dhikr did it count, and how many of those were not dhikr at
all. This module measures that, on long recordings with hand annotations.

The headline number is **false activations per hour**. It is the one that decides
whether the feature is usable: a missed dhikr is a small annoyance the user can
see and repeat, a counter that ticks during a conversation is a broken feature.
So the threshold is not chosen to maximise accuracy or even F1 - it is the lowest
threshold that stays inside the configured FA/hour budget, and when no threshold
does, this module says so instead of quietly picking 0.99.

Everything works from cached :class:`~src.streaming.WindowScan` objects, so a
sweep over twelve thresholds costs twelve state-machine passes, not twelve runs
of the model.
"""

from __future__ import annotations

import json
import logging
import math
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .config import CalibrationConfig, DetectorConfig, EventMatchConfig, StreamingConfig
from .streaming import StreamingEvent, WindowScan, detect_events

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "AnnotatedEvent",
    "CalibrationResult",
    "MatchResult",
    "NegativeStressResult",
    "StreamingClip",
    "StreamingMetrics",
    "ThresholdPoint",
    "annotation_template",
    "calibrate_per_class",
    "calibrate_threshold",
    "clip_threshold_sweep",
    "evaluate_streaming",
    "load_annotations",
    "match_events",
    "negative_stress_test",
    "sweep_thresholds",
]

SECONDS_PER_HOUR = 3600.0

# Recall below this at the chosen operating point means the threshold is quiet
# rather than accurate: it meets the false-activation budget by hardly firing.
LOW_RECALL = 0.5


# ---------------------------------------------------------------------------
# Annotations
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class AnnotatedEvent:
    """One dhikr somebody actually said, as marked by hand."""

    label: str
    start: float
    end: float

    @property
    def duration(self) -> float:
        return max(self.end - self.start, 0.0)

    @property
    def midpoint(self) -> float:
        return (self.start + self.end) / 2.0


@dataclass
class StreamingClip:
    """One long-form recording and what is in it."""

    path: Path
    events: List[AnnotatedEvent] = field(default_factory=list)
    # Recordings with no target dhikr at all: conversation, TV, other dhikr,
    # street noise. These carry the release-critical number - every event on one
    # of them is a false activation, and no annotation can be "missed".
    negative_only: bool = False
    # What kind of negative audio, when it is one: `other_dhikr`, `tv`, ... Free
    # text, matched against the dataset's negative categories where they overlap.
    negative_type: str = ""
    note: str = ""

    @property
    def name(self) -> str:
        return self.path.name

    @property
    def is_negative(self) -> bool:
        return self.negative_only or not self.events


def annotation_template(files: Sequence[str]) -> Dict[str, object]:
    """A skeleton ``annotations.json`` for a folder of recordings."""
    return {
        "files": [
            {"file": name, "events": [{"class": "006", "start": 0.0, "end": 0.0}]}
            for name in files
        ]
    }


def _parse_event(payload: Dict) -> Optional[AnnotatedEvent]:
    label = payload.get("class", payload.get("label"))
    if label is None:
        return None
    start = float(payload.get("start", 0.0))
    end = float(payload.get("end", start))
    if end < start:
        start, end = end, start
    return AnnotatedEvent(label=str(label), start=start, end=end)


def load_annotations(
    path: PathLike, audio_dir: Optional[PathLike] = None
) -> List[StreamingClip]:
    """Read ``annotations.json``.

    The format is deliberately small - annotating long recordings by hand is the
    expensive part, so the file asks for as little as possible::

        {
          "files": [
            {"file": "test_001.wav",
             "events": [{"class": "006", "start": 12.3, "end": 13.8},
                        {"class": "007", "start": 25.1, "end": 27.0}]},
            {"file": "negative_tv.wav", "events": [], "negative_type": "tv"}
          ]
        }

    A bare list of those entries is accepted too, ``label`` works as well as
    ``class``, and timings need only be roughly right - matching is tolerant (see
    ``streaming.matching`` in the config). A file with no events is a
    negative-only recording, which is where the false-activation rate comes from.
    """
    source = Path(path)
    if not source.is_file():
        raise FileNotFoundError(
            f"no annotations at {source} - see 'Streaming test recordings' in "
            f"README.md for the folder layout"
        )
    payload = json.loads(source.read_text(encoding="utf-8"))
    entries = payload.get("files", []) if isinstance(payload, dict) else payload
    root = Path(audio_dir) if audio_dir else source.parent

    clips: List[StreamingClip] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        name = entry.get("file") or entry.get("path")
        if not name:
            LOGGER.warning("annotation entry without a 'file' key, skipping: %r", entry)
            continue
        candidate = Path(name)
        resolved = candidate if candidate.is_absolute() else root / candidate
        events = [
            event
            for event in (_parse_event(item) for item in entry.get("events", []) or [])
            if event is not None
        ]
        clips.append(
            StreamingClip(
                path=resolved,
                events=sorted(events, key=lambda item: item.start),
                negative_only=bool(entry.get("negative", not events)),
                negative_type=str(entry.get("negative_type", "") or ""),
                note=str(entry.get("note", "") or ""),
            )
        )

    missing = [clip.name for clip in clips if not clip.path.is_file()]
    if missing:
        LOGGER.warning(
            "%d annotated file(s) are not on disk and will be skipped: %s",
            len(missing),
            ", ".join(missing[:5]),
        )
    return clips


# ---------------------------------------------------------------------------
# Matching
# ---------------------------------------------------------------------------
@dataclass
class MatchResult:
    """Which detections matched which annotations, for one recording."""

    matched: List[Tuple[StreamingEvent, AnnotatedEvent]] = field(default_factory=list)
    duplicates: List[Tuple[StreamingEvent, AnnotatedEvent]] = field(default_factory=list)
    false_positives: List[StreamingEvent] = field(default_factory=list)
    missed: List[AnnotatedEvent] = field(default_factory=list)

    @property
    def true_positives(self) -> int:
        return len(self.matched)


def _matches(
    detection: StreamingEvent, annotation: AnnotatedEvent, config: EventMatchConfig
) -> bool:
    if config.require_label_match and detection.label != annotation.label:
        return False
    overlap = detection.overlaps(annotation.start, annotation.end)
    if overlap > config.min_overlap_seconds:
        return True
    if config.min_overlap_seconds > 0.0:
        return False
    # No overlap: accept a trigger close to the annotated span. Hand annotation
    # is not millisecond-accurate, and the detector fires part-way through a
    # phrase rather than at its start.
    tolerance = config.tolerance_seconds
    if tolerance <= 0.0:
        return False
    distance = max(
        annotation.start - detection.end, detection.start - annotation.end, 0.0
    )
    if distance <= tolerance:
        return True
    return abs(detection.trigger_time - annotation.midpoint) <= tolerance


def match_events(
    detected: Sequence[StreamingEvent],
    annotated: Sequence[AnnotatedEvent],
    config: Optional[EventMatchConfig] = None,
) -> MatchResult:
    """Pair detections with annotations, in time order.

    Each annotation can be matched once. A second detection landing on an
    already-matched annotation is a **duplicate**, not a false positive: it is the
    same utterance counted twice, which is a different bug (the detector's
    cooldown or hysteresis) from firing at something nobody said. Both hurt
    precision; keeping them apart is what makes the report actionable.
    """
    config = config or EventMatchConfig()
    result = MatchResult()
    claimed: Dict[int, StreamingEvent] = {}

    for detection in sorted(detected, key=lambda item: item.trigger_time):
        candidates = [
            index
            for index, annotation in enumerate(annotated)
            if _matches(detection, annotation, config)
        ]
        if not candidates:
            result.false_positives.append(detection)
            continue
        free = [index for index in candidates if index not in claimed]
        if free:
            # Nearest free annotation, so a detection does not steal the match of
            # a neighbouring one it happens to reach.
            index = min(
                free,
                key=lambda position: abs(
                    detection.trigger_time - annotated[position].midpoint
                ),
            )
            claimed[index] = detection
            result.matched.append((detection, annotated[index]))
        else:
            result.duplicates.append((detection, annotated[candidates[0]]))

    result.missed = [
        annotation
        for index, annotation in enumerate(annotated)
        if index not in claimed
    ]
    return result


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------
@dataclass
class LabelMetrics:
    label: str
    expected: int = 0
    detected: int = 0
    true_positives: int = 0
    false_positives: int = 0
    false_negatives: int = 0
    duplicates: int = 0

    @property
    def recall(self) -> float:
        return self.true_positives / self.expected if self.expected else float("nan")

    @property
    def precision(self) -> float:
        return self.true_positives / self.detected if self.detected else float("nan")


@dataclass
class StreamingMetrics:
    """Event-level results over a set of long recordings."""

    expected: int
    detected: int
    true_positives: int
    false_positives: int
    false_negatives: int
    duplicates: int
    duration_seconds: float
    negative_duration_seconds: float = 0.0
    negative_false_positives: int = 0
    per_label: Dict[str, LabelMetrics] = field(default_factory=dict)
    per_negative_type: Dict[str, Dict[str, float]] = field(default_factory=dict)
    per_file: List[Dict[str, object]] = field(default_factory=list)
    threshold: Optional[float] = None
    highest_false_confidence: float = 0.0

    # -- headline numbers ---------------------------------------------------
    @property
    def precision(self) -> float:
        """Correct detections over detections that fired at *something real*.

        Duplicates are excluded here and reported separately, because they are a
        different defect with a different fix: a false positive is the model
        firing at audio nobody spoke a dhikr into, a duplicate is the detector
        counting one utterance twice (cooldown / hysteresis). See
        :attr:`counting_precision` for the number the user actually experiences.
        """
        total = self.true_positives + self.false_positives
        return self.true_positives / total if total else float("nan")

    @property
    def counting_precision(self) -> float:
        """Correct counts over every count the app would have made.

        Duplicates *are* in the denominator: on the phone a double count is as
        wrong as a phantom one. This is the number to quote when describing what
        the user sees; :attr:`precision` is the one to quote when describing the
        model.
        """
        total = self.true_positives + self.false_positives + self.duplicates
        return self.true_positives / total if total else float("nan")

    @property
    def recall(self) -> float:
        return self.true_positives / self.expected if self.expected else float("nan")

    @property
    def f1(self) -> float:
        precision, recall = self.precision, self.recall
        if not np.isfinite(precision) or not np.isfinite(recall) or precision + recall == 0:
            return float("nan")
        return 2.0 * precision * recall / (precision + recall)

    @property
    def hours(self) -> float:
        return self.duration_seconds / SECONDS_PER_HOUR

    @property
    def false_activations_per_hour(self) -> float:
        """The release-critical number: wrong counts per hour of listening.

        Duplicates are excluded here - they are counted separately, because they
        are a detector-tuning problem while a false activation is a model problem,
        and mixing them hides which one to fix.
        """
        return self.false_positives / self.hours if self.hours > 0 else float("nan")

    @property
    def negative_false_activations_per_hour(self) -> float:
        """FA/hour measured on recordings with no dhikr in them at all."""
        hours = self.negative_duration_seconds / SECONDS_PER_HOUR
        return self.negative_false_positives / hours if hours > 0 else float("nan")

    @property
    def duplicate_rate(self) -> float:
        return self.duplicates / self.expected if self.expected else float("nan")

    # -- reporting ----------------------------------------------------------
    def to_dict(self) -> Dict[str, object]:
        return {
            "threshold": self.threshold,
            "expected": self.expected,
            "detected": self.detected,
            "true_positives": self.true_positives,
            "false_positives": self.false_positives,
            "false_negatives": self.false_negatives,
            "duplicates": self.duplicates,
            "duration_seconds": round(self.duration_seconds, 2),
            "negative_duration_seconds": round(self.negative_duration_seconds, 2),
            "precision": self.precision,
            "counting_precision": self.counting_precision,
            "recall": self.recall,
            "f1": self.f1,
            "false_activations_per_hour": self.false_activations_per_hour,
            "negative_false_activations_per_hour": (
                self.negative_false_activations_per_hour
            ),
            "duplicate_rate": self.duplicate_rate,
            "highest_false_confidence": self.highest_false_confidence,
            "per_label": {
                label: {
                    "expected": item.expected,
                    "detected": item.detected,
                    "true_positives": item.true_positives,
                    "false_positives": item.false_positives,
                    "false_negatives": item.false_negatives,
                    "duplicates": item.duplicates,
                    "recall": item.recall,
                    "precision": item.precision,
                }
                for label, item in sorted(self.per_label.items())
            },
            "per_negative_type": self.per_negative_type,
            "per_file": self.per_file,
        }

    def save(self, directory: PathLike, name: str = "streaming_evaluation") -> Path:
        target = Path(directory)
        target.mkdir(parents=True, exist_ok=True)
        destination = target / f"{name}.json"
        destination.write_text(
            json.dumps(_jsonable(self.to_dict()), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        LOGGER.info("streaming evaluation written to %s", destination)
        return destination

    def summary(self, target_fa_per_hour: Optional[float] = None) -> str:
        lines = [
            f"audio               : {self.duration_seconds / 60.0:.1f} min "
            f"({self.negative_duration_seconds / 60.0:.1f} min with no dhikr)",
            f"Expected Dhikr      : {self.expected}",
            f"Correct detections  : {self.true_positives}",
            f"Missed              : {self.false_negatives}",
            f"False detections    : {self.false_positives}",
            f"Duplicate detections: {self.duplicates}",
            "",
            f"Event precision     : {self.precision:.1%}",
            f"Event recall        : {self.recall:.1%}",
            f"Event F1            : {self.f1:.1%}",
            f"False activations/hr: {self.false_activations_per_hour:.2f}",
        ]
        if self.duplicates:
            lines.append(
                f"Counting precision  : {self.counting_precision:.1%} "
                f"(duplicates included - what the user sees)"
            )
        if self.negative_duration_seconds > 0:
            lines.append(
                f"  on negative audio : "
                f"{self.negative_false_activations_per_hour:.2f}/hr "
                f"({self.negative_false_positives} in "
                f"{self.negative_duration_seconds / 60.0:.1f} min)"
            )
        if self.per_negative_type:
            lines.append("")
            lines.append("false activations by negative audio type:")
            for name, payload in sorted(self.per_negative_type.items()):
                lines.append(
                    f"  {name:<18}{int(payload['false_positives']):>4} in "
                    f"{payload['minutes']:.1f} min "
                    f"({payload['per_hour']:.2f}/hr)"
                )
        if self.per_label:
            lines.append("")
            lines.append(f"{'class':<10}{'expected':>9}{'found':>7}{'missed':>8}{'false':>7}{'recall':>9}")
            for label, item in sorted(self.per_label.items()):
                lines.append(
                    f"{label:<10}{item.expected:>9}{item.true_positives:>7}"
                    f"{item.false_negatives:>8}{item.false_positives:>7}"
                    f"{item.recall:>9.1%}"
                )
        if target_fa_per_hour is not None and np.isfinite(self.false_activations_per_hour):
            verdict = (
                "within budget"
                if self.false_activations_per_hour <= target_fa_per_hour
                else "OVER BUDGET"
            )
            lines.append("")
            lines.append(
                f"FA/hour budget      : {target_fa_per_hour:g} - {verdict}"
            )
        if self.duration_seconds < 20.0 * 60.0:
            lines.append(
                f"\n!! only {self.duration_seconds / 60.0:.1f} minutes of streaming audio. "
                f"FA/hour extrapolates from what happened here: at this length one "
                f"false activation is worth "
                f"{SECONDS_PER_HOUR / max(self.duration_seconds, 1.0):.1f}/hour, so the "
                f"number moves in huge steps and cannot be compared against a budget of "
                f"0.5. Record more, and record the boring cases - conversation, TV, a "
                f"quiet room."
            )
        return "\n".join(lines)


def _jsonable(value):
    """Replace non-finite floats with null so the report is valid JSON."""
    if isinstance(value, dict):
        return {key: _jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_jsonable(item) for item in value]
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, (np.floating, np.integer)):
        return _jsonable(value.item())
    return value


def evaluate_streaming(
    pairs: Sequence[Tuple[StreamingClip, WindowScan]],
    detector: Optional[DetectorConfig] = None,
    matching: Optional[EventMatchConfig] = None,
) -> StreamingMetrics:
    """Score detections against annotations over every recording.

    ``pairs`` is ``(clip, scan)`` - the annotation and the cached per-window
    probabilities. Re-running this with a different ``detector`` costs only the
    state machine, which is what makes the threshold sweep affordable.
    """
    detector = detector or DetectorConfig()
    matching = matching or EventMatchConfig()

    metrics = StreamingMetrics(
        expected=0,
        detected=0,
        true_positives=0,
        false_positives=0,
        false_negatives=0,
        duplicates=0,
        duration_seconds=0.0,
        threshold=detector.confidence_threshold,
    )
    negative_types: Dict[str, Dict[str, float]] = {}

    for clip, scan in pairs:
        events = detect_events(scan.times, scan.probabilities, scan.labels, detector)
        result = match_events(events, clip.events, matching)

        metrics.expected += len(clip.events)
        metrics.detected += len(events)
        metrics.true_positives += result.true_positives
        metrics.false_positives += len(result.false_positives)
        metrics.false_negatives += len(result.missed)
        metrics.duplicates += len(result.duplicates)
        metrics.duration_seconds += scan.duration

        for event in result.false_positives:
            metrics.highest_false_confidence = max(
                metrics.highest_false_confidence, float(event.peak_confidence)
            )

        if clip.is_negative:
            metrics.negative_duration_seconds += scan.duration
            metrics.negative_false_positives += len(result.false_positives)
            category = clip.negative_type or "unlabelled"
            bucket = negative_types.setdefault(
                category, {"false_positives": 0.0, "seconds": 0.0}
            )
            bucket["false_positives"] += len(result.false_positives)
            bucket["seconds"] += scan.duration

        for annotation in clip.events:
            item = metrics.per_label.setdefault(
                annotation.label, LabelMetrics(annotation.label)
            )
            item.expected += 1
        for event in events:
            item = metrics.per_label.setdefault(event.label, LabelMetrics(event.label))
            item.detected += 1
        for detection, _ in result.matched:
            metrics.per_label[detection.label].true_positives += 1
        for detection in result.false_positives:
            metrics.per_label[detection.label].false_positives += 1
        for detection, _ in result.duplicates:
            metrics.per_label[detection.label].duplicates += 1
        for annotation in result.missed:
            metrics.per_label[annotation.label].false_negatives += 1

        metrics.per_file.append(
            {
                "file": clip.name,
                "duration": round(scan.duration, 2),
                "negative_only": clip.is_negative,
                "negative_type": clip.negative_type,
                "expected": len(clip.events),
                "detected": len(events),
                "true_positives": result.true_positives,
                "false_positives": len(result.false_positives),
                "missed": len(result.missed),
                "duplicates": len(result.duplicates),
            }
        )

    metrics.per_negative_type = {
        name: {
            "false_positives": payload["false_positives"],
            "minutes": payload["seconds"] / 60.0,
            "per_hour": (
                payload["false_positives"] / (payload["seconds"] / SECONDS_PER_HOUR)
                if payload["seconds"] > 0
                else float("nan")
            ),
        }
        for name, payload in negative_types.items()
    }
    return metrics


# ---------------------------------------------------------------------------
# Negative-only stress test
# ---------------------------------------------------------------------------
@dataclass
class NegativeStressResult:
    """What the detector does over audio containing no dhikr at all.

    Release-critical, and the cheapest evaluation to collect: no annotation is
    needed, because the right answer for the whole recording is zero. Any
    conversation, television, Quran recitation or street recording works.
    """

    files: int
    duration_seconds: float
    windows: int
    false_events: int
    per_label: Dict[str, int] = field(default_factory=dict)
    predicted_distribution: Dict[str, int] = field(default_factory=dict)
    highest_confidence: float = 0.0
    highest_confidence_label: str = ""
    highest_confidence_time: float = 0.0
    per_file: List[Dict[str, object]] = field(default_factory=list)

    @property
    def hours(self) -> float:
        return self.duration_seconds / SECONDS_PER_HOUR

    @property
    def false_activations_per_hour(self) -> float:
        return self.false_events / self.hours if self.hours > 0 else float("nan")

    def to_dict(self) -> Dict[str, object]:
        return {
            "files": self.files,
            "duration_seconds": round(self.duration_seconds, 2),
            "windows": self.windows,
            "false_events": self.false_events,
            "false_activations_per_hour": self.false_activations_per_hour,
            "per_label": dict(self.per_label),
            "predicted_distribution": dict(self.predicted_distribution),
            "highest_confidence": self.highest_confidence,
            "highest_confidence_label": self.highest_confidence_label,
            "highest_confidence_time": self.highest_confidence_time,
            "per_file": self.per_file,
        }

    def summary(self, target_fa_per_hour: Optional[float] = None) -> str:
        lines = [
            f"files               : {self.files}",
            f"audio duration      : {self.duration_seconds / 60.0:.1f} min",
            f"windows processed   : {self.windows}",
            f"false target events : {self.false_events}",
            f"false activations/hr: {self.false_activations_per_hour:.2f}",
        ]
        if self.per_label:
            lines.append(
                "  by class          : "
                + ", ".join(f"{label}={count}" for label, count in sorted(self.per_label.items()))
            )
        if self.predicted_distribution:
            lines.append("")
            lines.append("windows won by each class (should be almost all 'unknown'):")
            for label, count in self.predicted_distribution.items():
                share = count / max(self.windows, 1)
                lines.append(f"  {label:<12}{count:>7}  {share:>6.1%}")
        if self.highest_confidence_label:
            lines.append("")
            lines.append(
                f"highest false confidence: {self.highest_confidence:.3f} "
                f"({self.highest_confidence_label} at "
                f"{self.highest_confidence_time:.1f} s) - listen to that moment; it is "
                f"the closest this model came to counting nothing as a dhikr"
            )
        if target_fa_per_hour is not None and np.isfinite(self.false_activations_per_hour):
            lines.append("")
            lines.append(
                f"budget {target_fa_per_hour:g}/hr - "
                + (
                    "PASS"
                    if self.false_activations_per_hour <= target_fa_per_hour
                    else "FAIL"
                )
            )
        return "\n".join(lines)


def negative_stress_test(
    scans: Sequence[WindowScan],
    detector: Optional[DetectorConfig] = None,
) -> NegativeStressResult:
    """Run the detector over recordings that contain no target dhikr."""
    detector = detector or DetectorConfig()
    result = NegativeStressResult(files=len(scans), duration_seconds=0.0, windows=0, false_events=0)
    distribution: Dict[str, int] = {}

    for scan in scans:
        events = detect_events(scan.times, scan.probabilities, scan.labels, detector)
        result.duration_seconds += scan.duration
        result.windows += scan.num_windows
        result.false_events += len(events)
        for event in events:
            result.per_label[event.label] = result.per_label.get(event.label, 0) + 1
        for label, count in scan.predicted_distribution().items():
            distribution[label] = distribution.get(label, 0) + count

        scores = scan.target_scores(ignore=detector.ignore_labels)
        if scores.size:
            position = int(np.argmax(scores))
            peak = float(scores[position])
            if peak > result.highest_confidence:
                result.highest_confidence = peak
                columns = [
                    index
                    for index, label in enumerate(scan.labels)
                    if label.lower() not in {name.lower() for name in detector.ignore_labels}
                ]
                best = columns[int(np.argmax(scan.probabilities[position, columns]))]
                result.highest_confidence_label = scan.labels[best]
                result.highest_confidence_time = float(scan.times[position])

        result.per_file.append(
            {
                "file": scan.source or "(unnamed)",
                "duration": round(scan.duration, 2),
                "windows": scan.num_windows,
                "false_events": len(events),
            }
        )

    result.predicted_distribution = dict(
        sorted(distribution.items(), key=lambda item: item[1], reverse=True)
    )
    return result


# ---------------------------------------------------------------------------
# Threshold calibration
# ---------------------------------------------------------------------------
@dataclass
class ThresholdPoint:
    """One point of the sweep."""

    threshold: float
    recall: float
    precision: float
    f1: float
    false_activations_per_hour: float
    true_positives: int
    false_positives: int
    duplicates: int
    expected: int

    def row(self) -> Dict[str, object]:
        return {
            "threshold": round(self.threshold, 3),
            "recall": round(self.recall, 4) if np.isfinite(self.recall) else None,
            "precision": round(self.precision, 4) if np.isfinite(self.precision) else None,
            "F1": round(self.f1, 4) if np.isfinite(self.f1) else None,
            "FA/hour": (
                round(self.false_activations_per_hour, 3)
                if np.isfinite(self.false_activations_per_hour)
                else None
            ),
            "found": self.true_positives,
            "false": self.false_positives,
            "duplicates": self.duplicates,
        }


@dataclass
class CalibrationResult:
    """The chosen operating point, and whether it actually met the budget."""

    threshold: Optional[float]
    satisfied: bool
    policy: str
    target_false_activations_per_hour: float
    points: List[ThresholdPoint] = field(default_factory=list)
    per_class: Dict[str, float] = field(default_factory=dict)
    label: Optional[str] = None
    reason: str = ""

    @property
    def chosen(self) -> Optional[ThresholdPoint]:
        if self.threshold is None:
            return None
        return min(
            self.points,
            key=lambda point: abs(point.threshold - self.threshold),
            default=None,
        )

    def to_dict(self) -> Dict[str, object]:
        return {
            "threshold": self.threshold,
            "satisfied": self.satisfied,
            "policy": self.policy,
            "label": self.label,
            "target_false_activations_per_hour": self.target_false_activations_per_hour,
            "per_class": dict(self.per_class),
            "reason": self.reason,
            "points": [asdict(point) for point in self.points],
        }

    def to_dataframe(self):
        import pandas as pd  # lazily imported: only notebooks need pandas

        return pd.DataFrame([point.row() for point in self.points])

    def summary(self) -> str:
        lines = [
            f"policy            : {self.policy}",
            f"FA/hour budget    : {self.target_false_activations_per_hour:g}",
        ]
        if self.threshold is None:
            lines.append("recommended       : none")
        else:
            lines.append(f"recommended       : {self.threshold:.2f}")
            point = self.chosen
            if point is not None:
                def percent(value: float) -> str:
                    return f"{value:.1%}" if np.isfinite(value) else "n/a"

                lines.append(
                    f"at that threshold : recall {percent(point.recall)} | precision "
                    f"{percent(point.precision)} | "
                    f"{point.false_activations_per_hour:.2f} FA/hour"
                )
        if self.per_class:
            lines.append("per-class         : " + ", ".join(
                f"{label} {value:.2f}" for label, value in sorted(self.per_class.items())
            ))
        if self.reason:
            lines.append(f"\n!! {self.reason}")
        return "\n".join(lines)


def sweep_thresholds(
    pairs: Sequence[Tuple[StreamingClip, WindowScan]],
    detector: DetectorConfig,
    matching: EventMatchConfig,
    thresholds: Sequence[float],
    label: Optional[str] = None,
) -> List[ThresholdPoint]:
    """Evaluate every candidate threshold on the cached scans.

    ``label`` sweeps one class's threshold while every other class keeps the one
    it already has, which is what per-class calibration needs: 006 and 007 are not
    equally hard, and forcing them to share a threshold costs recall on the easy
    one to protect the difficult one.
    """
    points: List[ThresholdPoint] = []
    for threshold in thresholds:
        if label is None:
            candidate = DetectorConfig(
                **{
                    **asdict(detector),
                    "confidence_threshold": float(threshold),
                    # Keep the hysteresis ratio rather than a fixed release value:
                    # a release threshold above the activation is meaningless, and
                    # a fixed one stops being hysteresis as activation moves.
                    "release_threshold": min(
                        detector.release_threshold, float(threshold)
                    ),
                    "per_class_thresholds": {},
                }
            )
        else:
            per_class = dict(detector.per_class_thresholds)
            per_class[label] = float(threshold)
            candidate = DetectorConfig(
                **{
                    **asdict(detector),
                    "release_threshold": min(
                        detector.release_threshold, float(threshold)
                    ),
                    "per_class_thresholds": per_class,
                }
            )

        metrics = evaluate_streaming(pairs, candidate, matching)
        if label is not None:
            per_label = metrics.per_label.get(label, LabelMetrics(label))
            recall = per_label.recall
            precision = per_label.precision
            expected = per_label.expected
            true_positives = per_label.true_positives
            false_positives = per_label.false_positives
            duplicates = per_label.duplicates
            f1 = (
                2.0 * precision * recall / (precision + recall)
                if np.isfinite(precision) and np.isfinite(recall) and precision + recall > 0
                else float("nan")
            )
            fa_per_hour = (
                false_positives / metrics.hours if metrics.hours > 0 else float("nan")
            )
        else:
            recall, precision, f1 = metrics.recall, metrics.precision, metrics.f1
            expected = metrics.expected
            true_positives = metrics.true_positives
            false_positives = metrics.false_positives
            duplicates = metrics.duplicates
            fa_per_hour = metrics.false_activations_per_hour

        points.append(
            ThresholdPoint(
                threshold=float(threshold),
                recall=recall,
                precision=precision,
                f1=f1,
                false_activations_per_hour=fa_per_hour,
                true_positives=true_positives,
                false_positives=false_positives,
                duplicates=duplicates,
                expected=expected,
            )
        )
    return points


def _choose(
    points: Sequence[ThresholdPoint],
    target: float,
    policy: str,
    label: Optional[str] = None,
) -> CalibrationResult:
    """Apply the policy to a finished sweep."""
    result = CalibrationResult(
        threshold=None,
        satisfied=False,
        policy=policy,
        target_false_activations_per_hour=float(target),
        points=list(points),
        label=label,
    )
    if not points:
        result.reason = "no thresholds were evaluated"
        return result

    if policy == "max_f1":
        best = max(
            points,
            key=lambda point: (point.f1 if np.isfinite(point.f1) else -1.0),
        )
        result.threshold = best.threshold
        result.satisfied = (
            np.isfinite(best.false_activations_per_hour)
            and best.false_activations_per_hour <= target
        )
        if not result.satisfied:
            result.reason = (
                f"policy 'max_f1' ignores the false-activation budget: the chosen "
                f"threshold {best.threshold:.2f} produces "
                f"{best.false_activations_per_hour:.2f} FA/hour against a budget of "
                f"{target:g}. Use it to explore, not to ship."
            )
        return result

    within = [
        point
        for point in points
        if np.isfinite(point.false_activations_per_hour)
        and point.false_activations_per_hour <= target
    ]
    if within:
        # Inside the budget, recall is what is left to maximise; the lowest
        # threshold breaks ties, since recall usually plateaus over several
        # candidates and the lowest of those leaves the most headroom for a
        # quieter speaker.
        chosen = max(
            within,
            key=lambda point: (
                point.recall if np.isfinite(point.recall) else -1.0,
                -point.threshold,
            ),
        )
        result.threshold = chosen.threshold
        result.satisfied = True
        if np.isfinite(chosen.recall) and chosen.recall < LOW_RECALL:
            # Inside the budget, but only because it barely fires at all. This is
            # a pass on paper and a broken feature in the app, so it says so.
            result.reason = (
                f"the budget is met at {chosen.threshold:.2f}, but recall there is "
                f"only {chosen.recall:.1%}: every threshold quiet enough to stay "
                f"inside {target:g} FA/hour also misses most real dhikr. The model "
                f"is not separating dhikr from the rest of the audio well enough to "
                f"have a usable operating point - more speakers and more hard "
                f"negatives, not a different threshold."
            )
        return result

    strictest = max(points, key=lambda point: point.threshold)
    result.threshold = None
    result.satisfied = False
    result.reason = (
        f"NO threshold in the sweep meets the budget of {target:g} false "
        f"activations per hour - even {strictest.threshold:.2f} produces "
        f"{strictest.false_activations_per_hour:.2f}/hour"
        + (f" for '{label}'" if label else "")
        + ". Raising the threshold further is not the fix: the model is firing "
        "confidently on audio that is not a dhikr. Collect hard negatives of "
        "whatever it fires on, plus more speakers, and retrain. Shipping this "
        "means a counter that ticks during conversation."
    )
    return result


def calibrate_threshold(
    pairs: Sequence[Tuple[StreamingClip, WindowScan]],
    streaming: StreamingConfig,
    calibration: Optional[CalibrationConfig] = None,
) -> CalibrationResult:
    """Pick the global activation threshold from streaming data.

    The policy is deliberately not "best F1": F1 weighs a missed dhikr and a
    false count equally, and they are not equal here. The default policy takes the
    false-activation budget as a hard constraint and maximises recall inside it.
    """
    calibration = calibration or streaming.calibration
    points = sweep_thresholds(
        pairs,
        streaming.detector,
        streaming.matching,
        calibration.thresholds(),
    )
    return _choose(
        points,
        streaming.target_false_activations_per_hour,
        calibration.policy,
    )


def calibrate_per_class(
    pairs: Sequence[Tuple[StreamingClip, WindowScan]],
    streaming: StreamingConfig,
    labels: Sequence[str],
    calibration: Optional[CalibrationConfig] = None,
    base_threshold: Optional[float] = None,
) -> Dict[str, CalibrationResult]:
    """One threshold per target class, each with its own share of the FA budget.

    Phrases are not equally difficult - a nested phrase that contains another one
    needs a stricter threshold than a distinct one - so a single global value
    either lets the hard class fire too often or throttles the easy class for
    nothing. The budget is divided evenly between the classes so the *total* stays
    inside the configured FA/hour.
    """
    calibration = calibration or streaming.calibration
    ignored = {name.lower() for name in streaming.detector.ignore_labels}
    targets = [label for label in labels if label.lower() not in ignored]
    if not targets:
        return {}

    share = streaming.target_false_activations_per_hour / float(len(targets))
    detector = streaming.detector
    if base_threshold is not None:
        detector = DetectorConfig(
            **{**asdict(detector), "confidence_threshold": float(base_threshold)}
        )

    results: Dict[str, CalibrationResult] = {}
    for label in targets:
        points = sweep_thresholds(
            pairs,
            detector,
            streaming.matching,
            calibration.thresholds(),
            label=label,
        )
        results[label] = _choose(points, share, calibration.policy, label=label)
    return results


# ---------------------------------------------------------------------------
# Clip-level fallback
# ---------------------------------------------------------------------------
def clip_threshold_sweep(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    class_names: Sequence[str],
    thresholds: Sequence[float],
    unknown_class: str = "unknown",
    negative_types: Optional[Sequence[str]] = None,
    hard_negative_type: str = "hard_negative",
) -> List[Dict[str, object]]:
    """Threshold sweep on isolated clips, for when no streaming data exists yet.

    This cannot produce a false-activation *rate* - clips have no duration in the
    sense an hour of listening does - so it reports what it can: how often a real
    phrase is accepted, and how often unknown audio is. Read it as a shortlist for
    the streaming sweep, never as a substitute: a model that rejects 99% of
    2-second unknown clips can still fire several times a minute on continuous
    speech, because continuous speech contains far more windows and far more
    near-misses than a clip set does.
    """
    truth = np.asarray(y_true).ravel()
    probabilities = np.asarray(y_prob, dtype=np.float32)
    names = list(class_names)
    target_columns = [index for index, name in enumerate(names) if name != unknown_class]
    if not target_columns:
        raise ValueError("no target classes to sweep - the manifest holds only unknown")

    best_target = probabilities[:, target_columns].max(axis=1)
    predicted = np.array(
        [target_columns[index] for index in probabilities[:, target_columns].argmax(axis=1)]
    )
    is_target = np.isin(truth, target_columns)
    is_unknown = ~is_target
    categories = (
        np.asarray(negative_types, dtype=object)
        if negative_types is not None
        else np.full(truth.shape, "", dtype=object)
    )
    is_hard = is_unknown & (categories == hard_negative_type)

    rows: List[Dict[str, object]] = []
    for threshold in thresholds:
        accepted = best_target >= threshold
        correct = accepted & is_target & (predicted == truth)
        row: Dict[str, object] = {
            "threshold": round(float(threshold), 3),
            "target recall": float(correct.sum() / max(int(is_target.sum()), 1)),
            "unknown accepted": float(
                (accepted & is_unknown).sum() / max(int(is_unknown.sum()), 1)
            ),
        }
        if int(is_hard.sum()):
            row["hard-negative accepted"] = float(
                (accepted & is_hard).sum() / int(is_hard.sum())
            )
        rows.append(row)
    return rows

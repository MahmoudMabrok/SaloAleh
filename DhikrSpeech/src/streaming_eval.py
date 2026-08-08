"""Streaming evaluation: event metrics, false activations per hour, calibration.

A model that classifies isolated clips well is not the thing being shipped. What
ships listens continuously, and its quality is four numbers:

============================  ==========================================
false activations / hour      counts nobody said. The release-critical one.
event precision / recall      of the counted events, how many were real, and
                              of the real repetitions, how many were counted
duplicate rate                one utterance counted twice
hard-negative FP rate         near-misses ("سبحان الله" for target 007)
                              accepted as the target
============================  ==========================================

Everything here is measured through :class:`src.streaming.StreamingDetector`, so
the numbers describe the deployed behaviour rather than a clip-level proxy for
it. Scoring is separated from counting on purpose: :func:`score_clips` runs the
model once, and every threshold in a sweep then replays the *state machine* over
those cached timelines. A 60-point sweep therefore costs one forward pass, not
sixty.

Annotations live in ``streaming_test/annotations.json``::

    [
      {"file": "session_001.wav", "target": "007",
       "events": [{"start": 12.3, "end": 14.1}, ...]},
      {"file": "tv_noise.wav", "target": "007", "category": "background_audio",
       "events": []}
    ]

A clip with no events is a negative-only stress recording (requirement 23): every
event detected in it is a false activation, and ``category`` says what kind of
audio produced it.
"""

from __future__ import annotations

import json
import logging
import math
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple, Union

import numpy as np

from .config import CalibrationConfig, Config, DetectorConfig
from .streaming import (
    Event,
    ScoreTimeline,
    StreamingDetector,
    detect_events,
    detector_with_threshold,
)

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "CalibrationResult",
    "ClipEventResult",
    "EventMetrics",
    "MatchResult",
    "NegativeClipReport",
    "ScoredClip",
    "StreamingClip",
    "StreamingEvaluation",
    "ThresholdRow",
    "calibrate_threshold",
    "evaluate_negative_clips",
    "evaluate_timelines",
    "load_annotations",
    "load_streaming_set",
    "match_events",
    "score_clips",
    "streaming_audio_root",
]

SECONDS_PER_HOUR = 3600.0


# ---------------------------------------------------------------------------
# Annotations
# ---------------------------------------------------------------------------
@dataclass
class StreamingClip:
    """One long-form recording and the repetitions it contains."""

    file: str
    events: List[Tuple[float, float]] = field(default_factory=list)
    target: Optional[str] = None
    # For recordings that hold no target at all: what kind of audio this is, so a
    # false activation can be attributed ("TV", "other_dhikr", "street").
    category: Optional[str] = None
    duration: Optional[float] = None
    notes: str = ""

    @property
    def expected(self) -> int:
        return len(self.events)

    @property
    def is_negative_only(self) -> bool:
        return not self.events

    def matches_target(self, target_folder: Optional[str]) -> bool:
        """Whether this recording belongs to a given target's evaluation set.

        An annotation without a ``target`` is shared material - room tone, TV,
        conversation - and counts for every target.
        """
        if target_folder is None or self.target is None:
            return True
        text = str(self.target).strip()
        try:
            return f"{int(text):03d}" == target_folder
        except ValueError:
            return text == target_folder


def load_annotations(path: PathLike) -> List[StreamingClip]:
    """Read ``annotations.json``. Accepts a list or a ``{"clips": [...]}`` wrapper."""
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(payload, dict):
        payload = payload.get("clips") or payload.get("recordings") or []
    clips: List[StreamingClip] = []
    for entry in payload:
        events: List[Tuple[float, float]] = []
        for item in entry.get("events") or []:
            if isinstance(item, dict):
                start = float(item["start"])
                end = float(item.get("end", start))
            else:  # a bare [start, end] pair
                start, end = float(item[0]), float(item[1])
            if end < start:
                raise ValueError(f"{entry.get('file')}: event ends before it starts ({start}, {end})")
            events.append((start, end))
        events.sort()
        clips.append(
            StreamingClip(
                file=str(entry["file"]),
                events=events,
                target=str(entry["target"]) if entry.get("target") is not None else None,
                category=entry.get("category"),
                duration=float(entry["duration"]) if entry.get("duration") else None,
                notes=str(entry.get("notes", "")),
            )
        )
    return clips


# ---------------------------------------------------------------------------
# Matching
# ---------------------------------------------------------------------------
@dataclass
class MatchResult:
    """Which detections landed on which annotated repetition."""

    matched: List[Tuple[int, int]] = field(default_factory=list)   # (truth, detection)
    duplicates: List[Tuple[int, int]] = field(default_factory=list)
    false_detections: List[int] = field(default_factory=list)
    missed: List[int] = field(default_factory=list)


def match_events(
    detections: Sequence[Event],
    truth: Sequence[Tuple[float, float]],
    tolerance: float = 0.75,
) -> MatchResult:
    """Assign detections to annotated repetitions, in time order.

    A detection belongs to a repetition when its peak time falls inside
    ``[start - tolerance, end + tolerance]``. The peak is used rather than the
    span because the span is a window-length superset of the utterance and would
    happily overlap the neighbouring repetition when someone repeats the dhikr
    quickly.

    A second detection landing on an already-matched repetition is a
    **duplicate**, not a false positive: it is the same failure the state machine
    exists to prevent, and lumping the two together would hide it.
    """
    result = MatchResult()
    consumed: Dict[int, int] = {}

    ordered = sorted(range(len(detections)), key=lambda index: detections[index].time)
    for detection_index in ordered:
        detection = detections[detection_index]
        best: Optional[int] = None
        best_distance = math.inf
        for truth_index, (start, end) in enumerate(truth):
            if not (start - tolerance) <= detection.time <= (end + tolerance):
                continue
            centre = (start + end) / 2.0
            distance = abs(detection.time - centre)
            if distance < best_distance:
                best, best_distance = truth_index, distance
        if best is None:
            result.false_detections.append(detection_index)
        elif best in consumed:
            result.duplicates.append((best, detection_index))
        else:
            consumed[best] = detection_index
            result.matched.append((best, detection_index))

    result.missed = [index for index in range(len(truth)) if index not in consumed]
    return result


# ---------------------------------------------------------------------------
# Metrics
# ---------------------------------------------------------------------------
@dataclass
class EventMetrics:
    """Event-level quality over one or more recordings."""

    expected: int = 0
    detected: int = 0
    correct: int = 0
    missed: int = 0
    false_events: int = 0
    duplicates: int = 0
    duration_seconds: float = 0.0
    false_by_category: Dict[str, int] = field(default_factory=dict)
    max_negative_score: float = 0.0

    def __add__(self, other: "EventMetrics") -> "EventMetrics":
        merged_categories: Counter = Counter(self.false_by_category)
        merged_categories.update(other.false_by_category)
        return EventMetrics(
            expected=self.expected + other.expected,
            detected=self.detected + other.detected,
            correct=self.correct + other.correct,
            missed=self.missed + other.missed,
            false_events=self.false_events + other.false_events,
            duplicates=self.duplicates + other.duplicates,
            duration_seconds=self.duration_seconds + other.duration_seconds,
            false_by_category=dict(merged_categories),
            max_negative_score=max(self.max_negative_score, other.max_negative_score),
        )

    @property
    def precision(self) -> float:
        """Correct detections over everything the detector counted.

        Duplicates count against precision because on device they *are* extra
        counts - the user sees the number move twice for one dhikr.
        """
        return self.correct / self.detected if self.detected else float("nan")

    @property
    def recall(self) -> float:
        return self.correct / self.expected if self.expected else float("nan")

    @property
    def f1(self) -> float:
        precision, recall = self.precision, self.recall
        if not np.isfinite(precision) or not np.isfinite(recall) or (precision + recall) == 0:
            return float("nan")
        return 2.0 * precision * recall / (precision + recall)

    @property
    def duplicate_rate(self) -> float:
        return self.duplicates / self.expected if self.expected else float("nan")

    @property
    def false_activations_per_hour(self) -> float:
        """The release-critical number. Undefined without measured audio."""
        if self.duration_seconds <= 0.0:
            return float("nan")
        return self.false_events / (self.duration_seconds / SECONDS_PER_HOUR)

    def to_dict(self) -> Dict[str, object]:
        return {
            "expected": self.expected,
            "detected": self.detected,
            "correct": self.correct,
            "missed": self.missed,
            "false_events": self.false_events,
            "duplicates": self.duplicates,
            "duration_seconds": round(self.duration_seconds, 2),
            "duration_hours": round(self.duration_seconds / SECONDS_PER_HOUR, 4),
            "precision": _round(self.precision),
            "recall": _round(self.recall),
            "f1": _round(self.f1),
            "duplicate_rate": _round(self.duplicate_rate),
            "false_activations_per_hour": _round(self.false_activations_per_hour),
            "false_by_category": dict(sorted(self.false_by_category.items())),
            "max_negative_score": _round(self.max_negative_score),
        }

    def summary(self) -> str:
        lines = [
            f"expected repetitions : {self.expected}",
            f"detected             : {self.detected}",
            f"correct              : {self.correct}",
            f"missed               : {self.missed}",
            f"false events         : {self.false_events}",
            f"duplicates           : {self.duplicates}",
            "",
            f"event precision      : {_percent(self.precision)}",
            f"event recall         : {_percent(self.recall)}",
            f"event F1             : {_percent(self.f1)}",
            f"duplicate rate       : {_percent(self.duplicate_rate)}",
            f"FA / hour            : {self.false_activations_per_hour:.2f} "
            f"over {self.duration_seconds / SECONDS_PER_HOUR:.2f} h",
        ]
        if self.false_by_category:
            lines.append("")
            lines.append("false activations by audio category:")
            for name, count in sorted(
                self.false_by_category.items(), key=lambda item: item[1], reverse=True
            ):
                lines.append(f"  {name:<20} {count}")
        return "\n".join(lines)


def _round(value: float, digits: int = 4) -> Optional[float]:
    return None if value is None or not np.isfinite(value) else round(float(value), digits)


def _percent(value: float) -> str:
    return "n/a" if not np.isfinite(value) else f"{value * 100:.1f}%"


# ---------------------------------------------------------------------------
# Scoring and evaluation
# ---------------------------------------------------------------------------
@dataclass
class ScoredClip:
    """A recording's score timeline, kept so threshold sweeps are cheap."""

    clip: StreamingClip
    timeline: ScoreTimeline


@dataclass
class ClipEventResult:
    """Per-recording outcome at one operating point."""

    file: str
    category: Optional[str]
    metrics: EventMetrics
    events: List[Event] = field(default_factory=list)
    false_times: List[float] = field(default_factory=list)

    def to_dict(self) -> Dict[str, object]:
        return {
            "file": self.file,
            "category": self.category,
            "metrics": self.metrics.to_dict(),
            "events": [event.to_dict() for event in self.events],
            "false_times": [round(float(value), 2) for value in self.false_times],
        }


def score_clips(
    detector: StreamingDetector,
    clips: Sequence[StreamingClip],
    root: PathLike,
    target_folder: Optional[str] = None,
    progress: Optional[Callable[[str, int, int], None]] = None,
) -> List[ScoredClip]:
    """Run the model over every annotated recording once.

    Recordings annotated for a different target are skipped; recordings with no
    ``target`` are shared negative material and are always included.
    """
    root = Path(root)
    selected = [clip for clip in clips if clip.matches_target(target_folder)]
    scored: List[ScoredClip] = []
    for position, clip in enumerate(selected, start=1):
        path = root / clip.file
        if not path.is_file():
            LOGGER.warning("annotation refers to %s, which does not exist - skipped", path)
            continue
        if progress:
            progress(clip.file, position, len(selected))
        timeline = detector.score(_load(path, detector.frontend.sample_rate))
        scored.append(ScoredClip(clip=clip, timeline=timeline))
    if not scored:
        LOGGER.warning(
            "no streaming recordings were scored - check %s and the 'target' field of "
            "each annotation",
            root,
        )
    return scored


def _load(path: Path, sample_rate: int) -> np.ndarray:
    from .audio import load_audio

    return load_audio(path, sample_rate)


@dataclass
class StreamingEvaluation:
    """Aggregate event metrics plus the per-recording breakdown."""

    metrics: EventMetrics
    per_clip: List[ClipEventResult] = field(default_factory=list)
    detector: Optional[DetectorConfig] = None
    tolerance: float = 0.75

    @property
    def negative_only(self) -> "EventMetrics":
        """Metrics restricted to recordings that contain no target at all."""
        total = EventMetrics()
        for item in self.per_clip:
            if item.metrics.expected == 0:
                total = total + item.metrics
        return total

    def worst_clips(self, limit: int = 5) -> List[ClipEventResult]:
        """Recordings producing the most false activations, worst first."""
        return sorted(
            self.per_clip,
            key=lambda item: (item.metrics.false_events, item.metrics.missed),
            reverse=True,
        )[:limit]

    def to_dict(self) -> Dict[str, object]:
        return {
            "detector": asdict(self.detector) if self.detector else None,
            "match_tolerance_seconds": self.tolerance,
            "overall": self.metrics.to_dict(),
            "negative_only": self.negative_only.to_dict(),
            "per_clip": [item.to_dict() for item in self.per_clip],
        }

    def save(self, path: PathLike) -> Path:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
        )
        LOGGER.info("streaming evaluation written to %s", destination)
        return destination

    def summary(self) -> str:
        lines = [self.metrics.summary()]
        negative = self.negative_only
        if negative.duration_seconds > 0:
            lines.append("")
            lines.append(
                f"negative-only stress: {negative.duration_seconds / SECONDS_PER_HOUR:.2f} h "
                f"with no target, {negative.false_events} false event(s), "
                f"{negative.false_activations_per_hour:.2f} FA/h, peak confidence "
                f"{negative.max_negative_score:.3f}"
            )
        worst = [item for item in self.worst_clips() if item.metrics.false_events]
        if worst:
            lines.append("")
            lines.append("worst recordings:")
            for item in worst:
                lines.append(
                    f"  {item.file:<32} {item.metrics.false_events} false "
                    f"({item.category or 'uncategorised'}) at "
                    + ", ".join(f"{value:.1f}s" for value in item.false_times[:6])
                )
        return "\n".join(lines)


def evaluate_timelines(
    scored: Sequence[ScoredClip],
    detector: DetectorConfig,
    tolerance: float = 0.75,
) -> StreamingEvaluation:
    """Replay the event state machine over cached timelines at one operating point."""
    total = EventMetrics()
    per_clip: List[ClipEventResult] = []

    for item in scored:
        events = detect_events(item.timeline, detector)
        match = match_events(events, item.clip.events, tolerance)
        duration = item.clip.duration or item.timeline.duration_seconds
        category = item.clip.category or ("target_session" if item.clip.events else "uncategorised")
        false_times = [events[index].time for index in match.false_detections]

        metrics = EventMetrics(
            expected=len(item.clip.events),
            detected=len(events),
            correct=len(match.matched),
            missed=len(match.missed),
            false_events=len(match.false_detections),
            duplicates=len(match.duplicates),
            duration_seconds=float(duration),
            false_by_category=({category: len(match.false_detections)}
                               if match.false_detections else {}),
            max_negative_score=(
                item.timeline.max_score if item.clip.is_negative_only else 0.0
            ),
        )
        total = total + metrics
        per_clip.append(
            ClipEventResult(
                file=item.clip.file,
                category=item.clip.category,
                metrics=metrics,
                events=events,
                false_times=false_times,
            )
        )

    return StreamingEvaluation(
        metrics=total, per_clip=per_clip, detector=detector, tolerance=tolerance
    )


# ---------------------------------------------------------------------------
# Hard negatives (clip level)
# ---------------------------------------------------------------------------
@dataclass
class NegativeClipReport:
    """Clip-level false-positive rate, broken down by negative category.

    The streaming numbers say how often the detector fires on continuous audio;
    this says *what kind of sound* it fires on, one clip at a time, which is what
    tells a collection effort where the next 50 recordings should come from.
    """

    threshold: float
    total: int
    false_positives: int
    by_type: Dict[str, Dict[str, float]] = field(default_factory=dict)
    worst: List[Dict[str, object]] = field(default_factory=list)

    @property
    def false_positive_rate(self) -> float:
        return self.false_positives / self.total if self.total else float("nan")

    def rate_for(self, negative_type: str) -> float:
        entry = self.by_type.get(negative_type)
        if not entry or not entry.get("clips"):
            return float("nan")
        return float(entry["false_positives"]) / float(entry["clips"])

    @property
    def hard_negative_fp_rate(self) -> float:
        return self.rate_for("hard_negative")

    def to_dict(self) -> Dict[str, object]:
        return {
            "threshold": self.threshold,
            "clips": self.total,
            "false_positives": self.false_positives,
            "false_positive_rate": _round(self.false_positive_rate),
            "hard_negative_fp_rate": _round(self.hard_negative_fp_rate),
            "by_type": self.by_type,
            "worst": self.worst,
        }

    def summary(self) -> str:
        lines = [
            f"threshold            : {self.threshold:.2f}",
            f"negative clips       : {self.total}",
            f"false detections     : {self.false_positives} "
            f"({_percent(self.false_positive_rate)})",
            "",
            f"{'category':<20}{'clips':>7}{'FP':>6}{'rate':>9}{'max P':>8}",
        ]
        for name, entry in sorted(
            self.by_type.items(), key=lambda item: item[1].get("false_positive_rate", 0.0),
            reverse=True,
        ):
            lines.append(
                f"{name:<20}{int(entry['clips']):>7}{int(entry['false_positives']):>6}"
                f"{entry['false_positive_rate'] * 100:>8.1f}%{entry['max_score']:>8.3f}"
            )
        if self.worst:
            lines.append("")
            lines.append("most confident false positives:")
            for item in self.worst:
                lines.append(
                    f"  {item['score']:.3f}  {item['negative_type']:<16} {item['path']}"
                )
        return "\n".join(lines)


def evaluate_negative_clips(
    detector: StreamingDetector,
    records: Sequence,
    root: PathLike,
    threshold: float,
    limit_per_type: Optional[int] = None,
    worst_examples: int = 12,
) -> NegativeClipReport:
    """Score negative clips one window at a time and report what gets through.

    ``records`` are :class:`src.dataset.ManifestRecord` negatives (the ones whose
    ``negative_type`` is set). Each clip is scored as a single window - it is
    already exactly one window long - so this is the same score the streaming
    detector would see with the utterance centred.
    """
    root = Path(root)
    by_type: Dict[str, Dict[str, float]] = defaultdict(
        lambda: {"clips": 0.0, "false_positives": 0.0, "max_score": 0.0, "mean_score": 0.0}
    )
    seen: Counter = Counter()
    offenders: List[Dict[str, object]] = []
    total = false_positives = 0

    for record in records:
        negative_type = getattr(record, "negative_type", None) or "unknown"
        if limit_per_type and seen[negative_type] >= limit_per_type:
            continue
        seen[negative_type] += 1
        path = record.resolve(root)
        if not path.is_file():
            LOGGER.warning("negative clip missing: %s", path)
            continue
        samples = _load(path, detector.frontend.sample_rate)
        features = detector.frontend.features(samples)
        score = float(np.asarray(detector.scorer(features)).ravel()[0])

        total += 1
        entry = by_type[negative_type]
        entry["clips"] += 1
        entry["mean_score"] += score
        entry["max_score"] = max(entry["max_score"], score)
        if score >= threshold:
            false_positives += 1
            entry["false_positives"] += 1
            offenders.append(
                {
                    "path": str(record.path),
                    "negative_type": negative_type,
                    "score": round(score, 4),
                }
            )

    for entry in by_type.values():
        clips = entry["clips"] or 1.0
        entry["mean_score"] = round(entry["mean_score"] / clips, 4)
        entry["max_score"] = round(entry["max_score"], 4)
        entry["false_positive_rate"] = round(entry["false_positives"] / clips, 4)

    offenders.sort(key=lambda item: item["score"], reverse=True)
    return NegativeClipReport(
        threshold=float(threshold),
        total=total,
        false_positives=false_positives,
        by_type={name: dict(entry) for name, entry in sorted(by_type.items())},
        worst=offenders[:worst_examples],
    )


# ---------------------------------------------------------------------------
# Threshold calibration
# ---------------------------------------------------------------------------
@dataclass
class ThresholdRow:
    """One operating point of the sweep."""

    activation: float
    release: float
    metrics: EventMetrics

    def to_dict(self) -> Dict[str, object]:
        return {
            "activation_threshold": round(self.activation, 4),
            "release_threshold": round(self.release, 4),
            **self.metrics.to_dict(),
        }


@dataclass
class CalibrationResult:
    """Outcome of the sweep, including the honest failure case."""

    rows: List[ThresholdRow] = field(default_factory=list)
    activation: Optional[float] = None
    release: Optional[float] = None
    target_fa_per_hour: float = 0.5
    min_recall: float = 0.0
    reason: str = ""

    @property
    def satisfied(self) -> bool:
        return self.activation is not None

    @property
    def chosen(self) -> Optional[ThresholdRow]:
        if self.activation is None:
            return None
        return min(
            (row for row in self.rows if math.isclose(row.activation, self.activation)),
            key=lambda row: abs(row.release - (self.release or row.release)),
            default=None,
        )

    def best_by_fa(self) -> Optional[ThresholdRow]:
        """The row with the fewest false activations, whatever its recall."""
        finite = [row for row in self.rows if np.isfinite(row.metrics.false_activations_per_hour)]
        if not finite:
            return None
        return min(
            finite,
            key=lambda row: (row.metrics.false_activations_per_hour, -(row.metrics.recall or 0.0)),
        )

    def to_dict(self) -> Dict[str, object]:
        chosen = self.chosen
        return {
            "satisfied": self.satisfied,
            "reason": self.reason,
            "target_false_activations_per_hour": self.target_fa_per_hour,
            "min_event_recall": self.min_recall,
            "activation_threshold": self.activation,
            "release_threshold": self.release,
            "chosen": chosen.to_dict() if chosen else None,
            "sweep": [row.to_dict() for row in self.rows],
        }

    def to_dataframe(self):
        import pandas as pd  # lazily imported: only notebooks need pandas

        return pd.DataFrame([row.to_dict() for row in self.rows])

    def save(self, path: PathLike) -> Path:
        destination = Path(path)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
        )
        return destination

    def summary(self) -> str:
        if not self.rows:
            return "no thresholds were evaluated - the streaming set is empty."
        if self.satisfied:
            row = self.chosen
            assert row is not None
            return "\n".join(
                [
                    f"activation threshold : {self.activation:.2f}",
                    f"release threshold    : {self.release:.2f}",
                    f"FA / hour            : {row.metrics.false_activations_per_hour:.2f} "
                    f"(budget {self.target_fa_per_hour:.2f})",
                    f"event precision      : {_percent(row.metrics.precision)}",
                    f"event recall         : {_percent(row.metrics.recall)}",
                    "",
                    "Lowest threshold inside the false-activation budget, which is also "
                    "the highest recall inside it.",
                ]
            )
        best = self.best_by_fa()
        detail = (
            f"The best any threshold managed is "
            f"{best.metrics.false_activations_per_hour:.2f} FA/h at activation "
            f"{best.activation:.2f} (recall {_percent(best.metrics.recall)})."
            if best
            else ""
        )
        return "\n".join(
            [
                "CALIBRATION FAILED - no threshold meets the release criteria.",
                f"budget: <= {self.target_fa_per_hour:.2f} FA/h"
                + (f" and recall >= {self.min_recall:.2f}" if self.min_recall else ""),
                detail,
                "",
                self.reason,
                "",
                "This is a data result, not a tuning result: pushing the threshold higher "
                "trades away recall without fixing what the model is confusing. Collect "
                "hard negatives of the kind listed in the streaming report first.",
            ]
        )


def calibrate_threshold(
    scored: Sequence[ScoredClip],
    config: CalibrationConfig,
    detector: DetectorConfig,
    tolerance: float = 0.75,
) -> CalibrationResult:
    """Sweep the activation threshold under a false-activation budget.

    Selection is *the lowest activation threshold whose measured FA/hour stays
    inside the budget* (and whose recall clears ``min_event_recall`` when one is
    set). Lower thresholds detect more, so the lowest admissible one is also the
    highest-recall admissible one.

    When nothing qualifies the result says so. Reaching for 0.99 and reporting it
    as "calibrated" would ship a model that counts almost nothing and hide the
    fact that the negatives, not the threshold, are the problem (requirement 25).
    """
    rows: List[ThresholdRow] = []
    if not scored:
        return CalibrationResult(
            rows=rows,
            target_fa_per_hour=config.target_false_activations_per_hour,
            min_recall=config.min_event_recall,
            reason="no annotated streaming recordings were scored.",
        )

    steps = int(round((config.max_threshold - config.min_threshold) / config.step)) + 1
    for index in range(max(steps, 1)):
        activation = min(config.min_threshold + index * config.step, config.max_threshold)
        candidate = detector_with_threshold(
            detector,
            activation,
            release=config.release_threshold,
            release_ratio=config.release_ratio,
        )
        evaluation = evaluate_timelines(scored, candidate, tolerance)
        rows.append(
            ThresholdRow(
                activation=activation,
                release=candidate.release_threshold,
                metrics=evaluation.metrics,
            )
        )
        if activation >= config.max_threshold:
            break

    budget = config.target_false_activations_per_hour
    admissible = [
        row
        for row in rows
        if np.isfinite(row.metrics.false_activations_per_hour)
        and row.metrics.false_activations_per_hour <= budget
        and (
            not config.min_event_recall
            or (np.isfinite(row.metrics.recall) and row.metrics.recall >= config.min_event_recall)
        )
    ]
    if not admissible:
        within_budget = [
            row
            for row in rows
            if np.isfinite(row.metrics.false_activations_per_hour)
            and row.metrics.false_activations_per_hour <= budget
        ]
        reason = (
            "No threshold stays inside the false-activation budget."
            if not within_budget
            else (
                f"Thresholds inside the budget exist, but none reaches the required "
                f"event recall of {config.min_event_recall:.2f} - the model cannot be "
                f"both quiet enough and sensitive enough on this data."
            )
        )
        return CalibrationResult(
            rows=rows,
            target_fa_per_hour=budget,
            min_recall=config.min_event_recall,
            reason=reason,
        )

    chosen = min(admissible, key=lambda row: row.activation)
    return CalibrationResult(
        rows=rows,
        activation=chosen.activation,
        release=chosen.release,
        target_fa_per_hour=budget,
        min_recall=config.min_event_recall,
        reason=(
            f"lowest activation threshold within {budget:.2f} FA/h "
            f"({len(admissible)} of {len(rows)} swept thresholds qualified)"
        ),
    )


def load_streaming_set(config: Config) -> List[StreamingClip]:
    """Read the project's ``streaming_test/annotations.json``, or an empty list."""
    root = config.streaming_path
    annotations = root / config.streaming.annotations_file
    if not annotations.is_file():
        LOGGER.warning(
            "no streaming annotations at %s - event metrics, FA/hour and threshold "
            "calibration cannot run, and clip accuracy alone cannot decide whether this "
            "model is shippable.",
            annotations,
        )
        return []
    return load_annotations(annotations)


def streaming_audio_root(config: Config) -> Path:
    """Directory the annotation ``file`` fields are relative to."""
    root = config.streaming_path
    nested = root / config.streaming.audio_dirname
    return nested if nested.is_dir() else root

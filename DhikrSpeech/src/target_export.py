"""Per-target export: one independent model per dhikr, plus its Android contract.

``exports/007/`` holds everything the app needs to run target 007 and nothing
about any other target::

    dhikr_007_float32.tflite      the reference variant
    dhikr_007_int8.tflite         what ships, when quantisation is verified
    model_metadata.json           the Android contract (see build_target_metadata)
    labels.txt                    unknown / target, in output order
    evaluation.json               clip-level metrics
    streaming_evaluation.json     event metrics, FA/hour, calibration
    frontend_test.wav / _expected.npy / _metadata.json

The metadata is the point. Android should need **no hidden constants**: window
length, hop, every front-end parameter, the input and output quantisation scales
and zero points, the detector thresholds this target was calibrated at, and the
numbers the decision was made on. A constant that lives only in Kotlin is a
constant that will drift from the model it was measured for.

Quantisation is not assumed to be free. :func:`compare_variants` scores the same
audio through Keras, float32 TFLite and INT8 TFLite - positives, general
negatives, hard negatives and streaming windows - and reports drift, flipped
decisions and the change in false activations per hour. INT8 is recommended only
when it survives that (requirement 30).
"""

from __future__ import annotations

import hashlib
import json
import logging
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Union

import numpy as np

from .config import Config, DetectorConfig
from .streaming_eval import CountAccuracy, EventMetrics

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "QuantizationReport",
    "ScoreDrift",
    "VariantComparison",
    "build_target_metadata",
    "compare_scores",
    "compare_variants",
    "interpreter_specs",
    "model_filename",
    "output_labels",
    "sha256_file",
    "target_column",
    "tensor_spec",
    "write_target_labels",
    "write_target_metadata",
]


def model_filename(target_id: int, variant: str) -> str:
    """``dhikr_007_int8.tflite`` - the name Android loads by."""
    return f"dhikr_{int(target_id):03d}_{variant}.tflite"


def sha256_file(path: PathLike) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def output_labels(output_mode: str = "softmax") -> List[str]:
    """``labels.txt`` contents, one per model output.

    A softmax detector has two outputs and reads ``unknown`` / ``target``; a
    sigmoid detector has one, which *is* ``P(target)``. Writing two labels for a
    one-output model would make every consumer that checks
    ``len(labels) == num_outputs`` fall back to positional names.
    """
    return ["target"] if output_mode == "sigmoid" else ["unknown", "target"]


def target_column(output_mode: str = "softmax") -> int:
    """Which output column holds ``P(target)``."""
    return 0 if output_mode == "sigmoid" else 1


def write_target_labels(
    path: PathLike, target_id: int, target_text: str, output_mode: str = "softmax"
) -> Path:
    """``labels.txt`` in output order, plus the target's text alongside it."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    labels = output_labels(output_mode)
    destination.write_text("\n".join(labels) + "\n", encoding="utf-8")
    destination.with_name("labels_target.json").write_text(
        json.dumps(
            {
                "target_phrase_id": f"{int(target_id):03d}",
                "target_phrase_text": target_text,
                "output_mode": output_mode,
                "labels": labels,
                "target_index": target_column(output_mode),
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    return destination


def tensor_spec(detail: Optional[Dict]) -> Dict[str, object]:
    """Shape, dtype and quantisation of one interpreter tensor.

    ``scale``/``zero_point`` are what an INT8 model needs on the other side:
    ``real = (quantised - zero_point) * scale``. A float model reports
    ``scale = 0``, which is the interpreter's way of saying "not quantised", and
    is passed through unchanged rather than normalised away - Android checks the
    dtype, not the scale.
    """
    if not detail:
        return {}
    quantization = detail.get("quantization") or (0.0, 0)
    scale = float(quantization[0]) if len(quantization) > 0 else 0.0
    zero_point = int(quantization[1]) if len(quantization) > 1 else 0
    return {
        "name": str(detail.get("name", "")),
        "shape": [int(value) for value in detail.get("shape", [])],
        "dtype": np.dtype(detail["dtype"]).name if detail.get("dtype") is not None else "",
        "quantization": {
            "scale": scale,
            "zero_point": zero_point,
            "quantized": bool(scale),
        },
    }


def interpreter_specs(interpreter) -> Dict[str, Dict[str, object]]:
    """``{"input": ..., "output": ...}`` for a built TFLite interpreter."""
    return {
        "input": tensor_spec(interpreter.get_input_details()[0]),
        "output": tensor_spec(interpreter.get_output_details()[0]),
    }


# ---------------------------------------------------------------------------
# The Android contract
# ---------------------------------------------------------------------------
def build_target_metadata(
    config: Config,
    target_id: int,
    target_text: str,
    architecture: str,
    frontend: Dict,
    detector: DetectorConfig,
    model_file: str,
    parameter_count: int,
    file_size_bytes: int,
    sha256: str,
    tensors: Optional[Dict[str, Dict[str, object]]] = None,
    dataset: Optional[Dict[str, object]] = None,
    streaming: Optional[EventMetrics] = None,
    counts: Optional[CountAccuracy] = None,
    evaluation: Optional[Dict[str, object]] = None,
    calibration: Optional[Dict[str, object]] = None,
    quantization: Optional[Dict[str, object]] = None,
    readiness: Optional[Dict[str, object]] = None,
    model_version: str = "1",
    variants: Optional[Dict[str, str]] = None,
) -> Dict[str, object]:
    """Assemble ``model_metadata.json``.

    Deliberately self-contained: everything Android needs to load this model,
    build its features, run the detector and know how far to trust it. In
    particular the detector thresholds belong to *this* target - parameters
    calibrated for 006 say nothing about 007, and shipping one set of constants
    for every model is exactly the mistake this file exists to prevent
    (requirement 26).
    """
    frames, mels, channels = config.input_shape
    window_seconds = config.streaming.window_for(config.audio.clip_seconds)

    payload: Dict[str, object] = {
        "target_phrase_id": f"{int(target_id):03d}",
        "target_phrase_text": target_text,
        "model_version": str(model_version),
        "architecture": architecture,
        "output_mode": config.target.output_mode,
        "task": (
            "binary phrase spotting: does this window contain the complete target "
            "phrase? Every other sound, including other dhikr and incomplete "
            "versions of this phrase, is 'unknown'."
        ),
        "sample_rate": int(config.audio.sample_rate),
        "clip_samples": int(config.audio.clip_samples),
        "window_seconds": float(window_seconds),
        "hop_seconds": float(config.streaming.hop_seconds),
        "labels": output_labels(config.target.output_mode),
        "target_index": target_column(config.target.output_mode),
        "feature": {
            "n_mels": int(config.features.n_mels),
            "window_ms": float(config.features.window_ms),
            "hop_ms": float(config.features.hop_ms),
            "n_fft": int(config.features.n_fft),
            "fmin": float(config.features.fmin),
            "fmax": float(min(config.features.fmax, config.audio.sample_rate / 2.0)),
            "log_offset": float(config.features.log_offset),
            "normalization": config.features.normalize,
            "center": bool(config.features.center),
            "input_shape": [frames, mels, channels],
            "input_layout": "(frames, mel_bins, 1) float32 log-mel, time major",
            **{key: value for key, value in frontend.items() if key not in {"n_mels", "n_fft"}},
        },
        "audio_conditioning": {
            "channels": int(config.audio.channels),
            "bit_depth": int(config.audio.bit_depth),
            "trim_windows": False,
            "loudness_normalize": bool(config.audio.normalize.enabled),
            "target_dbfs": float(config.audio.normalize.target_dbfs),
            "peak_ceiling": float(config.audio.normalize.peak_ceiling),
            "fit_mode": config.audio.fit_mode,
            "note": (
                "Streaming windows are level-normalised and never silence-trimmed; "
                "trimming a window slides its content in time."
            ),
        },
        # `frontend`, `audio` and `classes` repeat information already present
        # above, in the shape the pre-single-target tooling reads (space/ builds
        # its front-end from `frontend` and falls back to config.yaml without it,
        # which would silently feed the model features it was never trained on).
        # Cheap duplication, and it keeps the Gradio test app working against a
        # per-target export.
        "frontend": dict(frontend),
        "audio": {
            "sample_rate": int(config.audio.sample_rate),
            "channels": int(config.audio.channels),
            "bit_depth": int(config.audio.bit_depth),
            "clip_seconds": float(config.audio.clip_seconds),
            "clip_samples": int(config.audio.clip_samples),
            "fit_mode": config.audio.fit_mode,
            "trim": {
                "enabled": bool(config.audio.trim.enabled),
                "top_db": float(config.audio.trim.top_db),
                "pad_ms": float(config.audio.trim.pad_ms),
            },
            "normalize": {
                "enabled": bool(config.audio.normalize.enabled),
                "target_dbfs": float(config.audio.normalize.target_dbfs),
                "peak_ceiling": float(config.audio.normalize.peak_ceiling),
            },
        },
        "classes": [
            {"index": index, "label": label}
            for index, label in enumerate(output_labels(config.target.output_mode))
        ],
        "tensors": tensors or {},
        "detection": {
            "activation_threshold": float(detector.activation_threshold),
            "release_threshold": float(detector.release_threshold),
            "min_consecutive_hits": int(detector.min_consecutive_hits),
            "min_event_seconds": float(detector.min_event_seconds),
            "release_windows": int(detector.release_windows),
            "cooldown_ms": float(detector.cooldown_ms),
            "smoothing": {
                "mode": config.streaming.smoothing.mode,
                "ema_alpha": float(config.streaming.smoothing.ema_alpha),
                "window": int(config.streaming.smoothing.window),
            },
            "state_machine": "idle -> candidate -> confirmed -> cooldown -> idle",
            "note": (
                "Count on confirmation, then wait for the score to fall below the "
                "release threshold for release_windows before re-arming. The cooldown "
                "is a safety net, not the separator between repetitions."
            ),
        },
        "model": {
            "file": model_file,
            "parameter_count": int(parameter_count),
            "file_size_bytes": int(file_size_bytes),
            "file_size_kb": round(file_size_bytes / 1024.0, 2),
            "sha256": sha256,
            "variants": variants or {},
        },
        "seed": int(config.seed),
    }

    if dataset:
        payload["dataset"] = dataset
    if evaluation:
        payload["evaluation"] = evaluation
    if streaming is not None:
        payload["streaming"] = streaming.to_dict()
    if counts is not None:
        payload["counts"] = counts.to_dict()
    if calibration:
        payload["calibration"] = calibration
    if quantization:
        payload["quantization"] = quantization
    if readiness:
        payload["readiness"] = readiness
    return payload


def write_target_metadata(path: PathLike, payload: Dict[str, object]) -> Path:
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    LOGGER.info("target metadata written to %s", destination)
    return destination


# ---------------------------------------------------------------------------
# Quantisation comparison
# ---------------------------------------------------------------------------
@dataclass
class ScoreDrift:
    """How far a variant's ``P(target)`` moved from the reference, on one clip set.

    ``disagreements`` is the number that actually matters: probabilities drifting
    by 0.02 in the middle of the range change nothing, the same drift across the
    activation threshold changes a count.
    """

    name: str
    samples: int
    threshold: float
    max_delta: float
    mean_delta: float
    disagreements: int
    reference_accept_rate: float
    candidate_accept_rate: float

    @property
    def disagreement_rate(self) -> float:
        return self.disagreements / self.samples if self.samples else float("nan")

    def to_dict(self) -> Dict[str, object]:
        return {**asdict(self), "disagreement_rate": round(self.disagreement_rate, 4)}


def compare_scores(
    reference: Sequence[float],
    candidate: Sequence[float],
    name: str,
    threshold: float,
) -> ScoreDrift:
    """Drift between two score vectors over the same clips, in the same order."""
    left = np.asarray(list(reference), dtype=np.float64).ravel()
    right = np.asarray(list(candidate), dtype=np.float64).ravel()
    if left.size != right.size:
        raise ValueError(
            f"'{name}': {left.size} reference scores vs {right.size} candidate scores - "
            "both variants must be scored on the same clips, in the same order"
        )
    if left.size == 0:
        return ScoreDrift(name, 0, float(threshold), 0.0, 0.0, 0, float("nan"), float("nan"))

    delta = np.abs(left - right)
    accepted_left = left >= threshold
    accepted_right = right >= threshold
    return ScoreDrift(
        name=name,
        samples=int(left.size),
        threshold=float(threshold),
        max_delta=float(delta.max()),
        mean_delta=float(delta.mean()),
        disagreements=int(np.count_nonzero(accepted_left != accepted_right)),
        reference_accept_rate=float(accepted_left.mean()),
        candidate_accept_rate=float(accepted_right.mean()),
    )


@dataclass
class VariantComparison:
    """One exported variant measured against the Keras reference."""

    variant: str
    drifts: List[ScoreDrift] = field(default_factory=list)
    reference_events: Optional[EventMetrics] = None
    candidate_events: Optional[EventMetrics] = None
    tolerance: float = 0.05
    max_fa_increase: float = 0.10
    size_bytes: int = 0

    @property
    def max_delta(self) -> float:
        return max((drift.max_delta for drift in self.drifts), default=0.0)

    @property
    def disagreements(self) -> int:
        return sum(drift.disagreements for drift in self.drifts)

    @property
    def fa_per_hour_delta(self) -> float:
        """Extra false activations per hour this variant costs. NaN without events."""
        if self.reference_events is None or self.candidate_events is None:
            return float("nan")
        reference = self.reference_events.false_activations_per_hour
        candidate = self.candidate_events.false_activations_per_hour
        if not np.isfinite(reference) or not np.isfinite(candidate):
            return float("nan")
        return float(candidate - reference)

    @property
    def event_count_delta(self) -> Optional[int]:
        if self.reference_events is None or self.candidate_events is None:
            return None
        return int(self.candidate_events.detected - self.reference_events.detected)

    @property
    def accepted(self) -> bool:
        """Whether this variant is safe to recommend for production.

        Rejected when the probabilities drift beyond tolerance **or** when it
        costs measurably more false activations per hour - the metric the whole
        project is optimised for. An unmeasured event comparison is not treated
        as a pass by itself; it just cannot veto.
        """
        if self.max_delta > self.tolerance:
            return False
        drift = self.fa_per_hour_delta
        if np.isfinite(drift) and drift > self.max_fa_increase:
            return False
        return True

    def reasons(self) -> List[str]:
        notes: List[str] = []
        if self.max_delta > self.tolerance:
            notes.append(
                f"probabilities drift up to {self.max_delta:.4f}, beyond the "
                f"{self.tolerance:.2f} tolerance"
            )
        drift = self.fa_per_hour_delta
        if np.isfinite(drift) and drift > self.max_fa_increase:
            notes.append(
                f"false activations rise by {drift:+.2f}/hour, beyond the "
                f"{self.max_fa_increase:.2f} allowance - this variant counts things the "
                f"reference does not"
            )
        if self.disagreements:
            notes.append(
                f"{self.disagreements} clip decision(s) flipped across the threshold"
            )
        if self.reference_events is None or self.candidate_events is None:
            notes.append(
                "no streaming comparison was run, so the event-level effect of "
                "quantisation is unmeasured - clip drift alone cannot rule it out"
            )
        return notes

    def to_dict(self) -> Dict[str, object]:
        return {
            "variant": self.variant,
            "accepted": self.accepted,
            "max_probability_delta": round(self.max_delta, 6),
            "tolerance": self.tolerance,
            "threshold_disagreements": self.disagreements,
            "fa_per_hour_delta": (
                round(self.fa_per_hour_delta, 4)
                if np.isfinite(self.fa_per_hour_delta)
                else None
            ),
            "event_count_delta": self.event_count_delta,
            "size_bytes": self.size_bytes,
            "clip_sets": [drift.to_dict() for drift in self.drifts],
            "reference_events": (
                self.reference_events.to_dict() if self.reference_events else None
            ),
            "candidate_events": (
                self.candidate_events.to_dict() if self.candidate_events else None
            ),
            "reasons": self.reasons(),
        }

    def summary(self) -> str:
        verdict = "ok" if self.accepted else "REJECTED"
        drift = self.fa_per_hour_delta
        lines = [
            f"{self.variant:<14} max delta {self.max_delta:.4f} | "
            f"flips {self.disagreements:3d} | "
            f"FA/h {'n/a' if not np.isfinite(drift) else f'{drift:+.2f}'} | {verdict}"
        ]
        for drift_row in self.drifts:
            lines.append(
                f"    {drift_row.name:<18} {drift_row.samples:4d} clips | "
                f"max {drift_row.max_delta:.4f} | flips {drift_row.disagreements}"
            )
        for reason in self.reasons():
            lines.append(f"    !! {reason}")
        return "\n".join(lines)


@dataclass
class QuantizationReport:
    """Every exported variant, and which one to ship."""

    comparisons: List[VariantComparison] = field(default_factory=list)

    def recommended(self) -> Optional[VariantComparison]:
        """Smallest accepted variant, preferring INT8 when it survives.

        Never falls back to a rejected variant: when nothing is accepted the
        answer is "none", so the notebook has to say so rather than quietly
        shipping the one that failed verification.
        """
        accepted = [item for item in self.comparisons if item.accepted]
        if not accepted:
            return None
        return min(accepted, key=lambda item: (item.size_bytes or float("inf"), item.variant))

    def to_dict(self) -> Dict[str, object]:
        recommended = self.recommended()
        return {
            "recommended": recommended.variant if recommended else None,
            "variants": [item.to_dict() for item in self.comparisons],
        }

    def summary(self) -> str:
        lines = [item.summary() for item in self.comparisons]
        recommended = self.recommended()
        lines.append("")
        if recommended is None:
            lines.append(
                "NO VARIANT PASSED. Do not ship a quantised model on the strength of "
                "clip accuracy: re-check the calibration set (it must be real training "
                "features), and compare float32 before assuming INT8 is the problem."
            )
        else:
            lines.append(f"recommended for production: {recommended.variant}")
            if recommended.variant != "int8":
                lines.append(
                    "INT8 was not recommended - it is ~4x smaller, so this is a real "
                    "cost. The reasons above say whether it drifted or whether it "
                    "counted more."
                )
        return "\n".join(lines)


def compare_variants(
    reference_scores: Dict[str, Sequence[float]],
    candidate_scores: Dict[str, Dict[str, Sequence[float]]],
    threshold: float,
    tolerance: float = 0.05,
    max_fa_increase: float = 0.10,
    reference_events: Optional[EventMetrics] = None,
    candidate_events: Optional[Dict[str, EventMetrics]] = None,
    sizes: Optional[Dict[str, int]] = None,
) -> QuantizationReport:
    """Compare exported variants against the Keras reference on several clip sets.

    ``reference_scores`` and each entry of ``candidate_scores`` are keyed by clip
    set - ``positives``, ``negatives``, ``hard_negatives``, ``streaming_windows``
    - because a variant that is faithful on positives and drifts on hard
    negatives is exactly the variant that starts counting the wrong phrase, and a
    single averaged number would hide it.
    """
    report = QuantizationReport()
    events = candidate_events or {}
    sizes = sizes or {}

    for variant, per_set in candidate_scores.items():
        drifts = [
            compare_scores(reference_scores[name], scores, name, threshold)
            for name, scores in per_set.items()
            if name in reference_scores
        ]
        report.comparisons.append(
            VariantComparison(
                variant=variant,
                drifts=drifts,
                reference_events=reference_events,
                candidate_events=events.get(variant),
                tolerance=tolerance,
                max_fa_increase=max_fa_increase,
                size_bytes=int(sizes.get(variant, 0)),
            )
        )
    return report

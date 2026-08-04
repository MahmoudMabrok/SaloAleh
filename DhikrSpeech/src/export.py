"""SavedModel / TFLite export, benchmarking and numerical verification.

Three TFLite variants are produced from one SavedModel:

===============  ==========================  ========================
variant          weights                     activations
===============  ==========================  ========================
float32          float32                     float32
dynamic_range    int8 (weights only)         float32
int8             int8                        int8 (needs a calibration set)
===============  ==========================  ========================

``int8`` is the one to ship on Android unless a measurement says otherwise; it is
roughly 4x smaller than float32 and runs on integer kernels. Always read the
verification report before shipping a quantised model.
"""

from __future__ import annotations

import json
import logging
import shutil
import statistics
import time
import warnings
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Dict, Iterator, List, Optional, Sequence, Tuple, Union

import numpy as np
import tensorflow as tf

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]

__all__ = [
    "BenchmarkResult",
    "ExportedModel",
    "VerificationResult",
    "benchmark_tflite",
    "convert_tflite",
    "export_saved_model",
    "make_interpreter",
    "representative_dataset_from_arrays",
    "verify_tflite",
    "write_labels",
    "write_metadata",
]

_MODES = ("float32", "dynamic_range", "int8")


# ---------------------------------------------------------------------------
# SavedModel
# ---------------------------------------------------------------------------
def export_saved_model(model, directory: PathLike, overwrite: bool = True) -> Path:
    """Write a SavedModel, using the Keras 3 ``export`` API when available."""
    target = Path(directory)
    if target.exists() and overwrite:
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)

    if hasattr(model, "export"):  # Keras 3
        model.export(str(target))
    else:  # Keras 2
        model.save(str(target), save_format="tf")
    LOGGER.info("SavedModel written to %s", target)
    return target


# ---------------------------------------------------------------------------
# Conversion
# ---------------------------------------------------------------------------
def representative_dataset_from_arrays(
    features: np.ndarray, max_samples: int = 300
) -> Callable[[], Iterator[List[np.ndarray]]]:
    """Calibration generator for full INT8 quantisation.

    ``features`` must be real training features - the converter measures the
    activation range from them, so synthetic noise would produce a badly scaled
    model.
    """
    array = np.asarray(features, dtype=np.float32)
    if array.ndim == 3:  # (N, frames, mels) -> add the channel axis
        array = array[..., np.newaxis]
    if array.ndim != 4:
        raise ValueError(f"expected (N, frames, mels, 1) features, got shape {array.shape}")
    subset = array[: max(int(max_samples), 1)]
    if subset.shape[0] == 0:
        raise ValueError("the representative dataset is empty")

    def generator() -> Iterator[List[np.ndarray]]:
        for sample in subset:
            yield [sample[np.newaxis, ...].astype(np.float32)]

    return generator


def collect_features(dataset, max_samples: int) -> np.ndarray:
    """Pull up to ``max_samples`` feature tensors out of a batched tf.data pipeline."""
    collected: List[np.ndarray] = []
    total = 0
    for batch_features, _ in dataset:
        batch = np.asarray(batch_features, dtype=np.float32)
        collected.append(batch)
        total += batch.shape[0]
        if total >= max_samples:
            break
    if not collected:
        raise ValueError("the dataset produced no batches")
    return np.concatenate(collected, axis=0)[:max_samples]


def convert_tflite(
    saved_model_dir: PathLike,
    mode: str = "float32",
    representative_dataset: Optional[Callable[[], Iterator[List[np.ndarray]]]] = None,
) -> bytes:
    """Convert a SavedModel to a TFLite flatbuffer in one of the three modes."""
    if mode not in _MODES:
        raise ValueError(f"mode must be one of {_MODES}, got '{mode}'")

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))

    if mode == "dynamic_range":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif mode == "int8":
        if representative_dataset is None:
            raise ValueError("int8 quantisation requires a representative dataset")
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = representative_dataset
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.int8
        converter.inference_output_type = tf.int8

    flatbuffer = converter.convert()
    LOGGER.info("converted to TFLite (%s): %.1f KB", mode, len(flatbuffer) / 1024.0)
    return flatbuffer


def write_tflite(flatbuffer: bytes, path: PathLike) -> Path:
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(flatbuffer)
    return destination


# ---------------------------------------------------------------------------
# Interpreter helpers
# ---------------------------------------------------------------------------
def make_interpreter(model_path: PathLike, num_threads: int = 1):
    """Build a TFLite interpreter.

    LiteRT is preferred because ``tf.lite.Interpreter`` is deprecated and warns on
    every construction from TF 2.20; ``tf.lite`` remains the fallback so the code
    keeps working on TF builds that predate the split.
    """
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=str(model_path), num_threads=num_threads)
    except ImportError:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            return tf.lite.Interpreter(model_path=str(model_path), num_threads=num_threads)


def _quantize_input(array: np.ndarray, detail: Dict) -> np.ndarray:
    """Cast float features into the interpreter's input dtype."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(dtype)
    quantized = np.round(array / scale) + zero_point
    info = np.iinfo(dtype)
    return np.clip(quantized, info.min, info.max).astype(dtype)


def _dequantize_output(array: np.ndarray, detail: Dict) -> np.ndarray:
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(np.float32)
    return (array.astype(np.float32) - zero_point) * scale


def tflite_predict(interpreter, features: np.ndarray) -> np.ndarray:
    """Run one clip (or a batch, one clip at a time) through an interpreter."""
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]

    batch = np.asarray(features, dtype=np.float32)
    if batch.ndim == 3:
        batch = batch[np.newaxis, ...]

    outputs: List[np.ndarray] = []
    for sample in batch:
        interpreter.set_tensor(
            input_detail["index"], _quantize_input(sample[np.newaxis, ...], input_detail)
        )
        interpreter.invoke()
        raw = interpreter.get_tensor(output_detail["index"])
        outputs.append(_dequantize_output(raw, output_detail)[0])
    return np.stack(outputs)


# ---------------------------------------------------------------------------
# Benchmarking
# ---------------------------------------------------------------------------
@dataclass
class BenchmarkResult:
    """Latency and footprint of one exported model.

    ``size_kb`` is the flatbuffer on flash. ``arena_estimate_kb`` sums every
    tensor the interpreter allocates - a close proxy for the runtime arena, not a
    reading from the allocator. ``expected_android_ms`` is
    ``mean_latency_ms * export.android_latency_factor``: an estimate to compare
    variants with, never a substitute for measuring on a real device.
    """

    name: str
    path: str
    size_kb: float
    input_dtype: str
    output_dtype: str
    input_shape: List[int]
    runs: int
    mean_latency_ms: float
    median_latency_ms: float
    p95_latency_ms: float
    min_latency_ms: float
    max_latency_ms: float
    arena_estimate_kb: float
    expected_android_ms: float

    def summary(self) -> str:
        return (
            f"{self.name:<14} {self.size_kb / 1024.0:6.2f} MB | "
            f"{self.mean_latency_ms:7.2f} ms mean | {self.p95_latency_ms:7.2f} ms p95 | "
            f"arena ~{self.arena_estimate_kb:7.1f} KB | "
            f"android ~{self.expected_android_ms:7.2f} ms | io {self.input_dtype}/{self.output_dtype}"
        )


def _arena_estimate_bytes(interpreter) -> float:
    total = 0
    for detail in interpreter.get_tensor_details():
        shape = detail.get("shape")
        dtype = detail.get("dtype")
        if shape is None or dtype is None or len(shape) == 0:
            continue
        try:
            total += int(np.prod(shape)) * int(np.dtype(dtype).itemsize)
        except (TypeError, ValueError):
            continue
    return float(total)


def benchmark_tflite(
    model_path: PathLike,
    name: str,
    runs: int = 100,
    warmup: int = 10,
    android_latency_factor: float = 3.0,
    num_threads: int = 1,
) -> BenchmarkResult:
    """Time ``runs`` single-clip invocations after ``warmup`` untimed ones."""
    path = Path(model_path)
    interpreter = make_interpreter(path, num_threads=num_threads)
    interpreter.allocate_tensors()

    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    shape = [int(dim) for dim in input_detail["shape"]]

    sample = np.random.default_rng(0).standard_normal(shape).astype(np.float32)
    payload = _quantize_input(sample, input_detail)

    for _ in range(max(warmup, 0)):
        interpreter.set_tensor(input_detail["index"], payload)
        interpreter.invoke()

    timings: List[float] = []
    for _ in range(max(runs, 1)):
        start = time.perf_counter()
        interpreter.set_tensor(input_detail["index"], payload)
        interpreter.invoke()
        interpreter.get_tensor(output_detail["index"])
        timings.append((time.perf_counter() - start) * 1000.0)

    ordered = sorted(timings)
    mean_latency = float(statistics.fmean(timings))
    return BenchmarkResult(
        name=name,
        path=str(path),
        size_kb=path.stat().st_size / 1024.0,
        input_dtype=np.dtype(input_detail["dtype"]).name,
        output_dtype=np.dtype(output_detail["dtype"]).name,
        input_shape=shape,
        runs=len(timings),
        mean_latency_ms=mean_latency,
        median_latency_ms=float(statistics.median(timings)),
        p95_latency_ms=float(ordered[min(int(len(ordered) * 0.95), len(ordered) - 1)]),
        min_latency_ms=float(ordered[0]),
        max_latency_ms=float(ordered[-1]),
        arena_estimate_kb=_arena_estimate_bytes(interpreter) / 1024.0,
        expected_android_ms=mean_latency * float(android_latency_factor),
    )


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------
@dataclass
class VerificationResult:
    """Agreement between the Keras model and an exported TFLite model."""

    name: str
    samples: int
    agreement: float
    max_probability_delta: float
    mean_probability_delta: float
    tolerance: float

    @property
    def passed(self) -> bool:
        return self.agreement >= 0.99 and self.max_probability_delta <= self.tolerance

    def summary(self) -> str:
        verdict = "ok" if self.passed else "CHECK"
        return (
            f"{self.name:<14} agreement {self.agreement:6.2%} | "
            f"max delta {self.max_probability_delta:.4f} | "
            f"mean delta {self.mean_probability_delta:.4f} | {verdict}"
        )


def verify_tflite(
    model_path: PathLike,
    keras_model,
    features: np.ndarray,
    name: str,
    tolerance: float = 0.05,
) -> VerificationResult:
    """Compare TFLite predictions against the Keras model on real features."""
    batch = np.asarray(features, dtype=np.float32)
    if batch.ndim == 3:
        batch = batch[..., np.newaxis]

    reference = np.asarray(keras_model(batch, training=False), dtype=np.float32)

    interpreter = make_interpreter(model_path)
    interpreter.allocate_tensors()
    predicted = tflite_predict(interpreter, batch)

    deltas = np.abs(reference - predicted)
    agreement = float(
        (reference.argmax(axis=1) == predicted.argmax(axis=1)).mean()
    )
    return VerificationResult(
        name=name,
        samples=int(batch.shape[0]),
        agreement=agreement,
        max_probability_delta=float(deltas.max()),
        mean_probability_delta=float(deltas.mean()),
        tolerance=float(tolerance),
    )


# ---------------------------------------------------------------------------
# Sidecar files
# ---------------------------------------------------------------------------
def write_labels(path: PathLike, class_names: Sequence[str], phrases: Optional[Dict] = None) -> Path:
    """One label per line, in class-index order - the format Android expects."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(class_names) + "\n", encoding="utf-8")

    if phrases is not None:
        mapping = destination.with_name("labels_phrases.json")
        payload = [
            {
                "index": index,
                "label": label,
                "id": int(label) if label.isdigit() else None,
                "text": phrases.get(int(label), "") if label.isdigit() else "",
            }
            for index, label in enumerate(class_names)
        ]
        mapping.write_text(
            json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
        )
    return destination


def write_metadata(
    path: PathLike,
    config,
    class_names: Sequence[str],
    frontend: Dict,
    benchmarks: Sequence[BenchmarkResult] = (),
    verifications: Sequence[VerificationResult] = (),
    metrics: Optional[Dict] = None,
) -> Path:
    """Single JSON describing the exported model, its front-end and its numbers."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    frames, mels, channels = config.input_shape
    payload = {
        "model": {
            "name": config.model.name,
            "input_shape": [frames, mels, channels],
            "input_layout": "(frames, mel_bins, 1) float32 log-mel, time major",
            "output": "softmax probabilities, one per class",
            "num_classes": len(class_names),
        },
        "classes": [
            {"index": index, "label": label} for index, label in enumerate(class_names)
        ],
        "audio": {
            "sample_rate": config.audio.sample_rate,
            "channels": config.audio.channels,
            "bit_depth": config.audio.bit_depth,
            "clip_seconds": config.audio.clip_seconds,
            "clip_samples": config.audio.clip_samples,
            "trim": asdict(config.audio.trim),
            "normalize": asdict(config.audio.normalize),
            "fit_mode": config.audio.fit_mode,
        },
        "frontend": frontend,
        "benchmarks": [asdict(item) for item in benchmarks],
        "verification": [asdict(item) for item in verifications],
        "metrics": metrics or {},
        "seed": config.seed,
    }
    destination.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    LOGGER.info("export metadata written to %s", destination)
    return destination


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------
@dataclass
class ExportedModel:
    name: str
    path: Path
    benchmark: Optional[BenchmarkResult] = None
    verification: Optional[VerificationResult] = None


@dataclass
class ExportBundle:
    """Everything notebook 05 produces."""

    saved_model: Optional[Path]
    models: List[ExportedModel] = field(default_factory=list)
    labels_path: Optional[Path] = None
    metadata_path: Optional[Path] = None
    filterbank_path: Optional[Path] = None

    def table(self) -> str:
        lines = [item.benchmark.summary() for item in self.models if item.benchmark]
        checks = [item.verification.summary() for item in self.models if item.verification]
        return "\n".join(lines + [""] + checks) if checks else "\n".join(lines)

    def recommended(self) -> Optional[ExportedModel]:
        """Smallest model that still agrees with the Keras reference."""
        passing = [
            item
            for item in self.models
            if item.benchmark and (item.verification is None or item.verification.passed)
        ]
        if not passing:
            return None
        return min(passing, key=lambda item: item.benchmark.size_kb)


def export_all(
    model,
    config,
    class_names: Sequence[str],
    frontend: Dict,
    calibration_features: np.ndarray,
    verification_features: Optional[np.ndarray] = None,
    phrases: Optional[Dict] = None,
    metrics: Optional[Dict] = None,
    output_dir: Optional[PathLike] = None,
) -> ExportBundle:
    """Export every enabled variant, benchmark it and verify it against Keras."""
    export_config = config.export
    destination = Path(output_dir or config.paths.exports_path)
    destination.mkdir(parents=True, exist_ok=True)

    saved_model_dir = destination / "saved_model"
    export_saved_model(model, saved_model_dir)

    bundle = ExportBundle(saved_model=saved_model_dir if export_config.saved_model else None)
    representative = representative_dataset_from_arrays(
        calibration_features, export_config.representative_samples
    )

    requested: List[Tuple[str, bool]] = [
        ("float32", export_config.float32),
        ("dynamic_range", export_config.dynamic_range),
        ("int8", export_config.int8),
    ]

    for mode, enabled in requested:
        if not enabled:
            continue
        try:
            flatbuffer = convert_tflite(
                saved_model_dir,
                mode=mode,
                representative_dataset=representative if mode == "int8" else None,
            )
        except Exception as exc:  # noqa: BLE001 - one failed variant must not stop the rest
            LOGGER.error("conversion failed for %s: %s", mode, exc)
            continue

        model_path = write_tflite(flatbuffer, destination / f"dhikr_{mode}.tflite")
        exported = ExportedModel(name=mode, path=model_path)
        exported.benchmark = benchmark_tflite(
            model_path,
            name=mode,
            runs=export_config.benchmark_runs,
            warmup=export_config.benchmark_warmup,
            android_latency_factor=export_config.android_latency_factor,
        )
        if verification_features is not None and len(verification_features):
            exported.verification = verify_tflite(
                model_path,
                model,
                verification_features,
                name=mode,
                tolerance=export_config.verify_tolerance,
            )
        bundle.models.append(exported)

    bundle.labels_path = write_labels(destination / "labels.txt", class_names, phrases)
    bundle.metadata_path = write_metadata(
        destination / "model_meta.json",
        config,
        class_names,
        frontend,
        benchmarks=[item.benchmark for item in bundle.models if item.benchmark],
        verifications=[item.verification for item in bundle.models if item.verification],
        metrics=metrics,
    )
    return bundle

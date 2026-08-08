"""Inference layer for the DhikrSpeech Hugging Face Space.

The Space has to reproduce the training front-end exactly, so nothing here
reimplements it: the conditioning comes from ``src.audio`` and the log-mel
extractor from ``src.features``, the same modules the notebook trains with.

What this module adds on top is the part that is specific to the Space:

* loading an exported ``.tflite`` (or Keras) model **without** importing
  TensorFlow when a LiteRT runtime is available, so the Space stays light;
* rebuilding the front-end from ``model_meta.json`` rather than from
  ``configs/config.yaml``, because the exported model is the authority - a
  config that has moved on since the export would silently produce features the
  model was never trained on.

The sliding-window front-end, the TFLite runtime helpers and the event detector
are **not** reimplemented here: they come from ``src.streaming``, the same code
the notebook calibrates thresholds with. A Space that counted by its own rules
would happily disagree with the numbers the pipeline reports.
"""

from __future__ import annotations

import json
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

# The Space runs either from a checkout (src/ one level up) or from the synced
# Space repo (src/ next to app.py). Support both without a package install.
_HERE = Path(__file__).resolve().parent
for _candidate in (_HERE, _HERE.parent):
    if (_candidate / "src" / "__init__.py").exists() and str(_candidate) not in sys.path:
        sys.path.insert(0, str(_candidate))

from src.config import Config, DetectorConfig, default_config_path  # noqa: E402
from src.features import FeatureStats  # noqa: E402
from src.streaming import (  # noqa: E402
    StreamingFrontend,
    detect_events,
    dequantize_output as _dequantize_output,
    make_interpreter as _make_interpreter,
    quantize_input as _quantize_input,
)

LOGGER = logging.getLogger(__name__)

__all__ = [
    "Detection",
    "DhikrModel",
    "Frontend",
    "ScanResult",
    "count_detections",
    "discover_models",
    "load_phrases",
    "resolve_config",
]

UNKNOWN_LABEL = "unknown"


# ---------------------------------------------------------------------------
# Phrases
# ---------------------------------------------------------------------------
def load_phrases(path: Optional[Path] = None) -> Dict[int, str]:
    """``{phrase id: Arabic text}`` from ``phrases.json``; empty when missing."""
    candidates = [path] if path else []
    candidates += [_HERE / "phrases.json", _HERE.parent / "phrases.json"]
    for candidate in candidates:
        if candidate and candidate.exists():
            try:
                data = json.loads(candidate.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError) as exc:
                LOGGER.warning("could not read %s: %s", candidate, exc)
                continue
            return {int(item["id"]): str(item["text"]) for item in data if "id" in item}
    return {}


def display_label(label: str, phrases: Dict[int, str]) -> str:
    """``"006"`` -> ``"006 · سبحان الله وبحمده"``, ``"unknown"`` unchanged."""
    if label.isdigit():
        text = phrases.get(int(label), "")
        if text:
            return f"{label} · {text}"
    return label


# ---------------------------------------------------------------------------
# Front-end
# ---------------------------------------------------------------------------
def resolve_config(meta: Optional[Dict] = None, config_path: Optional[Path] = None) -> Config:
    """Config for a model, with ``model_meta.json`` overriding ``config.yaml``.

    The exported metadata records the front-end the weights were actually
    trained with. ``configs/config.yaml`` is a moving target - it may have been
    retuned since the export - so anything the metadata states wins.
    """
    base = Config.load(config_path or default_config_path())
    if not meta:
        return base

    overrides: Dict[str, object] = {}
    audio = meta.get("audio") or {}
    frontend = meta.get("frontend") or {}

    sample_rate = int(frontend.get("sample_rate") or audio.get("sample_rate") or base.audio.sample_rate)
    overrides["audio.sample_rate"] = sample_rate
    for key in ("clip_seconds", "fit_mode", "bit_depth"):
        if key in audio:
            overrides[f"audio.{key}"] = audio[key]
    for section in ("trim", "normalize"):
        for key, value in (audio.get(section) or {}).items():
            overrides[f"audio.{section}.{key}"] = value

    for key in ("n_mels", "n_fft", "fmin", "fmax", "log_offset", "center"):
        if key in frontend:
            overrides[f"features.{key}"] = frontend[key]
    if "normalize" in frontend:
        overrides["features.normalize"] = frontend["normalize"]
    if frontend.get("win_length"):
        overrides["features.window_ms"] = float(frontend["win_length"]) * 1000.0 / sample_rate
    if frontend.get("hop_length"):
        overrides["features.hop_ms"] = float(frontend["hop_length"]) * 1000.0 / sample_rate

    try:
        return base.with_overrides(overrides)
    except (KeyError, ValueError, TypeError) as exc:
        LOGGER.warning("model_meta.json front-end could not be applied (%s); using config.yaml", exc)
        return base


# The front-end is `src.streaming.StreamingFrontend` - the one implementation of
# the conditioning and windowing, shared with the notebook and the export
# verification. The alias keeps this module's older name working.
Frontend = StreamingFrontend


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------
def _load_keras(path: Path, input_shape: Tuple[int, int, int]):
    """Load a ``.keras`` file or a SavedModel directory as a callable model.

    A Keras 3 SavedModel is not a model - it reloads as a ``TFSMLayer``, a bare
    callable with no ``input_shape`` / ``output_shape``. Wrapping it in a
    ``Model`` built on the front-end's shape restores those, which is what the
    rest of this class introspects. It also means the shapes come from the
    front-end rather than from the graph, so a SavedModel cannot self-report a
    mismatch the way a ``.tflite`` file can.
    """
    try:
        import keras  # type: ignore
    except ImportError:
        try:
            from tensorflow import keras  # type: ignore
        except ImportError as exc:
            raise RuntimeError(
                f"{path.name} needs Keras/TensorFlow, which this Space does not "
                "install. Use a .tflite export (section 05 of the notebook), or add "
                "'tensorflow' to requirements.txt."
            ) from exc

    if not path.is_dir():
        return keras.models.load_model(str(path))

    last_error: Optional[Exception] = None
    for endpoint in ("serve", "serving_default"):
        try:
            inner = keras.layers.TFSMLayer(str(path), call_endpoint=endpoint)
            inputs = keras.Input(shape=tuple(int(dim) for dim in input_shape))
            outputs = inner(inputs)
            if isinstance(outputs, dict):  # signature-style output
                outputs = list(outputs.values())[0]
            return keras.Model(inputs, outputs)
        except Exception as exc:  # noqa: BLE001 - try the next endpoint name
            last_error = exc
    raise RuntimeError(f"could not load the SavedModel at {path}: {last_error}")


def _softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - logits.max(axis=-1, keepdims=True)
    exponentiated = np.exp(shifted)
    return exponentiated / np.clip(exponentiated.sum(axis=-1, keepdims=True), 1e-12, None)


@dataclass
class DhikrModel:
    """An exported model plus the front-end and labels that belong to it."""

    path: Path
    labels: List[str]
    frontend: Frontend
    meta: Dict = field(default_factory=dict)
    phrases: Dict[int, str] = field(default_factory=dict)
    backend: str = "tflite"
    _interpreter: object = field(default=None, repr=False)
    _keras: object = field(default=None, repr=False)

    # -- construction -------------------------------------------------------
    @classmethod
    def load(
        cls,
        model_path: Path,
        labels_path: Optional[Path] = None,
        meta_path: Optional[Path] = None,
        phrases: Optional[Dict[int, str]] = None,
    ) -> "DhikrModel":
        model_path = Path(model_path)
        folder = model_path.parent
        meta_path = meta_path or _first_existing(folder, "model_meta.json")
        labels_path = labels_path or _first_existing(folder, "labels.txt")

        meta: Dict = {}
        if meta_path and meta_path.exists():
            try:
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError) as exc:
                LOGGER.warning("could not read %s: %s", meta_path, exc)

        config = resolve_config(meta)
        stats_payload = (meta.get("frontend") or {}).get("stats")
        stats = FeatureStats.from_dict(stats_payload) if stats_payload else None
        frontend = Frontend(config=config, stats=stats)

        labels = _read_labels(labels_path, meta)
        model = cls(
            path=model_path,
            labels=labels,
            frontend=frontend,
            meta=meta,
            phrases=phrases if phrases is not None else load_phrases(),
        )
        model._open()
        return model

    def _open(self) -> None:
        suffix = self.path.suffix.lower()
        if suffix == ".tflite":
            self.backend = "tflite"
            self._interpreter = _make_interpreter(self.path)
            self._interpreter.allocate_tensors()
        else:
            self.backend = "keras"
            self._keras = _load_keras(self.path, self.frontend.input_shape)

        if len(self.labels) != self.num_classes:
            LOGGER.warning(
                "labels.txt has %d entries but the model has %d outputs; "
                "falling back to positional names",
                len(self.labels),
                self.num_classes,
            )
            self.labels = [f"class_{index}" for index in range(self.num_classes)]

    # -- introspection ------------------------------------------------------
    @property
    def input_shape(self) -> List[int]:
        if self.backend == "tflite":
            return [int(dim) for dim in self._interpreter.get_input_details()[0]["shape"]]
        return [int(dim) if dim else -1 for dim in self._keras.input_shape]

    @property
    def num_classes(self) -> int:
        if self.backend == "tflite":
            return int(self._interpreter.get_output_details()[0]["shape"][-1])
        return int(self._keras.output_shape[-1])

    @property
    def io_dtypes(self) -> Tuple[str, str]:
        if self.backend != "tflite":
            return ("float32", "float32")
        details = self._interpreter
        return (
            np.dtype(details.get_input_details()[0]["dtype"]).name,
            np.dtype(details.get_output_details()[0]["dtype"]).name,
        )

    @property
    def size_kb(self) -> float:
        if self.path.is_dir():
            return sum(f.stat().st_size for f in self.path.rglob("*") if f.is_file()) / 1024.0
        return self.path.stat().st_size / 1024.0

    def display_labels(self) -> List[str]:
        return [display_label(label, self.phrases) for label in self.labels]

    @property
    def has_unknown(self) -> bool:
        return any(label.lower() == UNKNOWN_LABEL for label in self.labels)

    def open_set_warning(self) -> Optional[str]:
        """Warn when the model has no way to say "that was not a dhikr".

        Softmax always sums to 1, so a model trained only on phrases assigns all
        of that mass to phrases - silence, breathing and speech included. It is
        not a bug in the model, but it changes how the scan tab must be read:
        with no ``unknown`` class the threshold is the *only* thing standing
        between background noise and a count.
        """
        if self.has_unknown:
            return None
        return (
            f"This model has no `unknown` class (its {self.num_classes} outputs are all "
            "phrases), so every window is scored as one phrase or another — including "
            "silence and background noise. Expect false counts when scanning, and lean "
            "on a high confidence threshold. Training with an `unknown` folder "
            "(`classes.include_unknown`) is the real fix."
        )

    def shape_mismatch(self) -> Optional[str]:
        """Human-readable warning when the front-end and the model disagree."""
        expected = self.frontend.input_shape  # (frames, mels, 1)
        actual = self.input_shape
        if len(actual) == 4 and tuple(actual[1:]) == tuple(expected):
            return None
        return (
            f"front-end produces {tuple(expected)} but the model expects "
            f"{tuple(actual[1:]) if len(actual) == 4 else tuple(actual)}. "
            "Predictions will be wrong - export labels.txt/model_meta.json together "
            "with the .tflite file."
        )

    # -- prediction ---------------------------------------------------------
    def predict(self, features: np.ndarray) -> np.ndarray:
        """``(N, frames, mels)`` (or one clip) -> ``(N, num_classes)`` probabilities."""
        batch = np.asarray(features, dtype=np.float32)
        if batch.ndim == 2:
            batch = batch[np.newaxis, ...]
        if batch.ndim == 3:
            batch = batch[..., np.newaxis]

        if self.backend == "keras":
            raw = np.asarray(self._keras(batch, training=False), dtype=np.float32)
        else:
            interpreter = self._interpreter
            input_detail = interpreter.get_input_details()[0]
            output_detail = interpreter.get_output_details()[0]
            outputs = []
            for sample in batch:
                interpreter.set_tensor(
                    input_detail["index"],
                    _quantize_input(sample[np.newaxis, ...], input_detail),
                )
                interpreter.invoke()
                outputs.append(
                    _dequantize_output(
                        interpreter.get_tensor(output_detail["index"]), output_detail
                    )[0]
                )
            raw = np.stack(outputs)

        # The exported graph ends in softmax, but an INT8 model dequantises to
        # values that only approximately sum to 1, and a model exported without
        # the activation would come back as logits. Renormalise either way so a
        # probability is always a probability.
        if raw.min() < 0.0 or not np.allclose(raw.sum(axis=-1), 1.0, atol=0.05):
            raw = _softmax(raw)
        else:
            raw = raw / np.clip(raw.sum(axis=-1, keepdims=True), 1e-12, None)
        return raw.astype(np.float32)

    def predict_clip(self, samples: np.ndarray, trim: bool = True) -> Tuple[np.ndarray, np.ndarray]:
        """One recording -> ``(probabilities, features)``."""
        features = self.frontend.features(samples, trim=trim)
        return self.predict(features)[0], features

    def scan(self, samples: np.ndarray, hop_seconds: float) -> "ScanResult":
        times, features = self.frontend.windows(samples, hop_seconds)
        return ScanResult(times=times, probabilities=self.predict(features), labels=self.labels)


def _first_existing(folder: Path, name: str) -> Optional[Path]:
    candidate = folder / name
    return candidate if candidate.exists() else None


def _read_labels(labels_path: Optional[Path], meta: Dict) -> List[str]:
    if labels_path and labels_path.exists():
        lines = [line.strip() for line in labels_path.read_text(encoding="utf-8").splitlines()]
        labels = [line for line in lines if line]
        if labels:
            return labels
    classes = meta.get("classes") or []
    if classes:
        return [str(entry.get("label", index)) for index, entry in enumerate(classes)]
    return []


# ---------------------------------------------------------------------------
# Scanning and counting
# ---------------------------------------------------------------------------
@dataclass
class Detection:
    """One accepted keyword hit.

    ``start`` and ``end`` are the first and last *confident window starts* of the
    run, so a hit seen in a single window has ``start == end``. ``time`` is where
    the model was surest.
    """

    label: str
    start: float
    end: float
    time: float
    confidence: float


@dataclass
class ScanResult:
    """Per-window probabilities over a recording."""

    times: np.ndarray
    probabilities: np.ndarray  # (windows, classes)
    labels: List[str]

    @property
    def windows(self) -> int:
        return int(self.probabilities.shape[0])


def count_detections(
    scan: ScanResult,
    threshold: float = 0.7,
    min_gap_seconds: float = 0.5,
    ignore: Sequence[str] = (UNKNOWN_LABEL,),
) -> Tuple[List[Detection], Dict[str, int]]:
    """Turn per-window probabilities into counted events.

    The counting itself is ``src.streaming.EventDetector`` - the same state
    machine the notebook calibrates and the Android app is specified against, so
    what this Space shows is what the pipeline measured. One dhikr spans many
    windows, so the unit of counting is a run of agreeing windows however long it
    lasts, held together by hysteresis (a release threshold below the activation
    one) and followed by a per-class cooldown.

    The two knobs the UI exposes map onto the detector: ``threshold`` is the
    activation threshold, and ``min_gap_seconds`` is the cooldown - the shortest
    silence that separates two counts of the same phrase.

    ``min_consecutive_hits`` is 1 here, unlike the production default of 2: this
    tab is a diagnostic for "does the model hear the phrase at all", and swallowing
    single-window hits would hide exactly the weak recognition it is there to
    show.
    """
    config = DetectorConfig(
        confidence_threshold=float(threshold),
        release_threshold=min(0.4, float(threshold)),
        min_consecutive_hits=1,
        release_windows=2,
        cooldown_ms=float(min_gap_seconds) * 1000.0,
        ignore_labels=list(ignore),
    )
    events = detect_events(scan.times, scan.probabilities, scan.labels, config)

    detections = [
        Detection(
            label=event.label,
            start=event.start,
            end=event.end,
            time=event.peak_time,
            confidence=event.peak_confidence,
        )
        for event in events
    ]
    counts: Dict[str, int] = {}
    for detection in detections:
        counts[detection.label] = counts.get(detection.label, 0) + 1
    return detections, counts


# ---------------------------------------------------------------------------
# Discovery
# ---------------------------------------------------------------------------
def discover_models(folder: Path, recursive: bool = False) -> List[Path]:
    """Exported models in ``folder``.

    ``.tflite`` files come first because they are what the Space is built to run;
    a SavedModel directory or ``.keras`` file is listed after them so a checkout
    with TensorFlow available can still use it.

    ``recursive`` is for fetched folders: a shared Drive folder or a Hub snapshot
    keeps its own structure (``exports/dhikr_int8.tflite``), so a top-level glob
    would find nothing. A SavedModel's own subdirectories are skipped - it is one
    model, not a folder of them.
    """
    folder = Path(folder)
    if not folder.exists():
        return []

    if recursive:
        candidates = sorted(folder.rglob("*"))
    else:
        candidates = sorted(folder.iterdir())

    saved_models = [
        path for path in candidates if path.is_dir() and (path / "saved_model.pb").exists()
    ]
    def inside_saved_model(path: Path) -> bool:
        return any(root in path.parents for root in saved_models)

    tflite, keras_files = [], []
    for path in candidates:
        if not path.is_file() or inside_saved_model(path):
            continue
        if path.suffix.lower() == ".tflite":
            tflite.append(path)
        elif path.suffix.lower() in (".keras", ".h5"):
            keras_files.append(path)
    return tflite + keras_files + saved_models

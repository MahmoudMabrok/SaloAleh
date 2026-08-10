"""Inference layer for the per-phrase DhikrSpeech Hugging Face Space.

The Space has to reproduce the training front-end exactly, so nothing here
reimplements it: the conditioning comes from ``src.audio`` and the log-mel
extractor from ``src.features``, the same modules the notebook trains with.

What this module adds on top is the part the pipeline does not have:

* loading an exported ``.tflite`` (or Keras) model **without** importing
  TensorFlow when a LiteRT runtime is available, so the Space stays light;
* rebuilding the target id, binary output mode, front-end and detector from that
  phrase's ``model_metadata.json`` rather than from the moving config;
* preserving sigmoid P(target) as a scalar probability (normalising one output
  would turn every window into 100% target);
* sliding-window inference followed by ``src.streaming.EventDetector``, the same
  calibrated hysteresis state machine the notebook measures and Android mirrors.

The older multi-class loader and run counter remain as compatibility utilities,
but the UI only offers exports that can be identified as one target phrase.
"""

from __future__ import annotations

import json
import logging
import re
import sys
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

# The Space runs either from a checkout (src/ one level up) or from the synced
# Space repo (src/ next to app.py). Support both without a package install.
_HERE = Path(__file__).resolve().parent
for _candidate in (_HERE, _HERE.parent):
    if (_candidate / "src" / "__init__.py").exists() and str(_candidate) not in sys.path:
        sys.path.insert(0, str(_candidate))

from src.audio import fit_length, normalize_loudness, trim_silence  # noqa: E402
from src.config import Config, default_config_path  # noqa: E402
from src.features import FeatureStats, LogMelExtractor  # noqa: E402
from src.streaming import ScoreTimeline, detect_events, smooth_scores  # noqa: E402

LOGGER = logging.getLogger(__name__)

__all__ = [
    "Detection",
    "DhikrModel",
    "Frontend",
    "ScanResult",
    "count_detections",
    "count_target_detections",
    "discover_models",
    "export_descriptor",
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


_TARGET_MODEL_NAME = re.compile(
    r"^dhikr_(?P<target>\d{3})_(?P<variant>float32|dynamic_range|int8)$"
)


def export_descriptor(model_path: Path, phrases: Optional[Dict[int, str]] = None) -> Dict[str, object]:
    """Cheap target/variant identity for a model picker, without opening LiteRT."""
    path = Path(model_path)
    meta: Dict[str, object] = {}
    for name in ("model_metadata.json", "model_meta.json"):
        candidate = path.parent / name
        if not candidate.is_file():
            continue
        try:
            meta = json.loads(candidate.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as exc:
            LOGGER.warning("could not read %s: %s", candidate, exc)
        break

    stem = path.stem
    match = _TARGET_MODEL_NAME.match(stem)
    raw_target = meta.get("target_phrase_id") or (match.group("target") if match else None)
    target_id = int(raw_target) if raw_target is not None and str(raw_target).isdigit() else None
    phrase_map = phrases if phrases is not None else load_phrases()
    target_text = str(meta.get("target_phrase_text") or "").strip()
    if not target_text and target_id is not None:
        target_text = phrase_map.get(target_id, "")

    variant = match.group("variant") if match else path.suffix.lower().lstrip(".")
    recommended = str((meta.get("quantization") or {}).get("recommended") or "")
    target_key = f"{target_id:03d}" if target_id is not None else None
    target_name = (
        f"{target_key} · {target_text}" if target_key and target_text else target_key or target_text
    )
    return {
        "path": path,
        "target_id": target_id,
        "target_key": target_key,
        "target_text": target_text,
        "target_name": target_name or "Unknown target",
        "variant": variant,
        "recommended_variant": recommended or None,
        "recommended": bool(recommended and variant == recommended),
        "metadata": meta,
    }


# ---------------------------------------------------------------------------
# Front-end
# ---------------------------------------------------------------------------
def resolve_config(meta: Optional[Dict] = None, config_path: Optional[Path] = None) -> Config:
    """Config for a model, with the export's metadata overriding ``config.yaml``.

    The exported metadata records the front-end the weights were actually
    trained with. ``configs/config.yaml`` is a moving target - it may have been
    retuned since the export - so anything the metadata states wins.
    """
    base = Config.load(config_path or default_config_path())
    if not meta:
        return base

    overrides: Dict[str, object] = {}
    audio = meta.get("audio") or {}
    frontend = meta.get("frontend") or meta.get("feature") or {}

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

    if meta.get("target_phrase_id") is not None:
        overrides["target.phrase_id"] = int(meta["target_phrase_id"])
    if meta.get("output_mode"):
        overrides["target.output_mode"] = str(meta["output_mode"])
    if meta.get("hop_seconds") is not None:
        overrides["streaming.hop_seconds"] = float(meta["hop_seconds"])

    detection = meta.get("detection") or {}
    for key in (
        "activation_threshold",
        "release_threshold",
        "min_consecutive_hits",
        "min_event_seconds",
        "release_windows",
        "cooldown_ms",
    ):
        if key in detection:
            overrides[f"streaming.detector.{key}"] = detection[key]
    for key, value in (detection.get("smoothing") or {}).items():
        if key in ("mode", "ema_alpha", "window"):
            overrides[f"streaming.smoothing.{key}"] = value

    try:
        return base.with_overrides(overrides)
    except (KeyError, ValueError, TypeError) as exc:
        LOGGER.warning("the export front-end could not be applied (%s); using config.yaml", exc)
        return base


@dataclass
class Frontend:
    """Waveform -> ``(frames, n_mels)`` log-mel, exactly as in training."""

    config: Config
    stats: Optional[FeatureStats] = None
    extractor: LogMelExtractor = field(init=False)

    def __post_init__(self) -> None:
        self.extractor = LogMelExtractor(
            config=self.config.features,
            sample_rate=self.config.audio.sample_rate,
            stats=self.stats,
        )

    # -- geometry -----------------------------------------------------------
    @property
    def sample_rate(self) -> int:
        return self.config.audio.sample_rate

    @property
    def clip_samples(self) -> int:
        return self.config.audio.clip_samples

    @property
    def clip_seconds(self) -> float:
        return float(self.config.audio.clip_seconds)

    @property
    def input_shape(self) -> Tuple[int, int, int]:
        return self.config.input_shape

    # -- conditioning -------------------------------------------------------
    def condition(self, samples: np.ndarray, trim: bool = True) -> np.ndarray:
        """Trim, loudness-normalise and fit one clip to the model's length.

        ``trim`` is a parameter rather than a config read because silence
        trimming is right for a clip the user recorded on purpose and wrong for
        a window cut out of a longer stream - trimming a window slides its
        content in time and destroys the alignment the scanner depends on.
        """
        result = np.asarray(samples, dtype=np.float32)
        audio = self.config.audio
        if trim and audio.trim.enabled:
            result = trim_silence(result, self.sample_rate, audio.trim.top_db, audio.trim.pad_ms)
        if audio.normalize.enabled:
            result = normalize_loudness(
                result, audio.normalize.target_dbfs, audio.normalize.peak_ceiling
            )
        return fit_length(result, self.clip_samples, audio.fit_mode)

    def features(self, samples: np.ndarray, trim: bool = True) -> np.ndarray:
        return self.extractor(self.condition(samples, trim=trim))

    def windows(
        self, samples: np.ndarray, hop_seconds: float
    ) -> Tuple[np.ndarray, np.ndarray]:
        """Slide the model's clip window over a longer recording.

        Returns ``(start_times, features)`` where ``features`` is
        ``(N, frames, n_mels)``. The tail is always covered: a recording shorter
        than one window, or a leftover shorter than the hop, is zero-padded into
        a final window rather than dropped.
        """
        audio = np.asarray(samples, dtype=np.float32)
        window = self.clip_samples
        hop = max(int(round(hop_seconds * self.sample_rate)), 1)

        if audio.size <= window:
            starts = [0]
        else:
            starts = list(range(0, audio.size - window + 1, hop))
            if starts[-1] + window < audio.size:
                starts.append(audio.size - window)

        stacked = [self.features(audio[start : start + window], trim=False) for start in starts]
        times = np.asarray(starts, dtype=np.float32) / float(self.sample_rate)
        return times, np.stack(stacked).astype(np.float32)


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------
def _make_interpreter(model_path: Path, num_threads: int = 1):
    """LiteRT first, ``tf.lite`` only as a fallback.

    ``ai-edge-litert`` is a ~30 MB wheel; TensorFlow is ~600 MB. Preferring
    LiteRT is what lets this Space run without TensorFlow installed at all.
    """
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=str(model_path), num_threads=num_threads)
    except ImportError:
        pass
    try:
        import tensorflow as tf  # type: ignore
    except ImportError as exc:  # pragma: no cover - environment problem, not logic
        raise RuntimeError(
            "no TFLite runtime available - install 'ai-edge-litert' (preferred) "
            "or 'tensorflow'"
        ) from exc
    return tf.lite.Interpreter(model_path=str(model_path), num_threads=num_threads)


def _quantize_input(array: np.ndarray, detail: Dict) -> np.ndarray:
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(dtype)
    info = np.iinfo(dtype)
    return np.clip(np.round(array / scale) + zero_point, info.min, info.max).astype(dtype)


def _dequantize_output(array: np.ndarray, detail: Dict) -> np.ndarray:
    dtype = detail["dtype"]
    if dtype == np.float32:
        return array.astype(np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return array.astype(np.float32)
    return (array.astype(np.float32) - zero_point) * scale


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
                "install. Use a .tflite export (section 06 of the notebook), or add "
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
        # A current per-target export (exports/007/) writes
        # `model_metadata.json`. The legacy name remains a read-only fallback;
        # the UI itself only offers models that identify one target phrase.
        meta_path = meta_path or _first_existing(folder, "model_metadata.json")
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

    @property
    def target_id(self) -> Optional[int]:
        raw = self.meta.get("target_phrase_id")
        if raw is not None and str(raw).isdigit():
            return int(raw)
        return export_descriptor(self.path, self.phrases)["target_id"]  # type: ignore[return-value]

    @property
    def target_text(self) -> str:
        text = str(self.meta.get("target_phrase_text") or "").strip()
        if text:
            return text
        return self.phrases.get(self.target_id or -1, "")

    @property
    def target_name(self) -> str:
        key = f"{self.target_id:03d}" if self.target_id is not None else "target"
        return f"{key} · {self.target_text}" if self.target_text else key

    @property
    def variant(self) -> str:
        return str(export_descriptor(self.path, self.phrases)["variant"])

    @property
    def recommended_variant(self) -> Optional[str]:
        value = (self.meta.get("quantization") or {}).get("recommended")
        return str(value) if value else None

    @property
    def is_single_target(self) -> bool:
        return self.target_id is not None and self.num_classes in (1, 2)

    @property
    def target_index(self) -> int:
        configured = self.meta.get("target_index")
        if configured is not None:
            index = int(configured)
            if 0 <= index < self.num_classes:
                return index
        for index, label in enumerate(self.labels):
            if label.lower() == "target":
                return index
        return 0 if self.num_classes == 1 else self.num_classes - 1

    def label_name(self, label: str) -> str:
        if self.is_single_target and label.lower() == "target":
            return self.target_name
        if self.is_single_target and label.lower() == UNKNOWN_LABEL:
            return "Not target · unknown"
        return display_label(label, self.phrases)

    def display_labels(self) -> List[str]:
        return [self.label_name(label) for label in self.labels]

    def target_scores(self, probabilities: np.ndarray) -> np.ndarray:
        values = np.asarray(probabilities, dtype=np.float32)
        if values.ndim == 1:
            values = values[np.newaxis, ...]
        return values[:, self.target_index]

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
        if self.has_unknown or self.is_single_target:
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
            "Predictions will be wrong - export labels.txt and the model metadata together "
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

        if raw.ndim == 1:
            raw = raw[:, np.newaxis]

        # A one-output per-target model is sigmoid P(target). Normalising its
        # single column would turn every prediction into 1.0, so it must stay a
        # scalar probability. Two-output models end in softmax; INT8
        # dequantisation only approximately preserves their sum.
        if self.num_classes == 1:
            if raw.min() < 0.0 or raw.max() > 1.0:
                raw = 1.0 / (1.0 + np.exp(-raw))
            return np.clip(raw, 0.0, 1.0).astype(np.float32)
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
    # Window index the run currently ends at. Adjacency has to be checked on the
    # index, not the timestamp: a rejected window in the middle leaves no gap in
    # time when the hop is small, but it does break the run.
    last_index: int = -1


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

    One dhikr spans many windows, so the top class stays above the threshold for
    as long as the phrase lasts. Counting windows would therefore count a single
    dhikr four or five times; so would a plain refractory timer whenever the
    phrase outlasts the timer, which is exactly the case for the longer dhikr.

    So the unit of counting is a **run**: consecutive windows agreeing on the
    same above-threshold label are one event, however long it lasts.
    ``min_gap_seconds`` then closes the gap *between* runs - two runs of the same
    label separated by less than it are the same dhikr flickering, not two. Runs
    are grouped per label, so two different phrases said back to back stay two
    counts even with no silence between them.

    Each event is reported at its most confident window.
    """
    ignored = {item.lower() for item in ignore}

    # -- pass 1: consecutive same-label windows over the threshold become runs --
    runs: List[Detection] = []
    for index in range(scan.windows):
        probabilities = scan.probabilities[index]
        best = int(np.argmax(probabilities))
        label = scan.labels[best]
        confidence = float(probabilities[best])
        time = float(scan.times[index])
        if confidence < threshold or label.lower() in ignored:
            continue
        current = runs[-1] if runs else None
        if current is not None and current.label == label and current.last_index == index - 1:
            current.end = time
            current.last_index = index
            if confidence > current.confidence:
                current.confidence = confidence
                current.time = time
        else:
            runs.append(
                Detection(
                    label=label,
                    start=time,
                    end=time,
                    time=time,
                    confidence=confidence,
                    last_index=index,
                )
            )

    # -- pass 2: merge same-label runs that a brief dip split in two -----------
    merged: List[Detection] = []
    last_end: Dict[str, float] = {}
    for run in runs:
        previous_end = last_end.get(run.label)
        if previous_end is not None and run.start - previous_end < min_gap_seconds:
            for existing in reversed(merged):
                if existing.label == run.label:
                    existing.end = run.end
                    if run.confidence > existing.confidence:
                        existing.confidence = run.confidence
                        existing.time = run.time
                    break
        else:
            merged.append(run)
        last_end[run.label] = run.end

    counts: Dict[str, int] = {}
    for detection in merged:
        counts[detection.label] = counts.get(detection.label, 0) + 1
    return merged, counts


def count_target_detections(
    scan: ScanResult,
    model: DhikrModel,
    activation_threshold: Optional[float] = None,
    release_threshold: Optional[float] = None,
) -> Tuple[List[Detection], Dict[str, int]]:
    """Count one per-target model with its exported production state machine."""
    if not model.is_single_target:
        raise ValueError("count_target_detections needs a per-target export")

    base = model.frontend.config.streaming.detector
    activation = float(
        base.activation_threshold if activation_threshold is None else activation_threshold
    )
    release = float(base.release_threshold if release_threshold is None else release_threshold)
    detector = replace(
        base,
        activation_threshold=activation,
        release_threshold=min(release, activation),
    )

    raw_scores = model.target_scores(scan.probabilities)
    scores = smooth_scores(raw_scores, model.frontend.config.streaming.smoothing)
    hop_seconds = (
        float(scan.times[1] - scan.times[0])
        if scan.times.size > 1
        else float(model.frontend.config.streaming.hop_seconds)
    )
    timeline = ScoreTimeline(
        times=np.asarray(scan.times, dtype=np.float32),
        scores=np.asarray(scores, dtype=np.float32),
        window_seconds=model.frontend.clip_seconds,
        hop_seconds=hop_seconds,
        raw_scores=(raw_scores if model.frontend.config.streaming.smoothing.mode != "none" else None),
    )
    events = detect_events(timeline, detector)
    detections = [
        Detection(
            label="target",
            start=event.start,
            end=event.end,
            time=event.time,
            confidence=event.peak_score,
        )
        for event in events
    ]
    return detections, {model.target_name: len(detections)}


# ---------------------------------------------------------------------------
# Discovery
# ---------------------------------------------------------------------------
def discover_models(folder: Path, recursive: bool = False) -> List[Path]:
    """Exported models in ``folder``.

    ``.tflite`` files come first because they are what the Space is built to run;
    a SavedModel directory or ``.keras`` file is listed after them so a checkout
    with TensorFlow available can still use it.

    ``recursive`` is for export roots: a shared Drive folder or Hub snapshot
    keeps its target structure (``exports/007/dhikr_007_int8.tflite``), so a
    top-level glob would find nothing. A SavedModel's own subdirectories are
    skipped - it is one model, not a folder of them.
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

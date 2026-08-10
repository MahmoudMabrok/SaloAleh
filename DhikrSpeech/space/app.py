"""Gradio Space for testing per-phrase DhikrSpeech exports.

Four things you cannot do from the training notebook:

* choose the target phrase first, then its recommended or comparison variant;
* test one clip as target versus not-target without corrupting sigmoid output;
* count that phrase in a recording with its exported production detector;
* inspect the target-specific calibration, data and release measurements.

Run locally with ``python app.py`` from a checkout; on Hugging Face the Space
launches this file directly.
"""

from __future__ import annotations

import json
import logging
import os
import shutil
import tempfile
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import gradio as gr
import librosa
import matplotlib
import numpy as np

# Hugging Face ZeroGPU is the only free hardware tier for this Space, and it
# refuses to start without a function marked @spaces.GPU. DhikrSpeech runs the
# LiteRT interpreter entirely on the CPU (see requirements.txt - no TensorFlow,
# no CUDA), so we expose one decorated entrypoint further down purely to satisfy
# that check. Off Spaces (local `python app.py`) the package is absent, so the
# decorator degrades to a no-op.
try:
    import spaces
except ImportError:  # local dev / non-ZeroGPU

    class _Spaces:
        @staticmethod
        def GPU(*args, **kwargs):
            if len(args) == 1 and callable(args[0]) and not kwargs:
                return args[0]  # bare  @spaces.GPU
            return lambda func: func  # param @spaces.GPU(duration=...)

    spaces = _Spaces()

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

from inference import (  # noqa: E402
    UNKNOWN_LABEL,
    DhikrModel,
    ScanResult,
    count_target_detections,
    discover_models,
    display_label,
    export_descriptor,
)
from sources import SourceError, configured_source, fetch, remote_cache_dir  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
LOGGER = logging.getLogger("dhikrspeech.space")

HERE = Path(__file__).resolve().parent
MODEL_DIR = Path(os.environ.get("DHIKR_MODEL_DIR", HERE / "model"))
UPLOAD_DIR = Path(tempfile.gettempdir()) / "dhikrspeech-uploads"
REMOTE_DIR = remote_cache_dir()
MAX_SCAN_SECONDS = float(os.environ.get("DHIKR_MAX_SCAN_SECONDS", "300"))

_CACHE: Dict[str, DhikrModel] = {}
_STARTUP_NOTE = ""

PLOT_BG = "#101418"
PLOT_FG = "#e6e6e6"
ACCENT = "#f2b544"


@spaces.GPU
def _zerogpu_entrypoint() -> str:
    """Exists only so Hugging Face ZeroGPU detects a GPU entrypoint at startup.

    ZeroGPU will not start a Space with no @spaces.GPU function, but DhikrSpeech
    has no GPU workload - the LiteRT interpreter runs on the CPU. This is never
    wired to the UI, so it is never called and no GPU is ever allocated; every
    inference runs on the base CPU process, which is what the model expects.

    If a future export ever needs the GPU, do NOT decorate the Gradio handlers
    wholesale (their matplotlib-figure returns cross the fork/pickle boundary and
    the module-level _CACHE would reload every call). Instead decorate the numeric
    core (model.predict_clip / model.scan), pre-load the model in the main process
    so the cache survives the fork, and build the figures on the CPU side.
    """
    return "ok"


# ---------------------------------------------------------------------------
# Model registry
# ---------------------------------------------------------------------------
def fetch_startup_source() -> str:
    """Pull the configured shared folder once, before the UI is built.

    Never fatal: a Drive outage, a revoked share link or no network at all must
    still leave a usable Space that can be handed a model by hand.
    """
    global _STARTUP_NOTE
    source = configured_source(HERE)
    if not source:
        return ""
    try:
        result = fetch(source, REMOTE_DIR)
    except SourceError as exc:
        LOGGER.warning("startup fetch failed: %s", exc)
        _STARTUP_NOTE = f"⚠️ Could not load the shared folder — {exc}"
        return _STARTUP_NOTE
    except Exception as exc:  # noqa: BLE001 - startup must not die on a fetch
        LOGGER.warning("startup fetch failed: %s", exc, exc_info=True)
        _STARTUP_NOTE = f"⚠️ Could not load the shared folder — {type(exc).__name__}: {exc}"
        return _STARTUP_NOTE
    LOGGER.info("%s", result.summary())
    _STARTUP_NOTE = f"Loaded from the shared folder: `{result.source}`"
    return _STARTUP_NOTE


def available_models() -> List[str]:
    """Every per-phrase model on disk, preserving target export folders."""
    found = (
        discover_models(MODEL_DIR, recursive=True)
        + discover_models(REMOTE_DIR, recursive=True)
        + discover_models(UPLOAD_DIR, recursive=True)
    )
    seen, described = set(), []
    for path in found:
        key = str(path)
        descriptor = export_descriptor(path)
        if key not in seen and descriptor["target_id"] is not None:
            seen.add(key)
            described.append((key, descriptor))
    variant_order = {"int8": 0, "dynamic_range": 1, "float32": 2}
    described.sort(
        key=lambda item: (
            int(item[1]["target_id"] or 9999),
            0 if item[1]["recommended"] else 1,
            variant_order.get(str(item[1]["variant"]), 9),
            item[0],
        ),
    )
    return [path for path, _descriptor in described]


def get_model(path: Optional[str]) -> DhikrModel:
    if not path:
        raise gr.Error(
            "No phrase model loaded. Add exports/<phrase id>/ with dhikr_<id>_*.tflite, "
            "labels.txt and model_metadata.json, or upload one phrase export."
        )
    cached = _CACHE.get(path)
    if cached is None:
        try:
            cached = DhikrModel.load(Path(path))
        except Exception as exc:  # noqa: BLE001 - surfaced to the user, not swallowed
            raise gr.Error(f"Could not load {Path(path).name}: {exc}") from exc
        _CACHE[path] = cached
    return cached


def model_choice_label(path: str) -> str:
    """Phrase-first picker label, with variant and source as secondary details."""
    resolved = Path(path)
    descriptor = export_descriptor(resolved)
    variant = str(descriptor["variant"]).replace("_", " ").upper()
    recommendation = " · recommended" if descriptor["recommended"] else ""
    label = f"{descriptor['target_name']} — {variant}{recommendation}"
    if str(resolved).startswith(str(REMOTE_DIR)):
        return f"{label}  (shared folder)"
    if str(resolved).startswith(str(UPLOAD_DIR)):
        return f"{label}  (uploaded)"
    return label


def phrase_banner(path: Optional[str]) -> str:
    if not path:
        return "### Select a phrase model to begin"
    model = get_model(path)
    recommendation = (
        " · ✅ recommended export"
        if model.recommended_variant and model.variant == model.recommended_variant
        else ""
    )
    banner = (
        f"## Testing {model.target_name}\n\n"
        f"Variant **{model.variant.replace('_', ' ').upper()}**{recommendation}. "
        "Every prediction below answers one binary question: did this audio contain "
        f"**{model.target_text or model.target_name}**?"
    )
    if not model.meta:
        banner += (
            "\n\n> ⚠️ This model has no `model_metadata.json`. Its target id came from the "
            "filename, but the Space had to fall back to the current front-end and detector "
            "configuration; results are not an export-parity check."
        )
    return banner


def model_control_updates(path: Optional[str]):
    if not path:
        return gr.update(), gr.update(), gr.update()
    model = get_model(path)
    config = model.frontend.config.streaming
    return (
        gr.update(value=float(config.hop_seconds)),
        gr.update(value=float(config.detector.activation_threshold)),
        gr.update(value=float(config.detector.release_threshold)),
    )


def fetch_source(source: str):
    """Paste-a-link handler for the Load-a-model tab."""
    try:
        result = fetch(source, REMOTE_DIR, restrict_hosts=True)
    except SourceError as exc:
        raise gr.Error(str(exc)) from exc

    for cached in [key for key in _CACHE if key.startswith(str(REMOTE_DIR))]:
        _CACHE.pop(cached, None)

    choices = available_models()
    fetched = [path for path in choices if path.startswith(str(result.directory))]
    selected = fetched[0] if fetched else (choices[0] if choices else None)
    message = result.summary()
    if not fetched:
        message += "\n\n⚠️ No runnable model among them — the folder needs a `.tflite` file."
    hop, activation, release = model_control_updates(selected)
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        message,
        model_info(selected) if selected else NO_MODEL,
        phrase_banner(selected),
        hop,
        activation,
        release,
    )


# ---------------------------------------------------------------------------
# Audio helpers
# ---------------------------------------------------------------------------
def to_mono_float(audio: Optional[Tuple[int, np.ndarray]], target_rate: int) -> np.ndarray:
    """Gradio audio tuple -> mono float32 at the model's sample rate."""
    if audio is None:
        raise gr.Error("Record or upload some audio first.")
    sample_rate, data = audio
    samples = np.asarray(data)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    if np.issubdtype(samples.dtype, np.integer):
        samples = samples.astype(np.float32) / float(np.iinfo(samples.dtype).max)
    else:
        samples = samples.astype(np.float32)
    if samples.size == 0:
        raise gr.Error("That recording is empty.")
    if sample_rate != target_rate:
        samples = librosa.resample(samples, orig_sr=sample_rate, target_sr=target_rate)
    return np.asarray(samples, dtype=np.float32)


# ---------------------------------------------------------------------------
# Plotting
# ---------------------------------------------------------------------------
def _style(axis) -> None:
    axis.set_facecolor(PLOT_BG)
    axis.tick_params(colors=PLOT_FG, labelsize=8)
    for spine in axis.spines.values():
        spine.set_color("#33393f")
    axis.xaxis.label.set_color(PLOT_FG)
    axis.yaxis.label.set_color(PLOT_FG)
    axis.title.set_color(PLOT_FG)


def clip_figure(samples: np.ndarray, features: np.ndarray, sample_rate: int, hop: int):
    """Waveform the model was fed, and the log-mel it turned into."""
    figure, (top, bottom) = plt.subplots(
        2, 1, figsize=(9, 4.6), gridspec_kw={"height_ratios": [1, 2]}
    )
    figure.patch.set_facecolor(PLOT_BG)

    duration = samples.size / float(sample_rate)
    top.plot(np.linspace(0, duration, samples.size), samples, color=ACCENT, linewidth=0.7)
    top.set_xlim(0, duration)
    top.set_ylabel("amplitude")
    top.set_title("conditioned waveform (trimmed, normalised, fitted)", fontsize=9)
    _style(top)

    frames = features.shape[0]
    bottom.imshow(
        features.T,
        origin="lower",
        aspect="auto",
        cmap="magma",
        extent=[0, frames * hop / float(sample_rate), 0, features.shape[1]],
    )
    bottom.set_xlabel("seconds")
    bottom.set_ylabel("mel bin")
    bottom.set_title(f"log-mel features · {frames} frames × {features.shape[1]} bins", fontsize=9)
    _style(bottom)

    figure.tight_layout()
    return figure


def scan_figure(samples: np.ndarray, scan: ScanResult, detections, model: DhikrModel):
    """Waveform, per-target score and the events from the production detector."""
    figure, (top, bottom) = plt.subplots(
        2, 1, figsize=(9, 5.2), gridspec_kw={"height_ratios": [1, 2]}, sharex=True
    )
    figure.patch.set_facecolor(PLOT_BG)

    duration = samples.size / float(model.frontend.sample_rate)
    top.plot(np.linspace(0, duration, samples.size), samples, color="#5a6572", linewidth=0.6)
    for detection in detections:
        # Span rather than a line: a detection is a run of windows, and seeing
        # its width is how you tell a clean hit from a threshold flicker.
        top.axvspan(detection.start, max(detection.end, detection.start + 0.02),
                    color=ACCENT, alpha=0.25)
        top.axvline(detection.time, color=ACCENT, linewidth=1.2, alpha=0.9)
    top.set_xlim(0, max(duration, 0.1))
    top.set_ylabel("amplitude")
    top.set_title(f"{len(detections)} detection(s)", fontsize=9)
    _style(top)

    if model.is_single_target:
        target_scores = model.target_scores(scan.probabilities)
        bottom.plot(
            scan.times,
            target_scores,
            label=model.target_name,
            color=ACCENT,
            linewidth=1.6,
        )
        bottom.plot(
            scan.times,
            1.0 - target_scores,
            label="not target",
            color="#6f7b86",
            linewidth=1.0,
            alpha=0.8,
        )
    else:
        palette = plt.get_cmap("tab10")
        for index, label in enumerate(scan.labels):
            bottom.plot(
                scan.times,
                scan.probabilities[:, index],
                label=display_label(label, model.phrases),
                color=palette(index % 10),
                linewidth=1.4,
            )
    bottom.set_ylim(-0.02, 1.02)
    bottom.set_xlabel("window start (seconds)")
    bottom.set_ylabel("probability")
    legend = bottom.legend(fontsize=7, loc="upper right", facecolor=PLOT_BG, edgecolor="#33393f")
    for text in legend.get_texts():
        text.set_color(PLOT_FG)
    _style(bottom)

    figure.tight_layout()
    return figure


# ---------------------------------------------------------------------------
# Tab 1 - single clip
# ---------------------------------------------------------------------------
def classify_clip(model_path: str, audio, apply_trim: bool):
    model = get_model(model_path)
    samples = to_mono_float(audio, model.frontend.sample_rate)

    probabilities, features = model.predict_clip(samples, trim=apply_trim)
    conditioned = model.frontend.condition(samples, trim=apply_trim)

    if model.is_single_target:
        target_probability = float(model.target_scores(probabilities)[0])
        scores = {
            model.target_name: target_probability,
            "Not target · unknown": 1.0 - target_probability,
        }
        table = [
            [model.target_name, round(target_probability, 4)],
            ["Not target · unknown", round(1.0 - target_probability, 4)],
        ]
    else:
        scores = {
            display_label(label, model.phrases): float(probability)
            for label, probability in zip(model.labels, probabilities)
        }
        table = [
            [display_label(label, model.phrases), round(float(probability), 4)]
            for label, probability in sorted(
                zip(model.labels, probabilities), key=lambda pair: -pair[1]
            )
        ]
    figure = clip_figure(
        conditioned, features, model.frontend.sample_rate, model.frontend.extractor.hop_length
    )

    duration = samples.size / model.frontend.sample_rate
    if model.is_single_target:
        threshold = model.frontend.config.streaming.detector.activation_threshold
        if target_probability >= threshold:
            note = (
                f"✅ **Detected {model.target_name}** · {target_probability:.1%} target score "
                f"(activation {threshold:.0%}) · {duration:.2f}s in, fitted to "
                f"{model.frontend.clip_seconds:g}s"
            )
        else:
            note = (
                f"🚫 **Not {model.target_name}** · {target_probability:.1%} target score "
                f"is below the exported {threshold:.0%} activation threshold · "
                f"{duration:.2f}s in, fitted to {model.frontend.clip_seconds:g}s"
            )
    else:
        best = int(np.argmax(probabilities))
        best_label = model.labels[best]
        confidence = float(probabilities[best])
        if best_label.lower() == UNKNOWN_LABEL:
            note = (
                f"🚫 **Rejected — not a dhikr** · the model's `unknown` class won at "
                f"{confidence:.1%} · {duration:.2f}s in, fitted to "
                f"{model.frontend.clip_seconds:g}s"
            )
        else:
            note = (
                f"**{display_label(best_label, model.phrases)}** · "
                f"{confidence:.1%} confidence · {duration:.2f}s in, "
                f"fitted to {model.frontend.clip_seconds:g}s"
            )
    warning = model.shape_mismatch()
    if warning:
        note += f"\n\n⚠️ {warning}"
    return scores, table, figure, note


# ---------------------------------------------------------------------------
# Tab 2 - scan a recording
# ---------------------------------------------------------------------------
def scan_recording(
    model_path: str,
    audio,
    hop_seconds: float,
    activation_threshold: float,
    release_threshold: float,
):
    model = get_model(model_path)
    if not model.is_single_target:
        raise gr.Error(
            "This scanner now expects a per-phrase export with target_phrase_id in "
            "model_metadata.json. Export one target from the current notebook."
        )
    samples = to_mono_float(audio, model.frontend.sample_rate)

    limit = int(MAX_SCAN_SECONDS * model.frontend.sample_rate)
    truncated = samples.size > limit
    if truncated:
        samples = samples[:limit]

    scan = model.scan(samples, hop_seconds=hop_seconds)
    detections, counts = count_target_detections(
        scan,
        model,
        activation_threshold=activation_threshold,
        release_threshold=release_threshold,
    )

    timeline = [
        [
            round(detection.start, 2),
            round(detection.end, 2),
            model.target_name,
            round(detection.confidence, 3),
        ]
        for detection in detections
    ]
    summary = [
        [label, count]
        for label, count in sorted(counts.items(), key=lambda pair: -pair[1])
    ]
    figure = scan_figure(samples, scan, detections, model)

    duration = samples.size / model.frontend.sample_rate
    detector = model.frontend.config.streaming.detector
    effective_release = min(float(release_threshold), float(activation_threshold))
    note = (
        f"**{len(detections)} × {model.target_name}** in {duration:.1f}s · "
        f"{scan.windows} windows of {model.frontend.clip_seconds:g}s at a {hop_seconds:g}s hop · "
        f"activation/release {activation_threshold:.0%}/{effective_release:.0%} · "
        f"{detector.min_consecutive_hits} confirming windows"
    )
    if truncated:
        note += f"\n\n⚠️ Recording truncated to the first {MAX_SCAN_SECONDS:g}s."
    open_set = model.open_set_warning()
    if open_set:
        note += f"\n\n⚠️ {open_set}"
    if not detections:
        note += (
            "\n\nThe selected phrase never confirmed. Lower activation carefully, or use "
            "the single-clip tab to inspect its target score."
        )
    return summary, timeline, figure, note


# ---------------------------------------------------------------------------
# Tab 3 - model info
# ---------------------------------------------------------------------------
def model_info(model_path: str) -> str:
    if not model_path:
        return (
            "### No model loaded\n\n"
            "Add an exported model to the Space (see the **Add phrase models** tab)."
        )
    model = get_model(model_path)
    frontend = model.frontend
    input_dtype, output_dtype = model.io_dtypes
    recommended = model.recommended_variant

    lines = [
        f"### {model.target_name}",
        f"`{model.path.name}`",
        "",
        "| | |",
        "|---|---|",
        f"| phrase model | {model.target_name} |",
        f"| variant | {model.variant}{' · recommended' if recommended == model.variant else ''} |",
        f"| recommended variant | {recommended or 'unmeasured'} |",
        f"| architecture | {model.meta.get('architecture', 'unknown')} |",
        f"| output mode | {model.meta.get('output_mode', 'legacy')} |",
        f"| backend | {model.backend} |",
        f"| size | {model.size_kb / 1024.0:.2f} MB |",
        f"| input shape | {model.input_shape} |",
        f"| dtypes | {input_dtype} in / {output_dtype} out |",
        f"| outputs | {model.num_classes} |",
        "",
        "**Labels**",
        "",
        "| index | label |",
        "|---|---|",
    ]
    lines += [f"| {index} | {label} |" for index, label in enumerate(model.display_labels())]

    lines += [
        "",
        "**Front-end** (must match the Android implementation exactly)",
        "",
        "| | |",
        "|---|---|",
        f"| sample rate | {frontend.sample_rate} Hz mono |",
        f"| clip | {frontend.clip_seconds:g} s ({frontend.clip_samples} samples), fit `{frontend.config.audio.fit_mode}` |",
        f"| window / hop | {frontend.extractor.win_length} / {frontend.extractor.hop_length} samples |",
        f"| n_fft / n_mels | {frontend.config.features.n_fft} / {frontend.config.features.n_mels} |",
        f"| mel range | {frontend.config.features.fmin:g}–{frontend.config.features.fmax:g} Hz, slaney |",
        f"| log | log(mel + {frontend.config.features.log_offset:g}) |",
        f"| normalise | {frontend.config.features.normalize} |",
        f"| trim | {'on' if frontend.config.audio.trim.enabled else 'off'} (top_db {frontend.config.audio.trim.top_db:g}) |",
    ]

    detector = frontend.config.streaming.detector
    smoothing = frontend.config.streaming.smoothing
    lines += [
        "",
        "**Per-phrase detector** (exported calibration; the scan tab uses this state machine)",
        "",
        "| | |",
        "|---|---|",
        f"| window hop | {frontend.config.streaming.hop_seconds:g} s |",
        f"| activation / release | {detector.activation_threshold:.3f} / {detector.release_threshold:.3f} |",
        f"| confirming windows | {detector.min_consecutive_hits} |",
        f"| release windows | {detector.release_windows} |",
        f"| cooldown | {detector.cooldown_ms:g} ms |",
        f"| smoothing | {smoothing.mode} |",
    ]

    readiness = model.meta.get("readiness") or {}
    dataset = model.meta.get("dataset") or {}
    streaming = model.meta.get("streaming") or {}
    counts = model.meta.get("counts") or {}
    if readiness or dataset or streaming or counts:
        lines += ["", "**Measurements carried by this phrase export**", ""]
        if readiness:
            lines.append(f"- readiness: **{readiness.get('status', 'unknown')}**")
        if dataset:
            lines.append(
                f"- dataset: {dataset.get('positive_clips', '?')} target / "
                f"{dataset.get('negative_clips', '?')} negative clips · "
                f"{dataset.get('positive_speakers', '?')} target speakers"
            )
        if streaming:
            precision = streaming.get("precision")
            recall = streaming.get("recall")
            false_per_hour = streaming.get("false_activations_per_hour")
            precision_text = f"{float(precision):.1%}" if precision is not None else "unmeasured"
            recall_text = f"{float(recall):.1%}" if recall is not None else "unmeasured"
            false_text = f"{float(false_per_hour):.3g}" if false_per_hour is not None else "unmeasured"
            lines.append(
                f"- streaming: precision {precision_text} · recall {recall_text} · "
                f"FA/hour {false_text}"
            )
        if counts:
            accuracy = counts.get("count_accuracy")
            lines.append(
                f"- count-only sessions: {counts.get('detected', '?')} detected / "
                f"{counts.get('expected', '?')} expected · "
                + (f"accuracy {float(accuracy):.1%}" if accuracy is not None else "accuracy unmeasured")
            )

    for warning in (model.shape_mismatch(), model.open_set_warning()):
        if warning:
            lines += ["", f"> ⚠️ {warning}"]
    if not model.meta:
        lines += [
            "",
            "> ⚠️ `model_metadata.json` is missing. Target text, front-end parity, "
            "calibrated counting thresholds and release measurements cannot be verified.",
        ]

    benchmarks = model.meta.get("benchmarks") or []
    if benchmarks:
        lines += [
            "",
            "**Benchmarks** (from the export)",
            "",
            "| variant | size | mean | p95 | est. Android |",
            "|---|---|---|---|---|",
        ]
        lines += [
            f"| {item.get('name', '?')} | {float(item.get('size_kb', 0)) / 1024.0:.2f} MB | "
            f"{float(item.get('mean_latency_ms', 0)):.2f} ms | "
            f"{float(item.get('p95_latency_ms', 0)):.2f} ms | "
            f"{float(item.get('expected_android_ms', 0)):.2f} ms |"
            for item in benchmarks
        ]

    verifications = model.meta.get("verification") or []
    if verifications:
        lines += [
            "",
            "**Verification against the Keras reference**",
            "",
            "| variant | agreement | max Δp |",
            "|---|---|---|",
        ]
        lines += [
            f"| {item.get('name', '?')} | {float(item.get('agreement', 0)):.2%} | "
            f"{float(item.get('max_probability_delta', 0)):.4f} |"
            for item in verifications
        ]

    metrics = model.meta.get("metrics") or {}
    if metrics:
        lines += ["", "**Metrics recorded at export**", ""]
        lines += [
            f"- {key}: {value:.4f}" if isinstance(value, (int, float)) else f"- {key}: {value}"
            for key, value in metrics.items()
        ]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Tab 4 - load a model
# ---------------------------------------------------------------------------
def install_uploads(files: Optional[List[str]]):
    """Install one phrase export without flattening its shared sidecar names."""
    if not files:
        raise gr.Error("Choose one phrase's .tflite file, labels.txt and model_metadata.json.")

    target_ids = set()
    for item in files:
        source = Path(item)
        parts = source.stem.split("_")
        if len(parts) >= 3 and parts[0] == "dhikr" and parts[1].isdigit():
            target_ids.add(f"{int(parts[1]):03d}")
        if source.name == "model_metadata.json":
            try:
                payload = json.loads(source.read_text(encoding="utf-8"))
                if str(payload.get("target_phrase_id") or "").isdigit():
                    target_ids.add(f"{int(payload['target_phrase_id']):03d}")
            except (OSError, json.JSONDecodeError):
                pass
    if len(target_ids) != 1:
        raise gr.Error(
            "Upload exactly one per-phrase export at a time. Its model filename must be "
            "dhikr_<phrase id>_<variant>.tflite and its metadata must name target_phrase_id."
        )

    target_id = next(iter(target_ids))
    destination_dir = UPLOAD_DIR / target_id
    # Replace the target bundle as a unit. Keeping an old labels/metadata file
    # beside a newly uploaded flatbuffer would silently apply stale calibration
    # to a different model version.
    if destination_dir.exists():
        shutil.rmtree(destination_dir)
    destination_dir.mkdir(parents=True, exist_ok=True)
    installed = []
    for item in files:
        source = Path(item)
        destination = destination_dir / source.name
        shutil.copyfile(source, destination)
        installed.append(destination.name)

    # Every cached upload is dropped, not just the files that were replaced: a
    # New labels.txt or model_metadata.json changes how an already-loaded model is
    # interpreted, and a stale cache entry would keep showing the old labels.
    for cached in [key for key in _CACHE if key.startswith(str(UPLOAD_DIR))]:
        _CACHE.pop(cached, None)

    choices = available_models()
    uploaded_models = [
        path
        for path in choices
        if Path(path).parent == destination_dir
    ]
    selected = uploaded_models[0] if uploaded_models else (choices[0] if choices else None)

    message = f"Installed phrase {target_id}: " + ", ".join(sorted(installed))
    if not uploaded_models:
        message += (
            "\n\n⚠️ No model file among them — upload the `.tflite` itself, not only "
            "the sidecar files."
        )
    elif "model_metadata.json" not in installed:
        message += (
            "\n\n⚠️ No `model_metadata.json` — the phrase text, calibrated detector and "
            "front-end contract are unavailable. Upload the complete target export."
        )
    hop, activation, release = model_control_updates(selected)
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        message,
        model_info(selected) if selected else "",
        phrase_banner(selected),
        hop,
        activation,
        release,
    )


def select_model(path: Optional[str]):
    """Phrase picker -> model state, target card and calibrated scan controls."""
    hop, activation, release = model_control_updates(path)
    return (
        path,
        model_info(path) if path else NO_MODEL,
        phrase_banner(path),
        hop,
        activation,
        release,
    )


def refresh_models(current: Optional[str]):
    choices = available_models()
    selected = current if current in choices else (choices[0] if choices else None)
    hop, activation, release = model_control_updates(selected)
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        model_info(selected) if selected else "",
        phrase_banner(selected),
        hop,
        activation,
        release,
    )


# ---------------------------------------------------------------------------
# UI
# ---------------------------------------------------------------------------
INTRO = """
# DhikrSpeech · per-phrase model playground

[SaloAleh](https://github.com/MahmoudMabrok/SaloAleh) now ships **one small binary TFLite model per
dhikr phrase**. Choose the phrase first, then test the recommended export or compare its variants.
Recording one clip asks whether that exact phrase is present; scanning a longer recording runs the
same calibrated hysteresis detector that counts it on device.

Audio is processed in-memory for the length of the request and never stored.
"""

NO_MODEL = """
### No model in this Space yet

Put one or more exports from section **06 · Export** of the notebook into `model/`:

```
model/
├── 006/
│   ├── dhikr_006_int8.tflite
│   ├── labels.txt
│   └── model_metadata.json
└── 007/
    ├── dhikr_007_int8.tflite
    ├── labels.txt
    └── model_metadata.json
```

The phrase folders keep identically named sidecars from overwriting each other. You can also upload
one complete phrase export at a time in **Add phrase models**. Uploads appear immediately; use
**Rescan** only after copying files into `model/` while the app is already running.
"""


def build_demo() -> gr.Blocks:
    startup_note = fetch_startup_source()
    initial = available_models()
    initial_selected = initial[0] if initial else None
    if initial_selected:
        initial_streaming = get_model(initial_selected).frontend.config.streaming
        initial_hop = float(initial_streaming.hop_seconds)
        initial_activation = float(initial_streaming.detector.activation_threshold)
        initial_release = float(initial_streaming.detector.release_threshold)
    else:
        initial_hop, initial_activation, initial_release = 0.2, 0.7, 0.4

    with gr.Blocks(title="DhikrSpeech · per-phrase models", theme=gr.themes.Soft()) as demo:
        gr.Markdown(INTRO)
        if startup_note:
            gr.Markdown(startup_note)

        with gr.Row():
            model_dropdown = gr.Dropdown(
                label="Phrase model · choose the phrase, then its export variant",
                choices=[(model_choice_label(path), path) for path in initial],
                value=initial_selected,
                scale=4,
            )
            rescan_button = gr.Button("Rescan", scale=1)
        selected_model = gr.State(initial_selected)
        phrase_markdown = gr.Markdown(phrase_banner(initial_selected))

        if not initial:
            gr.Markdown(NO_MODEL)

        with gr.Tab("Test one clip"):
            gr.Markdown(
                "Record or upload one attempt at the **selected phrase**. This is a binary "
                "target-vs-not-target decision, not a competition between every dhikr. The clip "
                "is conditioned exactly as in "
                "training — silence trimmed, loudness normalised, fitted to the model's "
                "window — and the spectrogram below is literally what the model saw."
            )
            with gr.Row():
                with gr.Column(scale=1):
                    clip_audio = gr.Audio(
                        sources=["microphone", "upload"], type="numpy", label="Clip"
                    )
                    clip_trim = gr.Checkbox(
                        value=True,
                        label="Trim silence (as in training)",
                        info="Turn off to feed the clip as recorded.",
                    )
                    clip_button = gr.Button("Classify", variant="primary")
                with gr.Column(scale=1):
                    clip_note = gr.Markdown()
                    clip_scores = gr.Label(num_top_classes=2, label="Target probability")
            clip_plot = gr.Plot(label="What the model saw")
            clip_table = gr.Dataframe(
                headers=["decision", "probability"], label="Binary output", interactive=False
            )

            clip_button.click(
                classify_clip,
                inputs=[selected_model, clip_audio, clip_trim],
                outputs=[clip_scores, clip_table, clip_plot, clip_note],
            )

        with gr.Tab("Count selected phrase"):
            gr.Markdown(
                "Slide the selected phrase model over a longer recording and count only that "
                "phrase. The exported activation/release thresholds and hysteresis state machine "
                "are loaded from `model_metadata.json`; changing the sliders is an experiment, "
                "not a new production calibration."
            )
            with gr.Row():
                with gr.Column(scale=1):
                    scan_audio = gr.Audio(
                        sources=["microphone", "upload"], type="numpy", label="Recording"
                    )
                    scan_hop = gr.Slider(
                        0.05, 1.0, value=initial_hop, step=0.05,
                        label="Window hop (s)",
                        info="Loaded from this phrase export; smaller = finer timing, more compute.",
                    )
                    scan_threshold = gr.Slider(
                        0.1, 0.99, value=initial_activation, step=0.01,
                        label="Activation threshold",
                        info="A candidate starts above this exported per-phrase threshold.",
                    )
                    scan_release = gr.Slider(
                        0.0, 0.99, value=initial_release, step=0.01,
                        label="Release threshold",
                        info="The score must fall below this to re-arm for the next repetition.",
                    )
                    scan_button = gr.Button("Scan and count", variant="primary")
                with gr.Column(scale=1):
                    scan_note = gr.Markdown()
                    scan_counts = gr.Dataframe(
                        headers=["phrase", "count"], label="Counted", interactive=False
                    )
            scan_plot = gr.Plot(label="Probabilities over time")
            scan_timeline = gr.Dataframe(
                headers=["from (s)", "to (s)", "phrase", "peak confidence"],
                label="Detections · first and last confident window of each",
                interactive=False,
            )

            scan_button.click(
                scan_recording,
                inputs=[
                    selected_model, scan_audio, scan_hop,
                    scan_threshold, scan_release,
                ],
                outputs=[scan_counts, scan_timeline, scan_plot, scan_note],
            )

        with gr.Tab("Phrase model details"):
            info_markdown = gr.Markdown(model_info(initial_selected) if initial_selected else NO_MODEL)

        with gr.Tab("Add phrase models"):
            gr.Markdown(
                "### From an export root\n"
                "Point the Space at a **Google Drive folder** (shared as *Anyone with the link*), "
                "a **Hugging Face repo**, or a local export root. Target subfolders such as "
                "`006/` and `007/` are preserved so each model keeps its own `labels.txt` and "
                "`model_metadata.json`."
            )
            with gr.Row():
                source_box = gr.Textbox(
                    label="Folder or repo",
                    placeholder="https://drive.google.com/drive/folders/…  ·  hf://user/repo",
                    scale=4,
                )
                fetch_button = gr.Button("Fetch", variant="primary", scale=1)
            fetch_status = gr.Markdown()

            gr.Markdown(
                "---\n### Or upload the files\n"
                "Upload **one complete phrase export at a time**. It lands in a target-specific "
                "temporary folder, so on a hosted Space it lasts only "
                "until it restarts. Commit the export to `model/`, or set `DHIKR_MODEL_SOURCE` / "
                "`model_source.txt` to the shared folder, to have it there on every start."
            )
            upload_files = gr.File(
                file_count="multiple",
                file_types=[".tflite", ".txt", ".json"],
                label="dhikr_<id>_*.tflite + labels.txt + model_metadata.json",
            )
            upload_button = gr.Button("Install", variant="secondary")
            upload_status = gr.Markdown()

            fetch_button.click(
                fetch_source,
                inputs=[source_box],
                outputs=[
                    model_dropdown, selected_model, fetch_status, info_markdown,
                    phrase_markdown, scan_hop, scan_threshold, scan_release,
                ],
            )
            upload_button.click(
                install_uploads,
                inputs=[upload_files],
                outputs=[
                    model_dropdown, selected_model, upload_status, info_markdown,
                    phrase_markdown, scan_hop, scan_threshold, scan_release,
                ],
            )

        model_dropdown.change(
            select_model,
            inputs=[model_dropdown],
            outputs=[
                selected_model, info_markdown, phrase_markdown,
                scan_hop, scan_threshold, scan_release,
            ],
        )
        rescan_button.click(
            refresh_models,
            inputs=[selected_model],
            outputs=[
                model_dropdown, selected_model, info_markdown, phrase_markdown,
                scan_hop, scan_threshold, scan_release,
            ],
        )

    return demo


if __name__ == "__main__":
    build_demo().queue().launch(
        server_name=os.environ.get("GRADIO_SERVER_NAME", "0.0.0.0"),
        server_port=int(os.environ.get("GRADIO_SERVER_PORT", "7860")),
        # This is a diagnostic tool: an unexpected exception is information, not
        # something to hide behind "an error occurred".
        show_error=True,
    )

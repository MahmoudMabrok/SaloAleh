"""Gradio Space for testing an exported DhikrSpeech model.

Three things you cannot do from the training notebook:

* **Single clip** - record or upload one dhikr and see what the model thinks,
  next to the log-mel it actually saw. This is the one to reach for when a
  class looks wrong in the confusion matrix.
* **Scan a recording** - slide the model's window over a long recording and
  count the dhikr in it. This is the Android use case end to end: the counter
  is the product, the classifier is a component of it.
* **Model info** - the exported metadata, benchmarks and verification numbers,
  so a Space visitor knows which build they are testing.

Run locally with ``python app.py`` from a checkout; on Hugging Face the Space
launches this file directly.
"""

from __future__ import annotations

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

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

from inference import (  # noqa: E402
    DhikrModel,
    ScanResult,
    count_detections,
    discover_models,
    display_label,
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
    """Every model on disk: bundled, fetched from a shared folder, or uploaded."""
    found = (
        discover_models(MODEL_DIR)
        + discover_models(REMOTE_DIR, recursive=True)
        + discover_models(UPLOAD_DIR)
    )
    seen, ordered = set(), []
    for path in found:
        key = str(path)
        if key not in seen:
            seen.add(key)
            ordered.append(key)
    return ordered


def get_model(path: Optional[str]) -> DhikrModel:
    if not path:
        raise gr.Error(
            "No model loaded. Drop dhikr_int8.tflite, labels.txt and model_meta.json "
            "into the Space's model/ folder, or upload them in the 'Load a model' tab."
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
    """Filename, plus where it came from when that is not obvious."""
    resolved = Path(path)
    if str(resolved).startswith(str(REMOTE_DIR)):
        return f"{resolved.name}  (shared folder)"
    if str(resolved).startswith(str(UPLOAD_DIR)):
        return f"{resolved.name}  (uploaded)"
    return resolved.name


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
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        message,
        model_info(selected) if selected else NO_MODEL,
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


def scan_figure(samples: np.ndarray, scan: ScanResult, detections, sample_rate: int, phrases):
    """Waveform with detection markers, and every class probability over time."""
    figure, (top, bottom) = plt.subplots(
        2, 1, figsize=(9, 5.2), gridspec_kw={"height_ratios": [1, 2]}, sharex=True
    )
    figure.patch.set_facecolor(PLOT_BG)

    duration = samples.size / float(sample_rate)
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

    palette = plt.get_cmap("tab10")
    for index, label in enumerate(scan.labels):
        bottom.plot(
            scan.times,
            scan.probabilities[:, index],
            label=display_label(label, phrases),
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

    best = int(np.argmax(probabilities))
    note = (
        f"**{display_label(model.labels[best], model.phrases)}** · "
        f"{probabilities[best]:.1%} confidence · "
        f"{samples.size / model.frontend.sample_rate:.2f}s in, "
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
    model_path: str, audio, hop_seconds: float, threshold: float, min_gap: float, skip_unknown: bool
):
    model = get_model(model_path)
    samples = to_mono_float(audio, model.frontend.sample_rate)

    limit = int(MAX_SCAN_SECONDS * model.frontend.sample_rate)
    truncated = samples.size > limit
    if truncated:
        samples = samples[:limit]

    scan = model.scan(samples, hop_seconds=hop_seconds)
    detections, counts = count_detections(
        scan,
        threshold=threshold,
        min_gap_seconds=min_gap,
        ignore=("unknown",) if skip_unknown else (),
    )

    timeline = [
        [
            round(detection.start, 2),
            round(detection.end, 2),
            display_label(detection.label, model.phrases),
            round(detection.confidence, 3),
        ]
        for detection in detections
    ]
    summary = [
        [display_label(label, model.phrases), count]
        for label, count in sorted(counts.items(), key=lambda pair: -pair[1])
    ]
    figure = scan_figure(samples, scan, detections, model.frontend.sample_rate, model.phrases)

    duration = samples.size / model.frontend.sample_rate
    note = (
        f"**{len(detections)} dhikr counted** in {duration:.1f}s · "
        f"{scan.windows} windows of {model.frontend.clip_seconds:g}s at a {hop_seconds:g}s hop"
    )
    if truncated:
        note += f"\n\n⚠️ Recording truncated to the first {MAX_SCAN_SECONDS:g}s."
    open_set = model.open_set_warning()
    if open_set:
        note += f"\n\n⚠️ {open_set}"
    if not detections:
        note += (
            "\n\nNothing crossed the threshold. Lower it, or check the single-clip tab "
            "to see whether the model recognises the phrase at all."
        )
    return summary, timeline, figure, note


# ---------------------------------------------------------------------------
# Tab 3 - model info
# ---------------------------------------------------------------------------
def model_info(model_path: str) -> str:
    if not model_path:
        return (
            "### No model loaded\n\n"
            "Add an exported model to the Space (see the **Load a model** tab)."
        )
    model = get_model(model_path)
    frontend = model.frontend
    input_dtype, output_dtype = model.io_dtypes

    lines = [
        f"### `{model.path.name}`",
        "",
        "| | |",
        "|---|---|",
        f"| backend | {model.backend} |",
        f"| size | {model.size_kb / 1024.0:.2f} MB |",
        f"| input shape | {model.input_shape} |",
        f"| dtypes | {input_dtype} in / {output_dtype} out |",
        f"| classes | {model.num_classes} |",
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

    for warning in (model.shape_mismatch(), model.open_set_warning()):
        if warning:
            lines += ["", f"> ⚠️ {warning}"]

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
    """Copy uploaded artefacts into a runtime folder and reselect a model."""
    if not files:
        raise gr.Error("Choose the .tflite file, plus labels.txt and model_meta.json.")
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    installed = []
    for item in files:
        source = Path(item)
        destination = UPLOAD_DIR / source.name
        shutil.copyfile(source, destination)
        installed.append(destination.name)

    # Every cached upload is dropped, not just the files that were replaced: a
    # new labels.txt or model_meta.json changes how an already-loaded model is
    # interpreted, and a stale cache entry would keep showing the old labels.
    for cached in [key for key in _CACHE if key.startswith(str(UPLOAD_DIR))]:
        _CACHE.pop(cached, None)

    choices = available_models()
    uploaded_models = [path for path in choices if str(UPLOAD_DIR) in path]
    selected = uploaded_models[0] if uploaded_models else (choices[0] if choices else None)

    message = "Installed: " + ", ".join(sorted(installed))
    if not uploaded_models:
        message += (
            "\n\n⚠️ No model file among them — upload the `.tflite` itself, not only "
            "the sidecar files."
        )
    elif "labels.txt" not in installed:
        message += (
            "\n\n⚠️ No `labels.txt` — classes will show as `class_0`, `class_1`, … "
            "Upload it alongside the model."
        )
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        message,
        model_info(selected) if selected else "",
    )


def select_model(path: Optional[str]):
    """Dropdown -> the shared State plus a refreshed info tab."""
    return path, (model_info(path) if path else NO_MODEL)


def refresh_models(current: Optional[str]):
    choices = available_models()
    selected = current if current in choices else (choices[0] if choices else None)
    return (
        gr.update(choices=[(model_choice_label(path), path) for path in choices], value=selected),
        selected,
        model_info(selected) if selected else "",
    )


# ---------------------------------------------------------------------------
# UI
# ---------------------------------------------------------------------------
INTRO = """
# DhikrSpeech · model playground

Test an exported **offline Arabic dhikr phrase spotter** — the TFLite model that
[SaloAleh](https://github.com/MahmoudMabrok/SaloAleh) ships to Android. Recording one clip tells you
whether the model knows a phrase; scanning a longer recording tells you whether it can *count* one,
which is what the app actually needs.

Audio is processed in-memory for the length of the request and never stored.
"""

NO_MODEL = """
### No model in this Space yet

Put an export from section **05 · Export** of the notebook into `model/`:

```
model/
├── dhikr_int8.tflite      (or dhikr_float32.tflite / dhikr_dynamic_range.tflite)
├── labels.txt
└── model_meta.json
```

…or upload them in the **Load a model** tab. Restart or hit **Rescan** afterwards.
"""


def build_demo() -> gr.Blocks:
    startup_note = fetch_startup_source()
    initial = available_models()
    initial_selected = initial[0] if initial else None

    with gr.Blocks(title="DhikrSpeech · model playground", theme=gr.themes.Soft()) as demo:
        gr.Markdown(INTRO)
        if startup_note:
            gr.Markdown(startup_note)

        with gr.Row():
            model_dropdown = gr.Dropdown(
                label="Model",
                choices=[(model_choice_label(path), path) for path in initial],
                value=initial_selected,
                scale=4,
            )
            rescan_button = gr.Button("Rescan", scale=1)
        selected_model = gr.State(initial_selected)

        if not initial:
            gr.Markdown(NO_MODEL)

        with gr.Tab("Single clip"):
            gr.Markdown(
                "Record or upload **one** dhikr. The clip is conditioned exactly as in "
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
                    clip_scores = gr.Label(num_top_classes=5, label="Prediction")
            clip_plot = gr.Plot(label="What the model saw")
            clip_table = gr.Dataframe(
                headers=["class", "probability"], label="All classes", interactive=False
            )

            clip_button.click(
                classify_clip,
                inputs=[selected_model, clip_audio, clip_trim],
                outputs=[clip_scores, clip_table, clip_plot, clip_note],
            )

        with gr.Tab("Scan a recording"):
            gr.Markdown(
                "Slide the model's window over a longer recording and **count** the dhikr in it. "
                "One dhikr keeps the model confident for as long as it lasts, so the unit of "
                "counting is a *run* of agreeing windows — however long — not a window."
            )
            with gr.Row():
                with gr.Column(scale=1):
                    scan_audio = gr.Audio(
                        sources=["microphone", "upload"], type="numpy", label="Recording"
                    )
                    scan_hop = gr.Slider(
                        0.05, 1.0, value=0.25, step=0.05,
                        label="Window hop (s)",
                        info="Smaller = finer timing, more compute.",
                    )
                    scan_threshold = gr.Slider(
                        0.1, 0.99, value=0.7, step=0.01,
                        label="Confidence threshold",
                        info="Raise it if noise is counted, lower it if dhikr are missed.",
                    )
                    scan_gap = gr.Slider(
                        0.0, 3.0, value=0.5, step=0.1,
                        label="Refractory period (s)",
                        info="Two runs of the same phrase closer than this are one dhikr.",
                    )
                    scan_skip_unknown = gr.Checkbox(
                        value=True, label="Ignore the 'unknown' class"
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
                    scan_threshold, scan_gap, scan_skip_unknown,
                ],
                outputs=[scan_counts, scan_timeline, scan_plot, scan_note],
            )

        with gr.Tab("Model info"):
            info_markdown = gr.Markdown(model_info(initial_selected) if initial_selected else NO_MODEL)

        with gr.Tab("Load a model"):
            gr.Markdown(
                "### From a shared folder\n"
                "Point the Space at a **Google Drive folder** (shared as *Anyone with the link*), "
                "a **Hugging Face repo**, or a direct file URL. Fetching the whole folder is what "
                "keeps `labels.txt` and `model_meta.json` with the `.tflite` — a lone model file "
                "loads with positional class names and a guessed front-end."
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
                "Both routes land in a temporary folder, so on a hosted Space they last only "
                "until it restarts. Commit the export to `model/`, or set `DHIKR_MODEL_SOURCE` / "
                "`model_source.txt` to the shared folder, to have it there on every start."
            )
            upload_files = gr.File(
                file_count="multiple",
                file_types=[".tflite", ".txt", ".json"],
                label="dhikr_*.tflite + labels.txt + model_meta.json",
            )
            upload_button = gr.Button("Install", variant="secondary")
            upload_status = gr.Markdown()

            fetch_button.click(
                fetch_source,
                inputs=[source_box],
                outputs=[model_dropdown, selected_model, fetch_status, info_markdown],
            )
            upload_button.click(
                install_uploads,
                inputs=[upload_files],
                outputs=[model_dropdown, selected_model, upload_status, info_markdown],
            )

        model_dropdown.change(
            select_model,
            inputs=[model_dropdown],
            outputs=[selected_model, info_markdown],
        )
        rescan_button.click(
            refresh_models,
            inputs=[selected_model],
            outputs=[model_dropdown, selected_model, info_markdown],
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

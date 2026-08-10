---
title: DhikrSpeech
emoji: 📿
colorFrom: yellow
colorTo: gray
sdk: gradio
sdk_version: 5.50.0
app_file: app.py
suggested_hardware: zero-a10g
pinned: false
license: mit
short_description: Test an offline Arabic dhikr phrase spotter and counter
---

# DhikrSpeech · per-phrase model playground

A Gradio Space for testing exported **DhikrSpeech** models. SaloAleh ships one independent binary
TFLite model per Arabic dhikr phrase; the picker therefore starts with the phrase, then shows the
available quantisation variants for that target.

The training notebook already reports accuracy, a confusion matrix and ROC curves. What it cannot
tell you is how the exported flatbuffer behaves on audio someone just spoke into a microphone, and
whether it can **count** dhikr in a continuous recording — which is the product, not the classifier.
That is what this Space is for.

| Tab | What it answers |
|---|---|
| **Test one clip** | Does the selected phrase model accept this clip, and what did it see? |
| **Count selected phrase** | How many repetitions of this exact phrase are in the recording? |
| **Phrase model details** | Which target, variant, detector calibration and measurements are loaded? |
| **Add phrase models** | Fetch an export root or upload one complete phrase bundle. |

Audio is processed in memory for the length of the request and never written to disk.

---

## Adding a model

The exports are not in git — they are produced on Drive by section **06 · Export** of
`notebooks/DhikrSpeech.ipynb`. There are three ways in, and the first needs no copying at all.

### 1 · From a shared folder (default)

`model_source.txt` holds a link the Space pulls on every start, so a fresh deploy comes up with all
available phrase models already loaded:

```
https://drive.google.com/drive/folders/<id>     # share as "Anyone with the link"
hf://<user>/<repo>                              # a Hugging Face model repo
https://example.com/dhikr_007_int8.tflite       # one direct model (metadata unavailable)
/mnt/exports                                    # a local path
```

`DHIKR_MODEL_SOURCE` overrides the file, so a hosted Space can be repointed from its **Settings →
Variables** without a commit. The **Add phrase models** tab also takes a link at runtime.

Only the export is fetched (`*.tflite`, `labels.txt`, `model_metadata.json`, …) — a `saved_model/`
directory is skipped unless `DHIKR_FETCH_SAVEDMODEL=1`, since the Space runs LiteRT and could not
load one anyway. Files are fetched individually and a refusal on one does not lose the rest: Drive
throttles per file once a link has seen traffic, and an all-or-nothing folder download would cost
the whole export over a single throttled file.

Two caveats about Drive specifically: the folder must be shared as **Anyone with the link**, and
Drive rate-limits popular files hard enough that a busy public Space will see failures. A Hugging
Face model repo is the more reliable home — `hf://user/repo` works the same way, supports private
repos through an `HF_TOKEN` secret, and is versioned.

### 2 · Committed to the Space

Put each target export in its own folder under `model/`:

```
model/
├── 006/
│   ├── dhikr_006_int8.tflite
│   ├── labels.txt
│   └── model_metadata.json
└── 007/
    ├── dhikr_007_int8.tflite
    ├── dhikr_007_float32.tflite
    ├── labels.txt
    └── model_metadata.json
```

The target folder is essential because every export has identically named sidecars. Flattening the
tree would make phrase 007's metadata overwrite phrase 006's. All three artefact types matter:

- **`labels.txt`** names the outputs. Without it every one shows as `class_0`, `class_1`, …
- **`model_metadata.json`** records the target phrase, binary output mode, front-end, calibrated
  activation/release detector and measurements. The Space trusts it over `configs/config.yaml`.
  A lone model can infer its target id from `dhikr_007_*.tflite`, but cannot reconstruct the phrase
  text or the detector safely.

Several variants can live inside one phrase folder. The picker marks the export's recommended
variant and keeps INT8/float32 comparison one click away without confusing variants with phrases.

### 3 · Uploaded at runtime

The **Add phrase models** tab takes one complete target bundle at a time. It installs it under a
temporary `<phrase id>/` folder, so adding 007 never overwrites 006. Uploads are lost on restart;
use one of the first two routes to make them stick.

---

### What the Space counts

The **Count selected phrase** tab uses `src.streaming.EventDetector`, the production hysteresis
state machine. Its window hop, activation threshold, release threshold, confirming windows,
release windows, cooldown and smoothing all come from the selected target's
`model_metadata.json`. Changing the visible thresholds is useful for diagnosis, but it does not
replace calibration in stage **05 · Streaming** of the notebook.

Each model is binary: `target` means this exact phrase; `unknown` or `1 - P(target)` means anything
else, including other dhikr. A one-output sigmoid export is handled as scalar P(target)—it is never
normalised as a one-class softmax, which would incorrectly turn every window into 100% target.

---

## Running it locally

From a checkout of the SaloAleh repo:

```bash
cd DhikrSpeech/space
pip install -r requirements.txt
python app.py                     # http://127.0.0.1:7860
```

`app.py` finds `src/`, `configs/config.yaml` and `phrases.json` one level up, so a checkout needs no
copying. Point it at exports somewhere else with `DHIKR_MODEL_DIR=/path/to/exports python app.py`.

---

## Deploying to Hugging Face

A Space is its own git repo and cannot reach into the parent folder, so the shared code has to be
copied in. `deploy.sh` does exactly that — it stages `space/` plus `src/`, `configs/config.yaml` and
`phrases.json`, then pushes:

```bash
export HF_TOKEN=hf_...                       # a write token from huggingface.co/settings/tokens
./deploy.sh <your-username>/dhikrspeech
```

The Space is created if it does not exist yet, **private**, because publishing a model to a public
URL is not a deploy script's decision — flip it from the Space's *Settings* page when you are ready.
Re-running the script updates it in place.

The staged copies (`src/`, `configs/`, `phrases.json` inside `space/`) are gitignored in this repo —
they exist only in the Space, so there is one source of truth for the pipeline code.

Add `model/<phrase id>/*.tflite` to the push with `--with-model` once exports are ready. The deploy
script copies the tree recursively so each target keeps its own metadata and labels.

---

## How the counting works

Scanning slides the selected phrase model's fixed window over the recording. The target score must
cross the activation threshold for the configured number of consecutive windows before one event
is confirmed. Once confirmed, it remains the same event while the score is above the lower release
threshold. Re-arming requires the configured number of released windows; the short cooldown is only
a safety net. This prevents one long utterance from being counted repeatedly without swallowing
rapid genuine repetitions.

The controls start at the values exported for this phrase:

| Symptom | Control |
|---|---|
| Noise and breaths are counted | Raise **activation**; then recalibrate against negative audio |
| One dhikr counted twice | Lower **release** or collect the fragment as a hard negative |
| A genuine repetition is merged | Raise **release** so the gap re-arms sooner |
| Dhikr missed entirely | Lower activation carefully; inspect the one-clip target score first |

The **detections table** shows each event's first and last confident window, so a count that spans
0.0–1.75 s is a solid hit and one that spans a single window is a flicker worth raising the
threshold against.

The probability plot shows only the selected phrase and its complement. A healthy model produces
clean target peaks and falls away between repetitions; a model flat near 1.0 is not usable however
good its held-out clip accuracy looked.

---

## Notes

- The Space installs **LiteRT**, not TensorFlow, so `.keras` and SavedModel exports need
  `tensorflow` added to `requirements.txt`. Every `.tflite` variant runs as-is. A SavedModel also
  cannot report a front-end mismatch — it reloads as a bare `TFSMLayer` with no declared shapes, so
  the front-end's shape is assumed rather than checked.
- Windows cut out of a longer recording are loudness-normalised but **not** silence-trimmed —
  trimming a window shifts its content in time and destroys the alignment the scanner depends on.
  Single clips are trimmed, matching training; the checkbox turns it off.
- `DHIKR_MAX_SCAN_SECONDS` (default 300) caps how much audio one scan will process.

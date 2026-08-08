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

# DhikrSpeech · model playground

A Gradio Space for testing an exported **DhikrSpeech** model — the offline Arabic dhikr phrase
spotter that [SaloAleh](https://github.com/MahmoudMabrok/SaloAleh) ships to Android as a quantised
TFLite file.

The training notebook already reports accuracy, a confusion matrix and ROC curves. What it cannot
tell you is how the exported flatbuffer behaves on audio someone just spoke into a microphone, and
whether it can **count** dhikr in a continuous recording — which is the product, not the classifier.
That is what this Space is for.

| Tab | What it answers |
|---|---|
| **Single clip** | Does the model recognise this phrase, and what did it actually see? |
| **Scan a recording** | How many dhikr are in this recording, and where? |
| **Model info** | Which build is this, what front-end does it need, how fast is it? |
| **Load a model** | Try a different export without redeploying. |

Audio is processed in memory for the length of the request and never written to disk.

---

## Adding a model

The exports are not in git — they are produced on Drive by section **05 · Export** of
`notebooks/DhikrSpeech.ipynb`. There are three ways in, and the first needs no copying at all.

### 1 · From a shared folder (default)

`model_source.txt` holds a link the Space pulls on every start, so a fresh deploy comes up with a
model already loaded:

```
https://drive.google.com/drive/folders/<id>     # share as "Anyone with the link"
hf://<user>/<repo>                              # a Hugging Face model repo
https://example.com/dhikr_int8.tflite           # a direct file URL
/mnt/exports                                    # a local path
```

`DHIKR_MODEL_SOURCE` overrides the file, so a hosted Space can be repointed from its **Settings →
Variables** without a commit. The **Load a model** tab also takes a link at runtime.

Only the export is fetched (`*.tflite`, `labels.txt`, `model_meta.json`, …) — a `saved_model/`
directory is skipped unless `DHIKR_FETCH_SAVEDMODEL=1`, since the Space runs LiteRT and could not
load one anyway. Files are fetched individually and a refusal on one does not lose the rest: Drive
throttles per file once a link has seen traffic, and an all-or-nothing folder download would cost
the whole export over a single throttled file.

Two caveats about Drive specifically: the folder must be shared as **Anyone with the link**, and
Drive rate-limits popular files hard enough that a busy public Space will see failures. A Hugging
Face model repo is the more reliable home — `hf://user/repo` works the same way, supports private
repos through an `HF_TOKEN` secret, and is versioned.

### 2 · Committed to the Space

Put the export in `model/`:

```
model/
├── dhikr_int8.tflite        # or dhikr_float32 / dhikr_dynamic_range
├── labels.txt               # one class label per line, in class-index order
└── model_meta.json          # front-end parameters, benchmarks, verification
```

All three matter:

- **`labels.txt`** names the classes. Without it every class shows as `class_0`, `class_1`, …
- **`model_meta.json`** records the front-end the weights were trained with, and the Space trusts it
  over `configs/config.yaml`. This is not a detail: a config that was retuned after the export would
  otherwise feed the model features it has never seen, and the predictions would be quietly wrong
  rather than visibly broken. When the two disagree about the input shape, the Space says so on
  screen instead of guessing.

Several `.tflite` files can live side by side — the dropdown at the top switches between them, so
comparing `int8` against `float32` on the same clip is one click.

### 3 · Uploaded at runtime

The **Load a model** tab takes the files directly. They go to a temp folder and are lost on restart;
use one of the first two routes to make a model stick.

---

## A model with no `unknown` class

If the export's classes are all phrases, the app says so on the scan tab and in the model info.
It is worth understanding why: softmax always sums to 1, so a model that only knows phrases has
nowhere to put silence, breathing or background speech — it assigns all of that mass to phrases and
reports high confidence while doing it. The classifier is not broken; it was never given the option
to say "that was not a dhikr".

For a counter this matters more than accuracy does, because most of a recording is *not* dhikr. Two
things help, in order: train with an `unknown` folder (`classes.include_unknown` in
`configs/config.yaml`), and until then raise the confidence threshold on the scan tab and read the
per-window probability plot rather than the count alone.

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

Add `model/*.tflite` to the push with `--with-model` once you are happy with an export; without it
the Space deploys empty and you upload a model through the UI.

---

## How the counting works

Scanning slides the model's fixed window (2 s by default) over the recording at a configurable hop
and classifies each position, so one dhikr produces a *run* of confident windows, not a single one —
and the longer the phrase, the longer the run.

That is why the unit of counting is the **run**, not the window: consecutive windows agreeing on the
same above-threshold label are one event however long they last. A plain "ignore this phrase for N
seconds after counting it" timer looks equivalent and is not — it splits any phrase that stays
confident for longer than N into two counts, which is exactly what the longer dhikr do.

The counting itself is `src.streaming.EventDetector` — the same state machine the notebook calibrates
in stage 06 and the Android app is specified against, so what this Space shows is what the pipeline
measured. A run is held together by **hysteresis**: an event needs the confidence threshold to start
but only a lower release threshold to continue, so a wobble mid-phrase cannot end one event and start
another.

The **refractory period** is the detector's cooldown: it closes gaps *between* runs, so two runs of
the same phrase closer together than it are one dhikr flickering, not two. It is applied per label,
so two different phrases said back to back stay two counts even with no silence between them.

One difference from production: this tab confirms an event from a **single** confident window, where
the shipped default asks for two in a row. This is a diagnostic for "does the model hear the phrase
at all", and swallowing single-window hits would hide exactly the weak recognition it is here to
show.

Three controls, and what to reach for when:

| Symptom | Control |
|---|---|
| Noise and breaths are counted | Raise the **confidence threshold** |
| One dhikr counted twice, with a dip between the halves | Raise the **refractory period** |
| A genuine repetition merged into one count | Lower the **refractory period** |
| Dhikr missed entirely | Lower the threshold; if it does not help, the model is the problem — check the clip tab |

The **detections table** shows each event's first and last confident window, so a count that spans
0.0–1.75 s is a solid hit and one that spans a single window is a flicker worth raising the
threshold against.

The per-window probability plot is the diagnostic: a model that works shows clean peaks per phrase,
a model that has collapsed shows one class flat near 1.0 for the whole recording.

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

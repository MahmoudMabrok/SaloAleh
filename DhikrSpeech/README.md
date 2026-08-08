# DhikrSpeech

Training pipeline for an **offline Arabic dhikr phrase spotter**. It takes short recordings of dhikr
phrases from Google Drive and produces a quantised TensorFlow Lite model that runs on Android with
no network access.

One Colab notebook, one config file, one reusable Python package. The notebook orchestrates;
every piece of logic lives in `src/` so nothing is duplicated between its sections.

```
recordings on Drive
   └─ 01 · Dataset       inspect + validate, speakers, is there enough of it
   └─ 02 · Preprocessing condition to 16 kHz mono, freeze a speaker-safe split
   └─ 03 · Training      DS-CNN, TensorBoard, checkpoints, resume
   └─ 04 · Evaluation    clip metrics, confusion matrix, ROC, error analysis
   └─ 05 · Export        SavedModel + 3 TFLite variants, benchmarked and verified
   └─ 06 · Streaming     sliding windows, event counting, FALSE ACTIVATIONS/HOUR,
                         threshold calibration, Android contract, readiness verdict
                            └─ app/src/main/assets/
```

**Clip accuracy is not the deliverable.** The app listens continuously and has to increment a counter
exactly once per utterance while staying silent through conversation, television and other dhikr.
Stage 06 measures that; a model that scores well in stage 04 can still count a conversation as
dhikr, and nothing before stage 06 would notice.

---

## Contents

> **New to machine learning or to audio?** Read
> [`docs/LEARNING_GUIDE.md`](docs/LEARNING_GUIDE.md) first. It explains *why* every part of this
> pipeline is the way it is — sampling, spectrograms, convolutions, quantisation, the counting
> logic — assuming no ML background. This README is the operational how-to; that one is the
> background.

- [Layout](#layout)
- [Quick start](#quick-start)
- [1 · Upload the dataset](#1--upload-the-dataset)
- [2 · Mount Drive and open a notebook](#2--mount-drive-and-open-a-notebook)
- [3 · Train](#3--train)
- [4 · Resume training](#4--resume-training)
- [5 · Export](#5--export)
- [6 · Streaming evaluation and calibration](#6--streaming-evaluation-and-calibration)
- [7 · Test the export](#7--test-the-export)
- [8 · Integrate into Android](#8--integrate-into-android)
- [Configuration](#configuration)
- [Data collection guide](#data-collection-guide)
- [One model per dhikr?](#one-model-per-dhikr)
- [Growing the dataset](#growing-the-dataset)
- [Troubleshooting](#troubleshooting)

---

## Layout

```text
DhikrSpeech/
├── notebooks/
│   └── DhikrSpeech.ipynb         the whole pipeline, six stages, run top to bottom
│                                 (+ stage 07, an optional one-vs-rest experiment)
├── src/
│   ├── config.py                 typed config loaded from configs/config.yaml
│   ├── audio.py                  decode, trim, normalise, fit length, write WAV
│   ├── dataset.py                scan, validate, preprocess, split, tf.data pipeline
│   ├── speakers.py               who spoke what, and proving the splits never share one
│   ├── quality.py                is there enough data, and enough variety, to train
│   ├── augmentation.py           noise, pitch, speed, gain, time shift, reverb, SpecAugment
│   ├── features.py               log mel front-end (+ its Android metadata)
│   ├── models.py                 DS-CNN (+ capacity vs. dataset size)
│   ├── trainer.py                seeds, schedules, callbacks, resume
│   ├── metrics.py                accuracy / P / R / F1 / ROC / error analysis
│   ├── streaming.py              sliding windows + the event-counting state machine
│   ├── streaming_eval.py         event metrics, FA/hour, threshold calibration
│   ├── readiness.py              the production-readiness verdict
│   ├── android.py                model_metadata.json + the front-end parity fixture
│   ├── experiments.py            one-vs-rest vs. multi-class comparison (stage 07)
│   ├── visualization.py          every chart
│   └── export.py                 SavedModel, TFLite, benchmark, verification
├── tests/                        pytest — the scoring logic, no TensorFlow needed
├── configs/
│   ├── config.yaml               the only place settings live
│   └── presets/                  documented overlays: tiny_dataset, standard, large_dataset
├── space/                        Gradio app for testing an export (Hugging Face Space)
│   ├── app.py                    four tabs: clip, scan, model info, load a model
│   ├── inference.py              model loading, sliding-window scan, counting
│   └── deploy.sh                 stage src/ + configs/ into a Space and push
├── docs/
│   └── LEARNING_GUIDE.md         the concepts behind the pipeline, from zero ML background
├── requirements.txt
└── README.md
```

The notebook contains no thresholds, paths or hyperparameters of its own — it reads
`configs/config.yaml`. Change behaviour there, not in a cell.

---

## Quick start

1. Put your recordings on Drive (below).
2. Open `notebooks/DhikrSpeech.ipynb` in Colab → **Runtime → Change runtime type → GPU**.
3. **Runtime → Run all**, or run section by section — `01 · Dataset` through `05 · Export`.
4. Run `06 · Streaming` against long-form recordings to calibrate the thresholds and get a
   readiness verdict. Skip it and the counter has never been measured as a counter.
5. Copy `exports/*.tflite`, `labels.txt`, `model_metadata.json` and `mel_filterbank.json` into
   the app.

The first cell mounts Drive, finds the project (cloning the repo if it is not already in the
runtime), installs anything missing and loads the config. There is nothing else to set up.

---

## 1 · Upload the dataset

Create this structure in **My Drive**:

```text
MyDrive/Dhikr Speech Dataset/
├── dataset/
│   ├── 006/                    every recording of phrase id 6
│   ├── 007/
│   ├── ...
│   └── unknown/                speech that is NOT a target dhikr
│       ├── hard_negative/      near-miss phrases  ← the ones that decide the model
│       ├── partial_phrase/     incomplete utterances
│       ├── other_dhikr/        dhikr that are not the targets
│       ├── normal_speech/      ordinary Arabic speech
│       └── noise/              recorded room tone kept as a *class* example
├── phrases.json
├── speakers.csv                optional: file,speaker — the best way to know who spoke what
├── noise/                      room / background recordings mixed under training clips.
│                               A sibling of dataset/, never a class inside it.
└── streaming_test/             long-form recordings for stage 06
    ├── audio/*.wav
    └── annotations.json
```

`checkpoints/`, `exports/`, `logs/`, `processed/` and `reports/` are created automatically.

**Folder names are class ids.** Folder `001` is phrase id `1`, zero-padded to three digits. Plain
`1` also works. The folder name — not the file name — decides the label.

**`phrases.json`** maps ids to text:

```json
[
  { "id": 1, "text": "سبحان الله" },
  { "id": 2, "text": "الحمد لله" },
  { "id": 3, "text": "الله أكبر" }
]
```

### Speaker naming — the single most important thing to get right

A model recognises a voice it has heard far more easily than a stranger's. If the same speaker
appears in both training and test, the test accuracy answers *"can it recognise these recordings
again"* — and the app is installed by people the model has never heard. The split therefore groups
**by speaker**, and stage 02 **fails** if a speaker turns up in two splits.

Three ways to tell the pipeline who spoke what, in order of preference:

1. **`speakers.csv`** next to `phrases.json` — explicit, nothing to infer:

   ```csv
   file,speaker
   006/ali_001.wav,ali
   006/ali_002.wav,ali
   007/fatima_001.wav,fatima
   ```

   `file` may be a bare name, a path relative to `dataset/`, or an absolute path.

2. **A per-speaker subfolder**: `dataset/006/ali/*.wav`.

3. **A filename convention** — `split.speaker.filename_patterns`, tried in order:

   * `sp<8 hex>` anywhere in the name. This is what `SpeechCollector` stamps into every upload
     (`<class>_sp<8 hex>_<timestamp>_<suffix>`), from a random id the volunteer's browser mints once
     and reuses, so it works with no configuration at all.
   * otherwise the leading token: `ali_001.wav`, `speaker01_003.wav`, `s7-12.wav`.

   A token that is the class folder's own name is **rejected** rather than used — without that guard
   a collector filename would resolve to its class id and every recording of a phrase would become
   one "speaker". Set `split.speaker.regex` to override the list with a single pattern of your own.

`split.speaker.source: auto` picks whichever covers the most files — once, for the whole dataset, so
`ali` from a folder and `ali` from a filename can never be silently treated as two people. Partial
coverage is used rather than discarded (a dataset part-way through a collector rollout groups its
newer files and leaves the older ones one group each), and stage 01 warns when it is partial.

**With none of them, the pipeline says so loudly and repeatedly**, and the readiness report will not
pass: the split is then random, the same voice can span it, and no number downstream describes an
unseen user. Use a **speaker id that is stable across sessions** — the same person recording twice
must get the same id, or the "unseen speaker" guarantee is only as good as the id.

Speaker ids never need to be real names. `sp01`, `sp02` … is enough, and better for privacy.

### Negatives, and why `hard_negative` is the important folder

**The `unknown` folder is not optional in practice.** A model trained only on dhikr phrases will
classify a cough, a TV, or "good morning" as whichever phrase sounds closest, and on device that
becomes a phantom count. Fill it with ordinary speech, silence, room tone and background noise.
Aim for at least as many `unknown` clips as an average phrase class.

Volunteer speech for this folder arrives on its own: the last card in `SpeechCollector` asks for any
ordinary word that is *not* a dhikr and uploads it directly to `dataset/unknown/`. It is not listed
in `phrases.json` — `scan_dataset` labels the folder by name — so nothing here needs configuring
beyond `classes.include_unknown`. Silence and room tone still have to be added by hand.

**The `noise/` folder fills itself too**, from the collector's last card, which asks volunteers for
background noise with no speech in it. Note where it lands: `noise/` is a **sibling** of `dataset/`,
not a class inside it, because these clips are never labelled — `augmentation.background_noise` mixes
them *underneath* real recordings so a model learned in a quiet room still works in a noisy one.
Uploads arrive as `.webm` (Chrome) or `.m4a` (Safari), which `NoiseBank.load` accepts along with
WAV/FLAC/OGG/MP3.

**Two different things are called noise, and they go to different places.** `noise/` — a sibling of
`dataset/`, where the collector's noise card uploads — is never labelled: `augmentation.background_noise`
mixes those clips *underneath* real recordings so a model learned in a quiet room still works in a
noisy one. `dataset/unknown/noise/` is a **class** example: room tone the model must actively
recognise as *not a dhikr* at inference. The same recording can reasonably sit in both, but it does
two different jobs, and filing augmentation noise inside `dataset/` instead of alongside it is a
mistake worth avoiding.

**Organise it into subfolders.** Everything under `unknown/` still trains as the single `unknown`
class — the subfolder does not become an output — but it is kept in the manifest as `negative_type`,
and the evaluation then reports the false-positive rate *per category*. That turns "the model has
some false positives" into "it fires on partial phrases and on nothing else", which is a
data-collection instruction rather than a mystery. A flat `unknown/*.wav` folder still works
unchanged.

**Hard negatives are the highest-value recordings in the dataset after the phrases themselves.**
They are the near misses: what someone says that is *almost* the target. For
`سبحان الله العظيم وبحمده` (007):

| record | why |
|---|---|
| `سبحان الله` | a prefix of it — and a target phrase in its own right elsewhere |
| `سبحان الله العظيم` | a longer prefix; the difference is two words at the end |
| `سبحان الله وبحمده` | phrase 006 — the model must tell these two apart |
| `الله العظيم وبحمده` | the tail without the opening |
| trailing off mid-phrase | what a distracted user actually does |

For `سبحان الله وبحمده` (006): `سبحان الله`, `وبحمده` alone, `سبحان الله العظيم`, and the other
similar phrases. Put them in `dataset/unknown/hard_negative/`.

**Record them from real people.** Cutting target recordings into fragments is a reasonable
augmentation and a reasonable *test*, but it is not a substitute: a cut clip has the prosody of a
completed phrase, and a model trained to reject those learns to reject an edit artefact rather than a
half-said dhikr. Ask volunteers to say the near misses deliberately.

### What makes a good recording

| | |
|---|---|
| length | 1–3 seconds, one phrase per file |
| format | WAV preferred; FLAC/OGG/MP3/M4A are decoded too |
| rate | anything — stage 02 resamples to 16 kHz mono |
| count | ~100 per phrase to verify the pipeline; **200–500** for a first model worth shipping |
| speakers | 10 minimum, 20+ for a model strangers will use — this matters more than raw count |
| variety | distances, rooms, phones, speeds; see the [data collection guide](#data-collection-guide) |

Recordings can be collected with the `SpeechCollector/` web app in this repository, which writes
straight to Drive in this layout.

### Real background noise

`noise/` is optional and the pipeline falls back to synthetic white/pink noise, but the two are not
equivalent. Real noise has *structure* — speech babble, a television, traffic — and structure is what
a false activation is made of; flat noise mostly teaches the model to ignore a hiss. Stage 02 prints
how much real noise it found, whether the synthetic fallback is active, and what to record.

A few minutes each, on the phone, of: a quiet room (it is not silence), a fan or air conditioning,
street traffic, inside a car, a TV or radio, people talking nearby, children, mosque or background
Quran recitation, the phone being handled, and the same room at different distances.

### Streaming test recordings

Stage 06 needs **long-form** audio, which the clip dataset does not contain:

```text
streaming_test/
├── audio/
│   ├── session_001.wav     someone reciting normally for a few minutes
│   ├── session_002.wav     another speaker, another room
│   ├── negative_tv.wav     television — no dhikr at all
│   └── negative_talk.wav   ordinary conversation — no dhikr at all
└── annotations.json
```

```json
{
  "files": [
    {
      "file": "session_001.wav",
      "events": [
        { "class": "006", "start": 12.3, "end": 13.8 },
        { "class": "007", "start": 25.1, "end": 27.0 }
      ]
    },
    { "file": "negative_tv.wav", "events": [], "negative_type": "tv" }
  ]
}
```

* Timings need only be roughly right — matching is tolerant (`streaming.matching.tolerance_seconds`,
  default 1 s, plus any overlap).
* A file with `"events": []` is **negative-only**: no annotation work at all, because the right
  answer for the whole recording is zero. These carry the false-activation rate, which is the number
  that decides the feature — collect *more* of these than of the recitation sessions.
* `"negative_type"` is free text (`tv`, `conversation`, `quran`, `street`) and is reported per
  category, so you learn what kind of audio breaks the detector.
* Aim for at least 20 minutes total. Below that a single false activation is worth several per hour
  and the number cannot be compared against a budget of 0.5.

**Where the recordings come from.** `SpeechCollector`'s **تسجيل ١٠ مرات** button on every phrase card
records one long take holding that dhikr ten times and uploads it to a *separate* tree on Drive:

```text
streaming/                      a SIBLING of dataset/, never scanned as a class
└── 006/
    └── 006_x10_sp3f9a2c41_20260803_183015_ab12cd.webm
```

The `x10` in the filename is the number of events the clip is meant to hold — the collector cannot
produce *timings*, so it writes the one thing it knows where it cannot be separated from the audio.
Turning a folder of these into the layout above is still manual: convert to WAV into
`streaming_test/audio/`, then annotate each utterance's start/end. The count is the check that the
annotation is complete, and a take whose events do not number ten is a take to look at rather than
annotate. `streaming/` is the recruitment side of this section; it is not a drop-in replacement for
it, and nothing in the pipeline reads that folder.

---

## 2 · Mount Drive and open a notebook

Upload `notebooks/DhikrSpeech.ipynb` to Colab (or open it from GitHub with
**File → Open notebook → GitHub**), then run the first cell. It:

1. calls `drive.mount("/content/drive")` — approve the permission prompt,
2. locates the project, cloning `MahmoudMabrok/SaloAleh` into `/content/SaloAleh` if needed,
3. installs any missing packages (Colab already has TensorFlow, NumPy, scikit-learn, matplotlib),
4. loads `configs/config.yaml` and prints a summary.

To run against a project copy somewhere else, set the path before running:

```python
import os
os.environ["DHIKR_PROJECT_ROOT"] = "/content/drive/MyDrive/DhikrSpeech"
```

Outside Colab: `pip install -r requirements.txt`, then run Jupyter from the `DhikrSpeech` folder
and point `paths.drive_root` in the config at a local directory.

---

## 3 · Train

Run sections `01` and `02` first — training reads `processed/manifest.csv`, which section 02 writes.

Set **Runtime → Change runtime type → GPU** before starting, or training falls back to CPU.

What the config turns on, all reported in the notebook as it runs:

| feature | config key |
|---|---|
| TensorBoard | `training.tensorboard.*` (charts appear inline, live) |
| mixed precision | `training.mixed_precision` (GPU only; ignored on CPU) |
| early stopping | `training.early_stopping.*` |
| checkpoint saving | `training.checkpoint.*` → `checkpoints/<run>/best_model.keras` |
| resume | `training.resume` |
| class weights | `training.class_weights` (balances uneven classes) |
| label smoothing | `training.label_smoothing` |
| LR schedule | `training.lr_schedule` — `cosine` (with warmup), `exponential`, `plateau`, `none` |
| train/val split | `split.*`, applied once in section 02 and reused everywhere |
| seed | `seed` — seeds Python, NumPy, TensorFlow and augmentation |

Everything is written to Drive as training runs, so a disconnected Colab session loses nothing:

```text
checkpoints/<run_name>/
├── best_model.keras        best epoch by val_accuracy
├── last.weights.h5         weights at the final epoch
├── history.json            merged across resumed runs
├── config_snapshot.yaml    the exact config this run used
├── model_summary.txt
└── backup/                 resume state (optimizer + epoch)
logs/<run_name>/
├── tensorboard/
└── training_log.csv
```

### How long

A few hundred clips per class on a Colab T4 is minutes, not hours. On CPU expect roughly 10×
that — usable for a smoke test, painful for a real run.

---

## 4 · Resume training

**Re-run section `03 · Training` with the same `RUN_NAME`.** `BackupAndRestore` restores the optimizer
state and the epoch counter from `checkpoints/<run>/backup/`, so training continues from where it
stopped rather than restarting. `history.json` accumulates across runs, so the charts stay
continuous.

This covers the two cases that actually happen:

- **Colab disconnected mid-run** — reopen the notebook, run all, training picks up.
- **More epochs wanted** — raise `training.epochs` and run again.

It is also the trap to know about: **a resumed run applies your config change on top of the old
model**, and prints one history for both runs. If you changed a hyperparameter because the last run
went badly, resuming measures the old run again. The training cell prints `resuming a run`, and
`artifacts.summary()` flags it.

Related controls:

- **Start fresh instead**: set `FRESH_START = True` in the notebook — it calls `trainer.reset_run()`,
  which deletes the backup, checkpoint, history and CSV log for that run name. Or set a different
  `RUN_NAME`, which gets its own checkpoints, logs and history.
- **Never resume**: `training.resume: false`.
- **Change the data, not the run**: after adding recordings, re-run `02` (it only processes new
  files) and then resume. To retrain from scratch on the enlarged dataset, use a new `RUN_NAME` —
  resuming into a changed class list will not work, because the output layer would change shape.

---

## 5 · Export

Section `05 · Export` writes to `exports/` on Drive:

| file | purpose |
|---|---|
| `saved_model/` | TensorFlow SavedModel (the conversion source) |
| `dhikr_float32.tflite` | reference; matches Keras exactly |
| `dhikr_dynamic_range.tflite` | int8 weights, float activations; ~4× smaller |
| `dhikr_int8.tflite` | fully int8; smallest and fastest on a phone |
| `labels.txt` | class labels, one per line, in model output order |
| `labels_phrases.json` | class index → phrase id → Arabic text |
| `model_meta.json` | the export report: input shape, front-end, benchmarks, verification, metrics |
| `model_metadata.json` | **the Android contract**: quantisation, window/hop, thresholds, detector params, SHA256 |
| `mel_filterbank.json` | the exact mel matrix, for the Android front-end |
| `frontend_test.wav` + `.npy` | front-end parity fixture (written by stage 06) |
| `history/<datetime>_<phrases>_<accuracy>/` | a dated snapshot of every published export |

Every variant is benchmarked (size, mean/median/p95 latency, arena estimate) and **verified against
the Keras model** — on held-out clips, and separately on **hard negatives**, because quantisation
damage is not uniform: a model can agree on 99 % of clean clips and disagree exactly on the near-miss
audio that decides the false-activation rate. A variant that fails any subset is not recommended,
however small it is, and `bundle.rejected()` says why.

Stage 06 then re-verifies the recommended variant **on streaming windows** and rejects it for
production if quantisation adds false activations beyond
`readiness.max_tflite_extra_false_activations_per_hour`. A 4× size saving that doubles the false-count
rate is not a smaller model, it is a worse product.

### Mixed precision and the converter

Training on a GPU enables `mixed_float16`, so the checkpoint computes in float16 — and TFLite has
**no float16 builtin kernels** for `Conv2D`, `DepthwiseConv2dNative` or `Relu`. Converting such a
checkpoint directly fails for *every* variant, quantised or not:

```
Could not translate MLIR to FlatBuffer ...
'tf.Conv2D' op is neither a custom op nor a flex op
```

`export_all` handles this: it rebuilds the model under a float32 policy and copies the weights over
before converting. That is lossless — mixed precision keeps master weights in float32 the whole time
and only casts for compute, so the rebuild drops the casts, not precision. Calling `convert_tflite`
directly on a mixed-precision model still fails; run it through `to_float32_model` first.

Two honest caveats about the benchmark table:

- `expected_android_ms` is the measured latency times `export.android_latency_factor` (default 3).
  It is an estimate for ranking variants — measure on a real device before quoting a number.
- `arena_estimate_kb` sums the tensors the interpreter allocates. It is a close proxy for the
  runtime arena, not a reading from the allocator.

---

## 6 · Streaming evaluation and calibration

Stage `06 · Streaming` is where the model stops being a classifier and starts being a counter. It
slides the training window over continuous audio, turns the per-window probabilities into **events**
with a state machine, and measures the things clip accuracy cannot see.

### The event detector

```
IDLE ──(conf ≥ activation)──▶ CANDIDATE ──(min_consecutive_hits)──▶ CONFIRMED  → count once
  ▲                               │                                     │
  │                          (released)                         (released, then)
  └───────────────────────────────┴──────────────────────────── COOLDOWN (per class)
```

Every rule exists to stop one way of counting wrong:

| rule | config key | stops |
|---|---|---|
| activation threshold | `streaming.detector.confidence_threshold` | firing on anything vaguely similar |
| consecutive hits | `streaming.detector.min_consecutive_hits` | a single glitch window becoming a count |
| release threshold *below* activation | `streaming.detector.release_threshold` | a confidence wobble splitting one utterance into two counts |
| release windows | `streaming.detector.release_windows` | closing an event during a natural pause mid-phrase |
| cooldown, **per class** | `streaming.detector.cooldown_ms` | the tail of an utterance starting another — while two *different* phrases said back to back still count as two |
| ignore `unknown` | `streaming.detector.ignore_labels` | counting the model's way of saying "not a dhikr" |

A plain `if probability > threshold: count += 1` fails all six. So does a plain refractory timer, as
soon as a phrase outlasts it — which is exactly the case for the longer dhikr.

### The numbers

| metric | meaning |
|---|---|
| event recall | dhikr counted ÷ dhikr spoken |
| event precision | correct counts ÷ counts that fired at something real |
| duplicates | one utterance counted twice — reported separately, because the fix is different |
| **false activations / hour** | **counts that fired at audio nobody spoke a dhikr into** |
| FA/hour on negative-only audio | the honest estimate: a phone left listening in a room |
| FA/hour by negative type | which *kind* of audio breaks it |

**False activations per hour is the release-critical number.** A missed dhikr is an annoyance the
user sees and repeats; a counter that ticks during a conversation is a broken feature. Everything
else is negotiable against it.

### Threshold calibration

The threshold is not chosen to maximise accuracy or F1 — F1 weighs a missed dhikr and a false count
equally, and here they are not equal. The default policy
(`streaming.calibration.policy: min_threshold_within_budget`) takes
`streaming.target_false_activations_per_hour` as a **hard constraint** and picks the lowest threshold
that stays inside it, maximising recall.

Two honest failure modes, both reported rather than hidden:

* **No threshold meets the budget.** The report says so instead of settling on 0.95. Raising the
  threshold further is not the fix — the model is firing *confidently* on non-dhikr audio, and the
  answer is hard negatives and more speakers.
* **A threshold meets the budget by hardly firing.** Inside the budget but with almost no recall is a
  pass on paper and a broken feature in the app; the report flags it.

`streaming.calibration.per_class` also calibrates one threshold per phrase, each with its own share
of the FA budget. Phrases are not equally difficult — a nested phrase needs a stricter threshold than
a distinct one, and forcing them to share costs recall on the easy one to protect the hard one.

### Production readiness

The stage ends with a verdict assembled from `readiness.*` — every check prints the measurement *and*
the limit it was compared against:

| status | meaning |
|---|---|
| `NOT READY` | something measured failed. Each check says what to do about it. |
| `EXPERIMENTAL` | nothing measured failed, but something that decides the answer was **not measured** — too few speakers, no streaming recordings, no hard negatives. |
| `READY FOR DEVICE TEST` | every check passed on enough data to mean something. The next step is a real phone in a real room, not a release. |

An unmeasured check never counts as a pass. A model with no streaming evaluation has not been shown
to count correctly; it has only failed to be shown wrong.

---

## 7 · Test the export

Section 04 reports how the model scores on held-out clips. It cannot tell you how the exported
flatbuffer behaves on audio someone just spoke, or whether it can **count** dhikr in a continuous
recording — which is what the app actually needs. `space/` is a Gradio app for exactly that:

```bash
cd space
pip install -r requirements.txt
python app.py                                       # http://127.0.0.1:7860
```

It pulls the export from the shared folder named in `space/model_source.txt` on startup — a Google
Drive folder, a `hf://user/repo`, a direct URL or a local path — so there is nothing to copy.
`DHIKR_MODEL_SOURCE` overrides it, `DHIKR_MODEL_DIR` points at a local exports folder instead, and
the *Load a model* tab takes a link or the files directly.

Four tabs: classify one clip (with the log-mel the model saw), scan a long recording and count the
dhikr in it, read the export's metadata and benchmarks, and swap models without redeploying.
Several `.tflite` variants can be loaded side by side, so comparing `int8` against `float32` on the
same clip is one click.

It runs on **LiteRT**, not TensorFlow, so it installs in seconds. It also reads the front-end from
`model_meta.json` rather than `configs/config.yaml` — the exported metadata is what the weights were
trained with, and a config retuned since the export would otherwise feed the model features it has
never seen. When the two disagree about the input shape, the app says so on screen.

It also warns when a model has **no `unknown` class**. That is worth watching for: softmax sums to 1,
so a model trained only on phrases has nowhere to put silence or background noise and hands it to a
phrase instead, confidently. Accuracy on held-out *phrase* clips stays high while the counter fires
on nothing at all — which is exactly the failure the evaluation notebook cannot see.

To publish it as a Hugging Face Space, `space/deploy.sh` stages `src/`, `configs/config.yaml` and
`phrases.json` alongside the app and pushes — the Space is a separate git repo and cannot import
from a parent folder, but the pipeline code stays single-sourced here. See `space/README.md`.

---

## 8 · Integrate into Android

Copy into `app/src/main/assets/`:

```text
dhikr_int8.tflite       (or whichever variant stage 05 recommends)
labels.txt
model_metadata.json     the contract: thresholds, window/hop, quantisation, detector params
model_meta.json         the export report: benchmarks, verification, provenance
mel_filterbank.json     the exact mel matrix
frontend_test.wav       parity fixture — see "Front-end parity" below
frontend_expected.npy
frontend_metadata.json
```

**`model_metadata.json` is the one that matters at runtime.** The model file alone does not describe
the system: the window and hop, the calibrated activation and release thresholds, the consecutive
hits, the cooldown and the smoothing are what turn probabilities into a counter, and they were
*measured* in stage 06. Ship the `.tflite` without them and the app invents its own operating point,
at which every number this pipeline reported stops applying. It contains:

```
metadata_version, model_version
model.{file, sha256, architecture, input_shape, num_classes}
audio.{sample_rate, clip_samples, fit_mode, normalize, trim_on_stream_windows}
frontend.{n_mels, n_fft, win_length, hop_length, window, center, fmin, fmax, log_offset, normalize}
streaming.{window_seconds, window_samples, hop_seconds, hop_samples, smoothing}
detector.{confidence_threshold, release_threshold, per_class_thresholds, min_consecutive_hits,
          min_event_duration_seconds, release_windows, cooldown_ms, ignore_labels, calibrated}
classes[], labels[]
tensors.{input, output}.{shape, dtype, quantized, scale, zero_point}
```

`detector.calibrated` is `false` when the thresholds are still the configured defaults — the file
says so in its own `warnings` field rather than letting a default be read as a measurement.

### Front-end parity

The model consumes log-mel features, not audio, so Android has to reproduce the feature extraction
exactly. A window length off by one sample, a symmetric instead of periodic Hann window, or a
different log offset does not crash anything — it quietly moves every feature and costs accuracy
nobody can attribute.

Stage 06 exports a fixture so the port can be checked against a number instead of a description:

| file | what it is |
|---|---|
| `frontend_test.wav` | a fixed signal: three tones through the mel range, a noise burst, a silent stretch |
| `frontend_expected.npy` | the exact `(frames, n_mels)` float32 tensor this pipeline produces from it |
| `frontend_conditioned.npy` | the waveform after conditioning, so a mismatch localises to conditioning vs. spectrogram |
| `frontend_metadata.json` | every step in order with its parameters, plus a checksum and the tolerance |

Decode the WAV on device, run your front-end, compare. Differences of a few `1e-5` are float
reordering; `1e-2` is a bug.

Gradle:

```kotlin
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    // optional, for the NNAPI/GPU delegates:
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
```

```kotlin
android {
    androidResources {
        noCompress += "tflite"   // the model must stay uncompressed to be memory-mapped
    }
}
```

### The model takes features, not audio

The TFLite model input is a `(frames, n_mels, 1)` **log mel spectrogram**, not a waveform. The app
must reproduce the training front-end exactly — a mismatch here is the single most common reason a
model that scored 98 % in section 04 behaves randomly on a phone.

Every parameter is in `model_meta.json`; with the defaults in this repository:

| parameter | value |
|---|---|
| sample rate | 16000 Hz, mono, PCM16 |
| clip length | 2.0 s → 32000 samples |
| FFT size | 512 |
| window | 480 samples (30 ms), Hann **periodic**, zero-padded centred into 512 |
| hop | 160 samples (10 ms) |
| centring | none — frame `t` starts at `t * hop` |
| mel bins | 40, Slaney scale and Slaney normalisation, 20 Hz – 7600 Hz |
| log | `ln(mel + 1e-6)` |
| normalisation | per clip: subtract the mean, divide by the standard deviation |
| model input | `(197, 40, 1)` float32 |

The mel filterbank is not re-derived on device — load `mel_filterbank.json`, which holds the exact
`[40][257]` matrix the model was trained with.

### Front-end in Kotlin

```kotlin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reproduces src/features.py::LogMelExtractor. Parameters must match model_meta.json.
 * melFilterbank is the "filters" array from mel_filterbank.json: [nMels][nFft / 2 + 1].
 */
class LogMelFrontend(
    private val melFilterbank: Array<FloatArray>,
    private val nFft: Int = 512,
    private val winLength: Int = 480,
    private val hopLength: Int = 160,
    private val nMels: Int = 40,
    private val logOffset: Float = 1e-6f,
) {
    // Hann (periodic), zero-padded and centred in the FFT frame - what librosa does
    // when winLength < nFft.
    private val window = FloatArray(nFft).also { w ->
        val pad = (nFft - winLength) / 2
        for (i in 0 until winLength) {
            w[pad + i] = (0.5 - 0.5 * cos(2.0 * PI * i / winLength)).toFloat()
        }
    }

    fun frameCount(numSamples: Int): Int =
        if (numSamples < nFft) 0 else 1 + (numSamples - nFft) / hopLength

    /** samples: mono float32 in [-1, 1], already trimmed/normalised to clipSamples. */
    fun extract(samples: FloatArray): Array<FloatArray> {
        val frames = frameCount(samples.size)
        val output = Array(frames) { FloatArray(nMels) }
        val real = FloatArray(nFft)
        val imaginary = FloatArray(nFft)
        val bins = nFft / 2 + 1
        val power = FloatArray(bins)

        for (t in 0 until frames) {
            val offset = t * hopLength
            for (i in 0 until nFft) {
                real[i] = samples[offset + i] * window[i]
                imaginary[i] = 0f
            }
            fft(real, imaginary)
            for (k in 0 until bins) {
                power[k] = real[k] * real[k] + imaginary[k] * imaginary[k]
            }
            for (m in 0 until nMels) {
                val filter = melFilterbank[m]
                var sum = 0f
                for (k in 0 until bins) sum += filter[k] * power[k]
                output[t][m] = ln(sum + logOffset)
            }
        }
        return normalizePerExample(output)
    }

    /** features.normalize = "per_example" in config.yaml. */
    private fun normalizePerExample(features: Array<FloatArray>): Array<FloatArray> {
        var sum = 0.0
        var count = 0
        for (row in features) for (value in row) { sum += value; count++ }
        if (count == 0) return features
        val mean = sum / count
        var variance = 0.0
        for (row in features) for (value in row) {
            val delta = value - mean
            variance += delta * delta
        }
        val std = sqrt(variance / count).toFloat()
        val scale = 1f / (std + 1e-8f)
        for (row in features) {
            for (i in row.indices) row[i] = (row[i] - mean.toFloat()) * scale
        }
        return features
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT; nFft must be a power of two. */
    private fun fft(real: FloatArray, imaginary: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle).toFloat()
            val stepImaginary = kotlin.math.sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wReal = 1f
                var wImaginary = 0f
                for (k in 0 until length / 2) {
                    val evenReal = real[i + k]
                    val evenImaginary = imaginary[i + k]
                    val oddReal = real[i + k + length / 2]
                    val oddImaginary = imaginary[i + k + length / 2]
                    val productReal = oddReal * wReal - oddImaginary * wImaginary
                    val productImaginary = oddReal * wImaginary + oddImaginary * wReal
                    real[i + k] = evenReal + productReal
                    imaginary[i + k] = evenImaginary + productImaginary
                    real[i + k + length / 2] = evenReal - productReal
                    imaginary[i + k + length / 2] = evenImaginary - productImaginary
                    val nextReal = wReal * stepReal - wImaginary * stepImaginary
                    wImaginary = wReal * stepImaginary + wImaginary * stepReal
                    wReal = nextReal
                }
                i += length
            }
            length = length shl 1
        }
    }
}
```

### Running the model

```kotlin
val interpreter = Interpreter(loadModelFile(context, "dhikr_int8.tflite"))
val labels = context.assets.open("labels.txt").bufferedReader().readLines()

// features: (197, 40) from LogMelFrontend -> (1, 197, 40, 1)
val input = Array(1) { Array(197) { t -> Array(40) { m -> floatArrayOf(features[t][m]) } } }
val output = Array(1) { FloatArray(labels.size) }
interpreter.run(input, output)

val probabilities = output[0]
val best = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
```

For the **int8** model, quantise the input and dequantise the output with the scale and zero point
from `interpreter.getInputTensor(0).quantizationParams()`:

```kotlin
val params = interpreter.getInputTensor(0).quantizationParams()
val quantized = ((value / params.scale) + params.zeroPoint).toInt().coerceIn(-128, 127).toByte()
```

### Counting reliably

Implement the state machine from [stage 06](#6--streaming-evaluation-and-calibration), with the
parameters from `model_metadata.json`. In outline, per window (every `hop_samples`, over the last
`window_samples`):

1. **Smooth** the probabilities if `streaming.smoothing.enabled` — causal, over the last
   `window` windows, then renormalise so a probability stays a probability.
2. **Never count `ignore_labels`.** `unknown` is a real output and is the model saying "not a dhikr".
3. **Start a candidate** when a countable class reaches its threshold
   (`per_class_thresholds[label]`, else `confidence_threshold`) and that class is not in cooldown.
4. **Confirm** after `min_consecutive_hits` windows — that is when the counter ticks, once.
5. **Keep the event open** while the class stays above `release_threshold`; close it after
   `release_windows` below it. This is the hysteresis, and it is what stops one utterance becoming
   two counts.
6. **Cool down** `cooldown_ms` for that class only, so a different phrase said immediately after
   still counts.

A plain `if probability > threshold: count++` produces several counts per dhikr; a plain refractory
timer double-counts any phrase that outlasts it. The thresholds in the metadata were calibrated
against a false-activation budget — changing them on device without re-running stage 06 discards
that measurement.

---

## Configuration

`configs/config.yaml` is the only place to change behaviour. Every key is commented in the file.
The ones worth knowing:

| key | what it does |
|---|---|
| `paths.drive_root` / `paths.project_dir` | where the dataset lives |
| `audio.clip_seconds` | model input length; changing it changes the input shape |
| `audio.trim` / `audio.normalize` | silence trimming and loudness normalisation |
| `features.n_mels`, `window_ms`, `hop_ms` | the front-end — must match the Android side |
| `augmentation.*` | per-transform probability and range (including optional `reverb`) |
| `split.val_ratio` / `test_ratio` | split sizes |
| `split.speaker.*` | where speaker ids come from, and whether leakage fails the run |
| `split.group_regex` | legacy: a bare filename regex, still honoured |
| `quality.*` | dataset-size recommendations the report measures against |
| `model.*` | DS-CNN width, depth, dropout |
| `model.bn_momentum` | BatchNorm moving-average momentum — see below |
| `classes.*` | which phrase ids the model learns — see [below](#training-on-a-subset-of-phrases) |
| `classes.negative_types` | the negative categories expected under `unknown/` |
| `training.*` | epochs, batch size, optimizer, schedule, early stopping |
| `training.macro_f1` | also track macro F1, which drops when a class is being ignored |
| `evaluation.confidence_threshold` | the clip-level reject gate |
| `streaming.hop_seconds` | how often the app runs inference |
| `streaming.detector.*` | the event state machine — the on-device counting rules |
| `streaming.target_false_activations_per_hour` | **the budget calibration must respect** |
| `streaming.calibration.*` | sweep range and the policy that picks the operating point |
| `readiness.*` | what "ready" means, check by check |
| `export.*` | which variants to build, calibration size, benchmark runs |

### Presets

`configs/presets/` holds documented overlays for three situations — `tiny_dataset`, `standard`,
`large_dataset` — each a small file of explicit values with the reasoning in its header:

```python
config = load_config(preset="tiny_dataset")     # or DHIKR_PRESET=tiny_dataset
```

A preset is opt-in, every key it changes is logged, and it resolves into a plain config: what the
modules read and what `Trainer` saves next to the checkpoint. `config.yaml` stays the place settings
live — no preset indirection survives into the run, and `tiny_dataset` does **not** relax the
readiness thresholds. A prototype dataset should read `EXPERIMENTAL`, and it does.

Config is validated on load: an unknown or misspelled key raises immediately rather than being
silently ignored.

After changing anything under `audio.*` or `features.*`, re-run section 02 with `OVERWRITE = True`
and train a fresh run — existing processed clips and checkpoints were built with the old settings.

### A note on `model.bn_momentum`

Keras defaults BatchNorm momentum to `0.99`, which needs thousands of updates before the moving
statistics converge. A few hundred clips give only a handful of steps per epoch, so the statistics
stay near their initial values while the model trains against batch statistics. The symptom is
distinctive and easy to misread as overfitting: **training accuracy climbs to 95 %+ while validation
accuracy sits exactly at chance**, because in inference mode the model collapses to a single class.

The default here is `0.9`, which converges in about 50 steps. Raise it toward `0.99` only once the
dataset is large enough to give hundreds of steps per epoch.

---

## One model per dhikr?

A recurring proposal: instead of one model that classifies every phrase, train **one binary detector
per dhikr**, each specialised in its own phrase. Stage `07 · Experiment` of the notebook answers it
for your dataset instead of arguing about it — it trains one one-vs-rest model per phrase and scores
them against the multi-class model on the same clips.

It is optional and it is not free: one training run per phrase, on top of the one you already have.
Nothing it does touches the exported model.

**What is held fixed**, so the result is about the approach rather than the setup: the same manifest
and splits, the same architecture, the same augmentation, the same optimiser, the same seed and the
same epoch budget. The evaluation dataset is built once and every model runs over those same tensors,
so the comparison is per-clip.

**Three questions**, because they can disagree:

| question | metric | why it matters |
|---|---|---|
| Can it detect *this* phrase? | ROC-AUC / average precision per phrase | Threshold-free — neither side wins on tuning |
| Can it name the *right* phrase? | accuracy on phrase clips, both restricted to the phrase columns | Where the nested phrases decide it |
| Can it stay quiet on non-dhikr? | accept rate on `unknown` clips, at one shared threshold | A committee of binaries has no `unknown` output, only a threshold |

The committee of binaries predicts by `argmax` over the per-model positive scores — the deployment
shape the proposal implies — and the multi-class model is scored by `argmax` over the same phrases.

**What to expect, and why.** The prior is that one-vs-rest loses, for reasons that are structural
rather than tuning:

- **The phrases are nested prefixes.** `سبحان الله` ⊂ `سبحان الله وبحمده` ⊂
  `سبحان الله العظيم وبحمده`, and `اللهم صل على محمد` ⊂ `اللهم صل وسلم على نبينا محمد`. A softmax
  learns the boundary between them because they compete for the same probability mass; the other
  phrases are free hard negatives. A binary detector is never shown that distinction as a label.
- **It discards a true constraint.** Exactly one phrase was spoken. Softmax encodes that; N
  independent binaries do not.
- **N backbones is the wrong direction on a small dataset.** Only the final `Dense` layer shrinks —
  the DS-CNN backbone is ~all the parameters and ~all the MACs. Ten binary models is ten times the
  capacity, the size and (if run together) the inference cost, on the same clips.
- **Each model faces a 1:N imbalance.** `train_one_vs_rest` applies class weights for exactly this
  reason, so the experiment does not understate one-vs-rest for the wrong cause.

The one thing it genuinely buys is operational: adding a phrase means training one new model instead
of invalidating every checkpoint. Cheap to have, expensive to pay for in accuracy.

**A tie is the expected outcome, and the report says so** rather than picking a winner from noise. It
reports Wilson intervals next to every accuracy and refuses to call a difference smaller than 0.02
AUC a result — on a split of a few dozen clips that is one or two recordings landing differently.

If what you actually want is *per-dhikr decisions* rather than N models, two cheaper routes reach it
without giving up the shared backbone: mask the softmax to the expected phrase plus `unknown` at
inference time (the app knows which dhikr a screen is counting), and give each phrase its own
threshold instead of one global number.

---

## Data collection guide

Data beats everything else in this pipeline, and not all data is equal. In priority order:

1. **More speakers** — the single highest-value thing to collect. Ten people saying a phrase twice
   teaches more than one person saying it twenty times, because the model has to learn the phrase
   rather than the voice.
2. **More target recordings** — 200–500 per phrase for a first shippable model.
3. **More realistic `unknown` speech** — ordinary Arabic conversation, not just noise.
4. **Hard negatives** — the near misses (see [above](#negatives-and-why-hard_negative-is-the-important-folder)).
5. **Real background noise** — rooms, traffic, TV, people.
6. **Streaming test recordings**, especially negative-only ones.

### Per target phrase, try to cover

| dimension | what to vary |
|---|---|
| speakers | as many as possible; give each a stable id |
| gender | male and female voices where available |
| age | different ages where available — children and older speakers sound very different |
| distance | 10 cm, arm's length, across the room, phone on a table |
| environment | quiet room, room with a fan, kitchen, outdoors, car |
| pace | normal, fast, slow, trailing off |
| phones | different microphones where available — they colour the spectrum differently |

Ten recordings from ten people across these conditions is worth far more than a hundred from one
person in one room, and it is what the difference between a demo and a product is made of.

### Collecting hard negatives

Give volunteers the near misses explicitly, as their own prompts:

* the target phrase's **prefixes** — `سبحان الله` and `سبحان الله العظيم` for
  `سبحان الله العظيم وبحمده`;
* the **other target phrase**, which is a hard negative for its neighbour;
* the phrase **trailing off** part-way, the way someone distracted actually says it;
* the phrase's **tail without its opening**;
* other dhikr that are not targets at all.

Record these the same way as the targets — same phones, same rooms, same speakers — and put them in
`dataset/unknown/hard_negative/`. Do **not** manufacture them by cutting target recordings: a cut
clip carries the prosody of a completed phrase, so the model learns to reject an edit artefact rather
than a half-said dhikr. Cropping is fine as augmentation or as an extra test, not as the source.

### A note on quantity vs. model size

Do not try to compensate for insufficient data with a bigger model. Stage 03 prints parameters per
training clip and recommends a `width_multiplier` when the network can simply memorise the dataset —
but that is damage control. The fix for a model that does not generalise is almost always more
speakers, in that order:

> speakers → target recordings → realistic unknown speech → hard negatives → real noise →
> speaker-safe split → streaming evaluation → threshold tuning → augmentation tuning →
> model architecture

---

## Growing the dataset

The pipeline is built to be re-run as recordings accumulate:

1. Drop new files into `dataset/<class>/`.
2. Run `01` to validate them (it flags duplicates against the whole dataset).
3. Run `02` — already-processed clips are skipped, so this costs only the new files.
4. Run `03` with a **new** `RUN_NAME`, then `04` and `05`.

**Adding a new phrase** means a new class: create the folder, add it to `phrases.json`, and train a
new run. The output layer changes shape, so an existing run cannot be resumed into it.

Re-splitting note: `assign_splits` re-randomises from the seed each time section 02 runs, so a clip
can move between train and test as the dataset grows. That is fine for tracking progress over time,
but do not compare two runs' test accuracy to the third decimal unless the manifest was unchanged.

---

## Troubleshooting

| symptom | cause and fix |
|---|---|
| `dataset directory not found` | `paths.drive_root` / `project_dir` do not match Drive. Section 01 prints the resolved paths. |
| `manifest not found` | Run section `02 · Preprocessing` first. |
| Accuracy pinned at exactly `1 / classes` and every clip predicted as the same class | The model collapsed — see [below](#a-run-stuck-at-chance). |
| Training accuracy high, validation stuck at chance | BatchNorm momentum — see [above](#a-note-on-modelbn_momentum). |
| Validation accuracy far below training | Genuine overfitting — see [below](#a-run-that-overfits). |
| Validation peaks in the first few epochs and never improves | Same thing: the dataset had nothing left to teach after those epochs. See [below](#a-run-that-overfits). |
| One class always wrong | Check its clips in section 01 — usually mislabelled or near-silent takes. Section 04 lets you listen to the errors. |
| Colab disconnects | Re-run `03` with the same `RUN_NAME`; it resumes. |
| `'tf.Conv2D' op is neither a custom op nor a flex op` | A mixed-precision (float16) checkpoint reached the converter. `export_all` rebuilds it in float32 automatically; if you call `convert_tflite` yourself, pass the model through `to_float32_model` first. |
| `int8` conversion fails | Calibration data is empty or all one class. Ensure the `train` split is non-empty and `export.representative_samples` ≥ 100. |
| Quantised model disagrees with Keras | Section 05 flags this. Increase `export.representative_samples`, or ship `dynamic_range` instead. |
| Model works in Colab, fails on the phone | The Android front-end does not match. Compare against `model_meta.json` — sample rate, window, hop, centring and normalisation must all match. |
| `OOM` during training | Lower `training.batch_size`, or `training.cache: false` for a dataset too large to hold in RAM. |
| Arabic text renders as boxes in charts | Expected: matplotlib does not shape Arabic. Charts use class ids; the id → phrase table is printed in section 01. |

### Training on a subset of phrases

Ten classes need a lot of recordings. `classes.include_phrases` narrows the vocabulary, which is the
cheapest way to get a working model out of a small dataset — the same clips give more per class, and
chance accuracy rises from 1/10 to 1/4, so validation numbers start meaning something much sooner.

```yaml
classes:
  include_phrases: [1, 2, 3, 4]   # null or [] trains on every folder found
  include_unknown: true           # keep the `unknown` filler folder if it exists
```

This is the shipped default: the four short, distinct phrases (سبحان الله / الحمد لله / الله أكبر /
لا إله إلا الله). Add ids back as the dataset grows.

The filter is applied where the dataset is indexed, so it decides the class vocabulary, the class
indices frozen into the manifest, the width of the model's output and `labels.txt` — nothing
downstream needs to know about it. Class indices stay contiguous from 0 whatever you select, and
section 01's phrase table gains a `trained` column showing what is in and what is out.

Two things to do after changing it:

1. **Re-run section `02 · Preprocessing`** — the manifest still carries the old classes otherwise. Already
   conditioned clips are skipped, so it is quick.
2. **Train under a fresh run** — `FRESH_START = True`, or a new `RUN_NAME`. An old checkpoint has
   the wrong number of outputs; `Trainer` compares the run's config snapshot and refuses to restore
   an incompatible backup rather than failing later with a shape error.

### A run stuck at chance

A summary like this is not a weak model, it is a dead one:

```
run              : ds_cnn
epochs completed : 39
best val_accuracy: 0.1000 (epoch 1)
```

`0.1000` with 10 classes is exactly what a constant prediction scores on a balanced split — the
output does not depend on the input. Section 03 now says so out loud (`prediction_distribution()`
and the `!!` notes in `artifacts.summary()`). Work through it in this order:

1. **Run the sanity check** (section 03, section 6b). It asks a fresh copy of the model to memorise
   ~40 unaugmented clips in 200 steps. It has to reach ~1.0; the report tells you what a failure
   means. Everything below only matters once that passes.
2. **Count the optimiser steps**, printed by the training cell as `total steps`. It is
   `ceil(train_clips / batch_size) × epochs`, and it is what convergence actually depends on — a
   DS-CNN from scratch needs thousands, and the cell warns below 2000. 80 clips at batch 64 is
   `2/2` steps per epoch, so a 60-epoch run is **120 gradient steps in total**; the model is still
   at its initialisation, which *is* the constant prediction you are seeing. Watch the loss rather
   than the accuracy to tell an undertrained run from a broken one: falling train loss with
   accuracy near chance is a run that needs more steps, while a flat loss at `ln(num_classes)`
   (2.30 for ten classes) is a run that is not learning at all. Lower `training.batch_size`, raise
   `training.epochs`.
3. **Check you are not resuming.** With `training.resume: true`, re-running the notebook restores
   the previous run's weights *and* optimiser state, and splices both runs' histories together — so
   a config change you made in between never took effect, and "epochs completed" counts epochs from
   a run you already abandoned. Set `FRESH_START = True` (or a new `RUN_NAME`) after any config
   change. The summary flags a resumed run, and `fit` compares the config against the snapshot
   stored with the backup — it raises when `classes.include_phrases` changed (the output width
   moved, so the restore cannot work) and logs a `WARNING` listing every other setting that drifted,
   because those restore *successfully* and quietly train something the config no longer describes.
4. **Then look at the data**, which is usually the real answer. Section 03 prints clips per class
   for every split. If section 04 reports `samples : 10` for ten classes, the validation split is
   one clip per class: accuracy can only be 0.0, 0.1, 0.2 … and its 95% interval runs from 0.02 to
   0.40. That split cannot distinguish a working model from a broken one, and a dataset that thin
   (≤ 13 clips per class, given how `assign_splits` floors the ratios) cannot train a 10-class
   classifier from scratch at all. Aim for 50–100+ recordings per class from 10+ speakers before
   the numbers start meaning anything.

### A run that overfits

The other failure, and the one a small dataset produces by default. Training accuracy reaches 1.0,
validation stops well short of it and never catches up:

```
run              : ds_cnn
epochs completed : 50
best accuracy    : 1.0000 (epoch 32, training split)
best val_accuracy: 0.8000 (epoch 10) - chance is 0.3333
restored weights : epoch 10 - train 0.8700 / val 0.8000 (this is the checkpoint)
val split        : 20 clips (one clip = 5.0 accuracy points)
```

Read those lines carefully, because the first two describe **two different models**. `best accuracy`
is whatever the run drifted to by epoch 32; the checkpoint on disk is epoch 10, restored by early
stopping. `restored weights` is the pair that actually shipped — compare *those* two numbers when
you want the gap of one model. Section 03 prints all of it and appends `!!` notes explaining what it
sees.

Nothing is broken here. The model learned the training recordings, which is what a network with more
parameters than recordings does. Work through it in this order — the first item is almost always the
answer, and the rest buy a point or two while hiding the real limit:

1. **More recordings, and more speakers.** Variety matters more than count: the same voice recorded
   twice is close to one recording, so 200 clips from 3 people generalise worse than 100 from 20.
   The [Voice dhikr screen](../CLAUDE.md) in the app recruits volunteers for exactly this.
2. **Speaker leakage is already handled — check that it applied.** `assign_splits` groups by
   speaker, and stage 02 fails outright if one turns up in two splits, so a leak cannot pass
   silently any more. What can still happen is having *no* speaker to group on: files uploaded by
   `SpeechCollector` are named `<class>_sp<8 hex>_<timestamp>_<suffix>`, where the `sp…` token is a
   random id the volunteer's browser mints once and reuses, and that token is matched out of the box
   (`split.speaker.filename_patterns`). Older uploads that predate it carry no token and become one
   group each — which is honest but not protective, so the stage 01 speaker report prints how many
   recordings have an id. If most of them do not, the validation numbers are still **optimistic**.
   If you record a batch yourself, name the files `<speaker>_<n>.wav` or list them in `speakers.csv`.
3. **Less capacity.** `model.width_multiplier: 0.5` quarters the parameter count; `model.dropout`
   already defaults to `0.3`. Raise the multiplier back toward `1.0` as the dataset grows.
4. **Stronger augmentation.** Widen the ranges under `augmentation.*`, and drop real room recordings
   into `noise/` — that is the cheapest accuracy win for a phone-deployed model.

Note also how coarse the measurement is. On a 20-clip validation split one recording is worth 5
accuracy points, so 0.80 and 0.85 are the same result. The summary prints the resolution and says so
when the split is too small to measure a gap at all.

Both `epochs` and `early_stopping.patience` are irrelevant to this failure. Validation peaked at
epoch 10 of 50; the other 40 epochs only fitted the training split harder, and a longer patience
would have added more of them.

---

## What the model is

**DS-CNN** — a strided convolutional stem followed by depthwise separable blocks, global average
pooling and a softmax. It is the reference topology from *Hello Edge: Keyword Spotting on
Microcontrollers*: small enough for a phone, and it maps entirely onto TFLite builtin ops, so INT8
quantisation needs no Flex delegate.

With the defaults here (`197 × 40` input, 4 blocks, 64 filters) it is roughly 24k parameters and
about 40 KB as an INT8 flatbuffer. Add classes, widen with `model.width_multiplier`, or deepen with
`model.blocks` as the dataset grows.

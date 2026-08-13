# DhikrSpeech

Training pipeline for an **offline Arabic dhikr phrase spotter**: recordings on Google Drive in,
one quantised TensorFlow Lite model per dhikr out, running on Android with no network access.

**The production architecture is not a classifier.** The user picks one dhikr before a listening
session, and the app loads that dhikr's own model. Its only job is:

| | |
|---|---|
| **TARGET** | the selected phrase, spoken completely |
| **UNKNOWN** | everything else — other dhikr, *incomplete versions of this phrase*, ordinary speech, Quran, TV, noise, silence |

So training is **single-target binary phrase spotting**, one independent model per phrase:

```text
exports/006/dhikr_006_int8.tflite     ← loaded when the user picks 006
exports/007/dhikr_007_int8.tflite     ← loaded when the user picks 007
```

and the thing being optimised is not accuracy. Counting a dhikr nobody said is far more damaging
than missing one, so the release metrics are **false activations per hour**, event precision,
event recall, duplicate rate and hard-negative rejection — measured on continuous audio, not on
isolated clips.

```
recordings on Drive
   └─ 01 · Dataset       positives, negatives by category, speakers, window length
   └─ 02 · Preprocessing condition to 16 kHz mono, split BY SPEAKER, freeze the manifest
   └─ 03 · Training      one detector (DS-CNN / DS-CNN Tiny / TC-ResNet8)
   └─ 04 · Evaluation    clip metrics per negative category
   └─ 05 · Streaming     events, FA/hour, threshold calibration   ← decides shipping
   └─ 06 · Export        TFLite + INT8 verification + Android metadata contract
                            └─ app/src/main/assets/dhikr/<target>/
```

One Colab notebook, one config file, one reusable Python package, plus a CLI for batch runs.

---

## Contents

> **New to machine learning or to audio?** Read
> [`docs/LEARNING_GUIDE.md`](docs/LEARNING_GUIDE.md) first. It explains *why* every part of this
> pipeline is the way it is — sampling, spectrograms, convolutions, quantisation, the counting
> logic — assuming no ML background. This README is the operational how-to; that one is the
> background.
>
> **Collecting recordings?** [`docs/DATA_COLLECTION.md`](docs/DATA_COLLECTION.md) is the guide:
> how many, from whom, and which negatives are worth ten times their number in positives.

- [Layout](#layout)
- [Quick start](#quick-start)
- [1 · Upload the dataset](#1--upload-the-dataset)
- [2 · Mount Drive and open a notebook](#2--mount-drive-and-open-a-notebook)
- [3 · Train](#3--train)
- [4 · Resume training](#4--resume-training)
- [5 · Streaming evaluation](#5--streaming-evaluation)
- [6 · Export](#6--export)
- [7 · Test the export](#7--test-the-export)
- [8 · Integrate into Android](#8--integrate-into-android)
- [Configuration](#configuration)
- [Why one model per dhikr](#why-one-model-per-dhikr)
- [Growing the dataset](#growing-the-dataset)
- [Troubleshooting](#troubleshooting)

---

## Layout

```text
DhikrSpeech/
├── notebooks/
│   └── DhikrSpeech.ipynb         batch build + detailed one-target drill-down
├── train.py                      CLI: train/export every target or an explicit subset
├── src/
│   ├── config.py                 typed config loaded from configs/config.yaml
│   ├── audio.py                  decode, trim, normalise, fit length, write WAV
│   ├── dataset.py                scan, validate, preprocess, manifest, tf.data pipeline
│   ├── targets.py                TARGET vs UNKNOWN mapping, negative categories, sampling
│   ├── speakers.py               speaker ids (csv / subfolder / filename), leak checks
│   ├── speaker_backfill.py       give pre-token recordings a speaker id
│   ├── quality.py                dataset quality report across all classes
│   ├── augmentation.py           noise, pitch, speed, gain, time shift, SpecAugment
│   ├── features.py               log mel front-end (+ its Android metadata)
│   ├── models.py                 DS-CNN, DS-CNN Tiny, TC-ResNet8
│   ├── trainer.py                seeds, schedules, callbacks, resume
│   ├── metrics.py                clip metrics, incl. detector metrics per negative type
│   ├── streaming.py              sliding-window inference + the event state machine
│   ├── streaming_eval.py         event metrics, FA/hour, stress tests, calibration
│   ├── parity.py                 front-end parity assets for the Android port
│   ├── target_export.py          Android metadata contract, INT8 verification
│   ├── readiness.py              NOT READY / EXPERIMENTAL / READY FOR DEVICE TEST
│   ├── pipeline.py               the stages, composed
│   ├── experiments.py            the historical one-vs-rest comparison (section 08)
│   ├── visualization.py          every chart
│   └── export.py                 SavedModel, TFLite, benchmark, verification
├── tests/                        pytest — no TensorFlow, no Drive needed
├── configs/config.yaml           the only place settings live
├── space/                        Gradio app for testing an export (Hugging Face Space)
├── docs/
│   ├── LEARNING_GUIDE.md         the concepts, from zero ML background
│   └── DATA_COLLECTION.md        what to record, and how much of it
├── requirements.txt
└── README.md
```

The notebook contains no thresholds, paths or hyperparameters of its own — it reads
`configs/config.yaml`. Change behaviour there, not in a cell.

---

## Quick start

1. Put your recordings on Drive (below).
2. Open `notebooks/DhikrSpeech.ipynb` in Colab → **Runtime → Change runtime type → GPU**.
3. Run **Build every configured phrase model end to end**. With the default
   `target.phrase_id: all`, it creates one independent export folder per collected phrase.
4. Use the detailed cells below it only when inspecting or debugging one target.
5. Copy the `exports/<target>/` folders into `app/src/main/assets/dhikr/<target>/`.

Or from a shell, for every target, one target, or a subset:

```bash
python train.py                               # target.phrase_id: all → every phrase
python train.py --all-targets                 # every phrase, explicitly
python train.py --target 007                  # dataset → train → evaluate → stream → export
python train.py --targets 001,002,006,007     # one independent model each, in sequence
python train.py --target 007 --stage dataset  # the dataset report only; no TensorFlow needed
python train.py --target 007 --arch tc_resnet8
```

`--stage dataset` is worth running first on a new target. It is fast, and it answers the
questions that decide whether training is worth starting at all: how many speakers, how many
hard negatives, and whether the split leaks.

---

## 1 · Upload the dataset

Create this structure in **My Drive**:

```text
MyDrive/Dhikr Speech Dataset/
├── dataset/
│   ├── 001/ 002/ ... 010/    one folder per dhikr; the target's folder is its positives,
│   │                         every other folder is a negative for that target
│   └── unknown/              negatives, shared by every target
│       ├── *.wav             flat filler, reported as `unknown`
│       ├── normal_speech/    ordinary Arabic conversation
│       ├── hard_negative/    near-misses - the ones that decide the model
│       ├── partial_phrase/   incomplete utterances
│       ├── other_dhikr/      recorded specifically as negatives
│       └── noise/            room, street, TV, kitchen
├── phrases.json
├── speakers.csv              optional: file,speaker - the most reliable speaker source
├── streaming/                long-form recordings + annotations.json  (section 05)
│   ├── audio/
│   └── annotations.json
└── noise/                    optional: real background recordings for augmentation
```

`checkpoints/`, `exports/`, `logs/`, `processed/` and `reports/` are created automatically.

**Folder names are phrase ids.** Folder `007` is phrase id 7, zero-padded to three digits.
`phrases.json` maps ids to text:

```json
[
  { "id": 6, "text": "سبحان الله وبحمده" },
  { "id": 7, "text": "سبحان الله العظيم وبحمده" }
]
```

### Negatives are not one thing

Every negative trains as `UNKNOWN`, but each keeps the category it came from, and that category
travels through the manifest into every report. This is what makes the difference between "the
model is 99% accurate" and "the model cannot tell a complete phrase from its first three words"
visible instead of averaged away.

| category | folder | what it is |
|---|---|---|
| `hard_negative` | `unknown/hard_negative/` | near-misses of a target phrase |
| `partial_phrase` | `unknown/partial_phrase/` | incomplete utterances |
| `other_dhikr` | the other `001..010` folders, or `unknown/other_dhikr/` | free, already collected |
| `normal_speech` | `unknown/normal_speech/` | ordinary Arabic |
| `noise` | `unknown/noise/` | street, room, kitchen, TV |
| `unknown` | `unknown/*.wav` | flat, uncategorised filler |

The category is the **first subfolder under `unknown/`**, derived once in
`src/dataset.py` and carried through the manifest. Everything under `unknown/` trains as one
`UNKNOWN` class — the subfolder never becomes an output — but evaluation reports the
false-positive rate per category, which is what says *what kind* of audio breaks the detector.

**Hard negatives are the ones that decide the model.** For target 007
(`سبحان الله العظيم وبحمده`), record real people saying:

```
سبحان الله
سبحان الله العظيم
سبحان الله وبحمده
الله العظيم وبحمده
```

plus mispronounced and trailed-off attempts. Without them the model learns to fire on the
opening words, which is the single most common cause of false counts — and because those clips
*are* dhikr, no amount of TV or room tone substitutes for them.

Untagged files under `unknown/hard_negative/` are shared across every target. Scope a collected
near-miss by ending its filename in `_hard_negative_<target_id>`: for example,
`unknown_spABC_01_000_hard_negative_006.wav` is used only while building target 006 and excluded
from every other target. This prevents a near-miss for 006 that is a complete 007 phrase from
teaching the 007 model to reject itself.

**Do not manufacture hard negatives by cropping positives.** A crop has the same voice, room,
microphone and level as the positive it came from, so the model can separate the two on cues
that will not exist on a phone. Real recordings of the shorter phrase are what is needed.

### The shared pool

General negatives — conversation, TV, Quran recitation, street, room tone — are the same for
every target, so they live under `dataset/unknown/` once. Conditioned clips are cached by
*source folder and audio geometry* (`processed/audio/16000hz_2s/unknown/noise/…`), so training a
second target reuses them instead of re-writing the whole pool.

### Speaker identity

The single most valuable piece of metadata. A model that heard the same voice in training and in
validation reports a number that says nothing about a stranger's phone.

Three sources, in order of preference (`split.speaker.source: auto` tries them in turn):

1. **`speakers.csv`** next to `phrases.json` — two columns, `file,speaker`. Nothing to infer.
2. **A per-speaker subfolder** — `dataset/006/ali/*.wav`.
3. **The filename** — matched against `split.speaker.filename_patterns`.

SpeechCollector names its uploads `<class>_sp<8 hex>_<timestamp>_<suffix>`, so the device token
is the first pattern tried. **That order matters more than it looks**: a pattern that simply took
the leading token would extract the *class id* from `006_sp8d358495_…`, making every recording of
a phrase one "speaker" — putting whole classes in one split, and looking entirely correct while
doing it. Recordings uploaded before the token shipped can be given one from the collector's
metadata sheet with `src/speaker_backfill.py`.

Splitting is by speaker and **globally** — a voice's target recordings and the negatives they
also recorded land in the same split, so a speaker cannot enter training through the negative
pool and be evaluated on through the positive one. The result is verified rather than assumed:
`split.speaker.require_disjoint` (default) makes a leak fail preprocessing.

When no source resolves, the pipeline prints **EVALUATION IS NOT SPEAKER-INDEPENDENT** and the
readiness verdict caps at `EXPERIMENTAL`.

### What makes a good recording

| | |
|---|---|
| length | 1–3 seconds, one phrase per file |
| format | WAV preferred; FLAC/OGG/MP3/M4A are decoded too |
| rate | anything — preprocessing resamples to 16 kHz mono |
| count | **100 minimum** per target, 200–500 for a real-world model |
| speakers | **10 minimum**, 20+ for a real-world model |
| variety | speakers, ages, speaking speed, distance, microphone, room, noise |

Speaker diversity beats clip count by a wide margin: 10 speakers × 20 clips teaches far more
than 1 speaker × 200. See [`docs/DATA_COLLECTION.md`](docs/DATA_COLLECTION.md).

Recordings can be collected with the `SpeechCollector/` web app in this repository, which writes
straight to Drive in this layout.

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

Run `01` and `02` first — training reads the target's manifest, which `02` writes.

Set **Runtime → Change runtime type → GPU**, or it falls back to CPU.

### Choosing the target

One id, one model. `TARGET_PHRASE_ID` in the notebook (or `--target` on the CLI) scopes the
manifest, the checkpoints and the export folder:

```
processed/manifests/target_007.csv
checkpoints/target_007_ds_cnn/
exports/007/
```

`config.for_target(id)` also applies `target.phrase_overrides[id]` — today that is
`clip_seconds`, because a two-word dhikr and a seven-word one do not belong in the same window.
The dataset report prints a recommendation from the measured utterance durations; applying it is
a human decision, because it invalidates every cached clip and checkpoint.

### The output head

`target.output_mode` picks between a 2-output softmax (`unknown`, `target`) and a 1-output
sigmoid `P(target)`. Sigmoid is the production default; legacy softmax remains supported. Either
way, downstream code reads one number — `src.streaming.target_score` — so nothing else changes.

### Architectures

| `model.name` | what it is |
|---|---|
| `ds_cnn` | the baseline: depthwise-separable CNN from *Hello Edge* |
| `ds_cnn_tiny` | the same topology, 3 blocks × 32 filters at half width |
| `tc_resnet8` | TC-ResNet8: mel bins as channels, convolution along time only |

`model_presets` in the config overrides only what makes each one that architecture, so a
comparison changes the architecture and nothing else. Section 07 of the notebook trains all
three on the same split and ranks them by *FA/hour first*, not accuracy.

BC-ResNet is deliberately not implemented — its broadcasting block needs care to convert
cleanly, and there is no measurement yet asking for it.

### Negative sampling

The negative pool is designed to grow without bound while the positives stay in the hundreds, so
training on all of it drowns the phrase:

```yaml
negative_sampling:
  ratio: 2.0                # negatives capped at 2 x positives
  weights:
    hard_negative: 4.0      # worth the most per clip
    partial_phrase: 3.0
    other_dhikr: 2.0
    general_speech: 1.0
    noise: 0.5
```

Sampling is without replacement and deterministic in the seed, and the number of dropped clips
is logged — a silent cut would read as full coverage. Only the **training** split is sampled;
sampling the evaluation splits would change what the numbers mean between runs.

### What the config turns on

| feature | config key |
|---|---|
| TensorBoard | `training.tensorboard.*` |
| mixed precision | `training.mixed_precision` (GPU only) |
| early stopping | `training.early_stopping.*` |
| checkpoints | `training.checkpoint.*` → `checkpoints/<run>/best_model.keras` |
| resume | `training.resume` |
| class weights | `training.class_weights` — matters here: negatives outnumber positives |
| LR schedule | `training.lr_schedule` |
| seed | `seed` |

Everything is written to Drive as training runs, so a disconnected Colab session loses nothing:

```text
checkpoints/<run_name>/
├── best_model.keras        best epoch by configured monitor (val_pr_auc by default)
├── last_model.keras        full model at the actual final epoch
├── last.weights.h5         weights at the actual final epoch
├── history.json            merged across resumed runs
├── config_snapshot.yaml    the exact config this run used
└── backup/                 resume state (optimizer + epoch)
logs/<run_name>/
├── tensorboard/
└── training_log.csv
```

### Reading the numbers

For a detector, **accuracy is not the headline**. With negatives at twice the positives a model
that never fires is already 67% accurate. The training log reports precision, recall, TARGET F1,
ROC AUC and PR AUC; checkpoints and early stopping use validation PR AUC by default.
`artifacts.summary()` computes "chance" as the majority class rather than `1/num_classes`, so a
model that has learned to always say no is visible instead of looking like 67% of a good one.

### How long

A few hundred clips on a Colab T4 is minutes, not hours. On CPU expect roughly 10× that.

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

## 5 · Streaming evaluation

**This is the stage that decides whether a model ships.** Everything before it scored clips;
production is an open microphone, and that changes the problem twice over.

*A phrase passes through many overlapping windows*, so `P(target) > threshold` counts one dhikr
four or five times. A plain refractory timer is no better: any timer long enough to swallow a
2-second phrase also swallows the next repetition of someone saying it quickly.

*The model spends almost all of its time listening to things that are not the target*, so the
number that matters is **false activations per hour**, and it cannot be measured on a balanced
test split.

### The event detector

Counting is a state machine with hysteresis, in `src/streaming.py`:

```
IDLE --score>=activation--> CANDIDATE --enough hits--> CONFIRMED
  ^                                                        |
  |                                          score<release for
  |                                          release_windows
  |                                                        v
  +---------------- cooldown elapsed ------------------ COOLDOWN
```

Re-arming is driven by the **release**, not by the cooldown: the detector becomes ready again as
soon as the confidence has genuinely fallen away, so four quick repetitions produce four events.
`cooldown_ms` is a short safety net on top of that — set it long enough to be the separator and
rapid repetitions get merged.

The state machine is online (`EventDetector.push` takes one window and returns an event the
moment it confirms one), which is what an Android service needs: the counter increments while
the user is still speaking, not when the phrase ends.

### What it needs

The folder is `paths.streaming_dir` (default `streaming`). If that name does not exist, the usual
alternatives — `streaming_test/`, `streaming_tests/`, `streaming_eval/` — are tried and the
fallback is logged once, so a set filed under a different name is used rather than reported
missing. `audio/` is optional: a flat folder of recordings next to `annotations.json` works too.

```text
streaming/
├── 007/                     SpeechCollector's repetition takes, per phrase
│   └── 007_x10_sp8d358495_20260803_183015_ab12cd.webm
├── negative/                its long-negative takes: zero dhikr, shared by every target
│   └── negative_x0_sp8d358495_20260803_190211_77cd10.webm
├── audio/
│   ├── session_001.wav      someone repeating the dhikr, minutes at a time
│   ├── tv_arabic.wav        zero target phrases, shared by every target
│   └── stream_negative_006.wav  zero target phrases, target 006 only
└── annotations.json
```

```json
[
  {"file": "007/007_x10_sp8d358495_20260803_183015_ab12cd.webm", "target": "007",
   "events": [], "expected_count": 10},
  {"file": "session_001.wav", "target": "007",
   "events": [{"start": 12.3, "end": 14.1}, {"start": 19.0, "end": 20.8}]},
  {"file": "tv_arabic.wav", "target": "007", "category": "background_audio",
   "events": [], "expected_count": 0}
]
```

`stream_negative_<target_id>` files require no manual annotation entry. They are loaded as
zero-count `hard_negative` recordings for that target only, including files added after
`annotations.json` already exists.

Three ways to state what is in a recording, in decreasing order of what they measure:

| annotation | measures |
|---|---|
| `events: [{start, end}, …]` | everything: precision, recall, duplicates, FA/hour |
| `expected_count: 0` | **FA/hour** and event precision — the release-critical pair |
| `expected_count: n` | count accuracy only |

`expected_count: 0` is a negative-only stress test: every event detected in it is a false
activation, and `category` attributes it. These are the cheapest recordings in the whole project
— leave a phone recording the television — and they carry the most important number.

An entry with **neither** states nothing, so it is excluded and reported rather than guessed at.
It used to be read as "no target in here", which scored a recording of somebody reciting the
target as pure false activations. An annotation with no `target` is shared material and counts
for every model.

The last two rows need no author. SpeechCollector's two evaluation recorders write what they
asked for into the filename — `007_x10_…` for a repetition take, `negative_x0_…` for minutes of
audio with no dhikr in it — so `load_streaming_set` derives `annotations.json` from them on first
run; see [DATA_COLLECTION.md](docs/DATA_COLLECTION.md#who-writes-the-annotations). The `x10` takes
measure count accuracy and cannot measure FA/hour, because every one of them contains the target;
the `x0` takes are what makes FA/hour measurable at all.

### What it reports

```
expected repetitions : 100
detected             :  99
correct              :  98
missed               :   2
false events         :   1
duplicates           :   0

event precision      : 99.0%
event recall         : 98.0%
FA / hour            : 0.20 over 5.00 h
```

Duplicates are counted separately from false positives — a duplicate is one utterance counted
twice, exactly what the state machine exists to prevent, and folding the two together would hide
it. It still costs precision, because on the phone a duplicate *is* an extra count.

The clip-level hard-negative pass runs alongside it and names names: which category of near-miss
gets through, and the most confident individual false positives, so a collection effort knows
what to record next.

### Threshold calibration

`0.5` is a default, not a threshold. The activation threshold that ships is the **lowest** one
whose measured FA/hour stays inside `calibration.target_false_activations_per_hour` — lower
thresholds detect more, so the lowest admissible one is also the highest-recall admissible one.
The release threshold scales with it (`calibration.release_ratio`), because a fixed release of
0.4 under an activation of 0.98 is not hysteresis, it is a cliff.

When no threshold qualifies, calibration **reports a failure**:

```
CALIBRATION FAILED - no threshold meets the release criteria.
budget: <= 0.50 FA/h
The best any threshold managed is 3.40 FA/h at activation 0.95 (recall 71.4%).
```

That is a data result, not a tuning result. Pushing the threshold higher trades away recall
without fixing what the model is confusing; the streaming report says which category of audio
produced the false activations, and that is what to go and record.

Scoring and counting are separate, so a 60-point sweep costs **one** forward pass over the
recordings, not sixty.

### Readiness

The verdict combines the streaming numbers with the dataset that produced them:

```
Target: 007  سبحان الله العظيم وبحمده

Dataset:
  [PASS] positive recordings          350 (minimum 100, 200+ recommended)
  [PASS] positive speakers            24 (minimum 10, 20+ recommended)
  [PASS] speaker leakage              no speaker crosses a split
Streaming:
  [PASS] event precision              98.8% (>= 95%)
  [PASS] event recall                 96.5% (>= 90%)
  [PASS] false activations/hour       0.22 over 5.00 h (<= 0.50)
Hard negatives:
  [PASS] hard-negative FP rate        0.7% of 1000 negative clips (<= 5%)
Quantisation:
  [PASS] INT8                         drift 0.0180, FA/h +0.00

STATUS: READY FOR DEVICE TEST
```

Three statuses: `NOT READY`, `EXPERIMENTAL`, `READY FOR DEVICE TEST`. An **unmeasured criterion
counts as unmeasured, not as a pass** — so a target with no streaming evaluation can never read
`READY`, however high its clip accuracy is. And `READY FOR DEVICE TEST` means exactly that: a
licence to try it on a phone, where on-device latency, microphone response and real rooms are
still unmeasured.

The thresholds live under `readiness:` in the config. They are project release criteria, not
scientific constants, and moving one should be a visible decision.

---

## 6 · Export

`exports/<target>/` holds everything the app needs for that one dhikr and nothing about any
other:

| file | purpose |
|---|---|
| `dhikr_007_float32.tflite` | reference variant; matches Keras |
| `dhikr_007_int8.tflite` | fully int8, ~4× smaller — what ships when it verifies |
| `model_metadata.json` | **the Android contract** — see below |
| `labels.txt` | `unknown` / `target`, in output order |
| `labels_target.json` | the target's id, its Arabic text, and which index is the target |
| `evaluation.json` | clip metrics, per negative category |
| `streaming_evaluation.json` | events, FA/hour, per-recording breakdown |
| `calibration.json` | the full threshold sweep |
| `frontend_test.wav` | front-end parity assets |
| `frontend_expected.npy` | |
| `frontend_metadata.json` | |
| `mel_filterbank.json` | the exact mel matrix |

### INT8 is not assumed to be free

Keras, float32 TFLite and INT8 TFLite are scored on the **same** positives, ordinary negatives,
hard negatives *and* streaming windows. The comparison reports probability drift, how many clip
decisions flipped across the threshold, and the change in false activations per hour.

INT8 is recommended only when it neither drifts beyond `readiness.max_int8_probability_drift`
nor adds more than `readiness.max_int8_fa_per_hour_increase`. A variant whose probabilities look
close but which *counts more* is rejected — that is the failure a probability-only check misses.
When nothing passes, the report says so rather than falling back to the variant that failed.

### The Android metadata contract

`model_metadata.json` is written so that **Android needs no hidden constants**:

```json
{
  "target_phrase_id": "007",
  "target_phrase_text": "سبحان الله العظيم وبحمده",
  "model_version": "1",
  "architecture": "ds_cnn_tiny",
  "output_mode": "softmax",
  "sample_rate": 16000,
  "clip_samples": 32000,
  "window_seconds": 2.0,
  "hop_seconds": 0.2,
  "labels": ["unknown", "target"],
  "target_index": 1,
  "feature": { "n_mels": 40, "window_ms": 30.0, "hop_ms": 10.0, "n_fft": 512,
               "fmin": 20.0, "fmax": 7600.0, "log_offset": 1e-06,
               "normalization": "per_example", "input_shape": [197, 40, 1] },
  "tensors": { "input":  { "dtype": "int8", "shape": [1, 197, 40, 1],
                           "quantization": { "scale": 0.0235, "zero_point": -128 } },
               "output": { "dtype": "int8", "shape": [1, 2],
                           "quantization": { "scale": 0.00390625, "zero_point": -128 } } },
  "detection": { "activation_threshold": 0.85, "release_threshold": 0.51,
                 "min_consecutive_hits": 2, "release_windows": 2, "cooldown_ms": 200.0,
                 "smoothing": { "mode": "none" } },
  "model": { "file": "dhikr_007_int8.tflite", "parameter_count": 23456,
             "file_size_bytes": 45678, "sha256": "…" },
  "dataset": { "positive_clips": 350, "positive_speakers": 24, "speaker_leakage": 0 },
  "streaming": { "false_activations_per_hour": 0.22, "precision": 0.988, "recall": 0.965 }
}
```

The detection block belongs to **this** target. Parameters calibrated for 006 say nothing about
007, so shipping one set of constants for every model is exactly the mistake this file prevents.

### Front-end parity

The model takes a log-mel tensor, not audio. When the Kotlin front-end differs, nothing fails:
the app runs and the model returns confident nonsense, indistinguishable from a bad model.

`frontend_test.wav` is written **already conditioned** — one window long, level-normalised, PCM16
so both sides decode identical integers. The device-side check is then unambiguous: decode it,
run the front-end straight over the samples, compare against `frontend_expected.npy` with
`|a - b| <= tolerance`. A wrong hop shows up as a shape mismatch, a missing log offset as whole
units of difference.

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

## 7 · Test the export

Section 04 reports how the model scores on held-out clips. It cannot tell you how the exported
flatbuffer behaves on audio someone just spoke, or whether it can **count** dhikr in a continuous
recording — which is what the app actually needs. `space/` is a Gradio app for exactly that:

```bash
cd space
pip install -r requirements.txt
python app.py                                       # http://127.0.0.1:7860
```

It pulls the export root from the shared folder named in `space/model_source.txt` on startup — a
Google Drive folder, a `hf://user/repo`, a direct URL or a local path — so there is nothing to copy.
Target subfolders (`006/`, `007/`, ...) are preserved because every phrase owns identically named
`labels.txt` and `model_metadata.json` sidecars.
`DHIKR_MODEL_SOURCE` overrides it, `DHIKR_MODEL_DIR` points at a local exports folder instead, and
the *Add phrase models* tab takes a link or the files directly.

The picker is phrase-first and marks the recommended quantisation variant. Four tabs test one clip
as target-vs-not-target, count only the selected phrase with the exported hysteresis detector, show
that phrase's calibration/measurements, and add more phrase bundles. Several `.tflite` variants can
live inside each target folder, so INT8/float32 comparison remains one click away.

It runs on **LiteRT**, not TensorFlow, so it installs in seconds. It reads the target id, output mode,
front-end and calibrated detector from each `model_metadata.json`, not from the current config. This
also matters for one-output sigmoid exports: the scalar is P(target) and must never be normalised as
a one-class softmax, which would make every window read 100% target.

To publish it as a Hugging Face Space, `space/deploy.sh` stages `src/`, `configs/config.yaml` and
`phrases.json` alongside the app and pushes — the Space is a separate git repo and cannot import
from a parent folder, but the pipeline code stays single-sourced here. See `space/README.md`.

---

## 8 · Integrate into Android

One model per dhikr, loaded when the user picks one. Copy each target's export into its own
asset folder:

```text
app/src/main/assets/dhikr/007/
├── dhikr_007_int8.tflite       (or whichever variant the export recommends)
├── model_metadata.json
├── labels.txt
└── mel_filterbank.json
```

Everything the runtime needs comes from `model_metadata.json` — window length, hop, front-end
parameters, quantisation scales and zero points, and this target's calibrated thresholds. Read
them; do not hard-code them. A constant that lives only in Kotlin will drift from the model it
was measured for, and the drift is silent.

Gradle:

```kotlin
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}
```

### The model takes features, not audio

The TFLite model input is a `(frames, n_mels, 1)` **log mel spectrogram**, not a waveform. The app
must reproduce the training front-end exactly — a mismatch here is the single most common reason a
model that scored 98 % in section 04 behaves randomly on a phone.

Every parameter is in `model_metadata.json`; with the defaults in this repository:

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
 * Reproduces src/features.py::LogMelExtractor. Parameters must match model_metadata.json.
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
val meta = Json.parseToJsonElement(assets.open("dhikr/007/model_metadata.json").readBytes().decodeToString())
val interpreter = Interpreter(loadModelFile(context, "dhikr/007/dhikr_007_int8.tflite"))

// features: (197, 40) from LogMelFrontend -> (1, 197, 40, 1)
val input = Array(1) { Array(197) { t -> Array(40) { m -> floatArrayOf(features[t][m]) } } }
val output = Array(1) { FloatArray(2) }
interpreter.run(input, output)

val pTarget = output[0][1]        // metadata: "target_index": 1
```

For the **int8** model, quantise the input and dequantise the output using the scale and zero
point — from the interpreter, or equivalently from `tensors` in the metadata:

```kotlin
val params = interpreter.getInputTensor(0).quantizationParams()
val quantized = ((value / params.scale) + params.zeroPoint).toInt().coerceIn(-128, 127).toByte()
```

A one-output sigmoid model (`"output_mode": "sigmoid"`) has a single value, which *is*
`P(target)`. Read `output_mode` rather than assuming a width.

### Counting

Do **not** increment whenever `P(target)` crosses the threshold. Overlapping windows would count
one utterance several times, and a plain refractory timer would merge two quick repetitions into
one. Mirror the state machine `src/streaming.py` implements and the export calibrated:

```kotlin
// every hop_seconds, over the last window_seconds of audio
when (state) {
    IDLE -> if (p >= activation) { state = CANDIDATE; hits = 1; start = now }

    CANDIDATE -> when {
        p >= activation -> {
            hits++
            if (hits >= minConsecutiveHits) { state = CONFIRMED; count++ }   // count here
        }
        p >= release -> Unit                 // inside the hysteresis band: still going
        else -> state = IDLE                 // fell away: it was not the phrase
    }

    CONFIRMED -> if (p < release) {
        if (++below >= releaseWindows) { state = COOLDOWN; cooldownUntil = now + cooldownMs }
    } else below = 0

    COOLDOWN -> if (now >= cooldownUntil) { state = IDLE; /* re-examine this window */ }
}
```

Four things matter here, and each is one of the numbers the export was calibrated on:

1. **Count on confirmation**, not on release — the counter should move while the user is still
   speaking.
2. **Hysteresis**: `activation > release`. Without the gap, a score hovering near one threshold
   produces a stream of counts.
3. **Re-arm on release**, not on the cooldown. A user repeating the dhikr quickly is separated by
   the dip between repetitions; a cooldown long enough to be the separator merges them.
4. **Re-examine the window that ends the cooldown** rather than discarding it, or a fast
   repetition arriving one window early is lost.

`min_consecutive_hits`, `release_windows`, `cooldown_ms`, and both thresholds come from
`model_metadata.json` → `detection`, calibrated for that target against a measured
false-activation budget. The `streaming_evaluation.json` in the same folder says what those
settings achieved: events counted, missed, false, and FA/hour.

---

## Configuration

`configs/config.yaml` is the only place to change behaviour. Every key is commented in the file.
The ones worth knowing:

| key | what it does |
|---|---|
| `paths.drive_root` / `paths.project_dir` | where the dataset lives |
| `target.phrase_id` | `all` = one model per collected/catalogued phrase; a numeric id = one model; `null` = legacy multi-class |
| `target.output_mode` | `sigmoid` (1 output, default) or legacy `softmax` (2 outputs) |
| `target.auto_other_dhikr_negatives` | use the other phrase folders as negatives |
| `target.phrase_overrides` | per-target `clip_seconds` |
| `negative_sampling.ratio` / `weights` | how much of the negative pool one run trains on |
| `split.speaker.source` | `auto` / `metadata` / `filename` / `parent` / `none` |
| `split.speaker.filename_patterns` | tried in order; the device token first, for a reason |
| `split.speaker.require_disjoint` | fail preprocessing when a speaker crosses a split |
| `audio.clip_seconds` | the window; changing it changes the input shape and the cache |
| `features.n_mels`, `window_ms`, `hop_ms` | the front-end — must match Android exactly |
| `augmentation.*` | per-transform probability and range |
| `model.name` + `model_presets` | `ds_cnn` / `ds_cnn_tiny` / `tc_resnet8` |
| `model.bn_momentum` | BatchNorm moving-average momentum — see below |
| `training.*` | epochs, batch size, optimizer, schedule, early stopping |
| `streaming.hop_seconds` | how often inference runs on device |
| `streaming.detector.*` | the starting operating point (calibration replaces it) |
| `streaming.smoothing.*` | optional score smoothing — off by default, and why |
| `calibration.target_false_activations_per_hour` | the budget the threshold is chosen under |
| `readiness.*` | the release criteria |
| `export.*` | which variants to build, calibration size, benchmark runs |

Config is validated on load: an unknown or misspelled key raises immediately rather than being
silently ignored.

After changing anything under `audio.*` or `features.*`, re-run preprocessing with
`OVERWRITE_AUDIO = True` and train a fresh run — cached clips and checkpoints were built with the
old settings. (Changing `audio.clip_seconds` gives that geometry its own cache directory, so the
old clips are not overwritten, just no longer used.)

### A note on `model.bn_momentum`

Keras defaults BatchNorm momentum to `0.99`, which needs thousands of updates before the moving
statistics converge. A few hundred clips give only a handful of steps per epoch, so the statistics
stay near their initial values while the model trains against batch statistics. The symptom is
distinctive and easy to misread as overfitting: **training accuracy climbs to 95 %+ while validation
accuracy sits exactly at chance**, because in inference mode the model collapses to a single class.

The default here is `0.9`, which converges in about 50 steps. Raise it toward `0.99` only once the
dataset is large enough to give hundreds of steps per epoch.

---

## Why one model per dhikr

The app knows which dhikr the user chose before a single window is scored. Once that is true,
asking the model to *also* work out which phrase it heard is solving a harder problem than the
product has, and paying for it in the metric that matters.

**A classifier's mistakes are the wrong shape.** A 10-way softmax spends its capacity on
boundaries between phrases the session will never contain, and its confidence is relative: on a
window of television it still distributes probability across phrases, and the highest one wins.
A single-target model is asked one question, and `unknown` is a first-class answer.

**Nested prefixes become the whole task.** `سبحان الله` ⊂ `سبحان الله وبحمده` ⊂
`سبحان الله العظيم وبحمده`. For a classifier these are three classes competing for probability
mass. For target 007 the first two are *hard negatives*, and getting them right is exactly what
"count only complete phrases" means. Training on them as negatives, and evaluating on them
separately, is only possible when the target is fixed.

**Per-target calibration.** The threshold, the release, the number of consecutive hits and the
window length that work for a two-word dhikr are not the ones for a seven-word one. One model
per dhikr means one calibrated operating point per dhikr, shipped alongside it.

**The cost is real and worth naming.** N models means N training runs, N exports, and roughly N
times the disk in assets — though only one is ever loaded, so runtime memory is unchanged, and
`ds_cnn_tiny` exists because a single-target problem does not need the full backbone. Adding a
phrase becomes cheap in the other direction: train one new model, invalidate nothing.

### The measurement behind this

Section `08 · Experiment` in the notebook is the comparison that settled it — one binary detector
per phrase against one multi-class model, on the same clips, with the same architecture,
augmentation, optimiser, seed and epoch budget. It is kept because the question keeps being
asked, and because it still answers something the production pipeline does not: how a *committee*
of binary detectors compares at telling the nested phrases apart.

It reports Wilson intervals next to every accuracy and refuses to call a difference smaller than
0.02 AUC a result. Note that its "committee" — `argmax` over N models' scores — is not what the
app does: Android loads exactly one model and reads one score against a calibrated threshold.

---

## Growing the dataset

The pipeline is built to be re-run as recordings accumulate:

1. Drop new files into `dataset/<target>/` or `dataset/unknown/<category>/`.
2. Re-run stage `01` to validate them (it flags duplicates across the whole dataset).
3. Re-run `02` — already-conditioned clips are skipped, so this costs only the new files.
4. Train with a **new** `RUN_NAME`, then re-run the streaming stage and re-calibrate.

**Re-calibrate after every dataset change.** The threshold was chosen against a measured
false-activation rate; a model trained on more data has a different score distribution, and the
old threshold is a number from a different measurement.

**Adding a new dhikr** is a new target, not a new class: create `dataset/<id>/`, add it to
`phrases.json`, and collect hard negatives for it under `unknown/hard_negative/`. The next
`python train.py` batch includes it automatically; `python train.py --target <id>` builds only that
new model. Nothing that already shipped is invalidated — which is the one unambiguous operational
win of this architecture.

Re-splitting note: the speaker-safe split re-randomises from the seed each time preprocessing
runs, so a speaker can move between train and test as the dataset grows. That is fine for
tracking progress, but do not compare two runs' test numbers to the third decimal unless the
manifest was unchanged.

What to collect, and in what order, is in [`docs/DATA_COLLECTION.md`](docs/DATA_COLLECTION.md).

---

## Troubleshooting

| symptom | cause and fix |
|---|---|
| `dataset directory not found` | `paths.drive_root` / `project_dir` do not match Drive. Stage 01 prints the resolved paths. |
| `target phrase 009 has no folder` | `target.phrase_id` points at a folder that does not exist under `dataset/`. |
| `manifest not found` | Run stage `02 · Preprocessing` for this target first. |
| `speaker leakage after splitting` | A speaker is in two splits. Fix the naming or the metadata; do **not** set `split.fail_on_leakage: false` to silence it — that hides the problem, it does not solve it. |
| `EVALUATION IS NOT SPEAKER-INDEPENDENT` in the logs | No speaker source resolved - add `speakers.csv`, or backfill tokens with `src/speaker_backfill.py`. Training still runs; every number it prints is optimistic. |
| Accuracy looks high, the counter fires constantly | Clip accuracy cannot see this. Run stage 05 — that is what it is for. |
| Detector never fires | Check the *majority-class* baseline, not accuracy: with negatives at 2× positives, "never fire" scores 67%. Look at recall and AUC in the clip report. |
| `CALIBRATION FAILED` | No threshold meets the FA/hour budget. A data result, not a tuning one — the streaming report names the audio category producing the false activations; collect hard negatives of that kind. |
| One repetition counted twice | `duplicates` in the streaming report. Raise `release_windows` or `min_consecutive_hits`, or lengthen `cooldown_ms` slightly — but check that rapid repetitions still count. |
| Rapid repetitions counted once | The opposite: `cooldown_ms` is acting as the separator. Shorten it; re-arming should come from the release. |
| Training accuracy high, validation stuck at chance | BatchNorm momentum — see [above](#a-note-on-modelbn_momentum). |
| Validation far below training | Genuine overfitting — see [below](#a-run-that-overfits). |
| Colab disconnects | Re-run training with the same `RUN_NAME`; it resumes. |
| `'tf.Conv2D' op is neither a custom op nor a flex op` | A mixed-precision (float16) checkpoint reached the converter. The export path rebuilds it in float32 automatically; if you call `convert_tflite` yourself, pass the model through `to_float32_model` first. |
| `int8` conversion fails | Calibration data is empty or all one class. Ensure the `train` split is non-empty and `export.representative_samples` ≥ 100. |
| INT8 rejected by the export | It drifted, or it added false activations per hour. The reasons are printed; ship `float32` in the meantime rather than overriding the check. |
| Model works in Colab, fails on the phone | Front-end mismatch. Run the parity check: decode `frontend_test.wav`, compute features, compare against `frontend_expected.npy`. |
| `OOM` during training | Lower `training.batch_size`, or `training.cache: false`. |
| Arabic renders as boxes in charts | Expected: matplotlib does not shape Arabic. Charts use folder ids; the id → phrase mapping is printed as a table. |

### The legacy multi-class mode

`target.phrase_id: null` puts the pipeline back in the multi-class mode it had before
single-target training, where `classes.include_phrases` picks the vocabulary:

```yaml
target:
  phrase_id: null
classes:
  include_phrases: [1, 2, 3, 4]   # null or [] trains on every folder found
  include_unknown: true
```

It exists for two things: the `08 · Experiment` comparison, which needs a multi-class model to
compare against, and reading manifests written before the change. **It is not the production
path** — nothing in it produces the per-target export, the streaming evaluation or the Android
metadata contract, so a model trained this way cannot be shipped by the app.

For the production batch, use `phrase_id: all`, not `null`. The batch selector intersects numeric
`dataset/<id>/` folders with `phrases.json`; catalog entries whose recordings have not been
collected yet are reported and skipped. Every iteration receives a numeric target-bound config, so
checkpoints, manifests, threshold calibration and `exports/<id>/` remain isolated.

After changing the vocabulary, re-run preprocessing (the manifest carries the old classes
otherwise) and train under a fresh run: an old checkpoint has the wrong number of outputs, and
`Trainer` refuses to restore an incompatible backup rather than failing later with a shape error.

### A run stuck at chance

A summary like this is not a weak model, it is a dead one:

```
run              : ds_cnn
epochs completed : 39
best val_accuracy: 0.1000 (epoch 1)
```

`0.1000` with 10 classes is exactly what a constant prediction scores on a balanced split — the
output does not depend on the input. For a **single-target detector** the equivalent number is
the majority class, not `1/num_classes`: with negatives at twice the positives, a model that
never fires scores 0.67, and `artifacts.summary()` reports chance that way so it cannot be
mistaken for two thirds of a working model. Section 03 now says so out loud (`prediction_distribution()`
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
3. **Check you are not resuming.** Resume defaults to false. With `training.resume: true`, re-running the notebook restores
   the previous run's weights *and* optimiser state, and splices both runs' histories together — so
   a config change you made in between never took effect, and "epochs completed" counts epochs from
   a run you already abandoned. Set `FRESH_START = True` (or a new `RUN_NAME`) after any config
   change. The summary flags a resumed run, and `fit` compares the config against the snapshot
   stored with the backup — it raises when `classes.include_phrases` changed (the output width
   moved, so the restore cannot work) and rejects every other run-producing setting that drifted.
   Existing checkpoints are left untouched; use a fresh run name or explicitly reset that run.
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
2. **Check the speaker split actually resolved.** Single-target preprocessing splits by speaker
   and verifies it, but only when speaker ids exist: with no speaker source resolving,
   every clip becomes its own group and the same voice lands in train
   *and* val. That makes validation accuracy **optimistic** and the real gap larger than the one
   printed. Files uploaded by `SpeechCollector` are named `<class>_<timestamp>_<suffix>`, with no
   speaker token, so there is nothing to resolve automatically — record batches with a speaker
   prefix, or ship a `speakers.json` alongside them. The dataset report and the readiness verdict
   both say when this is unresolved.
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

A **binary phrase spotter**, one per dhikr. It takes a `197 × 40` log-mel window and answers one
question: was the target phrase spoken, completely, in this window?

Three topologies, all consuming the same front-end and all mapping entirely onto TFLite builtin
ops, so INT8 quantisation needs no Flex delegate:

| | |
|---|---|
| **DS-CNN** | the baseline from *Hello Edge: Keyword Spotting on Microcontrollers* — a strided convolutional stem followed by depthwise separable blocks, global average pooling and the head. ~24k parameters, ~40 KB as an INT8 flatbuffer. |
| **DS-CNN Tiny** | the same topology at 3 blocks × 32 filters and half width. Single-target detection is a much easier problem than 10-way classification, so this is worth measuring before assuming the full backbone is needed. |
| **TC-ResNet8** | mel bins become channels and convolution runs along time alone, so the first layer already sees the whole spectrum at each time step — a fraction of the multiply-accumulates for a comparable result on short keywords. |

The head is a 2-output softmax (`unknown`, `target`) or a 1-output sigmoid, depending on
`target.output_mode`.

**But the model is only half of it.** What ships is the model *plus* its calibrated operating
point: activation and release thresholds, the number of consecutive hits, the release windows and
the cooldown, all chosen against a measured false-activation budget for that specific phrase and
travelling with it in `model_metadata.json`. A model with the wrong thresholds counts the wrong
things, and the thresholds for one dhikr are not the thresholds for another.

The success criterion is not clip accuracy. It is: when Android loads this model and listens
continuously, **every complete repetition produces about one event**, and incomplete phrases,
other dhikr, ordinary Arabic speech and environmental sound produce none.

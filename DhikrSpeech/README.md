# DhikrSpeech

Training pipeline for an **offline Arabic dhikr phrase spotter**. It takes short recordings of dhikr
phrases from Google Drive and produces a quantised TensorFlow Lite model that runs on Android with
no network access.

Five Colab notebooks, one config file, one reusable Python package. The notebooks orchestrate;
every piece of logic lives in `src/` so nothing is duplicated between them.

```
recordings on Drive
   └─ 01_dataset       inspect + validate
   └─ 02_preprocessing condition to 16 kHz mono, freeze the splits
   └─ 03_training      DS-CNN, TensorBoard, checkpoints, resume
   └─ 04_evaluation    metrics, confusion matrix, ROC, error analysis
   └─ 05_export        SavedModel + 3 TFLite variants, benchmarked and verified
                          └─ app/src/main/assets/
```

---

## Contents

- [Layout](#layout)
- [Quick start](#quick-start)
- [1 · Upload the dataset](#1--upload-the-dataset)
- [2 · Mount Drive and open a notebook](#2--mount-drive-and-open-a-notebook)
- [3 · Train](#3--train)
- [4 · Resume training](#4--resume-training)
- [5 · Export](#5--export)
- [6 · Integrate into Android](#6--integrate-into-android)
- [Configuration](#configuration)
- [Growing the dataset](#growing-the-dataset)
- [Troubleshooting](#troubleshooting)

---

## Layout

```text
DhikrSpeech/
├── notebooks/
│   ├── 01_dataset.ipynb          explore + validate the recordings
│   ├── 02_preprocessing.ipynb    condition audio, split, write the manifest
│   ├── 03_training.ipynb         train the DS-CNN
│   ├── 04_evaluation.ipynb       metrics, charts, error analysis
│   └── 05_export.ipynb           TFLite export, benchmark, verify
├── src/
│   ├── config.py                 typed config loaded from configs/config.yaml
│   ├── audio.py                  decode, trim, normalise, fit length, write WAV
│   ├── dataset.py                scan, validate, preprocess, split, tf.data pipeline
│   ├── augmentation.py           noise, pitch, speed, gain, time shift, SpecAugment
│   ├── features.py               log mel front-end (+ its Android metadata)
│   ├── models.py                 DS-CNN
│   ├── trainer.py                seeds, schedules, callbacks, resume
│   ├── metrics.py                accuracy / P / R / F1 / ROC / error analysis
│   ├── visualization.py          every chart
│   └── export.py                 SavedModel, TFLite, benchmark, verification
├── configs/config.yaml           the only place settings live
├── requirements.txt
└── README.md
```

The notebooks contain no thresholds, paths or hyperparameters of their own — they read
`configs/config.yaml`. Change behaviour there, not in a cell.

---

## Quick start

1. Put your recordings on Drive (below).
2. Open `notebooks/01_dataset.ipynb` in Colab → **Runtime → Run all**.
3. Repeat for `02` → `03` → `04` → `05`.
4. Copy `exports/*.tflite`, `labels.txt`, `model_meta.json` and `mel_filterbank.json` into the app.

Each notebook's first cell mounts Drive, finds the project (cloning the repo if it is not already
in the runtime), installs anything missing and loads the config. There is nothing else to set up.

---

## 1 · Upload the dataset

Create this structure in **My Drive**:

```text
MyDrive/Dhikr Speech Dataset/
├── dataset/
│   ├── 001/          every recording of phrase id 1
│   ├── 002/
│   ├── 003/
│   ├── ...
│   └── unknown/      speech and noise that is NOT a dhikr phrase
├── phrases.json
└── noise/            optional: room / background recordings for augmentation
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

**The `unknown` folder is not optional in practice.** A model trained only on dhikr phrases will
classify a cough, a TV, or "good morning" as whichever phrase sounds closest, and on device that
becomes a phantom count. Fill it with ordinary speech, silence, room tone and background noise.
Aim for at least as many `unknown` clips as an average phrase class.

### What makes a good recording

| | |
|---|---|
| length | 1–3 seconds, one phrase per file |
| format | WAV preferred; FLAC/OGG/MP3/M4A are decoded too |
| rate | anything — notebook 02 resamples to 16 kHz mono |
| count | **50 minimum per class**, 200+ for a usable model, 500+ for a good one |
| variety | many speakers, distances, rooms and phones; this matters more than raw count |

Recordings can be collected with the `SpeechCollector/` web app in this repository, which writes
straight to Drive in this layout.

---

## 2 · Mount Drive and open a notebook

Upload the `notebooks/` folder to Colab (or open the files from GitHub with
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

Run `01` and `02` first — training reads `processed/manifest.csv`, which notebook 02 writes.

In `03_training.ipynb`: **Runtime → Change runtime type → GPU**, then **Run all**.

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
| train/val split | `split.*`, applied once in notebook 02 and reused everywhere |
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

**Re-run `03_training.ipynb` with the same `RUN_NAME`.** `BackupAndRestore` restores the optimizer
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

`05_export.ipynb` writes to `exports/` on Drive:

| file | purpose |
|---|---|
| `saved_model/` | TensorFlow SavedModel (the conversion source) |
| `dhikr_float32.tflite` | reference; matches Keras exactly |
| `dhikr_dynamic_range.tflite` | int8 weights, float activations; ~4× smaller |
| `dhikr_int8.tflite` | fully int8; smallest and fastest on a phone |
| `labels.txt` | class labels, one per line, in model output order |
| `labels_phrases.json` | class index → phrase id → Arabic text |
| `model_meta.json` | input shape, audio and front-end parameters, benchmarks, metrics |
| `mel_filterbank.json` | the exact mel matrix, for the Android front-end |

Every variant is benchmarked (size, mean/median/p95 latency, arena estimate) and **verified against
the Keras model on held-out clips**. The notebook recommends the smallest variant that still agrees
with Keras.

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

## 6 · Integrate into Android

Copy into `app/src/main/assets/`:

```text
dhikr_int8.tflite       (or whichever variant notebook 05 recommends)
labels.txt
model_meta.json
mel_filterbank.json
```

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
model that scored 98 % in notebook 04 behaves randomly on a phone.

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

Three rules keep a live counter honest:

1. **Reject low confidence.** Discard predictions below the threshold chosen in notebook 04
   (`evaluation.confidence_threshold`). Without this the model labels every sound as *something*.
2. **Reject `unknown`.** It is a real class in the model and must never increment a counter.
3. **Debounce.** Run inference on a sliding window (for example every 250 ms over the last 2 s) and
   require the same class to win several consecutive windows before counting it once. Then hold a
   short refractory period so one spoken phrase cannot count twice.

Notebook 04's threshold sweep gives the accuracy and accept-rate for each threshold, which is how
you trade missed counts against phantom counts for your users.

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
| `augmentation.*` | per-transform probability and range |
| `split.val_ratio` / `test_ratio` | split sizes |
| `split.group_regex` | keep one speaker's clips inside one split |
| `model.*` | DS-CNN width, depth, dropout |
| `model.bn_momentum` | BatchNorm moving-average momentum — see below |
| `training.*` | epochs, batch size, optimizer, schedule, early stopping |
| `evaluation.confidence_threshold` | the on-device reject gate |
| `export.*` | which variants to build, calibration size, benchmark runs |

Config is validated on load: an unknown or misspelled key raises immediately rather than being
silently ignored.

After changing anything under `audio.*` or `features.*`, re-run notebook 02 with `OVERWRITE = True`
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

## Growing the dataset

The pipeline is built to be re-run as recordings accumulate:

1. Drop new files into `dataset/<class>/`.
2. Run `01` to validate them (it flags duplicates against the whole dataset).
3. Run `02` — already-processed clips are skipped, so this costs only the new files.
4. Run `03` with a **new** `RUN_NAME`, then `04` and `05`.

**Adding a new phrase** means a new class: create the folder, add it to `phrases.json`, and train a
new run. The output layer changes shape, so an existing run cannot be resumed into it.

Re-splitting note: `assign_splits` re-randomises from the seed each time notebook 02 runs, so a clip
can move between train and test as the dataset grows. That is fine for tracking progress over time,
but do not compare two runs' test accuracy to the third decimal unless the manifest was unchanged.

---

## Troubleshooting

| symptom | cause and fix |
|---|---|
| `dataset directory not found` | `paths.drive_root` / `project_dir` do not match Drive. Notebook 01 prints the resolved paths. |
| `manifest not found` | Run `02_preprocessing` first. |
| Accuracy pinned at exactly `1 / classes` and every clip predicted as the same class | The model collapsed — see [below](#a-run-stuck-at-chance). |
| Training accuracy high, validation stuck at chance | BatchNorm momentum — see [above](#a-note-on-modelbn_momentum). |
| Validation accuracy far below training | Genuine overfitting: more recordings, more speakers, stronger `augmentation.*`, or a smaller `model.width_multiplier`. |
| One class always wrong | Check its clips in notebook 01 — usually mislabelled or near-silent takes. Notebook 04 lets you listen to the errors. |
| Colab disconnects | Re-run `03` with the same `RUN_NAME`; it resumes. |
| `'tf.Conv2D' op is neither a custom op nor a flex op` | A mixed-precision (float16) checkpoint reached the converter. `export_all` rebuilds it in float32 automatically; if you call `convert_tflite` yourself, pass the model through `to_float32_model` first. |
| `int8` conversion fails | Calibration data is empty or all one class. Ensure the `train` split is non-empty and `export.representative_samples` ≥ 100. |
| Quantised model disagrees with Keras | Notebook 05 flags this. Increase `export.representative_samples`, or ship `dynamic_range` instead. |
| Model works in Colab, fails on the phone | The Android front-end does not match. Compare against `model_meta.json` — sample rate, window, hop, centring and normalisation must all match. |
| `OOM` during training | Lower `training.batch_size`, or `training.cache: false` for a dataset too large to hold in RAM. |
| Arabic text renders as boxes in charts | Expected: matplotlib does not shape Arabic. Charts use class ids; the id → phrase table is printed in notebook 01. |

### A run stuck at chance

A summary like this is not a weak model, it is a dead one:

```
run              : ds_cnn
epochs completed : 39
best val_accuracy: 0.1000 (epoch 1)
```

`0.1000` with 10 classes is exactly what a constant prediction scores on a balanced split — the
output does not depend on the input. Notebook 03 now says so out loud (`prediction_distribution()`
and the `!!` notes in `artifacts.summary()`). Work through it in this order:

1. **Run the sanity check** (notebook 03, section 6b). It asks a fresh copy of the model to memorise
   ~40 unaugmented clips in 200 steps. It has to reach ~1.0; the report tells you what a failure
   means. Everything below only matters once that passes.
2. **Count the optimiser steps**, printed by the training cell as `total steps`. It is
   `ceil(train_clips / batch_size) × epochs`, and it is what convergence actually depends on. A
   DS-CNN from scratch needs thousands. 300 clips at batch 64 for 60 epochs is 300 steps — the model
   is still at its initialisation, which *is* the constant prediction you are seeing. Lower
   `training.batch_size`, raise `training.epochs`.
3. **Check you are not resuming.** With `training.resume: true`, re-running the notebook restores
   the previous run's weights *and* optimiser state, and splices both runs' histories together — so
   a config change you made in between never took effect, and "epochs completed" counts epochs from
   a run you already abandoned. Set `FRESH_START = True` (or a new `RUN_NAME`) after any config
   change. The summary flags a resumed run.
4. **Then look at the data**: `split_counts` in notebook 03 and the per-class table in notebook 01.
   Ten classes need hundreds of clips each, from more than one or two speakers, before validation
   accuracy means anything.

---

## What the model is

**DS-CNN** — a strided convolutional stem followed by depthwise separable blocks, global average
pooling and a softmax. It is the reference topology from *Hello Edge: Keyword Spotting on
Microcontrollers*: small enough for a phone, and it maps entirely onto TFLite builtin ops, so INT8
quantisation needs no Flex delegate.

With the defaults here (`197 × 40` input, 4 blocks, 64 filters) it is roughly 24k parameters and
about 40 KB as an INT8 flatbuffer. Add classes, widen with `model.width_multiplier`, or deepen with
`model.blocks` as the dataset grows.

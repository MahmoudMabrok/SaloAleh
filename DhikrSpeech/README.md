# DhikrSpeech

Training pipeline for an **offline Arabic dhikr phrase spotter**. It takes short recordings of dhikr
phrases from Google Drive and produces a quantised TensorFlow Lite model that runs on Android with
no network access.

One Colab notebook, one config file, one reusable Python package. The notebook orchestrates;
every piece of logic lives in `src/` so nothing is duplicated between its sections.

```
recordings on Drive
   └─ 01 · Dataset       inspect + validate
   └─ 02 · Preprocessing condition to 16 kHz mono, freeze the splits
   └─ 03 · Training      DS-CNN, TensorBoard, checkpoints, resume
   └─ 04 · Evaluation    metrics, confusion matrix, ROC, error analysis
   └─ 05 · Export        SavedModel + 3 TFLite variants, benchmarked and verified
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
- [6 · Test the export](#6--test-the-export)
- [7 · Integrate into Android](#7--integrate-into-android)
- [Configuration](#configuration)
- [Growing the dataset](#growing-the-dataset)
- [Troubleshooting](#troubleshooting)

---

## Layout

```text
DhikrSpeech/
├── notebooks/
│   └── DhikrSpeech.ipynb         the whole pipeline, five sections, run top to bottom
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
├── space/                        Gradio app for testing an export (Hugging Face Space)
│   ├── app.py                    four tabs: clip, scan, model info, load a model
│   ├── inference.py              model loading, sliding-window scan, counting
│   └── deploy.sh                 stage src/ + configs/ into a Space and push
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
4. Copy `exports/*.tflite`, `labels.txt`, `model_meta.json` and `mel_filterbank.json` into the app.

The first cell mounts Drive, finds the project (cloning the repo if it is not already in the
runtime), installs anything missing and loads the config. There is nothing else to set up.

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

Volunteer speech for this folder arrives on its own: the last card in `SpeechCollector` asks for any
ordinary word that is *not* a dhikr and uploads it directly to `dataset/unknown/`. It is not listed
in `phrases.json` — `scan_dataset` labels the folder by name — so nothing here needs configuring
beyond `classes.include_unknown`. Silence, room tone and noise still have to be added by hand.

### What makes a good recording

| | |
|---|---|
| length | 1–3 seconds, one phrase per file |
| format | WAV preferred; FLAC/OGG/MP3/M4A are decoded too |
| rate | anything — section 02 resamples to 16 kHz mono |
| count | **50 minimum per class**, 200+ for a usable model, 500+ for a good one |
| variety | many speakers, distances, rooms and phones; this matters more than raw count |

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

## 6 · Test the export

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

## 7 · Integrate into Android

Copy into `app/src/main/assets/`:

```text
dhikr_int8.tflite       (or whichever variant section 05 recommends)
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

Three rules keep a live counter honest:

1. **Reject low confidence.** Discard predictions below the threshold chosen in section 04
   (`evaluation.confidence_threshold`). Without this the model labels every sound as *something*.
2. **Reject `unknown`.** It is a real class in the model and must never increment a counter.
3. **Debounce.** Run inference on a sliding window (for example every 250 ms over the last 2 s) and
   require the same class to win several consecutive windows before counting it once. Then hold a
   short refractory period so one spoken phrase cannot count twice.

Section 04's threshold sweep gives the accuracy and accept-rate for each threshold, which is how
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
| `classes.*` | which phrase ids the model learns — see [below](#training-on-a-subset-of-phrases) |
| `training.*` | epochs, batch size, optimizer, schedule, early stopping |
| `evaluation.confidence_threshold` | the on-device reject gate |
| `export.*` | which variants to build, calibration size, benchmark runs |

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
   change. The summary flags a resumed run.
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
2. **Watch for speaker leakage.** `assign_splits` is stratified per class but has no idea who is
   speaking, so with `split.group_regex: null` the same voice lands in train *and* val — which makes
   validation accuracy **optimistic**, and the real gap larger than the one printed. Files uploaded
   by `SpeechCollector` are named `<class>_<timestamp>_<suffix>`, with no speaker token, so there is
   nothing to group on automatically. If you record a batch yourself, name the files with a speaker
   prefix and set `split.group_regex` to match it.
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

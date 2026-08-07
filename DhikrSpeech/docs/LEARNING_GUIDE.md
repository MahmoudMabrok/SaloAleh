# DhikrSpeech — a learning guide

**Who this is for:** someone who can write Python and has finished a data-structures course, but has
never trained a neural network, never touched audio, and has never shipped a model to a phone. No
machine-learning background is assumed. Every term is defined the first time it appears.

**What this is:** the *why* behind DhikrSpeech. [`../README.md`](../README.md) tells you which cells
to run; this document tells you what those cells are doing and why each choice was made. Read this
once end to end, then keep the README open while you work.

**How to read it:** top to bottom. Each section builds on the previous one. Wherever a claim maps
onto real code, the file is named — go look at it, that is the point.

---

## Contents

- [0 · The problem in one paragraph](#0--the-problem-in-one-paragraph)
- [1 · Sound as numbers](#1--sound-as-numbers)
- [2 · From a waveform to a picture](#2--from-a-waveform-to-a-picture)
- [3 · The model: DS-CNN](#3--the-model-ds-cnn)
- [4 · Training](#4--training)
- [5 · Data discipline](#5--data-discipline)
- [6 · Reading the results](#6--reading-the-results)
- [7 · Shipping it to a phone](#7--shipping-it-to-a-phone)
- [8 · Counting at runtime](#8--counting-at-runtime)
- [9 · What makes *this* problem hard](#9--what-makes-this-problem-hard)
- [10 · Concept → file map](#10--concept--file-map)
- [11 · Glossary](#11--glossary)
- [12 · Exercises](#12--exercises)

---

## 0 · The problem in one paragraph

A user opens the app, taps a counter button once per dhikr, and their thumb does the counting. We
want the **phone's microphone** to do it instead: the user says `سبحان الله وبحمده` thirty-three
times and the app counts thirty-three, offline, with no server.

### This is keyword spotting, not speech recognition

There are two very different ways to attack that.

| | Speech recognition (ASR) | Keyword spotting (KWS) ← *what we do* |
|---|---|---|
| Output | Arbitrary text | One of a small fixed list of phrases |
| Model size | 100 MB – several GB | ~40 KB |
| Runs offline on a phone | Barely, with effort | Comfortably |
| Training data needed | Thousands of hours | Hundreds of clips |
| Failure mode | Wrong words | Wrong phrase, or a false alarm on noise |

A dhikr counter never needs to transcribe anything. It needs to answer one question about a two-second
window of audio: **"which of these N phrases is this, if any?"** That is a *classification* problem,
which is the simplest kind of machine-learning problem there is, and it is why a 40 KB model is enough.

### The exact shape of the task

The model is a function:

```
f(2 seconds of audio) → a probability for each class
```

Classes are the phrases we chose to train on, **plus one extra class called `unknown`**. With the
current config (`classes.include_phrases: [6, 7]`, `include_unknown: true`) there are three:

| index | class | meaning |
|---|---|---|
| 0 | `6` — سبحان الله وبحمده | phrase |
| 1 | `7` — سبحان الله العظيم وبحمده | phrase |
| 2 | `unknown` | anything else: silence, a cough, a car, ordinary speech |

The output is three numbers that sum to 1.0, e.g. `[0.93, 0.05, 0.02]` — "93% sure that was phrase 6".

> **Why `unknown` is not optional.** The final layer is a *softmax* (§3.7): it always distributes
> 100% of the probability across the classes it knows. A model trained on two phrases and nothing
> else has no way to say "that was a door slamming" — the door slam gets 100% split between the two
> phrases, and one of them will look confident. The `unknown` class gives the model somewhere to put
> everything that is not a dhikr. This is called the **open-set problem**, and it is the single most
> common reason a keyword spotter that scores 98% in a notebook is useless in the real world.
> `space/inference.py` prints a warning banner when it loads a model with no `unknown` label.

Counting is a second, separate step built on top of this classifier, described in §8.

---

## 1 · Sound as numbers

Before any machine learning: what *is* the audio the model receives?

### 1.1 Sampling

Sound is air pressure changing over time — a continuous curve. A computer cannot store a continuous
curve, so a microphone **samples** it: it measures the pressure at fixed intervals and stores each
measurement as a number.

Two settings define this (`config.yaml → audio`):

- **`sample_rate: 16000`** — 16,000 measurements per second. One second of audio is an array of
  16,000 numbers. Our fixed 2.0-second clip is therefore **32,000 numbers**.
- **`bit_depth: 16`** — each measurement is a 16-bit integer (PCM16), so it can take 65,536 distinct
  values. Internally we convert these to float32 in the range −1.0…+1.0 because that is what the
  maths wants.
- **`channels: 1`** — mono. One microphone, one stream of numbers. Stereo would be two, and the
  pipeline deliberately does not support it.

### 1.2 Why 16 kHz and not 44.1 kHz?

The **Nyquist–Shannon sampling theorem** says a sample rate of `R` can faithfully represent
frequencies up to `R/2` — the *Nyquist frequency*. At 16 kHz that ceiling is 8 kHz.

Music needs 44.1 kHz because cymbals and violin harmonics live above 8 kHz. Speech does not: almost
all the information that distinguishes one spoken word from another sits below 8 kHz — which is
exactly why telephony has used 8 kHz sampling for a century and you can still recognise your friend's
voice on a phone call.

So 16 kHz is the standard speech compromise: it keeps everything that matters and throws away
**two-thirds** of the data a 44.1 kHz recording would carry. Fewer numbers means a smaller model, a
faster model, and less data needed to train it. Notice `features.fmax: 7600` in the config — we do
not even use the full 8 kHz, because the very top of the band is mostly microphone noise.

### 1.3 Conditioning: making every clip comparable

Volunteers record on different phones, in different rooms, at different distances from the mic. Raw
recordings are wildly inconsistent, and inconsistency the model does not need is inconsistency it
will waste capacity learning around. `src/audio.py` normalises it away in four steps.

**Trim silence** (`audio.trim`, `top_db: 30.0`, `pad_ms: 50.0`)
People tap record, pause, then speak, then pause, then tap stop. `librosa.effects.trim` cuts
leading/trailing audio that is more than 30 dB quieter than the loudest part, then puts 50 ms back on
each side so we do not clip the start of the first consonant. Without this, the model would see the
phrase starting at a random offset in every clip.

**Normalise loudness** (`audio.normalize`, `target_dbfs: -20.0`, `peak_ceiling: 0.99`)
We scale each clip so its **RMS** (root-mean-square — the average energy, i.e. perceived loudness) hits
−20 dBFS. Now a whisper and a shout arrive at the model at the same level, so the model has to learn
the *shape* of the phrase rather than "loud = phrase". `peak_ceiling: 0.99` caps the result just
below the maximum representable value, because a sample above 1.0 wraps around into a nasty
distortion called clipping.

> **dBFS** = decibels relative to full scale. It is a logarithmic loudness scale where 0 dBFS is the
> loudest a digital signal can be and everything real is negative. Logarithmic because hearing is
> logarithmic: the jump from 20 dB to 30 dB sounds like the same size step as 60 dB to 70 dB, even
> though the second is 100× more energy. `audio.silence_dbfs: -55.0` is the line below which we call
> a clip empty.

**Fit to exactly 2.0 seconds** (`audio.clip_seconds: 2.0`, `fit_mode: center`)
The model is a fixed-size function: it takes exactly 32,000 samples, always. Short clips are padded
with silence, long clips are cropped, both symmetrically around the middle (`center`) so the phrase
stays roughly centred. `audio.min_duration`/`max_duration` (0.30 s / 8.00 s) are not applied here —
they are what the validator flags in section `01 · Dataset` so you can go look at a suspicious file.

**Resample to 16 kHz mono** — whatever the volunteer's phone recorded at.

The output of all of this is `processed/`: every recording as a 16 kHz mono PCM16 WAV of identical
length. This is done **once**, up front, because doing it on every training epoch would waste hours.

---

## 2 · From a waveform to a picture

We now have 32,000 numbers per clip. We could feed those straight to a network — and people do — but
it works far better to convert the waveform into a **spectrogram** first.

### 2.1 The intuition

The waveform tells you *pressure over time*. It does not tell you, in any accessible way, *which
frequencies are present* — and frequency content is exactly what distinguishes "sa" from "ba".

Think of sheet music. The waveform is the sound of the orchestra; the spectrogram is the score:
time along the x-axis, pitch up the y-axis, brightness for how loud each pitch is at each moment.
Vowels, consonants and pitch contours become *visible patterns*. And once your data looks like an
image, you can use the enormously well-understood machinery of image classification on it. That is
the whole trick of modern audio ML.

### 2.2 The four steps (`src/features.py`)

**Step 1 — STFT (Short-Time Fourier Transform)**

A **Fourier transform** decomposes a signal into the sine waves that sum to it: give it a chunk of
audio, get back "how much 100 Hz, how much 200 Hz, …". A *short-time* Fourier transform applies that
to short overlapping chunks so we can see how the frequency content changes over time.

The chunks are controlled by:

| setting | value | meaning |
|---|---|---|
| `window_ms` | 30.0 | each chunk covers 30 ms = 480 samples |
| `hop_ms` | 10.0 | the next chunk starts 10 ms later = 160 samples |
| `n_fft` | 512 | the FFT size the 480-sample window is zero-padded to |

Chunks overlap by 20 ms. Why overlap? Because a phoneme boundary that falls inside a chunk would be
smeared away — overlapping chunks guarantee every transition is captured cleanly by *some* chunk.
Why 30 ms specifically? Short enough that speech is roughly stationary within it, long enough to
resolve the low frequencies that carry the vowels. It is the standard speech-processing window and
you should not need to change it.

Each chunk is multiplied by a **Hann window** (a smooth bell) before the FFT. Chopping audio at a
hard edge introduces frequencies that were never there — an artefact called spectral leakage — and
tapering the edges to zero avoids it.

Result: `(257 frequency bins) × (197 frames)`. The 257 is `n_fft/2 + 1`; the FFT of a real signal is
symmetric, so half of it is redundant.

**Where 197 comes from** (`FeatureConfig.num_frames`, `src/config.py:263`) — worth doing by hand once:

```
clip     = 2.0 s × 16000       = 32000 samples
window   = max(win_length, n_fft) = max(480, 512) = 512
frames   = 1 + (32000 − 512) // 160 = 1 + 196 = 197
```

`features.center: false` is why the formula is that one. With `center: true` (librosa's default)
librosa pads the signal so the first frame is *centred* on sample 0, which is convenient in Python
and annoying to reproduce exactly in Kotlin. Turning it off makes the frame count a plain, portable
integer — and this pipeline cares deeply about the Android front-end producing bit-comparable
features (§7.4).

**Step 2 — Mel filterbank**

257 frequency bins is more resolution than we need, and the resolution is in the wrong place. Human
hearing is much better at distinguishing 200 Hz from 300 Hz than 7000 Hz from 7100 Hz — pitch
perception is roughly logarithmic. The **mel scale** is a warping of frequency that matches this.

We multiply the 257 bins by a fixed `(40 × 257)` matrix of overlapping triangular filters, narrow at
the bottom and wide at the top, and get **40 mel bins**. Cheap (one matrix multiply), a 6× reduction,
and it discards precisely the resolution ears do not use.

`mel_scale: slaney`, `mel_norm: slaney`, `fmin: 20`, `fmax: 7600` — these exact settings are dumped
to `mel_filterbank.json` at export time so Android can use *the identical matrix* rather than
recomputing it and drifting.

**Step 3 — Log**

`log(mel + 1e-6)`. Loudness is perceived logarithmically (§1.3), and the log also compresses the
dynamic range so a loud vowel does not numerically drown out a quiet consonant. The `1e-6`
(`features.log_offset`) exists because `log(0)` is −∞, and a single infinity poisons the whole
training run.

**Step 4 — Normalise** (`features.normalize: per_example`)

Subtract this clip's mean and divide by its standard deviation, so every clip arrives with mean 0 and
variance 1. Neural networks train much faster and more stably on inputs in that range.

*Per-example* rather than *global* (dataset-wide statistics) is a deliberate choice: it makes each
clip self-contained. The Android side can normalise a live microphone window using only that window,
with no dataset statistics baked into the app, and a user with an unusually quiet microphone is
automatically compensated.

### 2.3 The result

**`197 × 40 × 1`** float32 values. 197 time frames, 40 mel bins, 1 channel (a greyscale image;
photos have 3 for RGB). That is the model's input, and it is the number you will see everywhere:
`model_meta.json`, `Config.input_shape`, the TFLite input tensor.

Note the compression: 32,000 raw samples → 7,880 feature values, and the 7,880 are *far* more
informative per number.

---

## 3 · The model: DS-CNN

### 3.1 What "training a model" even means

A neural network is a big function with adjustable numbers in it, called **parameters** or
**weights**. Ours has about 24,000 of them.

Training is a loop:

1. Show the network a clip. It produces a guess (three probabilities).
2. Measure how wrong the guess is with a **loss function** — one number, lower is better.
3. Compute, for every one of the 24,000 parameters, "if I nudged this parameter slightly, would the
   loss go up or down, and by how much?" That vector of answers is the **gradient**, and it is
   computed by **backpropagation** (the chain rule from calculus, applied mechanically backwards
   through the network).
4. Nudge every parameter a little way *down* its gradient. That is **gradient descent**.
5. Repeat, hundreds of thousands of times.

Nobody designs the weights. You design the *shape* of the function — the **architecture** — and the
training loop finds the weights. §3.2–§3.8 are about that shape.

### 3.2 Convolution, from scratch

A **dense** (fully connected) layer connects every input to every output. On our 7,880-value input,
a dense layer with 64 outputs would need 7,880 × 64 ≈ 504,000 parameters — 20× our entire model, for
one layer. And it would be *bad*: it would learn "there is an /s/ at frame 40" as something entirely
unrelated to "there is an /s/ at frame 41", so it would need to see the phrase at every possible
offset to learn it.

A **convolution** fixes both problems. A small grid of weights — the **kernel**, say 3×3 — slides
across the input. At each position it multiplies element-wise and sums, producing one output value.

Two consequences, and they are the entire reason CNNs work:

- **Parameter sharing.** The *same* 9 weights are used at every position. 9 parameters instead of
  504,000.
- **Translation equivariance.** A pattern the kernel detects is detected wherever it appears. An /s/
  looks like an /s/ at frame 40 or frame 41. For audio this is exactly right — a phrase spoken 100 ms
  later is the same phrase.

One kernel detects one pattern. A layer has many kernels (`filters`), each learning a different one —
edges, onsets, harmonic stacks — and the stack of their outputs is called a **feature map**.

### 3.3 Depthwise separable convolution — the "DS" in DS-CNN

A normal convolution over `C_in` input channels producing `C_out` output channels with a `k × k`
kernel costs `k × k × C_in × C_out` parameters. With 64 in, 64 out, 3×3, that is 36,864 — for one
layer.

A **depthwise separable** convolution splits that single operation into two cheaper ones:

1. **Depthwise** — one `k × k` kernel *per input channel*, applied independently. It mixes across
   space (time and frequency) but never across channels. Cost: `k × k × C_in` = **576**.
2. **Pointwise** — a `1 × 1` convolution. It looks at a single position but all channels at once, so
   it mixes across channels but never across space. Cost: `C_in × C_out` = **4,096**.

Total 4,672 versus 36,864 — **7.9× fewer parameters**, and roughly the same reduction in
multiply-accumulates. The accuracy cost is small, because the two things a full convolution does
(spatial mixing, channel mixing) turn out to be largely separable in practice.

This is the core idea of MobileNet, and it is why an entire vision network fits in a phone. Here it
is why our model is 40 KB.

### 3.4 The architecture, layer by layer

From `build_ds_cnn` in [`../src/models.py`](../src/models.py):

```
Input (197, 40, 1)                              log-mel features
  │
  ├─ Conv2D 64, kernel (10,4), stride (2,2)     the "stem"
  ├─ BatchNormalization(momentum=0.9)
  └─ ReLU
  │                                             → (99, 20, 64)
  ├─ 4 × depthwise-separable block:
  │    DepthwiseConv2D (3,3) → BN → ReLU
  │    Conv2D 64 (1,1)       → BN → ReLU
  │                                             → (99, 20, 64), unchanged shape
  ├─ GlobalAveragePooling2D                     → (64,)
  ├─ Dropout(0.3)
  └─ Dense(num_classes, softmax, float32)       → (3,)
```

**The stem** is a normal (not separable) convolution, because at this point there is only 1 input
channel and the separable trick saves nothing. Its kernel is `(10, 4)` — **10 frames tall, 4 mel bins
wide**, i.e. deliberately asymmetric. 10 frames is 100 ms of audio, about the length of a phoneme;
4 mel bins is a narrow slice of pitch. Speech patterns are elongated in time, so the kernel is too.
This is a domain-specific choice, and it is one of the things that separates a speech CNN from an
image CNN where 3×3 is universal.

`stride: (2, 2)` means the kernel moves 2 steps at a time instead of 1, halving both dimensions
immediately: 197×40 → 99×20. That quarters the work every subsequent layer has to do.

**The blocks** keep the shape constant (stride 1, `padding: same`) and just deepen the representation:
block 1 sees simple patterns, block 4 sees combinations of combinations. Four blocks is a small
network by 2024 standards — appropriate for a few hundred training clips.

**`use_bias: false`** on every conv, because a BatchNormalization layer follows immediately and its
own shift parameter makes the convolution's bias mathematically redundant. Free parameter saving.

### 3.5 Batch normalisation — and the one setting you must not ignore

As data flows through layers, its scale drifts: layer 3's output might have standard deviation 0.01,
layer 7's might be 400. That makes training unstable. **BatchNormalization** re-centres and re-scales
the activations at each layer using the mean and variance of the current mini-batch.

At inference time there is no batch — you classify one clip. So BN keeps a **moving average** of the
mean and variance seen during training, and uses those instead. The moving average is updated as:

```
running = momentum × running + (1 − momentum) × batch_statistic
```

Keras defaults to `momentum = 0.99`, which needs *thousands* of update steps to converge from its
initial value.

> **This is the trap that eats a week.** With a few hundred clips at batch size 16, an epoch is
> ~20 optimiser steps. Even 300 epochs is only ~6,000 steps, and at 0.99 the moving statistics are
> still crawling towards the truth. Training accuracy looks fine — training mode uses the *batch*
> statistics, not the moving ones — and then the model **collapses at inference**, predicting one
> class for everything. The config sets **`bn_momentum: 0.9`**, which converges in ~50 steps. Raise
> it towards 0.99 only when the dataset is large.

### 3.6 ReLU, pooling, dropout

**ReLU** (`max(0, x)`) is the **activation function**: the non-linearity between layers. Without one,
stacking linear layers collapses algebraically into a single linear layer and depth buys nothing.
ReLU is chosen over fancier alternatives because it is one comparison, quantises cleanly to INT8, and
works.

**GlobalAveragePooling2D** takes the `(99, 20, 64)` feature map and averages each channel over all
positions, giving 64 numbers. This is the alternative to `Flatten`, which would give
99 × 20 × 64 = 126,720 numbers and require a vast final layer. GAP also makes the model
**length-agnostic in principle** and adds zero parameters. `model.pool: gap` — leave it.

**Dropout(0.3)** randomly zeroes 30% of those 64 numbers on each training step (and nothing at
inference). It sounds like vandalism; it is regularisation. It stops the network relying on any one
feature, forcing redundant representations, which generalise better. The config comments explain why
0.3 rather than the usual 0.2: with a small single-session dataset the network can memorise a clip
from a handful of frames, and the extra dropout costs almost nothing.

### 3.7 Softmax and the output layer

The final `Dense(num_classes)` produces one raw score (**logit**) per class. **Softmax** turns those
into a probability distribution:

```
softmax(z)_i = exp(z_i) / Σ_j exp(z_j)
```

Every output is positive and they sum to exactly 1.0. Note the consequence, already flagged in §0:
softmax *cannot* say "none of these". It always allocates the full 100%. Hence the `unknown` class.

`dtype="float32"` is pinned on this layer specifically. Training uses **mixed precision** (§4.6),
where most layers run in 16-bit for speed — but `exp()` in 16-bit overflows easily, and the TFLite
converter wants a clean float32 output tensor. Pinning just this one layer costs nothing.

### 3.8 Counting the parameters

Do this by hand once; it demystifies the whole thing.

| layer | arithmetic | parameters |
|---|---|---|
| `stem_conv` | 10 × 4 × 1 × 64 | 2,560 |
| `stem_bn` | 64 × 4 (γ, β, μ, σ²) | 256 |
| block × 4: depthwise | 3 × 3 × 64 | 576 |
| block × 4: dw BN | 64 × 4 | 256 |
| block × 4: pointwise | 1 × 1 × 64 × 64 | 4,096 |
| block × 4: pw BN | 64 × 4 | 256 |
| *(one block)* | | *5,184* |
| 4 blocks | 5,184 × 4 | 20,736 |
| `probabilities` | 64 × 3 + 3 | 195 |
| **total** | | **≈ 23,700** |

INT8-quantised (§7.2), one byte per weight plus overhead: **~40 KB**. Smaller than most PNG icons in
the app.

Two knobs scale this: `model.width_multiplier` (multiplies every filter count; 0.5 quarters the
parameters, since cost is roughly quadratic in width) and `model.blocks` (depth). Both should grow
only as the dataset grows.

---

## 4 · Training

### 4.1 The loss function

**Categorical cross-entropy.** If the true class has probability `p` under the model, the loss is
`−log(p)`. Predict 0.99 for the right class → loss 0.01. Predict 0.01 → loss 4.6. The `−log` shape
punishes confident mistakes savagely, which is what you want.

**`label_smoothing: 0.1`.** Instead of training towards the target `[0, 1, 0]`, we train towards
roughly `[0.05, 0.9, 0.05]`. Why deliberately aim at the wrong answer? Because `[0, 1, 0]` is only
reachable with infinite logits, so the optimiser pushes weights ever larger chasing it, and the model
becomes pathologically overconfident — 99.9% certain even when wrong. That matters enormously for us,
because at runtime we **threshold on confidence** (§8) to reject noise, and a threshold is meaningless
if the model says 99.9% about everything.

**`class_weights: true`.** If `unknown` has 400 clips and phrase 7 has 90, an unweighted model can
score 82% by always guessing `unknown`. Class weighting multiplies each clip's loss by
`total / (num_classes × class_count)`, so rare-class mistakes hurt proportionally more and the
laziest strategy stops being profitable.

### 4.2 Steps, not epochs — the second trap

An **epoch** is one pass over the training set. A **step** (or iteration) is one mini-batch: one
forward pass, one backward pass, one parameter update. Learning happens per *step*.

```
steps = ceil(train_clips / batch_size) × epochs
```

80 clips at `batch_size: 64` is 2 steps per epoch. A "60-epoch run" is then **120 gradient steps** —
a network initialised randomly has barely moved. You read the flat accuracy curve as "the model can't
learn this" when the truth is "the model has not been trained yet".

This is why the config looks unusual: **`batch_size: 16`, `epochs: 300`**. Small batches with many
epochs buy steps back. The training cell warns below 2,000 total steps. When the dataset reaches
thousands of clips, put the batch size back up to 64–128 and cut the epochs.

### 4.3 The optimiser

**`optimizer: adamw`**, `learning_rate: 1e-3`, `weight_decay: 1e-4`.

Adam adapts the step size per parameter using running estimates of the gradient's mean and variance —
it is the default choice for a reason. **AdamW** is Adam with *decoupled weight decay*: a gentle pull
of every weight towards zero on each step, which is a regulariser (small weights → simpler function →
better generalisation).

> **`adamw`, not `adam`, and this is not cosmetic.** Keras' plain `Adam` does not honour the
> `weight_decay` argument in the way you would expect — configure decay under `adam` and it is
> silently ignored. On a few-hundred-clip dataset that is a free regulariser thrown away with no
> error message. Under `adamw` it is actually applied.

**`gradient_clip_norm: 1.0`** rescales the gradient vector if its magnitude exceeds 1.0. One
pathological batch can otherwise produce a gradient large enough to blow the weights to NaN, and
there is no recovering from NaN.

### 4.4 Learning-rate schedule

The learning rate is how far each step moves. Too large and training oscillates or diverges; too
small and it never arrives. The best value changes over the run, so we schedule it.

- **`warmup_epochs: 5`** — start near zero and ramp up. The very first steps are taken from random
  weights, where gradients are large and meaningless; a full-size step there can wreck the run.
  Warmup is counted in *epochs* and converted to steps, so a small dataset needs more warmup epochs
  to buy the same number of warmup steps (§4.2 again).
- **`lr_schedule: cosine`** — after warmup, decay smoothly along a cosine curve from `1e-3` down to
  `min_learning_rate: 1e-5`. Big confident steps early to find the right basin; tiny careful steps
  late to settle into its bottom.

### 4.5 Knowing when to stop

**Early stopping** (`monitor: val_accuracy`, `patience: 40`, `restore_best_weights: true`) watches
accuracy on the validation split and stops when it has not improved for 40 epochs, then **restores
the weights from the best epoch**. Training past the optimum makes the model worse (§6.5), and this
is the standard defence.

Patience is in *epochs*, and epochs are tiny here — a patience of 5 tuned for a big-data run would
kill this one before it started. Hence 40.

**Checkpointing** (`save_best_only: true`) writes the weights to disk whenever validation accuracy
improves, so a Colab disconnect at epoch 250 does not cost you the run.

**`resume: true`** uses Keras' `BackupAndRestore`: re-running the training cell continues from where
it stopped — weights, optimiser state *and* epoch counter.

> **The resume trap.** Because resume restores everything, changing a config value and re-running
> applies your change *on top of the old model*, and you will not notice. `Trainer._check_resume_compatibility`
> ([`../src/trainer.py`](../src/trainer.py)) defends this: a changed `classes.include_phrases` **raises**
> (the output width moved — the old weights are structurally incompatible), and drift in any other
> resume-sensitive section logs a WARNING naming each changed key. After a config change, set
> `FRESH_START = True`.

### 4.6 Mixed precision

`mixed_precision: true` (honoured only when a GPU is present) runs most layers in 16-bit floats
instead of 32-bit. Half the memory traffic and, on modern GPUs, dedicated hardware for 16-bit matrix
maths — often ~2× faster. Numerically-sensitive parts (the loss, the softmax output) stay in float32.
On CPU it is a no-op.

---

## 5 · Data discipline

The model is the easy part. This section is where projects actually succeed or fail.

### 5.1 Three splits, and why

| split | ratio | used for |
|---|---|---|
| train | 75% | computing gradients — the model sees these constantly |
| validation | `val_ratio: 0.15` | choosing when to stop, which checkpoint to keep |
| test | `test_ratio: 0.10` | the final honest number, looked at **once** |

Accuracy on the training set is not evidence of anything: a model with enough capacity can memorise
its training data perfectly and be useless on anything new. Validation measures generalisation.

But validation is not *fully* honest either, because we use it to make decisions (when to stop, which
checkpoint to keep). Make enough decisions against it and you start fitting the validation set too.
The test split is held back from every decision so it stays clean. `evaluation.split: test`.

**`stratified: true`** keeps each class's proportion the same in all three splits. Without it, random
chance can hand a rare class three validation clips and make its measured recall meaningless.

### 5.2 Leakage — the mistake that fakes 99% accuracy

**Leakage** is when information from the training set reaches the evaluation set, inflating the score
without any real ability.

Here it has a very specific form. Suppose Ahmad recorded 20 clips in one session. If 15 land in train
and 5 in validation, the model can learn *Ahmad's voice in that room on that phone* and score
brilliantly on those 5 — while being hopeless on a new speaker. The number is real; the ability is not.

`split.group_regex` exists to prevent this: give it a pattern that extracts a speaker/session token
from the filename and whole groups are kept together on one side of the split.

> **It is currently `null` — grouping is off.** So the printed validation accuracy is **optimistic**;
> assume the real-world number is lower. This is a known, documented limitation, not an oversight.
> The moment filenames carry a speaker token, turn it on. The exact-ratio cost is worth it.

### 5.3 Augmentation

We have hundreds of clips and want thousands. **Augmentation** manufactures variety by distorting
training clips in ways that change the audio but not its label — a phrase spoken slightly faster, with
a car passing, on a tinnier microphone, is still that phrase. The model sees a different version every
epoch and cannot memorise any single one.

Applied **on the fly** (fresh randomness each epoch, no disk cost) and **only to the training split** —
augmenting validation or test would make the score measure something nobody will ever experience.

| augmentation | probability | range | simulates |
|---|---|---|---|
| background noise | 0.5 | SNR 5–25 dB | a room, traffic, a fan |
| pitch shift | 0.3 | ±2 semitones | different voices |
| speed perturb | 0.3 | 0.90–1.10× | speaking faster or slower |
| gain | 0.5 | ±6 dB | distance from the microphone |
| time shift | 0.5 | ±150 ms | phrase not centred in the window |
| SpecAugment | 0.5 | 1 freq mask ≤6 bins, 2 time masks ≤12 frames | — |

**SNR** (signal-to-noise ratio) in dB: 25 dB is a quiet room, 5 dB is genuinely noisy. If no noise
corpus is provided, `synthetic_when_missing: true` generates noise instead.

**SpecAugment** is the odd one — it operates on the *spectrogram*, not the audio, blacking out random
horizontal stripes (frequency bands) and vertical stripes (time slices). It forces the model to
identify a phrase from partial evidence, so it cannot hinge everything on one frequency band or one
instant. It is nearly free and it works remarkably well.

**Time shift matters more than it looks.** At runtime a sliding window will catch the phrase at every
possible alignment (§8), so the model must be robust to a phrase that starts 150 ms in. Training only
on trimmed, centred clips would produce a model that fails on exactly that.

---

## 6 · Reading the results

### 6.1 Accuracy, and why it is not enough

Accuracy is `correct / total`. Two problems:

- With 3 classes, **chance is 33%**, not 0%. With 10 classes it is 10%. "60% accuracy" means nothing
  until you know how many classes there are.
- It hides *which* mistakes. 90% accuracy where the 10% is "phrase 6 confused with phrase 7" is a
  different problem from 90% where the 10% is "noise classified as a phrase".

### 6.2 Per-class metrics

For one class:

- **Precision** = of everything the model *called* this class, what fraction really was? Low precision
  = false alarms. Directly, this is a counter that counts dhikr the user never said.
- **Recall** = of everything that really *was* this class, what fraction did the model find? Low
  recall = missed dhikr the user did say.
- **F1** = their harmonic mean, a single number when you need one.

Precision and recall trade off against each other, and the `evaluation.confidence_threshold: 0.5`
knob is the dial: raise it and precision rises while recall falls. Which side to err on is a product
decision — for a dhikr counter, under-counting a real dhikr is more acceptable than inventing one.

**Macro-average** averages the per-class metrics with equal weight, so a rare class counts as much as
a common one. **Micro-average** weights by class size and lets the biggest class dominate. With
imbalanced classes, read macro.

### 6.3 The confusion matrix

An `N × N` grid: rows are truth, columns are predictions, cell `(i, j)` is how many clips of class `i`
were called class `j`. The diagonal is correct. Everything off-diagonal is a specific, nameable,
fixable mistake, and `evaluation.top_k_confusions: 15` lists the worst pairs.

This is the single most informative artefact of an evaluation run. In our project you should expect
the heaviest off-diagonal cell to be phrase 6 ↔ phrase 7 (§9.1).

### 6.4 ROC, AUC and average precision

Fix a class, sweep the decision threshold from 1.0 down to 0.0, and plot the true-positive rate
against the false-positive rate. That curve is the **ROC**; its area is the **AUC**.

AUC has a lovely interpretation: it is the probability that a random positive example is scored
higher than a random negative one. 1.0 is perfect, 0.5 is a coin flip. Its value here is that it is
**threshold-free** — it measures how well the model *ranks*, separating "the model cannot tell these
apart" from "the model can, but 0.5 is the wrong threshold". **Average precision (AP)** is the
equivalent summary of the precision-recall curve, and it is the more honest one when classes are
imbalanced.

### 6.5 Diagnosing the two classic failures

**Collapse.** Accuracy sits at exactly `1 / num_classes` and the model predicts one class for
everything. This is not a weak model, it is a *broken* one — usually the BatchNorm momentum issue
(§3.5), a learning rate blown up, or a genuine data bug. `Metrics.collapsed_to` detects it and the
notebook flags it. The diagnostic is section 6b's sanity check: try to memorise ~40 unaugmented clips.
If the model cannot overfit 40 clips, the bug is in the pipeline, not in the amount of data.

**Overfitting.** Training accuracy reaches 1.0 while validation plateaus well below. The model has
memorised its training clips rather than learning the phrases. `artifacts.diagnose()` says so
explicitly, and also prints the epoch validation peaked at and *how many accuracy points one
validation clip is worth* — with 60 validation clips one clip is 1.7 points, so a "3-point
improvement" is two clips and probably noise.

> **`summary()` prints two numbers that are two different models.** `best accuracy` is the best
> *training* epoch; `best val_accuracy` is the restored checkpoint. Comparing them tells you nothing.
> The `restored weights` line shows the train/val pair *from the same epoch* — the model that actually
> shipped — and it is the only gap that means anything.

In order of effectiveness, the fixes are: **more recordings from more speakers** (always first),
stronger augmentation, `model.width_multiplier: 0.5`, more dropout, more weight decay.

### 6.6 Confidence intervals

`src/metrics.py` reports a **Wilson interval** next to every accuracy. With 60 test clips, an 85%
accuracy has a 95% interval of roughly 74–92%. So a model scoring 85% and one scoring 80% on 60 clips
are **not distinguishable** — the intervals overlap almost entirely.

Internalise this early. Most of the "improvements" you will celebrate on a small test set are noise,
and a project that chases noise for a month is a project that wasted a month.

---

## 7 · Shipping it to a phone

### 7.1 SavedModel → TFLite

The trained Keras model is a Python object needing the full TensorFlow runtime — hundreds of MB, and
it will not run on Android. **TensorFlow Lite** (now LiteRT) is a compact interpreter for a
serialised graph in a `.tflite` **flatbuffer** file: a few MB of runtime, no Python.

The conversion also *optimises*: BatchNorm layers are folded into the convolutions that precede them
(the maths permits it, since both are affine at inference), so BN costs literally nothing on device.

### 7.2 Quantisation

Weights are trained as float32 — 4 bytes each. **Quantisation** stores them as int8 — 1 byte — by
recording a scale and zero-point per tensor and mapping the float range onto −128…127.

Three variants are exported:

| variant | weights | activations | size | notes |
|---|---|---|---|---|
| `dhikr_float32.tflite` | float32 | float32 | ~95 KB | the reference; compare against this |
| `dhikr_dynamic_range.tflite` | int8 | float32 | ~30 KB | weights quantised, no calibration needed |
| `dhikr_int8.tflite` | int8 | int8 | ~40 KB | fully integer — fastest, needs calibration |

Full INT8 needs to know the *range of the activations*, which depends on real data. So the converter
runs `export.representative_samples: 300` real training clips through the model and records the
ranges. Get this wrong (or skip it) and accuracy falls off a cliff.

Roughly 4× smaller and often 2–3× faster, because integer arithmetic is cheap and mobile CPUs have
dedicated integer paths. The accuracy cost is usually well under a point — but "usually" is not
"always", which is why:

**`verify_tolerance: 0.05`.** After conversion the pipeline runs the same clips through the float32
model and each quantised model and asserts the output probabilities never differ by more than 0.05.
This catches a broken conversion before it reaches users, and it is the difference between a pipeline
and a script.

**`benchmark_runs: 100`** times the model on the Colab CPU; `android_latency_factor: 3.0` scales that
to an *estimated* phone latency. It is a documented estimate, not a measurement — calibrate per device.

### 7.3 What an export contains

```
exports/
├── saved_model/               full TF format (not shipped to the app)
├── dhikr_float32.tflite
├── dhikr_dynamic_range.tflite
├── dhikr_int8.tflite          ← what the app ships
├── labels.txt                 class index → name, in training order
├── model_meta.json            every front-end parameter, metrics, benchmarks
├── mel_filterbank.json        the exact 40×257 mel matrix
└── history/
    └── 2026-08-07T12-04_p6-7+unk_acc0.91/   dated snapshot of this export
```

`archive_export` copies each published export into `history/<datetime>_<phrases>_<accuracy>/` so you
can always go back to the model that was shipped in March. The bulky `saved_model/` is excluded.

### 7.4 Why `model_meta.json` is the most important file there

The model consumes **features, not audio**. If Android computes its log-mel spectrogram with a hop of
12 ms instead of 10, or `center: true` instead of `false`, or a different mel normalisation, then it
is feeding the model inputs from a distribution it has never seen. The model does not crash. It just
quietly becomes bad, and you will spend a week blaming the training.

So the export dumps *every* front-end parameter into `model_meta.json`, and the exact filterbank
matrix into `mel_filterbank.json`. The consumer derives its front-end from those files rather than
from `config.yaml` — the Gradio Space in `space/` does exactly this, deliberately, so that a retuned
config can never silently mismatch an older exported model.

**Rule: the training front-end and the inference front-end must be identical, to the last decimal.**

---

## 8 · Counting at runtime

The classifier answers "what is this 2-second clip?". The app needs "how many dhikr are in this
30-second recording?". `space/inference.py` bridges the two.

### 8.1 Sliding window

Cut the recording into overlapping 2-second windows — the same 2 seconds the model was trained on —
stepping forward by a hop of a few hundred milliseconds, and classify each. A recording shorter than
one window is zero-padded; a leftover tail shorter than the hop is turned into a final window anchored
at the end rather than dropped.

Windows must overlap, for the same reason STFT frames do: a phrase straddling a window boundary would
be cut in half by both windows and recognised by neither.

Note that windows are **not** silence-trimmed. Trimming a window cut out of a continuous stream would
slide its contents relative to the model's expectation. Trimming applies to whole recordings only.

### 8.2 Threshold

A window whose top probability is below `threshold` (default 0.7 at runtime, distinct from the
`evaluation.confidence_threshold: 0.5` used in reports) is rejected as "not a dhikr". Higher threshold
= fewer false alarms, more misses.

**With no `unknown` class, the threshold is the only thing standing between the user and a count made
of noise** — and the model will not cooperate, because softmax with nothing to reject makes noise look
confident. This is why `space/inference.py` shows a warning banner for such models.

### 8.3 Counting runs, not windows

Here is the part that is not obvious.

A dhikr lasts ~1.5 s. With a 0.3 s hop, it is fully or partly inside **five or six** consecutive
windows, and the model confidently says "phrase 6" for all of them. Count windows and you count one
dhikr six times.

The obvious fix — a **refractory period**, "ignore new detections for 1 s after one fires" — is also
wrong. Any phrase longer than the refractory period gets split into two counts, and phrase 7
(`سبحان الله العظيم وبحمده`) is exactly such a phrase.

So the unit of counting is a **run**: a maximal sequence of consecutive above-threshold windows that
agree on the same class is **one** dhikr, however long it lasts. The refractory period is demoted to a
much narrower job — merging two runs separated by a brief dip (a breath, a stumble) that should not
have split the phrase. Gaps are measured in *window index*, not timestamp, so a single rejected window
in the middle of a run leaves no hole.

Once you see it, this design is obviously right; it is also the kind of thing you only discover by
watching a real recording get counted wrong.

---

## 9 · What makes *this* problem hard

### 9.1 Nested prefixes

Look at `phrases.json`:

```
1  سبحان الله
6  سبحان الله وبحمده              ⊃ 1
7  سبحان الله العظيم وبحمده        ⊃ 1
9  اللهم صل على محمد
10 اللهم صل وسلم على نبينا محمد    ⊃ 9 (roughly)
```

Phrase 1 is a **literal prefix** of phrases 6 and 7. The first ~0.8 seconds of all three are, acoustically,
the same audio. A model that hears only that prefix genuinely cannot tell them apart — the information
that separates them arrives later, and there is no clever architecture that recovers information that
has not been spoken yet.

This has consequences everywhere:

- The confusion matrix (§6.3) will always show 6 ↔ 7 as its heaviest off-diagonal cell.
- The sliding window (§8.1) will see windows containing only the shared prefix, and must handle them.
- It is why the current config trains on `[6, 7]` — the two *long* phrases — rather than throwing all
  ten at a small dataset at once.

### 9.2 "Why not one binary model per dhikr?"

This proposal recurs, and it sounds compelling: instead of one 3-class model, train N small yes/no
detectors, one per phrase. Add a phrase without retraining the others! Tune each threshold
independently!

The repository does not argue about it — it **measures** it. Section `06 · Experiment` in the notebook
runs [`../src/experiments.py`](../src/experiments.py), which trains one one-vs-rest model per phrase
and scores it against the multi-class model on the *same clips*, holding the manifest, splits,
architecture, augmentation, optimiser, seed and epoch budget fixed so that the approach is the only
difference.

It reports three things separately, because they can disagree:

1. **Per-phrase detection** — threshold-free AUC/AP (§6.4).
2. **Naming the right phrase** — both sides restricted to the phrase columns, so `unknown` cannot
   absorb a mistake and flatter one side.
3. **Staying quiet on `unknown`** — the same accept rule applied to both.

And it is built to be able to return a **tie**: Wilson intervals sit next to every accuracy, and an AUC
difference under 0.02 is reported as noise rather than a winner. An experiment that *cannot* say "no
difference" is not an experiment.

The expected outcome is that one-vs-rest **loses on the nested prefixes**, and §9.1 says why: softmax
learns 6 and 7 as *competing* outputs — pushing one up pushes the other down, which is exactly the
pressure needed to find the distinguishing tail. A binary "is this phrase 6?" detector never sees
phrase 7 as a label at all; it only sees it as one anonymous negative among many, and has no reason to
learn the fine distinction.

The runs land in `checkpoints/ovr_{phrase}/`, separate from the shipped run, and are never exported.

**The transferable lesson:** when someone proposes an architecture change, the answer is a controlled
experiment with confidence intervals, not an opinion.

### 9.3 A small dataset, and where it leads

Almost every unusual setting in `config.yaml` traces back to one fact: there are a few hundred clips,
from few speakers, often one session each.

| setting | normal | here | because |
|---|---|---|---|
| `batch_size` | 64–128 | 16 | buy optimiser steps (§4.2) |
| `epochs` | 30–50 | 300 | same |
| `bn_momentum` | 0.99 | 0.9 | converges in ~50 steps, not thousands (§3.5) |
| `dropout` | 0.2 | 0.3 | a clip is memorisable from a few frames (§3.6) |
| `optimizer` | adam | adamw | do not throw away weight decay (§4.3) |
| `patience` | 5–10 | 40 | epochs are tiny here (§4.5) |
| `include_phrases` | all 10 | `[6, 7]` | more clips per class, chance 33% not 10% |

Every one of these should be **reverted as the dataset grows**. They are compensations for scarcity,
not permanent truths, and leaving them in place on a 10,000-clip dataset would be its own mistake.

And the real fix is never a hyperparameter. It is **more recordings, from more speakers, in more
rooms**. The `SpeechCollector/` app and the app's Voice-dhikr settings screen exist precisely to
produce them — including the `unknown` folder, which the collector's last card fills by asking
volunteers for any ordinary non-dhikr word.

---

## 10 · Concept → file map

| To understand… | Read |
|---|---|
| every setting, with commentary | `configs/config.yaml` |
| typed config, `input_shape`, frame arithmetic | `src/config.py` |
| decode, trim, normalise, fit length | `src/audio.py` |
| scan folders, validate, split, `tf.data` pipeline | `src/dataset.py` |
| the six augmentations | `src/augmentation.py` |
| log-mel front-end + its portable metadata | `src/features.py` |
| the DS-CNN itself | `src/models.py` |
| seeds, schedules, callbacks, resume guard | `src/trainer.py` |
| accuracy / P / R / F1 / ROC / Wilson / collapse | `src/metrics.py` |
| the one-vs-rest experiment | `src/experiments.py` |
| SavedModel, TFLite, quantise, verify, archive | `src/export.py` |
| sliding window, threshold, run-based counting | `space/inference.py` |
| the pipeline, orchestrated | `notebooks/DhikrSpeech.ipynb` |
| operational how-to | `README.md` |

---

## 11 · Glossary

**Activation function** — the non-linearity between layers. Here, ReLU.
**AP (average precision)** — area under the precision-recall curve; the honest summary under class imbalance.
**AUC** — area under the ROC curve; probability a random positive outranks a random negative.
**Augmentation** — label-preserving distortions applied to training data to manufacture variety.
**Backpropagation** — the chain rule applied backwards through the network to get gradients.
**BatchNorm** — re-centre/re-scale activations per mini-batch; keeps a moving average for inference.
**Class weights** — per-class loss multipliers that stop a big class dominating.
**Confusion matrix** — truth × prediction grid; the diagonal is correct.
**Cross-entropy** — `−log(probability of the true class)`; the classification loss.
**dBFS** — decibels relative to digital full scale; 0 is the loudest, real audio is negative.
**Depthwise separable convolution** — spatial mixing then channel mixing, ~8× cheaper than full conv.
**Dropout** — randomly zero activations during training to prevent co-adaptation.
**Early stopping** — halt when validation stops improving; restore the best epoch.
**Epoch / step** — one pass over the data / one mini-batch update. Learning happens per step.
**Flatbuffer** — the serialisation format a `.tflite` file uses.
**GAP** — global average pooling; average each channel over all positions. Zero parameters.
**Gradient descent** — nudge each parameter down its gradient to reduce the loss.
**KWS** — keyword spotting; classify a short clip into a small fixed vocabulary.
**Label smoothing** — soften one-hot targets to curb overconfidence.
**Leakage** — evaluation data contaminated by training information; fakes high scores.
**Logit** — a raw pre-softmax score.
**Mel scale** — perceptual frequency warping; finer resolution low, coarser high.
**Mixed precision** — 16-bit compute for speed, 32-bit where numerics demand it.
**Nyquist frequency** — half the sample rate; the highest faithfully representable frequency.
**Open-set problem** — a closed-set classifier cannot say "none of these"; hence `unknown`.
**Overfitting** — memorising training data; train accuracy ≫ validation accuracy.
**Precision / recall** — false-alarm rate / miss rate, from opposite directions.
**Quantisation** — store weights (and optionally activations) as int8 instead of float32.
**Refractory period** — here, only a run-merging device, not a per-detection timer.
**ROC** — true-positive rate vs false-positive rate as the threshold sweeps.
**RMS** — root-mean-square amplitude; the loudness measure we normalise on.
**SNR** — signal-to-noise ratio in dB; higher is cleaner.
**Softmax** — turn logits into a probability distribution summing to 1.
**SpecAugment** — mask random frequency bands and time slices of the spectrogram.
**Spectrogram** — time × frequency × energy; audio as an image.
**STFT** — Fourier transform over short overlapping windows.
**Stratified split** — preserve each class's proportion across splits.
**Stride** — how far a kernel moves per step; stride 2 halves the output dimension.
**TFLite / LiteRT** — the compact on-device interpreter and its model format.
**Weight decay** — pull weights towards zero each step; a regulariser.
**Wilson interval** — a confidence interval for a proportion that behaves well on small samples.

---

## 12 · Exercises

Do these in order. Each one takes a few minutes and teaches something the prose cannot.

1. **Recompute 197 by hand** from `clip_seconds`, `sample_rate`, `n_fft` and `hop_ms`. Then set
   `clip_seconds: 1.5` and predict the new frame count before running anything.
2. **Recompute the parameter count** for `width_multiplier: 0.5`. Why does halving the width quarter
   the parameters rather than halve them?
3. **Plot a log-mel spectrogram** of one clip of phrase 6 and one of phrase 7 side by side. Find, with
   your eyes, the point in time where they stop being the same audio. That point is §9.1.
4. **Break it on purpose.** Set `bn_momentum: 0.99`, train, and watch the model collapse at inference
   while training accuracy looks healthy. Then set it back. You will never lose a week to this bug.
5. **Compute a Wilson interval** for your test accuracy. Then decide honestly whether your last
   "improvement" was larger than the interval.
6. **Run section `06 · Experiment`** and read its verdict. Whether it agrees with §9.2 or not, note
   how the tie condition is defined *before* the numbers arrive.
7. **Scan a recording where you deliberately say phrase 6 three times.** If the count is 15, re-read
   §8.3 and work out which of the two failure modes you are looking at.

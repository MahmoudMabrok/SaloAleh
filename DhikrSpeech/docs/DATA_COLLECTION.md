# Collecting recordings for a dhikr detector

This is the guide for whoever is gathering the audio. It is written for one question: **what do
we record next, and does it matter more than what we recorded last week?**

The short answer, and the whole ordering:

> **Speaker diversity beats clip count. Hard negatives beat general negatives. Both beat a
> bigger model, by a wide margin.**

Ten speakers × 20 clips teaches a detector far more than one speaker × 200. The second is 200
recordings of *one voice in one room on one microphone*, and a model trained on it learns that
voice, not the phrase — which is invisible in every number the pipeline prints until somebody
else tries it.

---

## What the model has to learn

The app loads **one model per dhikr**. Its only job:

| | |
|---|---|
| **TARGET** | the selected phrase, spoken completely |
| **UNKNOWN** | everything else |

"Everything else" includes the hard part. For target 007 (`سبحان الله العظيم وبحمده`), all of
these must produce **no count**:

```
سبحان الله                    a prefix
سبحان الله العظيم              a longer prefix
سبحان الله وبحمده              a different dhikr that shares most of the words
الله العظيم وبحمده             a suffix
سبحان الله العظيم و…           trailed off, interrupted, mispronounced
```

A model shown only the target and some room tone will happily fire on every line above: it never
had to learn where the phrase *ends*, only how it starts. Recordings of those near-misses are
what teach phrase completion, and there is no substitute for them.

**Do not manufacture them by cropping positives.** A crop carries the same voice, room,
microphone and level as the positive it came from, so the model can separate the two on cues
that will not exist on a phone — and it will look like it worked.

---

## Targets: how much, from whom

Per dhikr:

| | prototype | real-world |
|---|---|---|
| positive recordings | ≥ 100 | 200–500 |
| distinct speakers | ≥ 10 | 20+ |

Below the prototype column the pipeline still runs — experimenting on 40 clips is a legitimate
thing to do — but the dataset report prints `PROTOTYPE ONLY` and the readiness verdict refuses to
call it shippable. That is the intended behaviour, not an obstacle: the numbers a 40-clip dataset
produces describe those 40 clips.

Vary, in roughly this order of value:

1. **Speaker** — different people, first and foremost.
2. **Age and voice** — including male and female voices where available, and older speakers,
   whose pace and articulation differ most from the median volunteer.
3. **Speaking speed** — the same person saying it quickly, normally and slowly. A user counting
   on a tasbih says it faster than a user recording for a dataset.
4. **Distance and microphone** — phone at the mouth, phone on a table, phone in a pocket;
   different handsets.
5. **Room** — a bare room, a furnished one, a car, outdoors.
6. **Background** — some clips with a television, a fan, traffic or other people talking.
7. **Pronunciation** — regional differences, and the natural run-together of a phrase repeated
   many times.

One recording per file, 1–3 seconds, one complete phrase. WAV is preferred; FLAC, OGG, MP3 and
M4A are decoded too. Any sample rate — preprocessing resamples to 16 kHz mono.

### Name files so the speaker survives

The single most valuable piece of metadata:

```
dataset/007/spk03_007_012.wav
```

with

```yaml
split:
  speaker_regex: '^(?P<speaker>[^_]+)_'
```

or a `speakers.json` mapping file names to speaker ids (`split.speaker_metadata`).

Without it the pipeline cannot keep one voice inside one split, and prints **EVALUATION IS NOT
SPEAKER-INDEPENDENT** — which means the accuracy it reports is measured partly on voices it
trained on. Speaker ids cost nothing at recording time and cannot be recovered afterwards.

---

## Hard negatives: the highest-value recordings in the project

Per target, under `dataset/negatives/hard/<target id>/`.

For each target, write down its prefixes, its suffixes, and the other dhikr that share most of
its words — then record real people saying those. Examples:

| target | hard negatives to record |
|---|---|
| `007` سبحان الله العظيم وبحمده | سبحان الله · سبحان الله العظيم · سبحان الله وبحمده · الله العظيم وبحمده |
| `006` سبحان الله وبحمده | سبحان الله · وبحمده · سبحان الله العظيم · سبحان الله العظيم وبحمده |
| `010` اللهم صل وسلم على نبينا محمد | اللهم صل على محمد · اللهم صل · على نبينا محمد |

Plus, for every target: incomplete attempts, interrupted ones, and the phrase trailing off
mid-word.

Aim for **50–100 hard negatives per target from 10+ speakers** before treating a detector as
finished. They are weighted highest in `negative_sampling.weights` for a reason — one hard
negative teaches more than ten clips of room tone.

Note that `negatives/hard/006/` is *excluded* when training 007 by default: a near-miss recorded
to fool 006 may well be 007's complete phrase, and using it as a negative would teach 007 to
reject itself.

Also useful, and shared across targets: `dataset/negatives/partial_phrase/` for incomplete
utterances of any dhikr.

---

## The shared negative pool

Collected **once**, reused by every target, under `dataset/negatives/shared/`:

| folder | what to record | target |
|---|---|---|
| `general_speech/` | ordinary Arabic conversation, phone calls, reading aloud | 1–2 hours |
| `background_audio/` | television, radio, Quran recitation | 1–2 hours |
| `noise/` | street, traffic, kitchen, fan, room tone, footsteps | 1 hour |
| `silence/` | genuinely quiet rooms | 15 minutes |

Also drop the other dhikr in as themselves — `dataset/001/`, `dataset/006/` and so on are used
automatically as `other_dhikr` negatives for every target that is not them, at no extra cost.

Real recordings only. Synthetic noise is a fallback, not a substitute: the training stage prints
**REAL NOISE** or **SYNTHETIC FALLBACK**, and the second one means augmentation is teaching
robustness to a sound no phone ever hears.

The pool is expected to be much larger than the positives. That is fine — `negative_sampling`
caps how much of it any one run trains on and weights which parts survive the cut.

---

## Streaming recordings: the release-critical set

This is the set most projects skip, and the only one that measures what the user experiences.

```text
streaming_test/
├── audio/
│   ├── session_001.wav      someone repeating the dhikr, minutes at a time
│   ├── session_002.wav
│   ├── tv_arabic.wav        ZERO target phrases
│   ├── conversation.wav     ZERO target phrases
│   └── street.wav           ZERO target phrases
└── annotations.json
```

```json
[
  {"file": "session_001.wav", "target": "007",
   "events": [{"start": 12.3, "end": 14.1}, {"start": 19.0, "end": 20.8}]},
  {"file": "tv_arabic.wav", "target": "007", "category": "background_audio", "events": []}
]
```

**Sessions** — someone actually using the app: repeating the dhikr at their own pace, sometimes
quickly, with pauses, in a normal room. Mark the start and end of every repetition. These give
event recall and the duplicate rate.

**Negative-only recordings** — anything at all, as long as it contains **no** target phrase.
Television, a podcast, conversation, Quran recitation, other dhikr, a busy street, an empty room.
Every event detected in them is a false activation, and `category` attributes it.

These are the cheapest recordings in the project — leave a phone recording the television for an
hour — and they carry **false activations per hour**, the number the release decision is made on.
Aim for **2+ hours** of negative-only audio spread across categories, and **at least 100
annotated repetitions** across sessions. An annotation with no `target` field is shared material
and counts for every target's evaluation.

Without this set the pipeline still trains, exports and reports — and the readiness verdict says
`EXPERIMENTAL` no matter how good the clip accuracy is, because nothing has measured whether the
detector counts once per repetition.

---

## Priority order

When there is time for one more recording session, this is the order:

1. **More speakers** saying the target.
2. **More positive recordings**, from those new speakers.
3. **Hard negatives** — prefixes, suffixes, near-miss dhikr, incomplete attempts.
4. **Negative-only streaming audio** — an hour of television costs nothing to produce.
5. **Realistic shared negatives** — conversation, street, kitchen.
6. **Real background noise** for `noise/`, replacing the synthetic fallback.

What is *not* on this list: a bigger model. A network with more parameters than the dataset has
recordings will drive training accuracy to 1.0 and change nothing about how the detector behaves
on a stranger's voice. Insufficient data cannot be solved by architecture, and the readiness
verdict is built so that it cannot be hidden by one either.

---

## Checking as you go

```bash
python train.py --target 007 --stage dataset
```

Fast, needs no TensorFlow or GPU, and prints exactly what this target has:

```
TARGET 007  سبحان الله العظيم وبحمده

positives : 350 clips | 24 speakers | 11.7 min
negatives : 912 clips | 41 speakers | 30.4 min (2.6x positives)
  hard_negative       120 clips |    4.0 min
  partial_phrase       80 clips |    2.6 min
  other_dhikr         400 clips |   13.3 min
  general_speech      200 clips |    6.7 min
  noise               112 clips |    3.8 min

window    : positives run 1.42-2.31 s (median 1.78); p95 + 0.35 s margin
            suggests audio.clip_seconds = 2.60 (reported only - not applied)
```

plus the recommendations, ordered by how much they cost, and the speaker-split verification. Run
it after every collection session; it is the fastest way to see whether the last hour of
recording moved anything.

# Dhikr model runtime

Android-only runtime for the one-model-per-phrase `DhikrSpeech` export contract. It owns bundle
discovery, validation, audio capture, log-mel extraction, LiteRT inference, smoothing, and event
detection.

## Add a model

Copy each complete export bundle into the app assets using its zero-padded phrase ID:

```text
app/src/androidMain/assets/dhikr_models/007/
├── dhikr_007_float32.tflite  # exact file named by model.file in this export's metadata
├── model_metadata.json
└── mel_filterbank.json
```

The runtime reads the model filename from `model.file` in `model_metadata.json`. Copy that exact
file: it may be INT8 when quantisation passed verification, or float32 when INT8 was rejected. Do
not choose a variant only from its filename or rename individual files. A model whose SHA-256,
tensor geometry, phrase ID, or filterbank does not match its metadata is rejected.

## Challenge integration

```kotlin
val runner = DhikrModelRunner(context)
runner.startListening("007") // also accepts 7 or the exact phrase text

runner.events.collect { event ->
    // One event is one confirmed complete recitation.
    challengeViewModel.increment()
}
```

Call `stopListening()` when the screen leaves composition and `close()` when its owner is destroyed.
For already-captured 16 kHz mono PCM, call `load(phrase)` followed by `detect(samples)`.

## Runtime compatibility

Inference uses LiteRT's portable multithreaded CPU kernels. XNNPACK is intentionally disabled
because some ARM64 Android virtual CPUs terminate the process with `SIGILL` while XNNPACK allocates
the model tensors. This phrase model is small enough that the safer backend is the default on both
emulators and physical devices.

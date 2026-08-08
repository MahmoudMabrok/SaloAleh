"""Tests for the Android contract: metadata, parity assets, INT8 verification.

None of this needs TensorFlow. The metadata is assembled from a config and a
handful of measurements; the parity assets are audio and numpy; the quantisation
comparison operates on score vectors, so it is fed hand-built ones whose verdict
is known.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.audio import read_wav
from src.config import Config, DetectorConfig
from src.features import LogMelExtractor
from src.parity import (
    PARITY_AUDIO_NAME,
    PARITY_EXPECTED_NAME,
    PARITY_METADATA_NAME,
    build_parity_signal,
    compare_parity,
    write_parity_assets,
)
from src.streaming_eval import EventMetrics
from src.target_export import (
    build_target_metadata,
    compare_scores,
    compare_variants,
    model_filename,
    tensor_spec,
    write_target_labels,
    write_target_metadata,
)

TARGET_TEXT = "سبحان الله العظيم وبحمده"


@pytest.fixture
def config() -> Config:
    return Config().for_target(7)


@pytest.fixture
def extractor(config: Config) -> LogMelExtractor:
    return LogMelExtractor(config=config.features, sample_rate=config.audio.sample_rate)


# ---------------------------------------------------------------------------
# Metadata contract
# ---------------------------------------------------------------------------
def metadata(config: Config, **kwargs):
    defaults = dict(
        target_id=7,
        target_text=TARGET_TEXT,
        architecture="ds_cnn_tiny",
        frontend={"window": "hann_periodic", "mel_scale": "slaney"},
        detector=config.streaming.detector,
        model_file=model_filename(7, "int8"),
        parameter_count=23_456,
        file_size_bytes=98_765,
        sha256="a" * 64,
    )
    defaults.update(kwargs)
    return build_target_metadata(config, **defaults)


def test_metadata_names_the_target(config: Config) -> None:
    payload = metadata(config)
    assert payload["target_phrase_id"] == "007"
    assert payload["target_phrase_text"] == TARGET_TEXT
    assert payload["labels"] == ["unknown", "target"]
    assert payload["target_index"] == 1


def test_metadata_carries_every_frontend_parameter(config: Config) -> None:
    """Android must not need a single constant that is not in this file."""
    feature = metadata(config)["feature"]
    for key in (
        "n_mels",
        "window_ms",
        "hop_ms",
        "n_fft",
        "fmin",
        "fmax",
        "log_offset",
        "normalization",
        "input_shape",
    ):
        assert key in feature, key


def test_metadata_carries_the_window_geometry(config: Config) -> None:
    payload = metadata(config)
    assert payload["sample_rate"] == 16000
    assert payload["clip_samples"] == config.audio.clip_samples
    assert payload["window_seconds"] == pytest.approx(config.audio.clip_seconds)
    assert payload["hop_seconds"] == pytest.approx(config.streaming.hop_seconds)


def test_metadata_carries_the_detector_settings(config: Config) -> None:
    detection = metadata(config)["detection"]
    assert detection["activation_threshold"] == pytest.approx(0.70)
    assert detection["release_threshold"] == pytest.approx(0.40)
    assert detection["min_consecutive_hits"] == 2
    assert detection["release_windows"] == 2
    assert detection["cooldown_ms"] == pytest.approx(200.0)
    assert "smoothing" in detection


def test_metadata_is_target_specific(config: Config) -> None:
    """Parameters calibrated for 006 must not silently describe 007."""
    tuned = DetectorConfig(activation_threshold=0.88, release_threshold=0.5)
    payload = metadata(config, detector=tuned)
    assert payload["detection"]["activation_threshold"] == pytest.approx(0.88)


def test_metadata_carries_the_model_fingerprint(config: Config) -> None:
    model = metadata(config)["model"]
    assert model["file"] == "dhikr_007_int8.tflite"
    assert model["parameter_count"] == 23_456
    assert model["file_size_bytes"] == 98_765
    assert model["sha256"] == "a" * 64


def test_metadata_includes_the_measurements_it_was_signed_off_on(config: Config) -> None:
    payload = metadata(
        config,
        streaming=EventMetrics(
            expected=100, detected=99, correct=98, missed=2, false_events=1,
            duration_seconds=5 * 3600.0,
        ),
        dataset={"positive_speakers": 24, "positive_clips": 350, "negative_clips": 900},
        readiness={"status": "READY FOR DEVICE TEST"},
    )
    assert payload["streaming"]["false_activations_per_hour"] == pytest.approx(0.2)
    assert payload["streaming"]["precision"] == pytest.approx(0.9899, abs=1e-3)
    assert payload["dataset"]["positive_speakers"] == 24
    assert payload["readiness"]["status"] == "READY FOR DEVICE TEST"


def test_metadata_reflects_the_output_mode(config: Config) -> None:
    sigmoid = config.with_overrides({"target.output_mode": "sigmoid"})
    assert metadata(sigmoid)["output_mode"] == "sigmoid"


def test_metadata_round_trips_through_json(tmp_path: Path, config: Config) -> None:
    path = write_target_metadata(tmp_path / "model_metadata.json", metadata(config))
    reloaded = json.loads(path.read_text(encoding="utf-8"))
    assert reloaded["target_phrase_text"] == TARGET_TEXT  # Arabic survives the round trip


def test_labels_are_written_in_output_order(tmp_path: Path) -> None:
    path = write_target_labels(tmp_path / "labels.txt", 7, TARGET_TEXT)
    assert path.read_text(encoding="utf-8").split() == ["unknown", "target"]
    sidecar = json.loads(path.with_name("labels_target.json").read_text(encoding="utf-8"))
    assert sidecar["target_index"] == 1
    assert sidecar["target_phrase_id"] == "007"


def test_a_sigmoid_model_gets_one_label(tmp_path: Path) -> None:
    """One label per output: two labels on a one-output model make every consumer
    that checks `len(labels) == num_outputs` fall back to positional names."""
    path = write_target_labels(tmp_path / "labels.txt", 7, TARGET_TEXT, output_mode="sigmoid")
    assert path.read_text(encoding="utf-8").split() == ["target"]
    sidecar = json.loads(path.with_name("labels_target.json").read_text(encoding="utf-8"))
    assert sidecar["target_index"] == 0


def test_metadata_labels_follow_the_output_mode(config: Config) -> None:
    softmax = metadata(config)
    assert softmax["labels"] == ["unknown", "target"] and softmax["target_index"] == 1
    sigmoid = metadata(config.with_overrides({"target.output_mode": "sigmoid"}))
    assert sigmoid["labels"] == ["target"] and sigmoid["target_index"] == 0
    assert [entry["label"] for entry in sigmoid["classes"]] == ["target"]


def test_metadata_carries_the_legacy_frontend_blocks(config: Config) -> None:
    """space/ builds its front-end from `frontend`/`audio` and silently falls back
    to config.yaml without them - which is the mismatch the metadata prevents."""
    payload = metadata(config, frontend={"win_length": 480, "hop_length": 160, "mel_norm": "slaney"})
    assert payload["frontend"]["win_length"] == 480
    assert payload["audio"]["sample_rate"] == config.audio.sample_rate
    assert payload["audio"]["clip_seconds"] == pytest.approx(config.audio.clip_seconds)
    assert payload["audio"]["normalize"]["target_dbfs"] == pytest.approx(-20.0)


def test_model_filename_is_zero_padded() -> None:
    assert model_filename(7, "int8") == "dhikr_007_int8.tflite"
    assert model_filename(10, "float32") == "dhikr_010_float32.tflite"


# ---------------------------------------------------------------------------
# Tensor specs
# ---------------------------------------------------------------------------
def test_tensor_spec_reports_quantisation() -> None:
    spec = tensor_spec(
        {"name": "input", "shape": [1, 197, 40, 1], "dtype": np.int8, "quantization": (0.0235, -128)}
    )
    assert spec["dtype"] == "int8"
    assert spec["quantization"]["scale"] == pytest.approx(0.0235)
    assert spec["quantization"]["zero_point"] == -128
    assert spec["quantization"]["quantized"] is True


def test_tensor_spec_marks_a_float_tensor_unquantised() -> None:
    spec = tensor_spec({"shape": [1, 2], "dtype": np.float32, "quantization": (0.0, 0)})
    assert spec["quantization"]["quantized"] is False


# ---------------------------------------------------------------------------
# Front-end parity
# ---------------------------------------------------------------------------
def test_parity_assets_are_written(tmp_path: Path, config: Config, extractor) -> None:
    assets = write_parity_assets(tmp_path, extractor, config.audio)
    for name in (PARITY_AUDIO_NAME, PARITY_EXPECTED_NAME, PARITY_METADATA_NAME):
        assert (tmp_path / name).is_file()
    assert assets.shape == list(config.input_shape[:2])


def test_the_pipeline_reproduces_its_own_parity_assets(
    tmp_path: Path, config: Config, extractor
) -> None:
    write_parity_assets(tmp_path, extractor, config.audio)
    samples, _ = read_wav(tmp_path / PARITY_AUDIO_NAME)
    result = compare_parity(extractor(samples), tmp_path)
    assert result.passed
    assert result.max_delta == pytest.approx(0.0)


def test_parity_detects_a_wrong_frontend(tmp_path: Path, config: Config, extractor) -> None:
    write_parity_assets(tmp_path, extractor, config.audio)
    samples, _ = read_wav(tmp_path / PARITY_AUDIO_NAME)
    wrong = LogMelExtractor(
        config=config.with_overrides({"features.log_offset": 1e-2}).features,
        sample_rate=config.audio.sample_rate,
    )
    result = compare_parity(wrong(samples), tmp_path)
    assert not result.passed
    assert "FAIL" in result.summary()


def test_parity_detects_a_wrong_hop(tmp_path: Path, config: Config, extractor) -> None:
    """A different hop changes the frame count, which the shape check catches."""
    write_parity_assets(tmp_path, extractor, config.audio)
    samples, _ = read_wav(tmp_path / PARITY_AUDIO_NAME)
    wrong = LogMelExtractor(
        config=config.with_overrides({"features.hop_ms": 20.0}).features,
        sample_rate=config.audio.sample_rate,
    )
    result = compare_parity(wrong(samples), tmp_path)
    assert not result.shape_matches
    assert "hop_length" in result.summary()


def test_the_parity_signal_is_deterministic(config: Config) -> None:
    first = build_parity_signal(config.audio, seed=1337)
    again = build_parity_signal(config.audio, seed=1337)
    assert np.array_equal(first, again)
    assert first.size == config.audio.clip_samples


def test_parity_metadata_documents_the_recipe(tmp_path: Path, config: Config, extractor) -> None:
    write_parity_assets(tmp_path, extractor, config.audio)
    payload = json.loads((tmp_path / PARITY_METADATA_NAME).read_text(encoding="utf-8"))
    assert payload["expected"]["shape"] == list(config.input_shape[:2])
    assert payload["audio"]["encoding"] == "PCM16"
    assert len(payload["audio"]["sha256"]) == 64
    assert payload["frontend"]["mel_norm"] == "slaney"


# ---------------------------------------------------------------------------
# INT8 verification
# ---------------------------------------------------------------------------
def test_identical_scores_show_no_drift() -> None:
    scores = [0.1, 0.5, 0.9]
    drift = compare_scores(scores, scores, "positives", threshold=0.7)
    assert drift.max_delta == 0.0
    assert drift.disagreements == 0


def test_drift_across_the_threshold_is_a_disagreement() -> None:
    drift = compare_scores([0.72, 0.30], [0.68, 0.31], "hard_negatives", threshold=0.7)
    assert drift.disagreements == 1
    assert drift.max_delta == pytest.approx(0.04)


def test_mismatched_score_lengths_are_refused() -> None:
    with pytest.raises(ValueError, match="same clips"):
        compare_scores([0.1, 0.2], [0.1], "positives", threshold=0.5)


def quantization_report(candidate_shift: float, candidate_fa: float):
    reference = {
        "positives": [0.95, 0.90, 0.88],
        "hard_negatives": [0.30, 0.45, 0.20],
    }
    candidate = {
        "int8": {
            name: [value + candidate_shift for value in scores]
            for name, scores in reference.items()
        }
    }
    return compare_variants(
        reference,
        candidate,
        threshold=0.7,
        tolerance=0.05,
        max_fa_increase=0.10,
        reference_events=EventMetrics(
            expected=10, detected=10, correct=10, duration_seconds=3600.0
        ),
        candidate_events={
            "int8": EventMetrics(
                expected=10,
                detected=10 + int(candidate_fa),
                correct=10,
                false_events=int(candidate_fa),
                duration_seconds=3600.0,
            )
        },
        sizes={"int8": 40_000},
    )


def test_int8_is_accepted_when_it_is_faithful() -> None:
    report = quantization_report(candidate_shift=0.01, candidate_fa=0)
    assert report.comparisons[0].accepted
    assert report.recommended().variant == "int8"


def test_int8_is_rejected_when_it_drifts_too_far() -> None:
    report = quantization_report(candidate_shift=0.20, candidate_fa=0)
    variant = report.comparisons[0]
    assert not variant.accepted
    assert any("drift" in reason for reason in variant.reasons())
    assert report.recommended() is None
    assert "NO VARIANT PASSED" in report.summary()


def test_int8_is_rejected_when_it_adds_false_activations() -> None:
    """The release-critical case: same probabilities, more counts."""
    report = quantization_report(candidate_shift=0.01, candidate_fa=2)
    variant = report.comparisons[0]
    assert not variant.accepted
    assert any("false activations" in reason for reason in variant.reasons())
    assert variant.fa_per_hour_delta == pytest.approx(2.0)


def test_an_unmeasured_event_comparison_is_flagged_but_not_a_veto() -> None:
    report = compare_variants(
        {"positives": [0.9, 0.8]},
        {"int8": {"positives": [0.9, 0.8]}},
        threshold=0.7,
    )
    variant = report.comparisons[0]
    assert variant.accepted
    assert any("unmeasured" in reason for reason in variant.reasons())


def test_the_smallest_accepted_variant_is_recommended() -> None:
    reference = {"positives": [0.9, 0.8]}
    report = compare_variants(
        reference,
        {
            "float32": {"positives": [0.9, 0.8]},
            "int8": {"positives": [0.9, 0.81]},
        },
        threshold=0.7,
        sizes={"float32": 160_000, "int8": 44_000},
    )
    assert report.recommended().variant == "int8"


def test_a_rejected_int8_never_becomes_the_recommendation() -> None:
    report = compare_variants(
        {"positives": [0.9, 0.8]},
        {
            "float32": {"positives": [0.9, 0.8]},
            "int8": {"positives": [0.2, 0.1]},
        },
        threshold=0.7,
        tolerance=0.05,
        sizes={"float32": 160_000, "int8": 44_000},
    )
    assert report.recommended().variant == "float32"


def test_quantization_report_serialises() -> None:
    payload = json.loads(json.dumps(quantization_report(0.01, 0).to_dict()))
    assert payload["recommended"] == "int8"
    assert payload["variants"][0]["clip_sets"][0]["name"] in ("positives", "hard_negatives")

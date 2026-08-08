"""Tests for the Android contract and the front-end parity fixture.

Both files exist to catch a silent mismatch, so both fail silently when they are
themselves wrong. A metadata file missing the quantisation scale does not crash
the app - it produces a model fed garbage; a parity fixture whose expected tensor
was computed from a different conditioning path passes on Android while the real
front-end is wrong.

These tests need neither TensorFlow nor a model: the metadata is assembled from
the config and a tensor description, and the parity fixture is the pipeline's own
front-end run over a generated WAV.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.android import (
    METADATA_VERSION,
    build_model_metadata,
    parity_signal,
    sha256_file,
    tensor_spec,
    write_frontend_parity,
    write_model_metadata,
)
from src.config import Config
from src.features import LogMelExtractor

CLASS_NAMES = ["006", "007", "unknown"]


@pytest.fixture
def config():
    return Config()


@pytest.fixture
def extractor(config):
    return LogMelExtractor(config.features, config.audio.sample_rate)


def int8_detail():
    return {
        "name": "serving_default_log_mel:0",
        "shape": [1, 197, 40, 1],
        "dtype": np.int8,
        "quantization": (0.0234, -128),
    }


# ---------------------------------------------------------------------------
# Tensor specification
# ---------------------------------------------------------------------------
def test_tensor_spec_carries_the_quantisation_parameters():
    spec = tensor_spec(int8_detail())
    assert spec["dtype"] == "int8"
    assert spec["quantized"] is True
    assert spec["scale"] == pytest.approx(0.0234)
    assert spec["zero_point"] == -128
    assert spec["shape"] == [1, 197, 40, 1]


def test_a_float_tensor_is_marked_unquantised():
    spec = tensor_spec(
        {"name": "x", "shape": [1, 197, 40, 1], "dtype": np.float32, "quantization": (0.0, 0)}
    )
    assert spec["quantized"] is False


def test_an_absent_tensor_detail_is_empty_rather_than_wrong():
    assert tensor_spec(None) == {}


# ---------------------------------------------------------------------------
# The contract
# ---------------------------------------------------------------------------
def test_metadata_contains_everything_android_needs(config, extractor):
    payload = build_model_metadata(
        config,
        CLASS_NAMES,
        extractor.metadata(),
        tensors={"input": tensor_spec(int8_detail())},
        global_threshold=0.86,
        thresholds={"006": 0.82, "007": 0.90},
    )
    assert payload["metadata_version"] == METADATA_VERSION
    assert payload["labels"] == CLASS_NAMES
    assert payload["classes"][1] == {"index": 1, "label": "007"}
    assert payload["audio"]["sample_rate"] == 16000
    assert payload["audio"]["clip_samples"] == 32000
    assert payload["model"]["input_shape"] == list(config.input_shape)
    assert payload["streaming"]["hop_samples"] == 4000
    assert payload["streaming"]["window_samples"] == 32000
    assert payload["detector"]["confidence_threshold"] == pytest.approx(0.86)
    assert payload["detector"]["per_class_thresholds"] == {"006": 0.82, "007": 0.90}
    assert payload["detector"]["cooldown_ms"] == config.streaming.detector.cooldown_ms
    assert payload["detector"]["min_consecutive_hits"] >= 1
    assert payload["tensors"]["input"]["scale"] == pytest.approx(0.0234)
    assert payload["frontend"]["n_mels"] == config.features.n_mels


def test_uncalibrated_thresholds_are_labelled_as_such(config, extractor):
    payload = build_model_metadata(config, CLASS_NAMES, extractor.metadata())
    assert payload["detector"]["calibrated"] is False
    assert any("not calibrated" in warning for warning in payload["warnings"])


def test_a_calibrated_threshold_moves_the_release_threshold_with_it(config, extractor):
    """Hysteresis is a band, not an absolute. A calibrated activation of 0.9 with
    the configured release of 0.4 would be a much wider band than the one that was
    measured."""
    ratio = (
        config.streaming.detector.release_threshold
        / config.streaming.detector.confidence_threshold
    )
    payload = build_model_metadata(
        config, CLASS_NAMES, extractor.metadata(), global_threshold=0.90
    )
    detector = payload["detector"]
    assert detector["release_threshold"] == pytest.approx(0.90 * ratio, abs=1e-3)
    assert detector["release_threshold"] < detector["confidence_threshold"]
    assert "warnings" not in payload


def test_per_class_thresholds_for_unknown_classes_are_dropped(config, extractor):
    payload = build_model_metadata(
        config, CLASS_NAMES, extractor.metadata(), thresholds={"006": 0.8, "999": 0.9}
    )
    assert payload["detector"]["per_class_thresholds"] == {"006": 0.8}


def test_metadata_records_the_model_hash(config, extractor, tmp_path):
    model = tmp_path / "dhikr_int8.tflite"
    model.write_bytes(b"not really a flatbuffer")
    payload = build_model_metadata(
        config, CLASS_NAMES, extractor.metadata(), model_path=model
    )
    assert payload["model"]["file"] == "dhikr_int8.tflite"
    assert payload["model"]["sha256"] == sha256_file(model)
    assert len(payload["model"]["sha256"]) == 64


def test_metadata_is_written_as_readable_json(config, extractor, tmp_path):
    payload = build_model_metadata(config, CLASS_NAMES, extractor.metadata())
    path = write_model_metadata(tmp_path / "model_metadata.json", payload)
    assert json.loads(path.read_text(encoding="utf-8")) == payload


# ---------------------------------------------------------------------------
# Front-end parity
# ---------------------------------------------------------------------------
def test_parity_signal_is_deterministic(config):
    first = parity_signal(16000, 2.0, seed=7)
    second = parity_signal(16000, 2.0, seed=7)
    assert np.array_equal(first, second)
    assert first.size == 32000
    assert np.abs(first).max() <= 1.0


def test_parity_export_writes_audio_features_and_instructions(config, extractor, tmp_path):
    paths = write_frontend_parity(tmp_path, extractor, config)
    assert set(paths) == {"audio", "features", "conditioned", "metadata"}
    for path in paths.values():
        assert path.is_file()

    features = np.load(paths["features"])
    assert features.shape == config.input_shape[:2]
    assert features.dtype == np.float32
    assert np.isfinite(features).all()

    metadata = json.loads(paths["metadata"].read_text(encoding="utf-8"))
    steps = [item["step"] for item in metadata["pipeline"]]
    assert steps == [
        "decode",
        "normalize",
        "trim",
        "fit_length",
        "stft",
        "mel",
        "log",
        "transpose",
        "normalize_features",
    ]
    # Trimming must be documented as OFF for stream windows: it slides the window
    # content in time and destroys every event timestamp.
    trim = next(item for item in metadata["pipeline"] if item["step"] == "trim")
    assert trim["enabled"] is False
    assert metadata["expected"]["shape"] == list(features.shape)
    assert metadata["tolerance"]["max_absolute_difference"] > 0


def test_the_expected_tensor_is_reproducible_from_the_exported_wav(config, extractor, tmp_path):
    """The whole point: Android reads the WAV and must arrive at this tensor.

    The reference has to start from the audio *as decoded from the file*, not
    from the float array in memory, or the fixture bakes in a PCM16 rounding
    difference nobody can explain.
    """
    from src.audio import fit_length, normalize_loudness, read_wav

    paths = write_frontend_parity(tmp_path, extractor, config)
    expected = np.load(paths["features"])

    decoded, sample_rate = read_wav(paths["audio"])
    assert sample_rate == config.audio.sample_rate
    conditioned = normalize_loudness(
        decoded,
        config.audio.normalize.target_dbfs,
        config.audio.normalize.peak_ceiling,
    )
    conditioned = fit_length(conditioned, config.audio.clip_samples, config.audio.fit_mode)
    assert np.allclose(conditioned, np.load(paths["conditioned"]), atol=1e-6)
    assert np.allclose(extractor(conditioned), expected, atol=1e-5)


def test_the_checksum_matches_the_exported_tensor(config, extractor, tmp_path):
    import hashlib

    paths = write_frontend_parity(tmp_path, extractor, config)
    features = np.load(paths["features"])
    metadata = json.loads(paths["metadata"].read_text(encoding="utf-8"))
    digest = hashlib.sha256(
        np.ascontiguousarray(features, dtype=np.float32).tobytes()
    ).hexdigest()
    assert metadata["expected"]["checksum_sha256"] == digest

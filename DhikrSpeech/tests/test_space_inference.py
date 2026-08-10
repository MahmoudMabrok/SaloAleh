"""Regression tests for the per-phrase Hugging Face Space contract."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from types import SimpleNamespace

import numpy as np
import pytest

SPACE_DIR = Path(__file__).resolve().parents[1] / "space"
sys.path.insert(0, str(SPACE_DIR))

from inference import (  # noqa: E402
    DhikrModel,
    ScanResult,
    count_target_detections,
    discover_models,
    export_descriptor,
    resolve_config,
)


class FakeInterpreter:
    def __init__(self, output: np.ndarray):
        self.output = np.asarray(output)
        self.input = None

    def get_input_details(self):
        return [
            {
                "index": 0,
                "shape": np.asarray([1, 2, 2, 1]),
                "dtype": np.float32,
                "quantization": (0.0, 0),
            }
        ]

    def get_output_details(self):
        return [
            {
                "index": 1,
                "shape": np.asarray(self.output.shape),
                "dtype": self.output.dtype,
                "quantization": (0.0, 0),
            }
        ]

    def set_tensor(self, _index, value):
        self.input = value

    def invoke(self):
        return None

    def get_tensor(self, _index):
        return self.output


def fake_model(
    tmp_path: Path,
    *,
    output: np.ndarray,
    labels,
    meta,
    config=None,
) -> DhikrModel:
    path = tmp_path / "dhikr_007_int8.tflite"
    path.write_bytes(b"model")
    frontend = SimpleNamespace(
        config=config or resolve_config(meta),
        clip_seconds=1.0,
    )
    return DhikrModel(
        path=path,
        labels=list(labels),
        frontend=frontend,
        meta=dict(meta),
        phrases={7: "سبحان الله العظيم وبحمده"},
        _interpreter=FakeInterpreter(output),
    )


def test_export_descriptor_names_the_phrase_variant_and_recommendation(tmp_path: Path) -> None:
    model_dir = tmp_path / "007"
    model_dir.mkdir()
    model_path = model_dir / "dhikr_007_int8.tflite"
    model_path.write_bytes(b"model")
    (model_dir / "model_metadata.json").write_text(
        json.dumps(
            {
                "target_phrase_id": "007",
                "target_phrase_text": "سبحان الله العظيم وبحمده",
                "quantization": {"recommended": "int8"},
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    descriptor = export_descriptor(model_path)

    assert descriptor["target_id"] == 7
    assert descriptor["target_name"] == "007 · سبحان الله العظيم وبحمده"
    assert descriptor["variant"] == "int8"
    assert descriptor["recommended"] is True


def test_resolve_config_applies_the_exported_target_and_detector() -> None:
    config = resolve_config(
        {
            "target_phrase_id": "007",
            "output_mode": "sigmoid",
            "hop_seconds": 0.15,
            "detection": {
                "activation_threshold": 0.82,
                "release_threshold": 0.35,
                "min_consecutive_hits": 3,
                "release_windows": 2,
                "cooldown_ms": 150,
                "smoothing": {"mode": "ema", "ema_alpha": 0.8, "window": 3},
            },
        }
    )

    assert config.target.phrase_id == 7
    assert config.target.output_mode == "sigmoid"
    assert config.streaming.hop_seconds == pytest.approx(0.15)
    assert config.streaming.detector.activation_threshold == pytest.approx(0.82)
    assert config.streaming.detector.release_threshold == pytest.approx(0.35)
    assert config.streaming.detector.min_consecutive_hits == 3
    assert config.streaming.smoothing.mode == "ema"


def test_sigmoid_target_probability_is_not_normalized_to_one(tmp_path: Path) -> None:
    model = fake_model(
        tmp_path,
        output=np.asarray([[0.23]], dtype=np.float32),
        labels=["target"],
        meta={"target_phrase_id": "007", "output_mode": "sigmoid", "target_index": 0},
    )

    probabilities = model.predict(np.zeros((1, 2, 2), dtype=np.float32))

    assert probabilities.shape == (1, 1)
    assert probabilities[0, 0] == pytest.approx(0.23)


def test_per_target_counter_uses_hysteresis_for_two_repetitions(tmp_path: Path) -> None:
    meta = {
        "target_phrase_id": "007",
        "target_phrase_text": "سبحان الله العظيم وبحمده",
        "output_mode": "softmax",
        "target_index": 1,
        "detection": {
            "activation_threshold": 0.7,
            "release_threshold": 0.4,
            "min_consecutive_hits": 2,
            "release_windows": 2,
            "cooldown_ms": 0,
        },
    }
    config = resolve_config(meta)
    model = fake_model(
        tmp_path,
        output=np.asarray([[0.1, 0.9]], dtype=np.float32),
        labels=["unknown", "target"],
        meta=meta,
        config=config,
    )
    target_scores = np.asarray([0.9, 0.9, 0.1, 0.1, 0.92, 0.93, 0.1, 0.1], dtype=np.float32)
    scan = ScanResult(
        times=np.arange(target_scores.size, dtype=np.float32) * 0.2,
        probabilities=np.column_stack([1.0 - target_scores, target_scores]),
        labels=["unknown", "target"],
    )

    detections, counts = count_target_detections(scan, model)

    assert len(detections) == 2
    assert counts == {"007 · سبحان الله العظيم وبحمده": 2}


def test_per_target_counter_reports_zero_for_the_selected_phrase(tmp_path: Path) -> None:
    meta = {
        "target_phrase_id": "007",
        "target_phrase_text": "سبحان الله العظيم وبحمده",
        "target_index": 0,
        "detection": {
            "activation_threshold": 0.7,
            "release_threshold": 0.4,
            "min_consecutive_hits": 2,
            "release_windows": 2,
            "cooldown_ms": 0,
        },
    }
    model = fake_model(
        tmp_path,
        output=np.asarray([[0.1]], dtype=np.float32),
        labels=["target"],
        meta=meta,
    )
    scan = ScanResult(
        times=np.arange(4, dtype=np.float32) * 0.2,
        probabilities=np.full((4, 1), 0.1, dtype=np.float32),
        labels=["target"],
    )

    detections, counts = count_target_detections(scan, model)

    assert detections == []
    assert counts == {"007 · سبحان الله العظيم وبحمده": 0}


def test_recursive_discovery_finds_every_phrase_export(tmp_path: Path) -> None:
    for target in ("006", "007"):
        folder = tmp_path / target
        folder.mkdir()
        (folder / f"dhikr_{target}_int8.tflite").write_bytes(b"model")

    assert [path.parent.name for path in discover_models(tmp_path, recursive=True)] == [
        "006",
        "007",
    ]

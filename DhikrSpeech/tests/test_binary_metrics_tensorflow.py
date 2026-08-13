"""TensorFlow-backed checks for the serializable epoch-level binary F1 metric."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

tf = pytest.importorskip("tensorflow")

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.trainer import TargetF1  # noqa: E402


def test_target_f1_accumulates_counts_across_batches_and_serializes() -> None:
    metric = TargetF1(threshold=0.5)
    metric.update_state([1, 1, 0], [[0.9], [0.1], [0.8]])  # TP=1 FP=1 FN=1
    metric.update_state([1, 0], [[0.7], [0.2]])             # TP=2 FP=1 FN=1
    assert float(metric.result()) == pytest.approx(2 * 2 / (2 * 2 + 1 + 1))

    serialized = tf.keras.metrics.serialize(metric)
    restored = tf.keras.metrics.deserialize(serialized)
    assert isinstance(restored, TargetF1)
    assert restored.get_config()["threshold"] == pytest.approx(0.5)

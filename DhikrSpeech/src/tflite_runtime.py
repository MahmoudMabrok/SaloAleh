"""TFLite / LiteRT runtime helpers shared by export, streaming, and the Space.

Prefer LiteRT so the Hugging Face Space can run without TensorFlow installed.
Quantisation / dequantisation must stay identical across notebook, export, and
on-device paths.
"""

from __future__ import annotations

from pathlib import Path
from typing import Union

import numpy as np

PathLike = Union[str, Path]

__all__ = [
    "dequantize_output",
    "make_interpreter",
    "quantize_input",
    "tflite_predict",
]


def make_interpreter(model_path: PathLike, num_threads: int = 1):
    """Create a TFLite interpreter. Prefers ``ai-edge-litert``, falls back to TF."""
    path = str(model_path)
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=path, num_threads=num_threads)
    except ImportError:
        pass
    try:
        import tensorflow as tf  # type: ignore
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError(
            "no TFLite runtime available — install 'ai-edge-litert' (preferred) "
            "or 'tensorflow'"
        ) from exc
    return tf.lite.Interpreter(model_path=path, num_threads=num_threads)


def quantize_input(array: np.ndarray, detail: dict) -> np.ndarray:
    """Convert a float32 array into the dtype / scale the interpreter expects."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return np.asarray(array, dtype=np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return np.asarray(array, dtype=dtype)
    info = np.iinfo(dtype)
    quantized = np.round(np.asarray(array, dtype=np.float32) / scale) + zero_point
    return np.clip(quantized, info.min, info.max).astype(dtype)


def dequantize_output(array: np.ndarray, detail: dict) -> np.ndarray:
    """Convert an interpreter output back to float32 probabilities."""
    dtype = detail["dtype"]
    if dtype == np.float32:
        return np.asarray(array, dtype=np.float32)
    scale, zero_point = detail["quantization"]
    if not scale:
        return np.asarray(array, dtype=np.float32)
    return (np.asarray(array, dtype=np.float32) - zero_point) * scale


def tflite_predict(interpreter, batch: np.ndarray) -> np.ndarray:
    """Run a feature batch through a TFLite interpreter with proper quantisation.

    Handles models whose input shape has batch=1 (the usual case) by looping,
    and models that accept a full batch in one invoke.
    """
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    batch = np.asarray(batch, dtype=np.float32)

    expected_rank = len(input_detail["shape"])
    if batch.ndim == expected_rank - 1:
        batch = batch[..., np.newaxis]

    expected_batch = int(input_detail["shape"][0])
    outputs: list = []

    if expected_batch == 1 or expected_batch == -1:
        for sample in batch:
            payload = quantize_input(sample[np.newaxis, ...], input_detail)
            interpreter.set_tensor(input_detail["index"], payload)
            interpreter.invoke()
            raw = interpreter.get_tensor(output_detail["index"])
            outputs.append(dequantize_output(raw, output_detail)[0])
        return np.stack(outputs, axis=0).astype(np.float32)

    payload = quantize_input(batch, input_detail)
    interpreter.set_tensor(input_detail["index"], payload)
    interpreter.invoke()
    raw = interpreter.get_tensor(output_detail["index"])
    return dequantize_output(raw, output_detail).astype(np.float32)

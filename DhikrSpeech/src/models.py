"""Model topologies.

DS-CNN (depthwise separable CNN) is the reference keyword-spotting architecture
from *Hello Edge: Keyword Spotting on Microcontrollers*; it is small, quantises
cleanly to INT8 and maps onto TFLite builtin ops with no Flex delegate.
"""

from __future__ import annotations

import logging
from typing import Callable, Dict, Sequence, Tuple

import tensorflow as tf

try:  # Keras 3 ships standalone and is what TensorFlow >= 2.16 uses
    import keras
except ImportError:  # pragma: no cover - TensorFlow 2.15 and older
    from tensorflow import keras

from .config import ModelConfig

LOGGER = logging.getLogger(__name__)

__all__ = ["MODEL_REGISTRY", "build_ds_cnn", "build_model", "model_summary_text"]


def _scaled(filters: int, multiplier: float) -> int:
    return max(int(round(filters * multiplier)), 8)


def build_ds_cnn(
    input_shape: Tuple[int, int, int], num_classes: int, config: ModelConfig
) -> keras.Model:
    """Conv stem followed by ``config.blocks`` depthwise separable blocks."""
    if num_classes < 2:
        raise ValueError("a classifier needs at least two classes")

    stem_filters = _scaled(config.stem_filters, config.width_multiplier)
    block_filters = _scaled(config.block_filters, config.width_multiplier)

    inputs = keras.Input(shape=input_shape, name="log_mel")

    x = keras.layers.Conv2D(
        filters=stem_filters,
        kernel_size=tuple(config.stem_kernel),
        strides=tuple(config.stem_stride),
        padding="same",
        use_bias=config.use_bias,
        name="stem_conv",
    )(inputs)
    x = keras.layers.BatchNormalization(momentum=config.bn_momentum, name="stem_bn")(x)
    x = keras.layers.Activation(config.activation, name="stem_act")(x)

    for index in range(config.blocks):
        prefix = f"block{index + 1}"
        x = keras.layers.DepthwiseConv2D(
            kernel_size=tuple(config.block_kernel),
            strides=(1, 1),
            padding="same",
            use_bias=config.use_bias,
            name=f"{prefix}_dw",
        )(x)
        x = keras.layers.BatchNormalization(
            momentum=config.bn_momentum, name=f"{prefix}_dw_bn"
        )(x)
        x = keras.layers.Activation(config.activation, name=f"{prefix}_dw_act")(x)
        x = keras.layers.Conv2D(
            filters=block_filters,
            kernel_size=(1, 1),
            padding="same",
            use_bias=config.use_bias,
            name=f"{prefix}_pw",
        )(x)
        x = keras.layers.BatchNormalization(
            momentum=config.bn_momentum, name=f"{prefix}_pw_bn"
        )(x)
        x = keras.layers.Activation(config.activation, name=f"{prefix}_pw_act")(x)

    if config.pool == "gap":
        x = keras.layers.GlobalAveragePooling2D(name="pool")(x)
    else:
        x = keras.layers.Flatten(name="pool")(x)

    if config.dropout > 0.0:
        x = keras.layers.Dropout(config.dropout, name="dropout")(x)

    # float32 output keeps the softmax numerically safe under mixed precision and
    # gives the converter a clean, quantisable final tensor.
    outputs = keras.layers.Dense(
        num_classes, activation="softmax", dtype="float32", name="probabilities"
    )(x)

    return keras.Model(inputs=inputs, outputs=outputs, name="ds_cnn")


MODEL_REGISTRY: Dict[str, Callable[[Tuple[int, int, int], int, ModelConfig], keras.Model]] = {
    "ds_cnn": build_ds_cnn,
}


def build_model(
    input_shape: Sequence[int], num_classes: int, config: ModelConfig
) -> keras.Model:
    """Instantiate the topology named by ``model.name``."""
    if config.name not in MODEL_REGISTRY:
        known = ", ".join(sorted(MODEL_REGISTRY))
        raise ValueError(f"unknown model '{config.name}'; available: {known}")
    shape = tuple(int(value) for value in input_shape)
    if len(shape) != 3:
        raise ValueError(f"input_shape must be (frames, mels, channels), got {shape}")
    model = MODEL_REGISTRY[config.name](shape, int(num_classes), config)
    LOGGER.info(
        "built %s: input %s -> %d classes, %s parameters",
        config.name,
        shape,
        num_classes,
        f"{model.count_params():,}",
    )
    return model


def model_summary_text(model: keras.Model) -> str:
    """``model.summary()`` as a string, for writing into a report."""
    lines: list = []
    model.summary(print_fn=lines.append)
    return "\n".join(lines)


def _shape_of(tensor) -> Tuple:
    """Static shape as a plain tuple, tolerating Keras 2 and Keras 3 tensors."""
    try:
        return tuple(int(dim) if dim is not None else 0 for dim in tuple(tensor.shape))
    except Exception:  # noqa: BLE001 - unbuilt or symbolic layers have no shape
        return ()


def estimate_flops(model: keras.Model) -> int:
    """Rough forward-pass FLOP count, useful when comparing width multipliers.

    Counts multiply-accumulates in Conv2D / DepthwiseConv2D / Dense layers only,
    which dominate this topology. Unmeasurable layers contribute 0.
    """
    total = 0
    for layer in model.layers:
        output_shape = _shape_of(getattr(layer, "output", None))
        input_shape = _shape_of(getattr(layer, "input", None))
        if len(output_shape) < 2:
            continue
        channels_out = output_shape[-1]
        positions = 1
        for dim in output_shape[1:-1]:
            positions *= max(dim, 1)

        if isinstance(layer, keras.layers.DepthwiseConv2D):
            kernel = layer.kernel_size[0] * layer.kernel_size[1]
            total += 2 * positions * channels_out * kernel
        elif isinstance(layer, keras.layers.Conv2D):
            kernel = layer.kernel_size[0] * layer.kernel_size[1]
            channels_in = input_shape[-1] if input_shape else 0
            total += 2 * positions * channels_out * channels_in * kernel
        elif isinstance(layer, keras.layers.Dense):
            units_in = input_shape[-1] if input_shape else 0
            total += 2 * units_in * channels_out
    return int(total)


def gpu_available() -> bool:
    return bool(tf.config.list_physical_devices("GPU"))

"""Model topologies.

DS-CNN (depthwise separable CNN) is the reference keyword-spotting architecture
from *Hello Edge: Keyword Spotting on Microcontrollers*; it is small, quantises
cleanly to INT8 and maps onto TFLite builtin ops with no Flex delegate. It stays
the baseline.

Single-target detection is a much easier problem than 10-way classification, so
two smaller options sit next to it:

``ds_cnn_tiny``
    the same topology with fewer, narrower blocks - a preset, not a new
    architecture, so a comparison against ``ds_cnn`` isolates capacity.
``tc_resnet8``
    *Temporal Convolution ResNet* (Choi et al., 2019). The mel axis becomes the
    channel axis and convolution runs along time only, which is what makes it a
    fraction of the multiply-accumulates at comparable accuracy on short
    keywords.

BC-ResNet is deliberately **not** implemented. Its broadcasting block needs a
sub-spectral pooling / broadcast-add pattern that only converts cleanly with
care, and requirement 12 says not to buy that complexity blind. Add it as a
registry entry when there is a measurement asking for it.

Every architecture consumes the same ``(frames, n_mels, 1)`` front-end and the
same head, so they can be swapped without touching the data pipeline.
"""

from __future__ import annotations

import logging
from typing import Callable, Dict, List, Sequence, Tuple

import tensorflow as tf

try:  # Keras 3 ships standalone and is what TensorFlow >= 2.16 uses
    import keras
except ImportError:  # pragma: no cover - TensorFlow 2.15 and older
    from tensorflow import keras

from .config import ModelConfig

LOGGER = logging.getLogger(__name__)

__all__ = [
    "MODEL_REGISTRY",
    "build_ds_cnn",
    "build_model",
    "build_tc_resnet8",
    "model_summary_text",
]


def _scaled(filters: int, multiplier: float) -> int:
    return max(int(round(filters * multiplier)), 8)


def _classifier_head(x, num_outputs: int, config: ModelConfig):
    """Pooling, dropout and the output layer shared by every architecture.

    One output is ``sigmoid`` P(target); two or more is ``softmax``. Both are
    kept float32 so mixed precision cannot destabilise the final activation and
    the converter gets a clean, quantisable output tensor.
    """
    if config.pool == "gap":
        x = keras.layers.GlobalAveragePooling2D(name="pool")(x)
    else:
        x = keras.layers.Flatten(name="pool")(x)

    if config.dropout > 0.0:
        x = keras.layers.Dropout(config.dropout, name="dropout")(x)

    if num_outputs == 1:
        return keras.layers.Dense(
            1, activation="sigmoid", dtype="float32", name="probability"
        )(x)
    return keras.layers.Dense(
        num_outputs, activation="softmax", dtype="float32", name="probabilities"
    )(x)


def build_ds_cnn(
    input_shape: Tuple[int, int, int], num_classes: int, config: ModelConfig
) -> keras.Model:
    """Conv stem followed by ``config.blocks`` depthwise separable blocks."""
    if num_classes < 1:
        raise ValueError("a model needs at least one output")

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

    outputs = _classifier_head(x, num_classes, config)
    return keras.Model(inputs=inputs, outputs=outputs, name=config.name or "ds_cnn")


def build_tc_resnet8(
    input_shape: Tuple[int, int, int], num_classes: int, config: ModelConfig
) -> keras.Model:
    """TC-ResNet8: residual blocks convolving along time, mel bins as channels.

    The reshape at the top is the whole idea. A 2-D CNN over a
    ``(frames, mels, 1)`` spectrogram slides a small kernel across both axes; TC-
    ResNet treats the ``mels`` axis as feature channels of a 1-D signal, so the
    very first layer already sees the entire spectrum at each time step. That
    removes most of the multiply-accumulates while keeping the receptive field
    over time, which is what a phrase spotter actually needs.

    Only Conv2D / BatchNorm / ReLU / Add / GlobalAveragePooling2D / Dense are
    used - all TFLite builtins, no Flex delegate, and all quantise to INT8.
    """
    if num_classes < 1:
        raise ValueError("a model needs at least one output")
    frames, mels, _ = input_shape
    channels: List[int] = [
        _scaled(int(value), config.width_multiplier) for value in config.tc_channels
    ]
    stem_channels, block_channels = channels[0], channels[1:]

    inputs = keras.Input(shape=input_shape, name="log_mel")
    # (frames, mels, 1) -> (frames, 1, mels): time stays the spatial axis, the
    # mel bins become channels.
    x = keras.layers.Reshape((frames, 1, mels), name="to_time_channels")(inputs)
    x = keras.layers.Conv2D(
        filters=stem_channels,
        kernel_size=(int(config.tc_stem_kernel), 1),
        padding="same",
        use_bias=config.use_bias,
        name="stem_conv",
    )(x)
    x = keras.layers.BatchNormalization(momentum=config.bn_momentum, name="stem_bn")(x)
    x = keras.layers.Activation(config.activation, name="stem_act")(x)

    kernel = (int(config.tc_kernel), 1)
    for index, filters in enumerate(block_channels):
        prefix = f"block{index + 1}"
        # Every block halves the time axis, so the receptive field grows
        # geometrically and the last block still sees the whole phrase.
        stride = (2, 1)
        residual = x
        x = keras.layers.Conv2D(
            filters=filters,
            kernel_size=kernel,
            strides=stride,
            padding="same",
            use_bias=config.use_bias,
            name=f"{prefix}_conv1",
        )(x)
        x = keras.layers.BatchNormalization(momentum=config.bn_momentum, name=f"{prefix}_bn1")(x)
        x = keras.layers.Activation(config.activation, name=f"{prefix}_act1")(x)
        x = keras.layers.Conv2D(
            filters=filters,
            kernel_size=kernel,
            padding="same",
            use_bias=config.use_bias,
            name=f"{prefix}_conv2",
        )(x)
        x = keras.layers.BatchNormalization(momentum=config.bn_momentum, name=f"{prefix}_bn2")(x)

        # Projection shortcut: the block changes both the stride and the channel
        # count, so the identity path has to be reshaped to match before the add.
        residual = keras.layers.Conv2D(
            filters=filters,
            kernel_size=(1, 1),
            strides=stride,
            padding="same",
            use_bias=config.use_bias,
            name=f"{prefix}_shortcut",
        )(residual)
        residual = keras.layers.BatchNormalization(
            momentum=config.bn_momentum, name=f"{prefix}_shortcut_bn"
        )(residual)

        x = keras.layers.Add(name=f"{prefix}_add")([x, residual])
        x = keras.layers.Activation(config.activation, name=f"{prefix}_act2")(x)

    outputs = _classifier_head(x, num_classes, config)
    return keras.Model(inputs=inputs, outputs=outputs, name=config.name or "tc_resnet8")


MODEL_REGISTRY: Dict[str, Callable[[Tuple[int, int, int], int, ModelConfig], keras.Model]] = {
    # ds_cnn_tiny is the same builder: it differs only through
    # `model_presets.ds_cnn_tiny` in config.yaml, so comparing the two measures
    # capacity and nothing else.
    "ds_cnn": build_ds_cnn,
    "ds_cnn_tiny": build_ds_cnn,
    "tc_resnet8": build_tc_resnet8,
}


def build_model(
    input_shape: Sequence[int], num_classes: int, config: ModelConfig
) -> keras.Model:
    """Instantiate the topology named by ``model.name``.

    ``num_classes`` is the number of *outputs*: 1 for a sigmoid detector, 2 for a
    ``target``/``unknown`` softmax, N for the legacy multi-class model. Pass
    ``Config.resolved_model()`` so architecture presets are applied.
    """
    if config.name not in MODEL_REGISTRY:
        known = ", ".join(sorted(MODEL_REGISTRY))
        raise ValueError(f"unknown model '{config.name}'; available: {known}")
    shape = tuple(int(value) for value in input_shape)
    if len(shape) != 3:
        raise ValueError(f"input_shape must be (frames, mels, channels), got {shape}")
    model = MODEL_REGISTRY[config.name](shape, int(num_classes), config)
    LOGGER.info(
        "built %s: input %s -> %d output(s), %s parameters",
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

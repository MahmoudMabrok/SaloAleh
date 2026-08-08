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

__all__ = [
    "MODEL_REGISTRY",
    "build_ds_cnn",
    "build_model",
    "capacity_report",
    "model_summary_text",
]

# Parameters per training clip above which the network can memorise the dataset
# instead of learning the phrase. Not a law - a threshold that matches what this
# project keeps running into at a few hundred clips.
PARAMETERS_PER_CLIP_WARN = 1000.0
PARAMETERS_PER_CLIP_HIGH = 5000.0


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


def capacity_report(
    model: keras.Model, train_clips: int, config: ModelConfig, num_classes: int
) -> str:
    """Model size measured against the dataset, with a recommendation.

    Reporting only - nothing is changed automatically, because a width multiplier
    that moves on its own would make two runs of the same config incomparable and
    every checkpoint's provenance a guess. The recommendation is a config edit for
    a person to make, and a deliberate one: it changes the model.
    """
    parameters = int(model.count_params())
    flops = estimate_flops(model)
    per_clip = parameters / float(max(train_clips, 1))

    lines = [
        f"parameters       : {parameters:,}",
        f"training clips   : {train_clips:,}",
        f"parameters/clip  : {per_clip:,.0f}",
        f"approx MFLOPs    : {flops / 1e6:.1f} per inference",
        f"width_multiplier : {config.width_multiplier:g}",
    ]

    if per_clip >= PARAMETERS_PER_CLIP_HIGH:
        suggestion = 0.5 if config.width_multiplier > 0.5 else config.width_multiplier
        lines.append(
            f"\n!! {per_clip:,.0f} parameters per training clip. A network this size "
            f"can memorise {train_clips:,} recordings outright, which shows up as "
            f"training accuracy at 1.0 with validation stuck far below it. "
            + (
                f"Recommended for a dataset this size: model.width_multiplier "
                f"{suggestion:g} (quarters the parameter count) and "
                f"model.dropout 0.4. Change it in config.yaml and run with "
                f"FRESH_START = True - it is not applied automatically, because a "
                f"model that silently resizes itself makes two runs of the same "
                f"config incomparable."
                if config.width_multiplier > 0.5
                else "The width is already at 0.5; the remaining fix is more "
                "recordings from more speakers, not less model."
            )
        )
    elif per_clip >= PARAMETERS_PER_CLIP_WARN:
        lines.append(
            f"\n!! {per_clip:,.0f} parameters per training clip is on the high side. "
            f"Watch the train/val gap; if it opens, model.width_multiplier "
            f"{max(config.width_multiplier / 2.0, 0.5):g} is the first knob."
        )
    else:
        lines.append(
            f"\ncapacity looks proportionate to the dataset "
            f"({per_clip:,.0f} parameters per clip). If validation plateaus here it "
            f"is unlikely to be model size - look at speaker variety first."
        )

    per_class = train_clips / float(max(num_classes, 1))
    if per_class < 50:
        lines.append(
            f"   ({per_class:.0f} training clips per class - at this scale the model "
            f"size is rarely the binding constraint; recordings and speakers are.)"
        )
    return "\n".join(lines)

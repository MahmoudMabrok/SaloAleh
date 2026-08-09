"""End-to-end orchestration for one target, and for a batch of them.

``train.py --target 007`` and ``train.py --targets 001,002,007`` run through
here. Batching trains **one target at a time and exports one independent model
each** - there is deliberately no path in this module that produces a single
multi-class model, because that is not what Android loads.

The stages are separate functions rather than one script so the notebook can run
them individually and inspect what each produced:

    prepare_target      scan -> speakers -> condition -> speaker-safe split ->
                        manifest + dataset report
    train_target        negative sampling -> tf.data -> model -> fit
    evaluate_target     clip-level detector metrics per negative category
    stream_target       streaming events, FA/hour, threshold calibration
    export_target       TFLite variants, INT8 verification, Android metadata,
                        front-end parity assets, readiness verdict

TensorFlow is imported inside the functions that need it, so ``prepare_target``
and the reporting helpers stay usable (and testable) without it.
"""

from __future__ import annotations

import json
import logging
import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Tuple, Union

import numpy as np

from .config import Config
from .dataset import (
    ManifestRecord,
    assign_splits,
    build_speaker_resolver,
    filter_split,
    load_phrases,
    preprocess_dataset,
    save_manifest,
    split_counts,
    verify_manifest_splits,
)
from .features import FeatureStats, LogMelExtractor
from .metrics import DetectorEvaluation, evaluate_detector
from .readiness import ReadinessReport, assess_readiness
from .speakers import SpeakerReport
from .streaming import StreamingDetector, detector_with_threshold
from .streaming_eval import (
    CalibrationResult,
    ScoredClip,
    StreamingEvaluation,
    calibrate_threshold,
    evaluate_timelines,
    load_streaming_set,
    score_clips,
    streaming_audio_root,
)
from .targets import (
    TARGET_INDEX,
    TargetDatasetReport,
    build_target_report,
    sample_negatives,
    scan_target_dataset,
)

LOGGER = logging.getLogger(__name__)

PathLike = Union[str, Path]
ProgressFn = Optional[Callable[[int, int], None]]

__all__ = [
    "TargetPreparation",
    "TargetRun",
    "evaluate_target",
    "prepare_target",
    "resolve_targets",
    "stream_target",
    "training_records",
]


def resolve_targets(config: Config, targets: Optional[Sequence[Union[int, str]]]) -> List[int]:
    """Normalise ``--targets 001,7`` into ``[1, 7]``, or fall back to the config."""
    if not targets:
        if not config.target.enabled:
            raise ValueError(
                "no target given and target.phrase_id is null in the config - "
                "pass --target 007 or set target.phrase_id"
            )
        return [int(config.target.phrase_id)]  # type: ignore[arg-type]

    resolved: List[int] = []
    for item in targets:
        text = str(item).strip()
        if not text:
            continue
        try:
            value = int(text)
        except ValueError as exc:
            raise ValueError(f"'{item}' is not a phrase id") from exc
        if value < 1:
            raise ValueError(f"phrase ids start at 1, got {value}")
        if value not in resolved:
            resolved.append(value)
    if not resolved:
        raise ValueError("no valid phrase ids were given")
    return resolved


# ---------------------------------------------------------------------------
# Stage 1 - dataset
# ---------------------------------------------------------------------------
@dataclass
class TargetPreparation:
    """Everything the dataset stage produced for one target."""

    config: Config
    target_id: int
    target_text: str
    records: List[ManifestRecord]
    report: TargetDatasetReport
    speakers: SpeakerReport
    manifest_path: Path

    def split(self, name: str) -> List[ManifestRecord]:
        return filter_split(self.records, name)

    def summary(self) -> str:
        return "\n\n".join([self.report.summary(), self.speakers.summary()])


def prepare_target(
    config: Config,
    target_id: Optional[int] = None,
    overwrite: bool = False,
    exclude: Optional[Sequence[str]] = None,
    progress: ProgressFn = None,
) -> TargetPreparation:
    """Index, condition and split one target's data; write its manifest.

    Conditioned clips go to ``processed/audio/<sample rate>hz_<window>s/<source
    folder>/``. Two things follow from that: the shared negative pool is written
    once and reused by every target with the same window geometry, and a target
    that overrides ``clip_seconds`` gets its own cache instead of silently
    reusing clips fitted to a different length.
    """
    bound = config.for_target(target_id) if target_id is not None else config
    if not bound.target.enabled:
        raise ValueError("prepare_target needs a target - pass target_id or set target.phrase_id")
    identifier = int(bound.target.phrase_id)  # type: ignore[arg-type]

    phrases = load_phrases(bound.paths.phrases_path)
    index = scan_target_dataset(
        bound.paths.dataset_path,
        phrases,
        bound.target,
        unknown_class=bound.paths.unknown_class,
        extensions=bound.audio.file_extensions,
        speaker_resolver=build_speaker_resolver(bound),
    )

    records, summary = preprocess_dataset(
        index,
        bound,
        exclude=exclude,
        overwrite=overwrite,
        progress=progress,
        destination_root=bound.clip_cache_path(),
    )
    LOGGER.info("conditioning: %s", summary.summary())

    # Speaker-grouped whenever the recordings carry a speaker, and verified
    # afterwards rather than assumed: `verify_manifest_splits` raises on a leak
    # when `split.speaker.require_disjoint` is set.
    records = assign_splits(records, bound.split, bound.seed)
    speakers = verify_manifest_splits(records, bound.split, source=f"target {identifier:03d}")

    manifest_path = bound.target_manifest_path(identifier)
    save_manifest(records, manifest_path)

    durations = {str(sample.path): 0.0 for sample in index.samples}
    for record in records:
        durations[record.source_path] = record.duration
    report = build_target_report(index, durations=durations, config=bound)

    LOGGER.info("splits: %s", split_counts(records))
    return TargetPreparation(
        config=bound,
        target_id=identifier,
        target_text=index.target_text or "",
        records=records,
        report=report,
        speakers=speakers,
        manifest_path=manifest_path,
    )


def training_records(config: Config, records: Sequence[ManifestRecord]) -> List[ManifestRecord]:
    """Training split after negative sampling; val and test are never sampled.

    Sampling the evaluation splits would change what the numbers mean between
    runs - the point of the cut is to balance *training*, not to make the model
    look better on a friendlier test set.
    """
    train = filter_split(records, "train")
    sampled = sample_negatives(train, config.negative_sampling, config.seed, split="train")
    return [record for record in sampled if record.split == "train"]


# ---------------------------------------------------------------------------
# Stage 2 - training
# ---------------------------------------------------------------------------
def build_extractor(config: Config, stats: Optional[FeatureStats] = None) -> LogMelExtractor:
    return LogMelExtractor(
        config=config.features, sample_rate=config.audio.sample_rate, stats=stats
    )


def train_target(
    preparation: TargetPreparation,
    run_name: Optional[str] = None,
    fresh: bool = False,
    verbose: int = 1,
    architecture: Optional[str] = None,
):
    """Train one detector on a prepared target. Returns ``(model, artifacts)``."""
    from .augmentation import NoiseBank, WaveformAugmentor
    from .dataset import compute_class_weights, make_tf_dataset
    from .models import build_model
    from .trainer import Trainer, configure_mixed_precision, set_global_seed

    config = preparation.config
    if architecture:
        config = config.with_overrides({"model.name": architecture})

    set_global_seed(config.seed)
    configure_mixed_precision(config.training.mixed_precision)

    extractor = build_extractor(config)
    noise_bank = NoiseBank.load(
        config.paths.noise_path,
        config.audio.sample_rate,
        allow_synthetic=config.augmentation.background_noise.synthetic_when_missing,
    )
    LOGGER.info("background noise: %s", describe_noise(noise_bank))
    augmentor = WaveformAugmentor(
        config=config.augmentation, audio=config.audio, noise_bank=noise_bank
    )

    train = training_records(config, preparation.records)
    validation = filter_split(preparation.records, "val")
    if not train or not validation:
        raise ValueError("training needs a non-empty train and val split")

    root = config.clip_cache_path()
    train_dataset = make_tf_dataset(
        train, config, extractor, training=True, augmentor=augmentor, processed_root=root
    )
    val_dataset = make_tf_dataset(
        validation, config, extractor, training=False, processed_root=root
    )

    outputs = config.target.num_outputs
    model = build_model(config.input_shape, outputs, config.resolved_model())
    trainer = Trainer(
        config=config,
        model=model,
        num_classes=outputs,
        steps_per_epoch=math.ceil(len(train) / config.training.batch_size),
        run_name=run_name or f"target_{preparation.target_id:03d}_{config.model.name}",
    )
    if fresh:
        trainer.reset_run()
    trainer.compile()

    class_weight = compute_class_weights(
        [record.class_index for record in train], max(outputs, 2)
    )
    if outputs == 1:
        # A sigmoid head has one output but two classes; the weights are keyed by
        # label, which is what Keras expects for binary labels either way.
        class_weight = {0: class_weight.get(0, 1.0), 1: class_weight.get(1, 1.0)}

    positives = sum(1 for record in validation if record.class_index == TARGET_INDEX)
    artifacts = trainer.fit(
        train_dataset,
        val_dataset,
        class_weight=class_weight,
        verbose=verbose,
        val_size=len(validation),
        positive_rate=positives / len(validation) if validation else None,
    )
    return trainer, artifacts


def describe_noise(noise_bank) -> str:
    """Say out loud whether augmentation used real recordings or the fallback."""
    if len(noise_bank):
        return f"REAL NOISE - {len(noise_bank)} recording(s) from the noise folder"
    if noise_bank.allow_synthetic:
        return (
            "SYNTHETIC FALLBACK - white/pink noise, because the noise folder is empty. "
            "Real room, street and TV recordings are worth considerably more; this "
            "fallback teaches robustness to a sound no phone ever hears."
        )
    return "NONE - background-noise augmentation is disabled and no recordings were found"


# ---------------------------------------------------------------------------
# Stage 3 - clip-level evaluation
# ---------------------------------------------------------------------------
def evaluate_target(
    preparation: TargetPreparation,
    model,
    split: Optional[str] = None,
    threshold: Optional[float] = None,
) -> DetectorEvaluation:
    """Score the held-out split as a detector, per negative category."""
    from .dataset import make_tf_dataset
    from .metrics import predict_dataset
    from .streaming import target_score

    config = preparation.config
    records = filter_split(preparation.records, split or config.evaluation.split)
    if not records:
        raise ValueError(f"the '{split or config.evaluation.split}' split is empty")

    dataset = make_tf_dataset(
        records,
        config,
        build_extractor(config),
        training=False,
        processed_root=config.clip_cache_path(),
        batch_size=config.evaluation.batch_size,
        shuffle=False,
    )
    truth, probabilities = predict_dataset(model, dataset)
    scores = target_score(probabilities, config.target.output_mode)
    return evaluate_detector(
        truth,
        scores,
        threshold=(
            threshold
            if threshold is not None
            else config.streaming.detector.activation_threshold
        ),
        negative_types=[record.negative_type for record in records],
        paths=[record.path for record in records],
    )


# ---------------------------------------------------------------------------
# Stage 4 - streaming
# ---------------------------------------------------------------------------
@dataclass
class StreamingOutcome:
    """Streaming evaluation before and after calibration."""

    scored: List[ScoredClip] = field(default_factory=list)
    calibration: Optional[CalibrationResult] = None
    evaluation: Optional[StreamingEvaluation] = None

    @property
    def measured(self) -> bool:
        return bool(self.scored)


def stream_target(
    preparation: TargetPreparation,
    model,
    calibrate: bool = True,
    progress: Optional[Callable[[str, int, int], None]] = None,
) -> StreamingOutcome:
    """Score the long-form recordings, calibrate, then evaluate at the result.

    Returns an empty outcome (rather than raising) when there is no streaming
    set: the run should still produce a model and a clip report, it just cannot
    claim the model is shippable - :func:`src.readiness.assess_readiness` turns
    the missing measurement into an ``unknown`` check that caps the status at
    EXPERIMENTAL.
    """
    config = preparation.config
    clips = load_streaming_set(config)
    if not clips:
        return StreamingOutcome()

    detector = StreamingDetector.from_config(config, model)
    scored = score_clips(
        detector,
        clips,
        streaming_audio_root(config),
        target_folder=f"{preparation.target_id:03d}",
        progress=progress,
    )
    if not scored:
        return StreamingOutcome()

    tolerance = config.streaming.match_tolerance_seconds
    calibration: Optional[CalibrationResult] = None
    operating_point = config.streaming.detector

    if calibrate and config.calibration.enabled:
        calibration = calibrate_threshold(scored, config.calibration, operating_point, tolerance)
        if calibration.satisfied:
            operating_point = detector_with_threshold(
                operating_point,
                calibration.activation,
                release=calibration.release,
                release_ratio=config.calibration.release_ratio,
            )
        else:
            LOGGER.warning(
                "threshold calibration failed for target %03d - reporting the "
                "configured operating point instead:\n%s",
                preparation.target_id,
                calibration.summary(),
            )

    return StreamingOutcome(
        scored=scored,
        calibration=calibration,
        evaluation=evaluate_timelines(scored, operating_point, tolerance),
    )


# ---------------------------------------------------------------------------
# Stage 5 - export
# ---------------------------------------------------------------------------
def _clip_score_sets(
    preparation: TargetPreparation,
    scorer,
    split: Optional[str] = None,
    per_type_limit: int = 200,
) -> Dict[str, Tuple[List[str], np.ndarray]]:
    """Score the evaluation split once per variant, grouped by clip set.

    Grouped rather than averaged because a variant that is faithful on positives
    and drifts on hard negatives is precisely the variant that starts counting
    the wrong phrase, and one pooled number would hide it.
    """
    from .audio import read_wav

    config = preparation.config
    split = split or config.evaluation.split
    root = config.clip_cache_path()
    groups: Dict[str, List[ManifestRecord]] = {"positives": [], "negatives": [], "hard_negatives": []}
    counts: Dict[str, int] = {}
    for record in filter_split(preparation.records, split):
        if record.class_index == TARGET_INDEX:
            key = "positives"
        elif record.negative_type == "hard_negative":
            key = "hard_negatives"
        else:
            key = "negatives"
        counts[key] = counts.get(key, 0) + 1
        if counts[key] > per_type_limit:
            continue
        groups[key].append(record)

    extractor = build_extractor(config)
    result: Dict[str, Tuple[List[str], np.ndarray]] = {}
    for name, records in groups.items():
        if not records:
            continue
        features = []
        for record in records:
            samples, _ = read_wav(record.resolve(root))
            features.append(extractor(samples))
        scores = np.asarray(scorer(np.stack(features)), dtype=np.float32)
        result[name] = ([record.path for record in records], scores)
    return result


def export_target(
    preparation: TargetPreparation,
    model,
    clip_evaluation: Optional[DetectorEvaluation] = None,
    streaming: Optional[StreamingOutcome] = None,
    architecture: Optional[str] = None,
    model_version: str = "1",
    output_dir: Optional[PathLike] = None,
) -> Dict[str, object]:
    """Convert, verify and describe one target's model for Android.

    Produces ``exports/<target>/`` with the TFLite variants, the metadata
    contract, ``labels.txt``, the clip and streaming reports, and the front-end
    parity assets. INT8 is compared against the Keras reference on positives,
    ordinary negatives, hard negatives *and* streaming windows, and is only
    recommended when it neither drifts nor adds false activations.
    """
    from .export import (
        collect_features,
        convert_tflite,
        export_saved_model,
        make_interpreter,
        representative_dataset_from_arrays,
        to_float32_model,
        write_tflite,
    )
    from .dataset import make_tf_dataset
    from .parity import write_parity_assets
    from .streaming import KerasScorer, StreamingDetector, TFLiteScorer, detector_with_threshold
    from .streaming_eval import evaluate_timelines
    from .target_export import (
        build_target_metadata,
        compare_variants,
        interpreter_specs,
        model_filename,
        sha256_file,
        write_target_labels,
        write_target_metadata,
    )

    config = preparation.config
    destination = Path(output_dir or config.target_export_path(preparation.target_id))
    destination.mkdir(parents=True, exist_ok=True)

    reference = to_float32_model(model)
    saved_model_dir = destination / "saved_model"
    export_saved_model(reference, saved_model_dir)

    train = training_records(config, preparation.records)
    calibration_dataset = make_tf_dataset(
        train,
        config,
        build_extractor(config),
        training=False,
        processed_root=config.clip_cache_path(),
        batch_size=config.evaluation.batch_size,
        shuffle=False,
    )
    representative = representative_dataset_from_arrays(
        collect_features(calibration_dataset, config.export.representative_samples),
        config.export.representative_samples,
    )

    variants: Dict[str, Path] = {}
    for variant in ("float32", "int8"):
        try:
            flatbuffer = convert_tflite(
                saved_model_dir,
                mode=variant,
                representative_dataset=representative if variant == "int8" else None,
            )
        except Exception as exc:  # noqa: BLE001 - one failed variant must not stop the rest
            LOGGER.error("conversion failed for %s: %s", variant, exc)
            continue
        variants[variant] = write_tflite(
            flatbuffer, destination / model_filename(preparation.target_id, variant)
        )
    if not variants:
        raise RuntimeError(
            f"no TFLite variant was produced for target {preparation.target_id:03d} - the "
            f"SavedModel in {saved_model_dir} is intact, so this is a conversion problem"
        )

    # -- verification: same clips through every variant ---------------------
    output_mode = config.target.output_mode
    reference_sets = _clip_score_sets(preparation, KerasScorer(reference, output_mode))
    reference_scores = {name: scores for name, (_, scores) in reference_sets.items()}

    candidate_scores: Dict[str, Dict[str, Sequence[float]]] = {}
    candidate_events = {}
    sizes: Dict[str, int] = {}
    operating_point = config.streaming.detector
    if streaming and streaming.calibration and streaming.calibration.satisfied:
        operating_point = detector_with_threshold(
            operating_point,
            streaming.calibration.activation,
            release=streaming.calibration.release,
            release_ratio=config.calibration.release_ratio,
        )

    for variant, path in variants.items():
        sizes[variant] = path.stat().st_size
        scorer = TFLiteScorer(path, output_mode=output_mode)
        candidate_scores[variant] = {
            name: scores for name, (_, scores) in _clip_score_sets(preparation, scorer).items()
        }
        if streaming and streaming.scored:
            # Re-score the long-form recordings through the exported flatbuffer:
            # clip drift alone cannot tell you whether quantisation changed the
            # *count*, which is the only number the user sees.
            detector = StreamingDetector(
                frontend=_frontend_for(config),
                scorer=scorer,
                detector=operating_point,
                smoothing=config.streaming.smoothing,
            )
            rescored = score_clips(
                detector,
                [item.clip for item in streaming.scored],
                streaming_audio_root(config),
                target_folder=f"{preparation.target_id:03d}",
            )
            candidate_events[variant] = evaluate_timelines(
                rescored, operating_point, config.streaming.match_tolerance_seconds
            ).metrics

    quantization = compare_variants(
        reference_scores,
        candidate_scores,
        threshold=operating_point.activation_threshold,
        tolerance=config.readiness.max_int8_probability_drift,
        max_fa_increase=config.readiness.max_int8_fa_per_hour_increase,
        reference_events=(
            streaming.evaluation.metrics if streaming and streaming.evaluation else None
        ),
        candidate_events=candidate_events,
        sizes=sizes,
    )

    # -- sidecars -----------------------------------------------------------
    write_target_labels(
        destination / "labels.txt",
        preparation.target_id,
        preparation.target_text,
        output_mode=config.target.output_mode,
    )
    extractor = build_extractor(config)
    parity = write_parity_assets(destination, extractor, config.audio)
    extractor.save_filterbank(destination / "mel_filterbank.json")

    if clip_evaluation:
        clip_evaluation.save(destination / "evaluation.json")
    if streaming and streaming.evaluation:
        streaming.evaluation.save(destination / "streaming_evaluation.json")
    if streaming and streaming.calibration:
        streaming.calibration.save(destination / "calibration.json")

    recommended = quantization.recommended()
    recommended_variant = recommended.variant if recommended else "float32"
    recommended_path = variants.get(recommended_variant, next(iter(variants.values())))
    interpreter = make_interpreter(recommended_path)
    interpreter.allocate_tensors()

    metadata = build_target_metadata(
        config,
        target_id=preparation.target_id,
        target_text=preparation.target_text,
        architecture=architecture or config.model.name,
        frontend=extractor.metadata(),
        detector=operating_point,
        model_file=recommended_path.name,
        parameter_count=int(reference.count_params()),
        file_size_bytes=recommended_path.stat().st_size,
        sha256=sha256_file(recommended_path),
        tensors=interpreter_specs(interpreter),
        dataset={
            "positive_clips": preparation.report.positive_clips,
            "positive_speakers": preparation.report.positive_speakers,
            "negative_clips": preparation.report.negative_clips,
            "speakers_known": preparation.report.speakers_known,
            "speaker_leakage": len(preparation.speakers.leaks),
        },
        streaming=streaming.evaluation.metrics if streaming and streaming.evaluation else None,
        counts=streaming.evaluation.counts if streaming and streaming.evaluation else None,
        evaluation=clip_evaluation.to_dict() if clip_evaluation else None,
        calibration=streaming.calibration.to_dict() if streaming and streaming.calibration else None,
        quantization=quantization.to_dict(),
        model_version=model_version,
        variants={name: path.name for name, path in variants.items()},
    )
    metadata["frontend_parity"] = parity.to_dict()
    write_target_metadata(destination / "model_metadata.json", metadata)

    return {
        "directory": destination,
        "variants": variants,
        "quantization": quantization,
        "metadata": metadata,
        "parity": parity,
    }


def _frontend_for(config: Config):
    from .streaming import StreamingFrontend

    return StreamingFrontend.from_config(config)


# ---------------------------------------------------------------------------
# Run summary
# ---------------------------------------------------------------------------
@dataclass
class TargetRun:
    """What one target's run produced, and whether it may ship."""

    target_id: int
    target_text: str
    preparation: TargetPreparation
    clip_evaluation: Optional[DetectorEvaluation] = None
    streaming: Optional[StreamingOutcome] = None
    readiness: Optional[ReadinessReport] = None
    export_dir: Optional[Path] = None
    error: str = ""

    @property
    def status(self) -> str:
        if self.error:
            return "FAILED"
        return self.readiness.status if self.readiness else "EXPERIMENTAL"

    def to_dict(self) -> Dict[str, object]:
        streaming = self.streaming
        return {
            "target_id": f"{self.target_id:03d}",
            "target_text": self.target_text,
            "status": self.status,
            "error": self.error,
            "export_dir": str(self.export_dir) if self.export_dir else None,
            "dataset": self.preparation.report.to_dict() if self.preparation else None,
            "speaker_isolation": self.preparation.isolation.to_dict()
            if self.preparation
            else None,
            "clip_evaluation": self.clip_evaluation.to_dict() if self.clip_evaluation else None,
            "streaming": (
                streaming.evaluation.to_dict()
                if streaming and streaming.evaluation
                else None
            ),
            "calibration": (
                streaming.calibration.to_dict() if streaming and streaming.calibration else None
            ),
            "readiness": self.readiness.to_dict() if self.readiness else None,
        }

    def summary(self) -> str:
        if self.error:
            return f"target {self.target_id:03d}: FAILED - {self.error}"
        parts = [self.preparation.summary()]
        if self.clip_evaluation:
            parts.append(self.clip_evaluation.summary())
        if self.streaming and self.streaming.evaluation:
            parts.append(self.streaming.evaluation.summary())
        if self.streaming and self.streaming.calibration:
            parts.append(self.streaming.calibration.summary())
        if self.readiness:
            parts.append(self.readiness.summary())
        return "\n\n".join(parts)


def readiness_for(
    run: TargetRun,
    quantization=None,
    hard_negatives=None,
) -> ReadinessReport:
    """Assemble the readiness verdict from whatever a run measured."""
    streaming = run.streaming
    return assess_readiness(
        run.preparation.config.readiness,
        target_id=run.target_id,
        target_text=run.target_text,
        dataset=run.preparation.report,
        isolation=run.preparation.speakers,
        streaming=streaming.evaluation.metrics if streaming and streaming.evaluation else None,
        counts=streaming.evaluation.counts if streaming and streaming.evaluation else None,
        hard_negatives=hard_negatives,
        quantization=quantization,
        calibration=streaming.calibration if streaming else None,
    )


def write_run_report(run: TargetRun, directory: PathLike) -> Path:
    """Persist one target's run summary next to the other reports."""
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    destination = target / f"target_{run.target_id:03d}_run.json"
    destination.write_text(
        json.dumps(run.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
    )
    return destination

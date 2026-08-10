#!/usr/bin/env python3
"""Train and export one dhikr detector, or a batch of them.

    python train.py                                  config selects all phrases
    python train.py --all-targets                    every phrase, explicitly
    python train.py --target 007                     one target, end to end
    python train.py --targets 001,002,006,007        one model each, in sequence
    python train.py --target 007 --stage dataset     dataset report only, no TF
    python train.py --target 007 --stage train       stop after training
    python train.py --target 007 --arch tc_resnet8   override the architecture

Each target is trained and exported **independently** - there is no mode that
produces one multi-class model, because Android loads one model per dhikr. A
batch run is a loop, and one target failing does not stop the rest.

``--stage dataset`` is worth running first on a new target: it is fast, needs no
TensorFlow, and answers the questions that decide whether training is worth
starting at all - how many speakers, how many hard negatives, and whether the
split leaks.
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path
from typing import List, Optional, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from src.config import Config, load_config  # noqa: E402
from src.pipeline import (  # noqa: E402
    TargetRun,
    evaluate_target,
    prepare_target,
    readiness_for,
    resolve_targets,
    stream_target,
    write_run_report,
)

LOGGER = logging.getLogger("dhikrspeech")

STAGES = ("dataset", "train", "evaluate", "stream", "export")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train one single-target dhikr detector per phrase.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--target", help="phrase id to train, e.g. 007")
    parser.add_argument(
        "--targets",
        help="comma-separated phrase ids; one independent model is trained per id",
    )
    parser.add_argument(
        "--all-targets",
        action="store_true",
        help="train every phrase in phrases.json (respecting classes.include_phrases)",
    )
    parser.add_argument("--config", help="path to config.yaml (default: configs/config.yaml)")
    parser.add_argument(
        "--stage",
        choices=STAGES,
        default="export",
        help="run up to and including this stage (default: export)",
    )
    parser.add_argument("--arch", help="override model.name for this run")
    parser.add_argument("--run-name", help="checkpoint/run name (default: target_<id>_<arch>)")
    parser.add_argument(
        "--fresh", action="store_true", help="wipe the run directory before training"
    )
    parser.add_argument(
        "--overwrite-audio",
        action="store_true",
        help="re-condition cached clips even when they already exist",
    )
    parser.add_argument(
        "--no-calibrate",
        action="store_true",
        help="skip the threshold sweep and use the configured operating point",
    )
    parser.add_argument("--quiet", action="store_true", help="less logging, no fit progress bar")
    return parser.parse_args(argv)


def run_target(
    config: Config,
    target_id: int,
    stage: str = "export",
    architecture: Optional[str] = None,
    run_name: Optional[str] = None,
    fresh: bool = False,
    overwrite_audio: bool = False,
    calibrate: bool = True,
    verbose: int = 1,
) -> TargetRun:
    """One target, from the dataset up to the requested stage."""
    preparation = prepare_target(config, target_id, overwrite=overwrite_audio)
    run = TargetRun(
        target_id=preparation.target_id,
        target_text=preparation.target_text,
        preparation=preparation,
    )
    print(preparation.summary())
    if stage == "dataset":
        run.readiness = readiness_for(run)
        return run

    from src.pipeline import export_target, train_target  # imports TensorFlow

    trainer, artifacts = train_target(
        preparation,
        run_name=run_name,
        fresh=fresh,
        verbose=verbose,
        architecture=architecture,
    )
    print(artifacts.summary())
    if stage == "train":
        run.readiness = readiness_for(run)
        return run

    model = trainer.load_best()
    run.clip_evaluation = evaluate_target(preparation, model)
    print(run.clip_evaluation.summary())
    if stage == "evaluate":
        run.readiness = readiness_for(run)
        return run

    run.streaming = stream_target(preparation, model, calibrate=calibrate)
    if run.streaming.measured:
        if run.streaming.calibration:
            print(run.streaming.calibration.summary())
        if run.streaming.evaluation:
            print(run.streaming.evaluation.summary())
    else:
        print(
            "no streaming recordings were evaluated. Clip metrics alone cannot decide "
            "whether this model is shippable - see streaming_test/ in the README."
        )
    if stage == "stream":
        run.readiness = readiness_for(run)
        return run

    exported = export_target(
        preparation,
        model,
        clip_evaluation=run.clip_evaluation,
        streaming=run.streaming,
        architecture=architecture or preparation.config.model.name,
    )
    run.export_dir = exported["directory"]
    print(exported["quantization"].summary())
    run.readiness = readiness_for(run, quantization=exported["quantization"])
    return run


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    logging.basicConfig(
        level=logging.WARNING if args.quiet else logging.INFO,
        format="%(levelname)s %(name)s: %(message)s",
    )

    config = load_config(args.config)
    requested: List[str] = []
    if args.target:
        requested.append(args.target)
    if args.targets:
        requested.extend(part for part in args.targets.split(",") if part.strip())
    if args.all_targets:
        requested.append("all")
    targets = resolve_targets(config, requested)

    runs: List[TargetRun] = []
    for position, target_id in enumerate(targets, start=1):
        print(f"\n{'=' * 72}\ntarget {target_id:03d}  ({position}/{len(targets)})\n{'=' * 72}")
        try:
            run = run_target(
                config,
                target_id,
                stage=args.stage,
                architecture=args.arch,
                run_name=(
                    f"{args.run_name}_{target_id:03d}"
                    if args.run_name and len(targets) > 1
                    else args.run_name
                ),
                fresh=args.fresh,
                overwrite_audio=args.overwrite_audio,
                calibrate=not args.no_calibrate,
                verbose=0 if args.quiet else 1,
            )
        except Exception as exc:  # noqa: BLE001 - one target must not stop the batch
            LOGGER.exception("target %03d failed", target_id)
            print(f"target {target_id:03d} FAILED: {type(exc).__name__}: {exc}")
            continue
        if run.readiness:
            print()
            print(run.readiness.summary())
        write_run_report(run, config.paths.reports_path)
        runs.append(run)

    print(f"\n{'=' * 72}")
    for run in runs:
        print(f"  {run.target_id:03d}  {run.status:<22} {run.target_text}")
    failed = len(targets) - len(runs)
    if failed:
        print(f"  {failed} target(s) failed - see the log above")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

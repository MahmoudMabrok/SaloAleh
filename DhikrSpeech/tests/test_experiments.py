"""Tests for the one-vs-rest comparison logic.

Only the scoring half is covered here, and deliberately so: training needs
TensorFlow, a GPU and the dataset on Drive, but the part that can silently
produce a *wrong verdict* is the arithmetic that turns predictions into a
comparison. These tests feed it hand-built probabilities whose right answer is
known by construction.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src.dataset import ManifestRecord
from src.experiments import (
    POSITIVE_INDEX,
    REST_INDEX,
    ComparisonReport,
    PhraseComparison,
    _confusions,
    _discrimination,
    _rejection,
    binary_class_names,
    binary_records,
    phrase_labels,
)
from src.metrics import wilson_interval

CLASS_NAMES = ["006", "007", "unknown"]
TARGETS = ["006", "007"]


def record(label: str, class_index: int, split: str = "train", name: str = "a") -> ManifestRecord:
    return ManifestRecord(
        path=f"{label}/{name}.wav",
        source_path=f"/raw/{label}/{name}.wav",
        label=label,
        class_index=class_index,
        phrase_id=int(label) if label.isdigit() else None,
        text=label,
        split=split,
        duration=2.0,
    )


@pytest.fixture
def records():
    return [
        record("006", 0, "train", "a"),
        record("006", 0, "test", "b"),
        record("007", 1, "train", "c"),
        record("007", 1, "test", "d"),
        record("unknown", 2, "train", "e"),
        record("unknown", 2, "test", "f"),
    ]


# ---------------------------------------------------------------------------
# Relabelling
# ---------------------------------------------------------------------------
def test_binary_records_marks_only_the_target_positive(records):
    relabelled = binary_records(records, "006")

    for original, updated in zip(records, relabelled):
        expected = POSITIVE_INDEX if original.label == "006" else REST_INDEX
        assert updated.class_index == expected
        # Everything except the class index must survive untouched, or the binary
        # model is not training on the same clips and splits.
        assert updated.path == original.path
        assert updated.label == original.label
        assert updated.split == original.split


def test_binary_records_puts_unknown_in_the_negatives(records):
    relabelled = binary_records(records, "006")
    unknowns = [item for item in relabelled if item.label == "unknown"]
    assert unknowns and all(item.class_index == REST_INDEX for item in unknowns)


def test_binary_records_does_not_mutate_the_input(records):
    before = [item.class_index for item in records]
    binary_records(records, "007")
    assert [item.class_index for item in records] == before


def test_binary_records_rejects_an_absent_label(records):
    with pytest.raises(ValueError, match="008"):
        binary_records(records, "008")


def test_binary_class_names_puts_the_phrase_at_the_positive_index():
    assert binary_class_names("006")[POSITIVE_INDEX] == "006"


def test_phrase_labels_drops_unknown():
    assert phrase_labels(CLASS_NAMES) == TARGETS


# ---------------------------------------------------------------------------
# Discrimination
# ---------------------------------------------------------------------------
def test_discrimination_scores_both_approaches_on_phrase_clips_only():
    # Four clips: 006, 006, 007, unknown. The unknown clip must be excluded.
    truth = np.array([0, 0, 1, 2])
    multiclass = np.array(
        [
            [0.7, 0.2, 0.1],  # 006 -> correct
            [0.2, 0.7, 0.1],  # 006 -> confused with 007
            [0.1, 0.8, 0.1],  # 007 -> correct
            [0.3, 0.3, 0.4],  # unknown, excluded from this test
        ]
    )
    binary = {
        "006": np.array([0.9, 0.9, 0.2, 0.1], dtype=np.float32),
        "007": np.array([0.1, 0.1, 0.9, 0.1], dtype=np.float32),
    }

    result = _discrimination(truth, multiclass, binary, CLASS_NAMES, TARGETS)

    assert result.num_clips == 3
    assert result.multiclass_correct == 2
    assert result.committee_correct == 3
    assert result.multiclass_confusions == [("006", "007", 1)]
    assert result.committee_confusions == []


def test_discrimination_ignores_the_unknown_column():
    """A phrase clip the multi-class model calls `unknown` still has to be named.

    Restricting to the phrase columns is what makes the two sides answer the same
    question - a committee of binaries has no `unknown` output to hide in.
    """
    truth = np.array([0])
    # `unknown` wins outright, but among the phrases 006 is ahead.
    multiclass = np.array([[0.3, 0.2, 0.5]])
    binary = {"006": np.array([0.4]), "007": np.array([0.3])}

    result = _discrimination(truth, multiclass, binary, CLASS_NAMES, TARGETS)

    assert result.num_clips == 1
    assert result.multiclass_correct == 1


def test_discrimination_needs_two_phrases_to_mean_anything():
    truth = np.array([0, 2])
    multiclass = np.array([[0.9, 0.1], [0.2, 0.8]])
    assert _discrimination(truth, multiclass, {"006": np.array([1.0, 0.0])},
                           ["006", "unknown"], ["006"]) is None


def test_discrimination_returns_none_without_phrase_clips():
    truth = np.array([2, 2])
    multiclass = np.zeros((2, 3))
    binary = {"006": np.zeros(2), "007": np.zeros(2)}
    assert _discrimination(truth, multiclass, binary, CLASS_NAMES, TARGETS) is None


def test_discrimination_accuracy_and_interval():
    result = _discrimination(
        np.array([0, 0, 1, 1]),
        np.array([[0.9, 0.1, 0.0]] * 2 + [[0.1, 0.9, 0.0]] * 2),
        {"006": np.array([1.0, 1.0, 1.0, 1.0]), "007": np.array([0.0, 0.0, 0.0, 0.0])},
        CLASS_NAMES,
        TARGETS,
    )
    assert result.multiclass_accuracy == 1.0
    assert result.committee_accuracy == 0.5
    low, high = result.intervals()["committee"]
    assert low < 0.5 < high


# ---------------------------------------------------------------------------
# Rejection
# ---------------------------------------------------------------------------
def test_rejection_uses_the_same_accept_rule_for_both_approaches():
    # Two unknown clips and two phrase clips.
    truth = np.array([0, 1, 2, 2])
    multiclass = np.array(
        [
            [0.9, 0.05, 0.05],  # phrase, best phrase score 0.9 -> accepted
            [0.1, 0.4, 0.5],    # phrase, best phrase score 0.4 -> rejected
            [0.8, 0.1, 0.1],    # unknown, best phrase score 0.8 -> false accept
            [0.2, 0.2, 0.6],    # unknown, best phrase score 0.2 -> correctly quiet
        ]
    )
    binary = {
        "006": np.array([0.95, 0.1, 0.3, 0.1]),
        "007": np.array([0.05, 0.9, 0.2, 0.1]),
    }

    result = _rejection(truth, multiclass, binary, CLASS_NAMES, TARGETS, "unknown", 0.5)

    assert result.unknown_clips == 2
    assert result.phrase_clips == 2
    assert result.multiclass_unknown_accepted == 1
    assert result.multiclass_phrase_accepted == 1
    assert result.committee_unknown_accepted == 0
    assert result.committee_phrase_accepted == 2
    assert result.multiclass_false_accept == 0.5
    assert result.committee_false_accept == 0.0
    assert result.committee_recall == 1.0


def test_rejection_is_none_without_an_unknown_class():
    truth = np.array([0, 1])
    multiclass = np.array([[0.9, 0.1], [0.1, 0.9]])
    binary = {"006": np.array([0.9, 0.1]), "007": np.array([0.1, 0.9])}
    assert _rejection(truth, multiclass, binary, TARGETS, TARGETS, "unknown", 0.5) is None


def test_rejection_rates_are_nan_when_a_group_is_empty():
    truth = np.array([2, 2])
    multiclass = np.array([[0.1, 0.1, 0.8]] * 2)
    binary = {"006": np.array([0.1, 0.1]), "007": np.array([0.1, 0.1])}
    result = _rejection(truth, multiclass, binary, CLASS_NAMES, TARGETS, "unknown", 0.5)
    assert result.phrase_clips == 0
    assert np.isnan(result.multiclass_recall)


# ---------------------------------------------------------------------------
# Confusions
# ---------------------------------------------------------------------------
def test_confusions_rank_by_frequency_and_skip_correct_predictions():
    true_labels = np.array(["006", "006", "006", "007", "007"])
    predicted = np.array(["007", "007", "006", "006", "007"])
    assert _confusions(true_labels, predicted) == [("006", "007", 2), ("007", "006", 1)]


def test_confusions_respect_top_k():
    true_labels = np.array(["a", "b", "c"])
    predicted = np.array(["x", "y", "z"])
    assert len(_confusions(true_labels, predicted, top_k=2)) == 2


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------
def comparison(label: str, multiclass: float, binary: float, support: int = 40):
    return PhraseComparison(
        label=label,
        support=support,
        negatives=100,
        auc_multiclass=multiclass,
        auc_binary=binary,
        ap_multiclass=multiclass,
        ap_binary=binary,
    )


def report_of(*comparisons, discrimination=None, rejection=None) -> ComparisonReport:
    return ComparisonReport(
        eval_split="test",
        num_eval_clips=140,
        class_names=CLASS_NAMES,
        phrases=TARGETS,
        per_phrase=list(comparisons),
        discrimination=discrimination,
        rejection=rejection,
    )


def test_auc_delta_is_binary_minus_multiclass():
    assert comparison("006", 0.90, 0.95).auc_delta == pytest.approx(0.05)
    assert comparison("006", 0.90, 0.85).auc_delta == pytest.approx(-0.05)


def test_mean_auc_skips_undefined_phrases():
    report = report_of(
        comparison("006", 0.90, 0.80),
        comparison("007", float("nan"), float("nan")),
    )
    assert report.mean_auc_multiclass == pytest.approx(0.90)
    assert report.mean_auc_binary == pytest.approx(0.80)


def test_verdict_calls_a_tie_a_tie():
    report = report_of(comparison("006", 0.90, 0.905), comparison("007", 0.88, 0.875))
    assert any("tie" in note for note in report.verdict())


def test_verdict_reports_a_real_difference():
    report = report_of(comparison("006", 0.70, 0.90), comparison("007", 0.72, 0.91))
    verdict = " ".join(report.verdict())
    assert "tie" not in verdict
    assert "2 phrase(s) better" in verdict


def test_verdict_warns_when_the_split_is_too_small():
    report = report_of(comparison("006", 0.9, 0.9, support=8))
    assert any("coarse" in note for note in report.verdict())


def test_verdict_flags_overlapping_intervals_as_no_evidence():
    """Two accuracies that differ on a handful of clips are not a finding."""
    discrimination = _discrimination(
        np.array([0, 0, 1, 1]),
        np.array([[0.9, 0.1, 0.0]] * 2 + [[0.1, 0.9, 0.0]] * 2),
        {"006": np.array([1.0, 1.0, 1.0, 1.0]), "007": np.array([0.0] * 4)},
        CLASS_NAMES,
        TARGETS,
    )
    report = report_of(comparison("006", 0.9, 0.9), discrimination=discrimination)
    assert any("cannot separate the two" in note for note in report.verdict())


def test_report_round_trips_to_json(tmp_path):
    discrimination = _discrimination(
        np.array([0, 1]),
        np.array([[0.9, 0.1, 0.0], [0.1, 0.9, 0.0]]),
        {"006": np.array([0.9, 0.1]), "007": np.array([0.1, 0.9])},
        CLASS_NAMES,
        TARGETS,
    )
    rejection = _rejection(
        np.array([0, 2]),
        np.array([[0.9, 0.05, 0.05], [0.1, 0.1, 0.8]]),
        {"006": np.array([0.9, 0.1]), "007": np.array([0.1, 0.1])},
        CLASS_NAMES,
        TARGETS,
        "unknown",
        0.5,
    )
    report = report_of(
        comparison("006", 0.9, 0.88), discrimination=discrimination, rejection=rejection
    )

    import json

    destination = report.save(tmp_path)
    payload = json.loads(destination.read_text(encoding="utf-8"))

    assert payload["phrases"] == TARGETS
    assert payload["per_phrase"][0]["auc_delta"] == pytest.approx(-0.02)
    assert payload["discrimination"]["multiclass_accuracy"] == 1.0
    assert payload["rejection"]["threshold"] == 0.5
    assert payload["verdict"]


def test_report_writes_undefined_metrics_as_null_not_nan(tmp_path):
    """A bare `NaN` is valid to Python's json and to nothing else."""
    report = report_of(comparison("006", float("nan"), float("nan"), support=0))
    raw = report.save(tmp_path).read_text(encoding="utf-8")

    assert "NaN" not in raw
    import json

    payload = json.loads(raw, parse_constant=lambda name: pytest.fail(f"bare {name}"))
    assert payload["per_phrase"][0]["auc_multiclass"] is None
    assert payload["mean_auc_binary"] is None


def test_summary_mentions_both_approaches():
    report = report_of(comparison("006", 0.9, 0.88))
    text = report.summary()
    assert "multi-class" in text and "one-vs-rest" in text


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------
@pytest.fixture
def wired(monkeypatch, records):
    """`compare_one_vs_rest` with training and TensorFlow stubbed out.

    The arithmetic is covered above; what this exercises is the wiring - that the
    right probabilities reach the right metric, and that every model is scored on
    the same evaluation tensors.
    """
    from src import experiments

    # Six clips in the fixture, two per class, one of each in `test`.
    truth = np.array([0, 1, 2])
    multiclass = np.array(
        [
            [0.80, 0.15, 0.05],  # 006 -> right
            [0.10, 0.85, 0.05],  # 007 -> right
            [0.20, 0.20, 0.60],  # unknown -> quiet
        ]
    )
    # `(rest, positive)` per clip. Two things are built in on purpose:
    #   * the 006 detector ranks a negative clip above its own positive one, so
    #     its per-phrase AUC (0.5) must come out below the multi-class model's;
    #   * on clip 1 (a real 007) the 006 detector is the louder of the two, so
    #     the committee vote picks the wrong phrase.
    binary = {
        "006": np.array([[0.7, 0.3], [0.1, 0.9], [0.9, 0.1]]),
        "007": np.array([[0.9, 0.1], [0.4, 0.6], [0.9, 0.1]]),
    }

    seen_datasets = []

    def fake_make_tf_dataset(*args, **kwargs):
        return "EVAL_DATASET"

    def fake_train(config, recs, label, extractor, **kwargs):
        return experiments.OneVsRestModel(
            label=label,
            run_name=kwargs.get("run_name") or label,
            model=f"MODEL_{label}",
            epochs_completed=7,
            best_val_accuracy=0.5,
            train_positives=1,
            train_negatives=2,
        )

    def fake_predict(model, dataset):
        seen_datasets.append(dataset)
        if model == "MULTICLASS":
            return truth, multiclass
        return truth, binary[str(model).removeprefix("MODEL_")]

    monkeypatch.setattr(experiments, "make_tf_dataset", fake_make_tf_dataset)
    monkeypatch.setattr(experiments, "train_one_vs_rest", fake_train)
    monkeypatch.setattr(experiments, "predict_dataset", fake_predict)
    return experiments, records, seen_datasets


def test_compare_scores_every_model_on_the_same_tensors(wired):
    experiments, records, seen_datasets = wired
    from src.config import Config

    report, detectors = experiments.compare_one_vs_rest(
        Config(), records, "MULTICLASS", extractor=None
    )

    # One multi-class pass plus one per detector, all over the same dataset object.
    assert len(seen_datasets) == 3
    assert set(seen_datasets) == {"EVAL_DATASET"}
    assert [detector.label for detector in detectors] == TARGETS
    assert report.phrases == TARGETS
    assert report.eval_split == "test"


def test_compare_pairs_each_phrase_with_its_own_scores(wired):
    experiments, records, _ = wired
    from src.config import Config

    report, _ = experiments.compare_one_vs_rest(
        Config(), records, "MULTICLASS", extractor=None
    )

    by_label = {item.label: item for item in report.per_phrase}
    assert set(by_label) == set(TARGETS)
    for item in by_label.values():
        assert item.support == 1
        assert item.negatives == 2
        assert item.binary_epochs == 7
    # The 006 detector ranks a negative above its own positive clip, so its AUC
    # has to land below the multi-class model's. If the two columns were reading
    # the same array this would come out equal.
    assert by_label["006"].auc_multiclass == 1.0
    assert by_label["006"].auc_binary == 0.5
    assert by_label["006"].auc_delta == pytest.approx(-0.5)


def test_compare_detects_the_committee_losing_a_phrase(wired):
    experiments, records, _ = wired
    from src.config import Config

    report, _ = experiments.compare_one_vs_rest(
        Config(), records, "MULTICLASS", extractor=None
    )

    assert report.discrimination.num_clips == 2
    assert report.discrimination.multiclass_correct == 2
    assert report.discrimination.committee_correct == 1
    assert report.discrimination.committee_confusions == [("007", "006", 1)]


def test_compare_rejects_a_model_with_the_wrong_output_width(wired):
    experiments, records, _ = wired
    from src.config import Config

    with pytest.raises(ValueError, match="different class vocabulary"):
        experiments.compare_one_vs_rest(
            Config(), records, "MODEL_006", extractor=None  # 2 outputs, 3 classes
        )


def test_compare_rejects_an_unknown_phrase(wired):
    experiments, records, _ = wired
    from src.config import Config

    with pytest.raises(ValueError, match="008"):
        experiments.compare_one_vs_rest(
            Config(), records, "MULTICLASS", extractor=None, phrases=["008"]
        )


def test_compare_rejects_an_empty_split(wired):
    experiments, records, _ = wired
    from src.config import Config

    with pytest.raises(ValueError, match="empty"):
        experiments.compare_one_vs_rest(
            Config(), records, "MULTICLASS", extractor=None, eval_split="nope"
        )


# ---------------------------------------------------------------------------
# Shared helper
# ---------------------------------------------------------------------------
def test_wilson_interval_brackets_the_estimate():
    low, high = wilson_interval(0.5, 100)
    assert low < 0.5 < high
    # A smaller sample must give a wider interval.
    wide_low, wide_high = wilson_interval(0.5, 10)
    assert (wide_high - wide_low) > (high - low)


def test_wilson_interval_is_nan_without_samples():
    low, high = wilson_interval(0.5, 0)
    assert np.isnan(low) and np.isnan(high)

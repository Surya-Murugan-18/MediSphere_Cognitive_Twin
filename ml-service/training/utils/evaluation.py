"""
Evaluation helpers — compute all required metrics on a fitted model.
All values are real; no placeholders or fabricated numbers.
"""
from __future__ import annotations

import numpy as np
from sklearn.metrics import (
    roc_auc_score,
    average_precision_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
    brier_score_loss,
    precision_recall_curve,
)


def compute_metrics(y_true: np.ndarray, y_prob: np.ndarray, threshold: float = 0.5) -> dict:
    """Return the full metric suite for binary classification."""
    y_pred = (y_prob >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred).ravel()
    return {
        "roc_auc":   round(float(roc_auc_score(y_true, y_prob)), 6),
        "pr_auc":    round(float(average_precision_score(y_true, y_prob)), 6),
        "precision": round(float(precision_score(y_true, y_pred, zero_division=0)), 6),
        "recall":    round(float(recall_score(y_true, y_pred, zero_division=0)), 6),
        "f1":        round(float(f1_score(y_true, y_pred, zero_division=0)), 6),
        "brier":     round(float(brier_score_loss(y_true, y_prob)), 6),
        "threshold": round(float(threshold), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def find_best_threshold_f1(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    """Find the threshold that maximises F1 on the provided split."""
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_prob)
    # thresholds has one fewer element than precisions/recalls
    f1_scores = np.where(
        (precisions[:-1] + recalls[:-1]) == 0,
        0.0,
        2 * precisions[:-1] * recalls[:-1] / (precisions[:-1] + recalls[:-1]),
    )
    if len(f1_scores) == 0:
        return 0.5
    best_idx = int(np.argmax(f1_scores))
    return float(thresholds[best_idx])


def find_best_threshold_recall_at_precision(
    y_true: np.ndarray,
    y_prob: np.ndarray,
    min_precision: float = 0.25,
) -> float:
    """
    Find the threshold that maximises recall subject to precision >= min_precision.
    Used for Readmission-30D where missing a readmission is more costly than a false alarm.
    """
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_prob)
    valid = precisions[:-1] >= min_precision
    if not np.any(valid):
        # No threshold meets min_precision — fall back to F1-maximising threshold
        return find_best_threshold_f1(y_true, y_prob)
    valid_recalls = np.where(valid, recalls[:-1], -1.0)
    best_idx = int(np.argmax(valid_recalls))
    return float(thresholds[best_idx])

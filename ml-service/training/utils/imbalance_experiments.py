"""
Imbalance experiment runner.
Three SEPARATE strategies — never auto-combined.
Returns fitted (pipeline, experiment_id) tuples.
"""
from __future__ import annotations

import numpy as np
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline as ImbPipeline
from sklearn.pipeline import Pipeline


def run_baseline(preprocessor, estimator, X_train, y_train):
    """No resampling, no class weighting."""
    pipe = Pipeline([("pre", preprocessor), ("clf", estimator)])
    pipe.fit(X_train, y_train)
    return pipe


def run_class_weight(preprocessor, estimator_cw, X_train, y_train):
    """Estimator already constructed with class_weight='balanced'."""
    pipe = Pipeline([("pre", preprocessor), ("clf", estimator_cw)])
    pipe.fit(X_train, y_train)
    return pipe


def run_smote(preprocessor, estimator, X_train, y_train, k_neighbors: int = 5):
    """
    SMOTE oversampling on the training fold only.
    Uses imblearn Pipeline so SMOTE is inside the pipeline fit
    (will NOT be applied at inference — imblearn Pipeline handles this correctly).
    """
    smote = SMOTE(random_state=42, k_neighbors=k_neighbors)
    pipe = ImbPipeline([
        ("pre",   preprocessor),
        ("smote", smote),
        ("clf",   estimator),
    ])
    pipe.fit(X_train, y_train)
    return pipe

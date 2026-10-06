"""
Diabetes-Risk Preprocessing Pipeline
======================================
Deployment-aligned feature set (8 features):
  Age, Sex, HighBP, HighChol, CholCheck, Stroke, HeartDiseaseorAttack, PhysHlth

Removed from training: BMI, GenHlth, Smoker, PhysActivity, Fruits, Veggies,
  HvyAlcoholConsump, NoDocbcCost, MentHlth, DiffWalk, Education, Income, AnyHealthcare

PhysHlth NOTE: BRFSS value (0-30 int) used in training.
               At inference, Alert count proxy is used (also 0-30 int, scaled the same way).
               This gap is documented and accepted.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.compose import ColumnTransformer

# ── Column definitions ────────────────────────────────────────────────────────
TRAINING_FEATURES = [
    "Age", "Sex", "HighBP", "HighChol", "CholCheck",
    "Stroke", "HeartDiseaseorAttack", "PhysHlth",
]
INFERENCE_FEATURES = TRAINING_FEATURES  # same — schema parity enforced

BINARY_COLS  = ["Sex", "HighBP", "HighChol", "CholCheck", "Stroke", "HeartDiseaseorAttack"]
SCALED_COLS  = ["Age", "PhysHlth"]  # ordinal/count → scale for LR

REMOVED_FROM_TRAINING = [
    "BMI", "GenHlth", "Smoker", "PhysActivity", "Fruits", "Veggies",
    "HvyAlcoholConsump", "NoDocbcCost", "MentHlth", "DiffWalk",
    "Education", "Income", "AnyHealthcare",
]

# BRFSS Age bracket → integer mapping
AGE_BRACKET_MAP = {
    (18, 24): 1,  (25, 29): 2,  (30, 34): 3,  (35, 39): 4,
    (40, 44): 5,  (45, 49): 6,  (50, 54): 7,  (55, 59): 8,
    (60, 64): 9,  (65, 69): 10, (70, 74): 11, (75, 79): 12,
    (80, 120): 13,
}


def age_years_to_bracket(age_years: int) -> int:
    """Convert age in years to BRFSS 1-13 bracket."""
    for (lo, hi), bracket in AGE_BRACKET_MAP.items():
        if lo <= age_years <= hi:
            return bracket
    return 13  # 80+


class ColumnSelector(BaseEstimator, TransformerMixin):
    def __init__(self, columns: list[str]):
        self.columns = columns

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=self.columns)
        X = X.copy()
        for col in X.columns:
            X[col] = X[col].astype(np.float64)
        return X[self.columns]


class PhysHlthClipper(BaseEstimator, TransformerMixin):
    """Clip PhysHlth (alert count or BRFSS value) to [0, 30]."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=TRAINING_FEATURES)
        X = X.copy()
        if "PhysHlth" in X.columns:
            X["PhysHlth"] = X["PhysHlth"].clip(0, 30)
        return X


def build_diabetes_preprocessor() -> Pipeline:
    """
    Build the Diabetes-Risk preprocessing pipeline.
    Single ColumnTransformer with named columns — robust at inference.
    Output: numpy array of shape (n, 8) — order: BINARY_COLS then SCALED_COLS.
    """
    return Pipeline([
        ("selector",     ColumnSelector(TRAINING_FEATURES)),
        ("phys_clip",    PhysHlthClipper()),
        ("transform",    _build_column_transformer()),
    ])


def _build_column_transformer() -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            ("bin", Pipeline([
                ("imputer", SimpleImputer(strategy="constant", fill_value=0.0)),
            ]), BINARY_COLS),
            ("scale", Pipeline([
                ("imputer", SimpleImputer(strategy="median")),
                ("scaler",  StandardScaler()),
            ]), SCALED_COLS),
        ],
        remainder="drop",
        verbose_feature_names_out=False,
    )


def prepare_diabetes_training_data(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    """
    Extract deployment-aligned features from raw BRFSS CSV.
    Drops all excluded columns. Returns (X, y).
    """
    # Drop exact duplicates before splitting (BRFSS survey artefact)
    df = df.drop_duplicates().reset_index(drop=True)
    y = df["Diabetes_binary"].astype(int)
    X = df[TRAINING_FEATURES].copy()
    return X, y


def get_feature_names_out() -> list[str]:
    """Return the final ordered feature names after preprocessing (for SHAP labelling)."""
    return BINARY_COLS + SCALED_COLS

"""
CVD-10Y Preprocessing Pipeline
===============================
Deployment-aligned feature set (12 features):
  male, age, sysBP, diaBP, heartRate, totChol, glucose,
  prevalentHyp, prevalentStroke, diabetes, BPMeds, pulse_pressure (engineered)

Removed from training: currentSmoker, cigsPerDay, BMI, education
Imputation: ONLY for transient missingness (lab not yet drawn, vitals not recorded).
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
BINARY_COLS  = ["male", "prevalentHyp", "prevalentStroke", "diabetes", "BPMeds"]
NUMERIC_COLS = ["age", "sysBP", "diaBP", "heartRate", "totChol", "glucose"]
# pulse_pressure is added by FeatureEngineer and then scaled

# Final ordered feature list consumed by the estimator
TRAINING_FEATURES = [
    "male", "age", "sysBP", "diaBP", "heartRate",
    "totChol", "glucose",
    "prevalentHyp", "prevalentStroke", "diabetes", "BPMeds",
    "pulse_pressure",
]
INFERENCE_FEATURES = TRAINING_FEATURES  # identical — schema parity enforced

# Raw columns from the dataset that the pipeline receives (before engineering)
RAW_FEATURES = ["male", "age", "sysBP", "diaBP", "heartRate",
                "totChol", "glucose",
                "prevalentHyp", "prevalentStroke", "diabetes", "BPMeds"]

# Columns present in the raw Framingham CSV that are EXCLUDED from training
EXCLUDED_FROM_TRAINING = ["currentSmoker", "cigsPerDay", "BMI", "education", "TenYearCHD"]

# Numeric columns to scale (after engineering, before imputation)
SCALED_COLS = NUMERIC_COLS + ["pulse_pressure"]


# ── Custom transformers ───────────────────────────────────────────────────────

class OutlierClipper(BaseEstimator, TransformerMixin):
    """Clip extreme physiological values before scaling."""
    _CLIPS = {
        "sysBP":    (50.0,  300.0),
        "diaBP":    (30.0,  200.0),
        "totChol":  (100.0, 600.0),
        "heartRate":(30.0,  220.0),
        "glucose":  (30.0,  500.0),
    }

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=RAW_FEATURES)
        X = X.copy()
        for col, (lo, hi) in self._CLIPS.items():
            if col in X.columns:
                X[col] = X[col].clip(lower=lo, upper=hi)
        return X


class PulsePressureEngineer(BaseEstimator, TransformerMixin):
    """Add pulse_pressure = sysBP - diaBP."""

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=RAW_FEATURES)
        X = X.copy()
        X["pulse_pressure"] = X["sysBP"] - X["diaBP"]
        return X


class ColumnSelector(BaseEstimator, TransformerMixin):
    """Select and order columns by name."""
    def __init__(self, columns: list[str]):
        self.columns = columns

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=self.columns)
        X = X.copy()
        # Cast all columns to float64 to avoid dtype mismatch with imputers
        for col in X.columns:
            X[col] = X[col].astype(np.float64)
        return X[self.columns]


def build_cvd_preprocessor() -> Pipeline:
    """
    Build the CVD preprocessing pipeline.
    Works on DataFrames with the raw feature columns.
    Output is a numpy array of shape (n_samples, 12) in TRAINING_FEATURES order.
    """
    return Pipeline([
        ("clipper",   OutlierClipper()),
        ("engineer",  PulsePressureEngineer()),
        ("selector",  ColumnSelector(TRAINING_FEATURES)),
        ("transform", _build_column_transformer()),
    ])


def _build_column_transformer() -> ColumnTransformer:
    """
    Single ColumnTransformer that:
    - Imputes + scales numeric columns (age, sysBP, diaBP, heartRate, totChol, glucose, pulse_pressure)
    - Imputes binary columns (male, prevalentHyp, prevalentStroke, diabetes, BPMeds) with 0
    Output column order: scaled numeric first, then binary (passthrough).
    """
    return ColumnTransformer(
        transformers=[
            ("num", Pipeline([
                ("imputer", SimpleImputer(strategy="median")),
                ("scaler",  StandardScaler()),
            ]), SCALED_COLS),
            ("bin", Pipeline([
                ("imputer", SimpleImputer(strategy="constant", fill_value=0.0)),
            ]), BINARY_COLS),
        ],
        remainder="drop",
        verbose_feature_names_out=False,
    )


def prepare_cvd_training_data(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    """
    Extract the deployment-aligned feature matrix and target from the raw Framingham CSV.
    Drops all excluded columns. Returns (X, y).
    """
    y = df["TenYearCHD"].copy()
    raw_cols = [c for c in RAW_FEATURES if c in df.columns]
    X = df[raw_cols].copy()
    return X, y


def get_feature_names_out() -> list[str]:
    """Return the final ordered feature names after preprocessing (for SHAP labelling)."""
    return SCALED_COLS + BINARY_COLS

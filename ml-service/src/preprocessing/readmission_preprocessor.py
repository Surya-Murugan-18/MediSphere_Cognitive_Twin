"""
Readmission-30D Preprocessing Pipeline
========================================
Prediction time: EARLY HOSPITALIZATION — at or shortly after admission.

Deployment-aligned feature set (9 feature groups):
  gender, age_midpoint, number_diagnoses,
  diag_group_1, diag_group_2, diag_group_3,
  number_emergency, diabetesMed, change

Mandatory exclusions (leakage / identifier / encounter-accumulating):
  encounter_id, patient_nbr, readmitted, discharge_disposition_id,
  time_in_hospital, weight, max_glu_serum, A1Cresult, payer_code,
  num_lab_procedures, num_procedures, num_medications

Deployment-alignment removals (no MediSphere equivalent):
  race, admission_type_id, admission_source_id, medical_specialty,
  number_inpatient, number_outpatient,
  22 individual medication columns → n_meds_up/down/steady (removed entirely)
"""
from __future__ import annotations

import re
import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer
from sklearn.compose import ColumnTransformer

from training.utils.condition_mapper import icd_code_to_group, all_groups

# ── Column definitions ────────────────────────────────────────────────────────
# These are the feature groups AFTER transformation
TRAINING_FEATURES_RAW = [
    "gender", "age_midpoint", "number_diagnoses",
    "diag_group_1", "diag_group_2", "diag_group_3",
    "number_emergency", "diabetesMed", "change",
]
TRAINING_FEATURES = TRAINING_FEATURES_RAW  # same at training and inference
INFERENCE_FEATURES = TRAINING_FEATURES_RAW

BINARY_COLS   = ["gender", "diabetesMed", "change"]
NUMERIC_COLS  = ["age_midpoint", "number_diagnoses", "number_emergency"]
CATEG_COLS    = ["diag_group_1", "diag_group_2", "diag_group_3"]

DIAG_CATEGORIES = all_groups()  # fixed set of valid ICD group names

# Age bracket → midpoint integer
AGE_BRACKET_MAP = {
    "[0-10)": 5,   "[10-20)": 15, "[20-30)": 25, "[30-40)": 35,
    "[40-50)": 45, "[50-60)": 55, "[60-70)": 65, "[70-80)": 75,
    "[80-90)": 85, "[90-100)": 95,
}

# Rows to drop from training only (expired patients — cannot be readmitted)
EXPIRED_DISPOSITION_IDS = {11, 19, 20, 21}

# All mandatory exclusion columns
MANDATORY_EXCLUSIONS = [
    "encounter_id", "patient_nbr", "readmitted",
    "discharge_disposition_id", "time_in_hospital",
    "weight", "max_glu_serum", "A1Cresult", "payer_code",
    "num_lab_procedures", "num_procedures", "num_medications",
]

DEPLOYMENT_REMOVALS = [
    "race", "admission_type_id", "admission_source_id", "medical_specialty",
    "number_inpatient", "number_outpatient",
]

MEDICATION_COLS = [
    "metformin", "repaglinide", "nateglinide", "chlorpropamide", "glimepiride",
    "acetohexamide", "glipizide", "glyburide", "tolbutamide", "pioglitazone",
    "rosiglitazone", "acarbose", "miglitol", "troglitazone", "tolazamide",
    "examide", "citoglipton", "insulin", "glyburide-metformin",
    "glipizide-metformin", "glimepiride-pioglitazone",
    "metformin-rosiglitazone", "metformin-pioglitazone",
]


# ── Custom transformers ───────────────────────────────────────────────────────

class AgeConverter(BaseEstimator, TransformerMixin):
    """Convert age bracket string → midpoint integer."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        X["age_midpoint"] = X["age"].map(AGE_BRACKET_MAP).fillna(55).astype(float)
        return X


class TargetBinarizer(BaseEstimator, TransformerMixin):
    """Convert readmitted → readmitted_binary. Training only."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        if "readmitted" in X.columns:
            X["readmitted_binary"] = (X["readmitted"] == "<30").astype(int)
        return X


class GenderEncoder(BaseEstimator, TransformerMixin):
    """Encode gender: Male=1, else=0."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        X["gender"] = (X["gender"].str.strip().str.lower() == "male").astype(int)
        return X


class DiabetesMedEncoder(BaseEstimator, TransformerMixin):
    """Encode diabetesMed: Yes=1, No=0."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        X["diabetesMed"] = (X["diabetesMed"].str.strip().str.lower() == "yes").astype(int)
        return X


class ChangeEncoder(BaseEstimator, TransformerMixin):
    """Encode change: Ch=1, No=0."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        X["change"] = (X["change"].str.strip().str.lower() == "ch").astype(int)
        return X


class ICDGrouper(BaseEstimator, TransformerMixin):
    """Map diag_1/2/3 ICD codes → clinical chapter group strings."""
    def fit(self, X, y=None):
        return self

    def transform(self, X):
        X = X.copy()
        for col, grp_col in [("diag_1", "diag_group_1"),
                              ("diag_2", "diag_group_2"),
                              ("diag_3", "diag_group_3")]:
            if col in X.columns:
                X[grp_col] = X[col].apply(icd_code_to_group)
        return X


class ColumnSelector(BaseEstimator, TransformerMixin):
    def __init__(self, columns: list[str]):
        self.columns = columns

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        # Fill missing columns with defaults before selecting
        if isinstance(X, np.ndarray):
            X = pd.DataFrame(X, columns=self.columns)
        X = X.copy()
        for col in self.columns:
            if col not in X.columns:
                if col in CATEG_COLS:
                    X[col] = "Unknown"
                elif col in BINARY_COLS:
                    X[col] = 0.0
                else:
                    X[col] = 0.0
        # Cast binary and numeric to float so imputers don't complain about dtype mismatch
        for col in BINARY_COLS + NUMERIC_COLS:
            if col in X.columns:
                X[col] = X[col].astype(float)
        return X[self.columns]


def build_readmission_preprocessor() -> Pipeline:
    """
    Build the Readmission-30D preprocessing pipeline.
    Applied to the transformed feature DataFrame (after raw dataset pre-processing).
    """
    return Pipeline([
        ("selector", ColumnSelector(TRAINING_FEATURES_RAW)),
        ("ct",       _build_column_transformer()),
    ])


def _build_column_transformer() -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            # Binary features — impute with 0 for safety, pass through
            ("bin", SimpleImputer(strategy="constant", fill_value=0.0), BINARY_COLS),
            # Numeric features — impute with median, then scale
            ("num", Pipeline([
                ("imp",   SimpleImputer(strategy="median")),
                ("scale", StandardScaler()),
            ]), NUMERIC_COLS),
            # Categorical diagnosis groups — OHE with known categories
            ("cat", Pipeline([
                ("imp", SimpleImputer(strategy="constant", fill_value="Unknown")),
                ("ohe", OneHotEncoder(
                    categories=[DIAG_CATEGORIES] * 3,
                    handle_unknown="ignore",
                    sparse_output=False,
                )),
            ]), CATEG_COLS),
        ],
        remainder="drop",
        verbose_feature_names_out=False,
    )


def prepare_readmission_training_data(
    df: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.Series, pd.Series]:
    """
    Full raw CSV → deployment-aligned feature matrix + target + patient_nbr groups.
    Steps:
      1. Binarize target
      2. Drop expired patients (training only)
      3. Convert age bracket
      4. Encode gender / diabetesMed / change
      5. Map ICD codes → group strings
      6. Select deployment-aligned columns only

    Returns (X, y, groups) where groups = patient_nbr for GroupKFold splitting.
    """
    df = df.copy()

    # 1. Binarize target
    df["readmitted_binary"] = (df["readmitted"] == "<30").astype(int)

    # 2. Drop expired patients
    if "discharge_disposition_id" in df.columns:
        df = df[~df["discharge_disposition_id"].isin(EXPIRED_DISPOSITION_IDS)].reset_index(drop=True)

    # Preserve patient groups for splitting
    groups = df["patient_nbr"].copy()

    # 3-5. Transform columns
    df = AgeConverter().transform(df)
    df = GenderEncoder().transform(df)
    df = DiabetesMedEncoder().transform(df)
    df = ChangeEncoder().transform(df)
    df = ICDGrouper().transform(df)

    # 6. Target and features
    y = df["readmitted_binary"].reset_index(drop=True)

    feature_cols = [c for c in TRAINING_FEATURES_RAW if c in df.columns]
    X = df[feature_cols].copy().reset_index(drop=True)

    return X, y, groups.reset_index(drop=True)


def get_feature_names_out(preprocessor) -> list[str]:
    """Return the final feature names after ColumnTransformer (for SHAP labelling)."""
    try:
        return list(preprocessor.named_steps["ct"].get_feature_names_out())
    except Exception:
        return BINARY_COLS + NUMERIC_COLS + [
            f"diag_group_{i+1}_{g}"
            for i in range(3)
            for g in DIAG_CATEGORIES
        ]

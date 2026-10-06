"""
Dataset splitting utilities — stratified for CVD/BRFSS, patient-grouped for Readmission.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.model_selection import StratifiedShuffleSplit, StratifiedGroupKFold


def stratified_split(
    X: pd.DataFrame,
    y: pd.Series,
    val_size: float = 0.15,
    test_size: float = 0.15,
    random_state: int = 42,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame,
           pd.Series,   pd.Series,   pd.Series]:
    """
    70/15/15 stratified split.
    Returns: X_train, X_val, X_test, y_train, y_val, y_test
    """
    # First cut off test set
    sss_test = StratifiedShuffleSplit(
        n_splits=1, test_size=test_size, random_state=random_state
    )
    train_val_idx, test_idx = next(sss_test.split(X, y))

    X_tv, y_tv = X.iloc[train_val_idx], y.iloc[train_val_idx]
    X_test, y_test = X.iloc[test_idx], y.iloc[test_idx]

    # Now cut val from train+val
    relative_val = val_size / (1.0 - test_size)
    sss_val = StratifiedShuffleSplit(
        n_splits=1, test_size=relative_val, random_state=random_state
    )
    train_idx, val_idx = next(sss_val.split(X_tv, y_tv))

    X_train = X_tv.iloc[train_idx].reset_index(drop=True)
    X_val   = X_tv.iloc[val_idx].reset_index(drop=True)
    X_test  = X_test.reset_index(drop=True)
    y_train = y_tv.iloc[train_idx].reset_index(drop=True)
    y_val   = y_tv.iloc[val_idx].reset_index(drop=True)
    y_test  = y_test.reset_index(drop=True)

    return X_train, X_val, X_test, y_train, y_val, y_test


def patient_grouped_split(
    df: pd.DataFrame,
    target_col: str,
    group_col: str = "patient_nbr",
    n_splits: int = 7,
    random_state: int = 42,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """
    Patient-grouped stratified split for Readmission-30D.
    Uses StratifiedGroupKFold so all encounters for a patient stay in one partition.
    Returns train_df, val_df, test_df (all with group_col still present for logging).
    """
    X = df.drop(columns=[target_col])
    y = df[target_col].values
    groups = df[group_col].values

    sgkf = StratifiedGroupKFold(n_splits=n_splits, shuffle=True, random_state=random_state)

    # First fold → ~85% train+val, ~15% test
    splits = list(sgkf.split(X, y, groups))
    tv_idx, test_idx = splits[0]

    df_tv   = df.iloc[tv_idx].reset_index(drop=True)
    df_test = df.iloc[test_idx].reset_index(drop=True)

    # Second fold on train+val → ~82/18 of the tv portion → effectively ~70/15 of total
    y_tv     = df_tv[target_col].values
    groups_tv = df_tv[group_col].values
    X_tv     = df_tv.drop(columns=[target_col])

    sgkf2 = StratifiedGroupKFold(n_splits=n_splits, shuffle=True, random_state=random_state + 1)
    splits2 = list(sgkf2.split(X_tv, y_tv, groups_tv))
    train_idx2, val_idx2 = splits2[0]

    df_train = df_tv.iloc[train_idx2].reset_index(drop=True)
    df_val   = df_tv.iloc[val_idx2].reset_index(drop=True)

    return df_train, df_val, df_test

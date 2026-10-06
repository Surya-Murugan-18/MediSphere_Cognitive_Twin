"""
Readmission-30D Training Script
=================================
Prediction time: EARLY HOSPITALIZATION.
Runs 9 experiments: 3 algorithms x 3 imbalance strategies.
Patient-grouped split via StratifiedGroupKFold.
Selects winner on validation AUROC. Calibrates. Evaluates once on test set.
"""
import os, sys, json, hashlib, warnings, datetime
warnings.filterwarnings("ignore")

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE)

import numpy as np
import pandas as pd
import joblib
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.calibration import CalibratedClassifierCV
from sklearn.pipeline import Pipeline
from imblearn.over_sampling import SMOTE
from xgboost import XGBClassifier
from sklearn.model_selection import StratifiedGroupKFold

from src.preprocessing.readmission_preprocessor import (
    build_readmission_preprocessor, prepare_readmission_training_data,
    TRAINING_FEATURES, MANDATORY_EXCLUSIONS, DEPLOYMENT_REMOVALS,
    get_feature_names_out,
)
from training.utils.evaluation import (
    compute_metrics, find_best_threshold_recall_at_precision,
)

RANDOM_STATE = 42
np.random.seed(RANDOM_STATE)

DATASET_PATH = os.path.join(BASE, "datasets", "diabetic_data.csv")
ARTIFACT_DIR = os.path.join(BASE, "models", "readmission_30d", "v1.0.0")
RESULTS_DIR  = os.path.join(BASE, "training", "results")
SPLITS_DIR   = os.path.join(BASE, "training", "splits")
os.makedirs(ARTIFACT_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR,  exist_ok=True)
os.makedirs(SPLITS_DIR,   exist_ok=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


# ─────────────────────────────────────────────────────────────────────────────
print("=" * 60)
print("READMISSION-30D TRAINING")
print("=" * 60)

df_raw = pd.read_csv(DATASET_PATH, na_values=["?", "None", "none"], low_memory=False)
dataset_hash = sha256(DATASET_PATH)
print(f"Dataset: {len(df_raw)} rows | SHA256: {dataset_hash[:16]}...")

# Prepare: binarize target, drop expired, convert features, select deployment columns
X_all, y_all, groups_all = prepare_readmission_training_data(df_raw)
print(f"After expired-row filter: {len(X_all)} rows")
print(f"Features: {list(X_all.columns)}")
print(f"Target dist: {y_all.value_counts().to_dict()}")
print(f"Unique patients: {groups_all.nunique()}")

# ─────────────────────────────────────────────────────────────────────────────
# Patient-grouped stratified split (70/15/15 by patient)
# ─────────────────────────────────────────────────────────────────────────────
sgkf = StratifiedGroupKFold(n_splits=7, shuffle=True, random_state=RANDOM_STATE)
splits = list(sgkf.split(X_all, y_all, groups_all))

# First fold → ~85% train+val, ~15% test
tv_idx, test_idx = splits[0]

X_tv,   y_tv,   g_tv   = X_all.iloc[tv_idx],   y_all.iloc[tv_idx],   groups_all.iloc[tv_idx]
X_test, y_test, g_test = X_all.iloc[test_idx], y_all.iloc[test_idx], groups_all.iloc[test_idx]

# Second fold on train+val → ~82/18 of the tv partition
sgkf2 = StratifiedGroupKFold(n_splits=7, shuffle=True, random_state=RANDOM_STATE + 1)
splits2 = list(sgkf2.split(X_tv, y_tv, g_tv))
train_idx2, val_idx2 = splits2[0]

X_train, y_train = X_tv.iloc[train_idx2], y_tv.iloc[train_idx2]
X_val,   y_val   = X_tv.iloc[val_idx2],   y_tv.iloc[val_idx2]

print(f"Train: {len(X_train)} | Val: {len(X_val)} | Test: {len(X_test)}")

# Save split info for reproducibility
split_info = {
    "train_indices": train_idx2.tolist(),
    "val_indices":   val_idx2.tolist(),
    "test_indices":  test_idx.tolist(),
    "n_train": len(X_train), "n_val": len(X_val), "n_test": len(X_test),
}
with open(os.path.join(SPLITS_DIR, "readmission_splits.json"), "w") as f:
    json.dump(split_info, f)

# ─────────────────────────────────────────────────────────────────────────────
# Pre-fit preprocessor on training data
# ─────────────────────────────────────────────────────────────────────────────
_shared_pre = build_readmission_preprocessor()
_shared_pre.fit(X_train, y_train)
X_train_pre = _shared_pre.transform(X_train)
X_val_pre   = _shared_pre.transform(X_val)
print(f"Preprocessed train shape: {X_train_pre.shape}")

# Verify no leakage columns survived
feature_names_pre = None
try:
    ct = _shared_pre.named_steps["ct"]
    feature_names_pre = list(ct.get_feature_names_out())
    print(f"Output feature names ({len(feature_names_pre)}): {feature_names_pre[:5]}...")
except Exception as e:
    print(f"Could not get feature names: {e}")

neg_pos_ratio = float(y_train.value_counts()[0] / y_train.value_counts()[1])
print(f"Neg/pos ratio: {neg_pos_ratio:.2f}")


def make_estimators(experiment: str):
    cw = "balanced" if experiment == "class_weight" else None
    spw = neg_pos_ratio if experiment == "class_weight" else 1.0
    return [
        ("lr",  LogisticRegression(max_iter=2000, solver="lbfgs",
                                   class_weight=cw, random_state=RANDOM_STATE)),
        ("rf",  RandomForestClassifier(n_estimators=300, class_weight=cw,
                                       random_state=RANDOM_STATE, n_jobs=-1,
                                       max_depth=20)),
        ("xgb", XGBClassifier(n_estimators=300, learning_rate=0.05,
                               scale_pos_weight=spw,
                               random_state=RANDOM_STATE,
                               eval_metric="aucpr",
                               verbosity=0, n_jobs=-1)),
    ]


# ─────────────────────────────────────────────────────────────────────────────
# Run all 9 experiments
# ─────────────────────────────────────────────────────────────────────────────
results = []

for experiment in ["baseline", "class_weight", "smote"]:
    print(f"\n--- Experiment: {experiment} ---")

    for alg_name, estimator in make_estimators(experiment):
        print(f"  Fitting {alg_name}...", end=" ", flush=True)

        if experiment == "smote":
            smote = SMOTE(random_state=RANDOM_STATE, k_neighbors=5)
            X_res, y_res = smote.fit_resample(X_train_pre, y_train)
            estimator.fit(X_res, y_res)
        else:
            estimator.fit(X_train_pre, y_train)

        y_prob_val = estimator.predict_proba(X_val_pre)[:, 1]
        # Readmission uses recall-at-precision threshold (clinical cost of FN > FP)
        threshold  = find_best_threshold_recall_at_precision(
            y_val.values, y_prob_val, min_precision=0.25)
        metrics    = compute_metrics(y_val.values, y_prob_val, threshold)

        print(f"val AUROC={metrics['roc_auc']:.4f}  F1={metrics['f1']:.4f}  "
              f"thr={metrics['threshold']:.3f}")

        results.append({
            "model_id":   "Readmission-30D",
            "algorithm":  alg_name,
            "experiment": experiment,
            "split":      "val",
            "estimator":  estimator,
            "threshold":  threshold,
            **{k: v for k, v in metrics.items() if k not in ("confusion_matrix", "threshold")},
        })

# Select winner
exp_order = {"class_weight": 0, "smote": 1, "baseline": 2}
results_sorted = sorted(results,
    key=lambda r: (-r["roc_auc"], exp_order.get(r["experiment"], 9)))
winner = results_sorted[0]
print(f"\n{'='*60}")
print(f"WINNER: {winner['algorithm']} / {winner['experiment']} "
      f"— val AUROC={winner['roc_auc']:.4f}")

# ─────────────────────────────────────────────────────────────────────────────
# Calibrate
# ─────────────────────────────────────────────────────────────────────────────
print("Calibrating...")
calibrated = CalibratedClassifierCV(winner["estimator"], method="isotonic", cv=None)
calibrated.fit(X_val_pre, y_val)

y_prob_cal = calibrated.predict_proba(X_val_pre)[:, 1]
final_threshold = find_best_threshold_recall_at_precision(
    y_val.values, y_prob_cal, min_precision=0.25)
val_metrics_cal = compute_metrics(y_val.values, y_prob_cal, final_threshold)
print(f"Post-cal val: AUROC={val_metrics_cal['roc_auc']:.4f}  "
      f"F1={val_metrics_cal['f1']:.4f}  thr={final_threshold:.3f}")

final_pipeline = Pipeline([("pre", _shared_pre), ("clf", calibrated)])

# ─────────────────────────────────────────────────────────────────────────────
# One-shot test-set evaluation
# ─────────────────────────────────────────────────────────────────────────────
X_test_pre = _shared_pre.transform(X_test)
y_prob_test = calibrated.predict_proba(X_test_pre)[:, 1]
test_metrics = compute_metrics(y_test.values, y_prob_test, final_threshold)
print(f"\nTEST: AUROC={test_metrics['roc_auc']:.4f}  "
      f"PR-AUC={test_metrics['pr_auc']:.4f}  "
      f"Precision={test_metrics['precision']:.4f}  "
      f"Recall={test_metrics['recall']:.4f}  "
      f"F1={test_metrics['f1']:.4f}  Brier={test_metrics['brier']:.4f}")
print(f"Confusion: {test_metrics['confusion_matrix']}")

# ─────────────────────────────────────────────────────────────────────────────
# SHAP background dataset
# ─────────────────────────────────────────────────────────────────────────────
print("Building SHAP background...")
pos_idx = np.where(y_train.values == 1)[0]
neg_idx = np.where(y_train.values == 0)[0]
rng = np.random.default_rng(RANDOM_STATE)
bg_pos = rng.choice(pos_idx, size=min(100, len(pos_idx)), replace=False)
bg_neg = rng.choice(neg_idx, size=min(100, len(neg_idx)), replace=False)
X_background = X_train_pre[np.concatenate([bg_pos, bg_neg])]
print(f"Background shape: {X_background.shape}")

# ─────────────────────────────────────────────────────────────────────────────
# Save artifacts
# ─────────────────────────────────────────────────────────────────────────────
joblib.dump(final_pipeline, os.path.join(ARTIFACT_DIR, "pipeline.joblib"), compress=3)
joblib.dump(X_background,   os.path.join(ARTIFACT_DIR, "shap_background.joblib"), compress=3)

import sklearn, xgboost, shap as _shap, imblearn
metadata = {
    "model_id": "Readmission-30D",
    "version":  "1.0.0",
    "description": "30-day hospital readmission risk — UCI Diabetes 130-US Hospitals",
    "target":   "readmitted_binary (<30=1, else=0)",
    "dataset":  "diabetic_data.csv",
    "dataset_sha256": dataset_hash,
    "algorithm":  winner["algorithm"],
    "imbalance_experiment": winner["experiment"],
    "training_features":  TRAINING_FEATURES,
    "inference_features": TRAINING_FEATURES,
    "features_removed_from_training": MANDATORY_EXCLUSIONS + DEPLOYMENT_REMOVALS + [
        "22 individual medication columns (n_meds_up/down/steady)",
    ],
    "proxy_features": {
        "diag_group_1/2/3": "Patient.conditions keyword → ICD chapter group",
        "number_emergency": "Count of HIGH-severity Alert records in prior 365 days",
        "diabetesMed": "Patient.conditions + CarePlan.recommendations[*].intervention keyword match",
        "change": "CarePlan.updatedAt within past 30 days",
    },
    "prediction_threshold": round(final_threshold, 4),
    "calibration_method": "isotonic",
    "prediction_time_assumption": "early_hospitalization",
    "evaluation": {
        "val":  {k: v for k, v in val_metrics_cal.items() if k != "confusion_matrix"},
        "test": {k: v for k, v in test_metrics.items()    if k != "confusion_matrix"},
        "test_confusion_matrix": test_metrics["confusion_matrix"],
    },
    "n_train": len(X_train), "n_val": len(X_val), "n_test": len(X_test),
    "class_distribution": {
        "train_pos": int(y_train.sum()),
        "train_neg": int((y_train == 0).sum()),
    },
    "patient_split": {
        "method": "StratifiedGroupKFold",
        "n_splits": 7,
        "n_patients_total": int(groups_all.nunique()),
    },
    "random_state": RANDOM_STATE,
    "python_version": sys.version.split()[0],
    "library_versions": {
        "scikit-learn": sklearn.__version__,
        "xgboost":      xgboost.__version__,
        "shap":         _shap.__version__,
        "imbalanced-learn": imblearn.__version__,
    },
    "created_at": datetime.datetime.utcnow().isoformat() + "Z",
    "created_by": "training/train_readmission.py",
}
with open(os.path.join(ARTIFACT_DIR, "metadata.json"), "w") as f:
    json.dump(metadata, f, indent=2)

results_rows = [{k: v for k, v in r.items() if k != "estimator"} for r in results]
pd.DataFrame(results_rows).to_csv(
    os.path.join(RESULTS_DIR, "readmission_results.csv"), index=False)

print(f"\nArtifacts saved to: {ARTIFACT_DIR}")
print("READMISSION-30D TRAINING COMPLETE")

"""
CVD-10Y Training Script
=======================
Runs 9 experiments: 3 algorithms x 3 imbalance strategies.
Selects winner on validation AUROC. Calibrates. Evaluates once on test set.
Saves pipeline.joblib, shap_background.joblib, metadata.json.
"""
import os, sys, json, hashlib, warnings, datetime
warnings.filterwarnings("ignore")

# ── Path setup ──────────────────────────────────────────────────────────────
BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE)

import numpy as np
import pandas as pd
import joblib
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.calibration import CalibratedClassifierCV
from sklearn.model_selection import RandomizedSearchCV
from sklearn.pipeline import Pipeline
from imblearn.pipeline import Pipeline as ImbPipeline
from imblearn.over_sampling import SMOTE
from xgboost import XGBClassifier

from src.preprocessing.cvd_preprocessor import (
    build_cvd_preprocessor, prepare_cvd_training_data, get_feature_names_out,
    TRAINING_FEATURES, BINARY_COLS,
)
from training.utils.splitters import stratified_split
from training.utils.evaluation import (
    compute_metrics, find_best_threshold_f1,
)

RANDOM_STATE = 42
np.random.seed(RANDOM_STATE)

DATASET_PATH = os.path.join(BASE, "datasets", "framingham.csv")
ARTIFACT_DIR = os.path.join(BASE, "models", "cvd_risk", "v1.0.0")
RESULTS_DIR  = os.path.join(BASE, "training", "results")
os.makedirs(ARTIFACT_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR,  exist_ok=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


# ─────────────────────────────────────────────────────────────────────────────
# 1. Load and split
# ─────────────────────────────────────────────────────────────────────────────
print("=" * 60)
print("CVD-10Y TRAINING")
print("=" * 60)

df_raw = pd.read_csv(DATASET_PATH)
dataset_hash = sha256(DATASET_PATH)
print(f"Dataset: {len(df_raw)} rows | SHA256: {dataset_hash[:16]}...")

X_raw, y = prepare_cvd_training_data(df_raw)
print(f"Features used: {list(X_raw.columns)}")
print(f"Target dist: {y.value_counts().to_dict()}")

X_train, X_val, X_test, y_train, y_val, y_test = stratified_split(
    X_raw, y, val_size=0.15, test_size=0.15, random_state=RANDOM_STATE
)
print(f"Train: {len(X_train)} | Val: {len(X_val)} | Test: {len(X_test)}")


# ─────────────────────────────────────────────────────────────────────────────
# 2. Algorithm definitions
# ─────────────────────────────────────────────────────────────────────────────
def make_estimators(experiment: str):
    """Return (name, estimator) pairs for the three algorithms."""
    cw = "balanced" if experiment == "class_weight" else None
    return [
        ("lr",  LogisticRegression(max_iter=1000, solver="lbfgs",
                                   class_weight=cw, random_state=RANDOM_STATE)),
        ("rf",  RandomForestClassifier(n_estimators=300, class_weight=cw,
                                       random_state=RANDOM_STATE, n_jobs=-1)),
        ("xgb", XGBClassifier(n_estimators=300, learning_rate=0.05,
                               scale_pos_weight=(y_train.value_counts()[0] /
                                                  y_train.value_counts()[1])
                               if experiment == "class_weight" else 1.0,
                               random_state=RANDOM_STATE,
                               eval_metric="aucpr",
                               verbosity=0)),
    ]


# ─────────────────────────────────────────────────────────────────────────────
# 3. Run all 9 experiments
# ─────────────────────────────────────────────────────────────────────────────
results = []

# Pre-fit a preprocessor once on training data and transform all splits
# This avoids the nested-Pipeline issue with imblearn while keeping SMOTE correct.
_shared_pre = build_cvd_preprocessor()
_shared_pre.fit(X_train, y_train)
X_train_pre = _shared_pre.transform(X_train)
X_val_pre_shared = _shared_pre.transform(X_val)

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

        y_prob_val = estimator.predict_proba(X_val_pre_shared)[:, 1]
        threshold  = find_best_threshold_f1(y_val.values, y_prob_val)
        metrics    = compute_metrics(y_val.values, y_prob_val, threshold)

        print(f"val AUROC={metrics['roc_auc']:.4f}  F1={metrics['f1']:.4f}  "
              f"thr={metrics['threshold']:.3f}")

        results.append({
            "model_id":    "CVD-10Y",
            "algorithm":   alg_name,
            "experiment":  experiment,
            "split":       "val",
            "estimator":   estimator,   # fitted estimator (pre-processor shared)
            "threshold":   threshold,
            **{k: v for k, v in metrics.items() if k not in ("confusion_matrix", "threshold")},
        })

# ─────────────────────────────────────────────────────────────────────────────
# 4. Select winner (highest val AUROC; tie → prefer class_weight > smote > baseline)
# ─────────────────────────────────────────────────────────────────────────────
exp_order = {"class_weight": 0, "smote": 1, "baseline": 2}
results_sorted = sorted(
    results,
    key=lambda r: (-r["roc_auc"], exp_order.get(r["experiment"], 9))
)
winner = results_sorted[0]
print(f"\n{'='*60}")
print(f"WINNER: {winner['algorithm']} / {winner['experiment']} "
      f"— val AUROC={winner['roc_auc']:.4f}")

# ─────────────────────────────────────────────────────────────────────────────
# 5. Calibrate on validation set
# ─────────────────────────────────────────────────────────────────────────────
print("Calibrating with isotonic regression...")
best_estimator = winner["estimator"]
pre_step = _shared_pre   # already fitted

calibrated = CalibratedClassifierCV(best_estimator, method="isotonic", cv=None)
calibrated.fit(X_val_pre_shared, y_val)

# Re-determine threshold on validation with calibrated probabilities
y_prob_cal_val = calibrated.predict_proba(X_val_pre_shared)[:, 1]
final_threshold = find_best_threshold_f1(y_val.values, y_prob_cal_val)
val_metrics_cal = compute_metrics(y_val.values, y_prob_cal_val, final_threshold)
print(f"Post-calibration val: AUROC={val_metrics_cal['roc_auc']:.4f}  "
      f"F1={val_metrics_cal['f1']:.4f}  thr={final_threshold:.3f}")

# Build the serialisable final pipeline (pre + calibrated estimator)
final_pipeline = Pipeline([
    ("pre", pre_step),
    ("clf", calibrated),
])

# ─────────────────────────────────────────────────────────────────────────────
# 6. One-shot test-set evaluation
# ─────────────────────────────────────────────────────────────────────────────
X_test_pre = _shared_pre.transform(X_test)
y_prob_test = calibrated.predict_proba(X_test_pre)[:, 1]
test_metrics = compute_metrics(y_test.values, y_prob_test, final_threshold)
print(f"\nTEST SET: AUROC={test_metrics['roc_auc']:.4f}  "
      f"PR-AUC={test_metrics['pr_auc']:.4f}  "
      f"Precision={test_metrics['precision']:.4f}  "
      f"Recall={test_metrics['recall']:.4f}  "
      f"F1={test_metrics['f1']:.4f}  "
      f"Brier={test_metrics['brier']:.4f}")
print(f"Confusion matrix: {test_metrics['confusion_matrix']}")

# ─────────────────────────────────────────────────────────────────────────────
# 7. SHAP background dataset
# ─────────────────────────────────────────────────────────────────────────────
print("Building SHAP background dataset...")
X_train_pre = _shared_pre.transform(X_train)
pos_idx = np.where(y_train.values == 1)[0]
neg_idx = np.where(y_train.values == 0)[0]
rng = np.random.default_rng(RANDOM_STATE)
bg_pos = rng.choice(pos_idx, size=min(100, len(pos_idx)), replace=False)
bg_neg = rng.choice(neg_idx, size=min(100, len(neg_idx)), replace=False)
bg_idx = np.concatenate([bg_pos, bg_neg])
X_background = X_train_pre[bg_idx]
print(f"Background shape: {X_background.shape}")

# ─────────────────────────────────────────────────────────────────────────────
# 8. Save artifacts
# ─────────────────────────────────────────────────────────────────────────────
pipeline_path = os.path.join(ARTIFACT_DIR, "pipeline.joblib")
shap_path     = os.path.join(ARTIFACT_DIR, "shap_background.joblib")
meta_path     = os.path.join(ARTIFACT_DIR, "metadata.json")

joblib.dump(final_pipeline, pipeline_path, compress=3)
joblib.dump(X_background,   shap_path,     compress=3)

import sklearn, xgboost, shap as _shap, imblearn
metadata = {
    "model_id":   "CVD-10Y",
    "version":    "1.0.0",
    "description": "10-year coronary heart disease risk — Framingham Heart Study",
    "target":     "TenYearCHD",
    "dataset":    "framingham.csv",
    "dataset_sha256": dataset_hash,
    "algorithm":  winner["algorithm"],
    "imbalance_experiment": winner["experiment"],
    "training_features": TRAINING_FEATURES,
    "inference_features": TRAINING_FEATURES,
    "features_removed_from_training": [
        "currentSmoker", "cigsPerDay", "BMI", "education"
    ],
    "proxy_features": {
        "BPMeds": "CarePlan.recommendations[*].intervention keyword match"
    },
    "prediction_threshold": round(final_threshold, 4),
    "calibration_method": "isotonic",
    "evaluation": {
        "val":  {k: v for k, v in val_metrics_cal.items() if k != "confusion_matrix"},
        "test": {k: v for k, v in test_metrics.items()    if k != "confusion_matrix"},
        "test_confusion_matrix": test_metrics["confusion_matrix"],
    },
    "n_train": len(X_train),
    "n_val":   len(X_val),
    "n_test":  len(X_test),
    "class_distribution": {
        "train_pos": int(y_train.sum()),
        "train_neg": int((y_train == 0).sum()),
    },
    "random_state": RANDOM_STATE,
    "python_version": sys.version.split()[0],
    "library_versions": {
        "scikit-learn":   sklearn.__version__,
        "xgboost":        xgboost.__version__,
        "shap":           _shap.__version__,
        "imbalanced-learn": imblearn.__version__,
    },
    "created_at": datetime.datetime.utcnow().isoformat() + "Z",
    "created_by": "training/train_cvd.py",
}
with open(meta_path, "w") as f:
    json.dump(metadata, f, indent=2)

# ─────────────────────────────────────────────────────────────────────────────
# 9. Save all experiment results to CSV
# ─────────────────────────────────────────────────────────────────────────────
results_rows = []
for r in results:
    row = {k: v for k, v in r.items() if k not in ("estimator",)}
    results_rows.append(row)
pd.DataFrame(results_rows).to_csv(
    os.path.join(RESULTS_DIR, "cvd_results.csv"), index=False
)

print(f"\nArtifacts saved to: {ARTIFACT_DIR}")
print("CVD-10Y TRAINING COMPLETE")

"""
SHAP Explainer — genuine Shapley value explanations.

Uses:
  - TreeExplainer for Random Forest / XGBoost
  - LinearExplainer for Logistic Regression

The CalibratedClassifierCV wraps the base estimator. We extract the base
estimator from the calibration wrapper and create the SHAP explainer on it,
then compute SHAP values on the preprocessed input (after the pipeline's
preprocessor step).

Output schema per the design:
  feature, value, contribution, direction
"""
from __future__ import annotations

import numpy as np
import shap


def _get_base_estimator(pipeline):
    """
    Extract the base (non-calibrated) estimator from the pipeline.
    The pipeline structure is: Pipeline([('pre', preprocessor), ('clf', calibrated_clf)])
    The calibrated_clf is a CalibratedClassifierCV wrapping the original estimator.
    """
    clf = pipeline.named_steps.get("clf") or pipeline.steps[-1][1]

    # CalibratedClassifierCV stores the base estimator in .estimator
    if hasattr(clf, "estimator"):
        return clf.estimator
    # If not calibrated, it's the raw estimator
    return clf


def _get_preprocessor(pipeline):
    """Extract the preprocessor step from the pipeline."""
    return pipeline.named_steps.get("pre") or pipeline.steps[0][1]


def create_explainer(pipeline, background: np.ndarray):
    """
    Create the appropriate SHAP explainer based on the base estimator type.

    Returns a callable: explain(X_preprocessed) -> np.ndarray of SHAP values.
    """
    base_est = _get_base_estimator(pipeline)
    est_type = type(base_est).__name__.lower()

    # TreeExplainer for tree-based models
    if any(t in est_type for t in ["randomforest", "xgb", "gradientboosting"]):
        explainer = shap.TreeExplainer(base_est, background)
        return explainer

    # LinearExplainer for logistic regression
    elif "logistic" in est_type:
        explainer = shap.LinearExplainer(base_est, background)
        return explainer

    # Fallback: KernelExplainer (slow but universal)
    else:
        def predict_fn(X):
            return base_est.predict_proba(X)
        explainer = shap.KernelExplainer(predict_fn, background)
        return explainer


def compute_shap_values(
    pipeline,
    background: np.ndarray,
    X_raw,
    n_features: int = 5,
):
    """
    Compute SHAP values for a single prediction.

    Parameters:
        pipeline: the loaded sklearn Pipeline (pre + calibrated clf)
        background: SHAP background dataset (numpy array, preprocessed space)
        X_raw: raw input as a pandas DataFrame (same schema as training input)
        n_features: number of top features to return (by |SHAP value|)

    Returns:
        list of dicts: [{feature, value, contribution, direction}, ...]
    """
    preprocessor = _get_preprocessor(pipeline)

    # Transform raw input through the preprocessor to get preprocessed array
    X_pre = preprocessor.transform(X_raw)
    if hasattr(X_pre, "values"):
        X_pre = X_pre.values
    X_pre = np.asarray(X_pre, dtype=np.float64)

    # Create explainer and compute SHAP values
    explainer = create_explainer(pipeline, background)

    shap_values = explainer.shap_values(X_pre)

    # Handle different SHAP output formats
    if isinstance(shap_values, list):
        # Binary classification: [class_0_shap, class_1_shap]
        shap_vals = shap_values[1]  # positive class
    elif isinstance(shap_values, np.ndarray) and shap_values.ndim == 3:
        # Shape (n_samples, n_features, n_classes) — take positive class
        shap_vals = shap_values[:, :, 1] if shap_values.shape[2] == 2 else shap_values[:, :, 0]
    else:
        shap_vals = shap_values

    # For a single sample, shape is (1, n_features)
    shap_flat = shap_vals[0] if shap_vals.ndim > 1 else shap_vals

    # Get feature names from the preprocessor's ColumnTransformer
    feature_names = _get_feature_names(pipeline)
    if feature_names is None or len(feature_names) != len(shap_flat):
        feature_names = [f"feature_{i}" for i in range(len(shap_flat))]

    # Get the raw input values for display
    raw_values = _get_raw_values(X_raw, feature_names)

    # Build result list
    results = []
    for i, (name, contribution) in enumerate(zip(feature_names, shap_flat)):
        val = raw_values.get(name, "N/A")
        direction = "increases" if contribution > 0 else "decreases"
        results.append({
            "feature": name,
            "value": str(val) if val is not None else "N/A",
            "contribution": round(float(contribution), 6),
            "direction": direction,
        })

    # Sort by |contribution| descending and take top N
    results.sort(key=lambda x: abs(x["contribution"]), reverse=True)
    results = results[:n_features]

    # Validate: all contributions must be finite
    for r in results:
        if not np.isfinite(r["contribution"]):
            raise ValueError(f"SHAP contribution for {r['feature']} is not finite: {r['contribution']}")

    return results


def _get_feature_names(pipeline):
    """Extract output feature names from the preprocessor's ColumnTransformer."""
    pre = _get_preprocessor(pipeline)
    # The preprocessor is a Pipeline; the last step is the ColumnTransformer
    try:
        ct = pre.named_steps.get("transform") or pre.steps[-1][1]
        if hasattr(ct, "get_feature_names_out"):
            names = list(ct.get_feature_names_out())
            # Clean up any transformer-prefixed names
            cleaned = []
            for n in names:
                # Remove prefixes like "num__" or "bin__"
                if "__" in n:
                    n = n.split("__", 1)[1]
                cleaned.append(n)
            return cleaned
    except Exception:
        pass
    return None


def _get_raw_values(X_raw, feature_names):
    """
    Map preprocessed feature names back to raw input values for display.
    For OHE features (e.g., 'diag_group_1_Circulatory'), show the group name.
    """
    raw_values = {}
    if not hasattr(X_raw, "columns"):
        return raw_values

    for name in feature_names:
        # Direct column match
        if name in X_raw.columns:
            raw_values[name] = X_raw[name].iloc[0]
        # OHE feature: e.g., "diag_group_1_Circulatory" → check if diag_group_1 == "Circulatory"
        elif "_" in name:
            parts = name.rsplit("_", 1)
            if len(parts) == 2:
                col, cat = parts
                if col in X_raw.columns:
                    raw_val = X_raw[col].iloc[0]
                    raw_values[name] = f"={cat}" if str(raw_val) == cat else "0"
                else:
                    raw_values[name] = "0"
            else:
                raw_values[name] = "N/A"
        else:
            raw_values[name] = "N/A"

    return raw_values

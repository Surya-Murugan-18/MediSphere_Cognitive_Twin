"""
Prediction endpoints for all three models.
"""
from __future__ import annotations

import os
import datetime
import numpy as np
import pandas as pd
from fastapi import APIRouter, HTTPException, Header, Depends

from src.models.model_registry import ModelRegistry
from src.api.schemas.cvd_schema import CVDRiskRequest, CVDRiskResponse, ShapFactor
from src.api.schemas.diabetes_schema import DiabetesRiskRequest, DiabetesRiskResponse
from src.api.schemas.readmission_schema import Readmission30DRequest, Readmission30DResponse

router = APIRouter()

# Registry instance is set by main.py at startup
_registry: ModelRegistry | None = None


def set_registry(reg: ModelRegistry):
    global _registry
    _registry = reg


def get_registry() -> ModelRegistry:
    if _registry is None:
        raise HTTPException(status_code=503, detail="Service not ready — models not loaded")
    return _registry


# ── Internal token verification ───────────────────────────────────────────────
_INTERNAL_TOKEN = os.environ.get("ML_INTERNAL_TOKEN", "dev-token")


def verify_token(x_internal_token: str = Header(..., alias="X-Internal-Token")):
    if x_internal_token != _INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="Unauthorized")


# ── Risk category mapping ─────────────────────────────────────────────────────
def risk_category_cvd(prob: float) -> str:
    if prob >= 0.20:
        return "High"
    elif prob >= 0.10:
        return "Medium"
    else:
        return "Low"


def risk_category_diabetes(prob: float, threshold: float) -> str:
    return "High" if prob >= threshold else "Low"


def risk_category_readmission(prob: float) -> str:
    if prob >= 0.30:
        return "High"
    elif prob >= 0.15:
        return "Medium"
    else:
        return "Low"


# ── CVD-10Y ───────────────────────────────────────────────────────────────────
@router.post("/cvd-risk", response_model=CVDRiskResponse, dependencies=[Depends(verify_token)])
def predict_cvd(req: CVDRiskRequest):
    reg = get_registry()
    try:
        model = reg.get("cvd-risk")
    except KeyError:
        raise HTTPException(status_code=500, detail="CVD model not loaded")

    # Build input DataFrame with raw feature names expected by the pipeline
    input_data = {
        "male": req.male,
        "age": req.age,
        "sysBP": req.sys_bp,
        "diaBP": req.dia_bp,
        "heartRate": req.heart_rate,
        "totChol": req.tot_chol,
        "glucose": req.glucose,
        "prevalentHyp": req.prevalent_hyp,
        "prevalentStroke": req.prevalent_stroke,
        "diabetes": req.diabetes,
        "BPMeds": req.bp_meds,
    }

    # Track imputed fields
    imputed = [k for k, v in input_data.items() if v is None]

    X = pd.DataFrame([input_data])
    proba = model.pipeline.predict_proba(X)[0, 1]
    threshold = model.metadata["prediction_threshold"]
    prediction = int(proba >= threshold)

    # SHAP
    try:
        from src.explainability.shap_explainer import compute_shap_values
        shap_results = compute_shap_values(
            model.pipeline, model.background, X, n_features=5
        )
        shap_factors = [ShapFactor(**s) for s in shap_results]
    except Exception:
        shap_factors = []

    return CVDRiskResponse(
        patient_id=req.patient_id,
        model_id=model.metadata["model_id"],
        model_version=model.metadata["version"],
        probability_score=round(float(proba), 6),
        risk_category=risk_category_cvd(proba),
        prediction_binary=prediction,
        threshold_used=threshold,
        shap_factors=shap_factors,
        imputed_fields=imputed,
        inference_timestamp=datetime.datetime.utcnow().isoformat() + "Z",
    )


# ── Diabetes-Risk ──────────────────────────────────────────────────────────────
@router.post("/diabetes-risk", response_model=DiabetesRiskResponse,
              dependencies=[Depends(verify_token)])
def predict_diabetes(req: DiabetesRiskRequest):
    reg = get_registry()
    try:
        model = reg.get("diabetes-risk")
    except KeyError:
        raise HTTPException(status_code=500, detail="Diabetes model not loaded")

    # Map age_years to BRFSS bracket
    from src.preprocessing.diabetes_preprocessor import age_years_to_bracket
    age_bracket = age_years_to_bracket(req.age_years) if req.age_years is not None else None

    input_data = {
        "Age": age_bracket,
        "Sex": req.sex_male,
        "HighBP": req.high_bp,
        "HighChol": req.high_chol,
        "CholCheck": req.chol_check,
        "Stroke": req.stroke,
        "HeartDiseaseorAttack": req.heart_disease_or_attack,
        "PhysHlth": req.phys_hlth_alert_count_30d,
    }

    imputed = [k for k, v in input_data.items() if v is None]

    X = pd.DataFrame([input_data])
    proba = model.pipeline.predict_proba(X)[0, 1]
    threshold = model.metadata["prediction_threshold"]
    prediction = int(proba >= threshold)

    try:
        from src.explainability.shap_explainer import compute_shap_values
        shap_results = compute_shap_values(
            model.pipeline, model.background, X, n_features=5
        )
        shap_factors = [ShapFactor(**s) for s in shap_results]
    except Exception:
        shap_factors = []

    return DiabetesRiskResponse(
        patient_id=req.patient_id,
        model_id=model.metadata["model_id"],
        model_version=model.metadata["version"],
        probability_score=round(float(proba), 6),
        risk_category=risk_category_diabetes(proba, threshold),
        prediction_binary=prediction,
        threshold_used=threshold,
        shap_factors=shap_factors,
        imputed_fields=imputed,
        inference_timestamp=datetime.datetime.utcnow().isoformat() + "Z",
    )


# ── Readmission-30D ───────────────────────────────────────────────────────────
@router.post("/readmission-30d", response_model=Readmission30DResponse,
              dependencies=[Depends(verify_token)])
def predict_readmission(req: Readmission30DRequest):
    reg = get_registry()
    try:
        model = reg.get("readmission-30d")
    except KeyError:
        raise HTTPException(status_code=500, detail="Readmission model not loaded")

    # Map age_years to bracket midpoint
    age_midpoint = None
    if req.age_years is not None:
        # Find the bracket midpoint
        if req.age_years >= 90:
            age_midpoint = 95
        elif req.age_years >= 80:
            age_midpoint = 85
        elif req.age_years >= 70:
            age_midpoint = 75
        elif req.age_years >= 60:
            age_midpoint = 65
        elif req.age_years >= 50:
            age_midpoint = 55
        elif req.age_years >= 40:
            age_midpoint = 45
        elif req.age_years >= 30:
            age_midpoint = 35
        elif req.age_years >= 20:
            age_midpoint = 25
        elif req.age_years >= 10:
            age_midpoint = 15
        else:
            age_midpoint = 5

    input_data = {
        "gender": float(req.gender_male) if req.gender_male is not None else None,
        "age_midpoint": float(age_midpoint) if age_midpoint is not None else None,
        "number_diagnoses": float(req.number_diagnoses) if req.number_diagnoses is not None else None,
        "diag_group_1": req.diag_group_primary or "Unknown",
        "diag_group_2": req.diag_group_secondary or "Unknown",
        "diag_group_3": req.diag_group_tertiary or "Unknown",
        "number_emergency": float(req.number_high_alerts_prior_year) if req.number_high_alerts_prior_year is not None else None,
        "diabetesMed": float(req.diabetes_med) if req.diabetes_med is not None else None,
        "change": float(req.care_plan_changed_30d) if req.care_plan_changed_30d is not None else None,
    }

    imputed = [k for k, v in input_data.items() if v is None]

    X = pd.DataFrame([input_data])
    proba = model.pipeline.predict_proba(X)[0, 1]
    threshold = model.metadata["prediction_threshold"]
    prediction = int(proba >= threshold)

    try:
        from src.explainability.shap_explainer import compute_shap_values
        shap_results = compute_shap_values(
            model.pipeline, model.background, X, n_features=5
        )
        shap_factors = [ShapFactor(**s) for s in shap_results]
    except Exception:
        shap_factors = []

    return Readmission30DResponse(
        patient_id=req.patient_id,
        model_id=model.metadata["model_id"],
        model_version=model.metadata["version"],
        probability_score=round(float(proba), 6),
        risk_category=risk_category_readmission(proba),
        prediction_binary=prediction,
        threshold_used=threshold,
        shap_factors=shap_factors,
        imputed_fields=imputed,
        prediction_time_assumption="early_hospitalization",
        inference_timestamp=datetime.datetime.utcnow().isoformat() + "Z",
    )

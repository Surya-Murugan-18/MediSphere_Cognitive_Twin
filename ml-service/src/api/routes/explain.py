"""
SHAP explanation endpoints — same input as predict, returns only SHAP factors.
"""
from __future__ import annotations

import os
from fastapi import APIRouter, HTTPException, Header, Depends

from src.models.model_registry import ModelRegistry
from src.api.schemas.cvd_schema import CVDRiskRequest, ShapFactor
from src.api.schemas.diabetes_schema import DiabetesRiskRequest
from src.api.schemas.readmission_schema import Readmission30DRequest
from src.api.routes.predict import get_registry, verify_token, set_registry
import pandas as pd

router = APIRouter()


@router.post("/cvd-risk", dependencies=[Depends(verify_token)])
def explain_cvd(req: CVDRiskRequest):
    reg = get_registry()
    model = reg.get("cvd-risk")
    input_data = {
        "male": req.male, "age": req.age, "sysBP": req.sys_bp,
        "diaBP": req.dia_bp, "heartRate": req.heart_rate,
        "totChol": req.tot_chol, "glucose": req.glucose,
        "prevalentHyp": req.prevalent_hyp, "prevalentStroke": req.prevalent_stroke,
        "diabetes": req.diabetes, "BPMeds": req.bp_meds,
    }
    X = pd.DataFrame([input_data])
    from src.explainability.shap_explainer import compute_shap_values
    results = compute_shap_values(model.pipeline, model.background, X, n_features=5)
    return {"model_id": "CVD-10Y", "shap_factors": results}


@router.post("/diabetes-risk", dependencies=[Depends(verify_token)])
def explain_diabetes(req: DiabetesRiskRequest):
    reg = get_registry()
    model = reg.get("diabetes-risk")
    from src.preprocessing.diabetes_preprocessor import age_years_to_bracket
    input_data = {
        "Age": age_years_to_bracket(req.age_years) if req.age_years else None,
        "Sex": req.sex_male, "HighBP": req.high_bp, "HighChol": req.high_chol,
        "CholCheck": req.chol_check, "Stroke": req.stroke,
        "HeartDiseaseorAttack": req.heart_disease_or_attack,
        "PhysHlth": req.phys_hlth_alert_count_30d,
    }
    X = pd.DataFrame([input_data])
    from src.explainability.shap_explainer import compute_shap_values
    results = compute_shap_values(model.pipeline, model.background, X, n_features=5)
    return {"model_id": "Diabetes-Risk", "shap_factors": results}


@router.post("/readmission-30d", dependencies=[Depends(verify_token)])
def explain_readmission(req: Readmission30DRequest):
    reg = get_registry()
    model = reg.get("readmission-30d")
    age_mid = 55
    if req.age_years:
        for lo, mid in [(90, 95), (80, 85), (70, 75), (60, 65),
                        (50, 55), (40, 45), (30, 35), (20, 25), (10, 15), (0, 5)]:
            if req.age_years >= lo:
                age_mid = mid
                break
    input_data = {
        "gender": float(req.gender_male) if req.gender_male is not None else None,
        "age_midpoint": float(age_mid),
        "number_diagnoses": float(req.number_diagnoses) if req.number_diagnoses is not None else None,
        "diag_group_1": req.diag_group_primary or "Unknown",
        "diag_group_2": req.diag_group_secondary or "Unknown",
        "diag_group_3": req.diag_group_tertiary or "Unknown",
        "number_emergency": float(req.number_high_alerts_prior_year) if req.number_high_alerts_prior_year is not None else None,
        "diabetesMed": float(req.diabetes_med) if req.diabetes_med is not None else None,
        "change": float(req.care_plan_changed_30d) if req.care_plan_changed_30d is not None else None,
    }
    X = pd.DataFrame([input_data])
    from src.explainability.shap_explainer import compute_shap_values
    results = compute_shap_values(model.pipeline, model.background, X, n_features=5)
    return {"model_id": "Readmission-30D", "shap_factors": results}

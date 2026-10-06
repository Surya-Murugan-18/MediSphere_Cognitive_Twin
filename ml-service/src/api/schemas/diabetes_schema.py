"""
Pydantic schemas for Diabetes-Risk prediction requests and responses.
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field

from .cvd_schema import ShapFactor


class DiabetesRiskRequest(BaseModel):
    patient_id: str = Field(..., max_length=50, pattern=r"^[A-Za-z0-9\-]+$")
    age_years: Optional[int] = Field(None, ge=0, le=120)
    sex_male: Optional[int] = Field(None, ge=0, le=1)
    high_bp: Optional[int] = Field(None, ge=0, le=1)
    high_chol: Optional[int] = Field(None, ge=0, le=1)
    chol_check: Optional[int] = Field(None, ge=0, le=1)
    stroke: Optional[int] = Field(None, ge=0, le=1)
    heart_disease_or_attack: Optional[int] = Field(None, ge=0, le=1)
    phys_hlth_alert_count_30d: Optional[int] = Field(None, ge=0, le=30)


class DiabetesRiskResponse(BaseModel):
    patient_id: str
    model_id: str
    model_version: str
    probability_score: float
    risk_category: str
    prediction_binary: int
    threshold_used: float
    shap_factors: list[ShapFactor]
    imputed_fields: list[str]
    inference_timestamp: str

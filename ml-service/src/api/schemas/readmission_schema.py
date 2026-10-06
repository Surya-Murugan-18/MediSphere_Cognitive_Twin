"""
Pydantic schemas for Readmission-30D prediction requests and responses.
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field

from .cvd_schema import ShapFactor


class Readmission30DRequest(BaseModel):
    patient_id: str = Field(..., max_length=50, pattern=r"^[A-Za-z0-9\-]+$")
    gender_male: Optional[int] = Field(None, ge=0, le=1)
    age_years: Optional[int] = Field(None, ge=0, le=120)
    number_diagnoses: Optional[int] = Field(None, ge=0, le=50)
    diag_group_primary: Optional[str] = Field(None)
    diag_group_secondary: Optional[str] = Field(None)
    diag_group_tertiary: Optional[str] = Field(None)
    number_high_alerts_prior_year: Optional[int] = Field(None, ge=0, le=365)
    diabetes_med: Optional[int] = Field(None, ge=0, le=1)
    care_plan_changed_30d: Optional[int] = Field(None, ge=0, le=1)


class Readmission30DResponse(BaseModel):
    patient_id: str
    model_id: str
    model_version: str
    probability_score: float
    risk_category: str
    prediction_binary: int
    threshold_used: float
    shap_factors: list[ShapFactor]
    imputed_fields: list[str]
    prediction_time_assumption: str
    inference_timestamp: str

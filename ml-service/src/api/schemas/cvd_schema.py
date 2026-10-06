"""
Pydantic schemas for CVD-10Y prediction requests and responses.
"""
from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, Field


class CVDRiskRequest(BaseModel):
    patient_id: str = Field(..., max_length=50, pattern=r"^[A-Za-z0-9\-]+$")
    male: Optional[int] = Field(None, ge=0, le=1)
    age: Optional[int] = Field(None, ge=0, le=120)
    sys_bp: Optional[float] = Field(None, ge=50, le=300)
    dia_bp: Optional[float] = Field(None, ge=30, le=200)
    heart_rate: Optional[int] = Field(None, ge=20, le=300)
    tot_chol: Optional[float] = Field(None, ge=50, le=700)
    glucose: Optional[float] = Field(None, ge=20, le=600)
    prevalent_hyp: Optional[int] = Field(None, ge=0, le=1)
    prevalent_stroke: Optional[int] = Field(None, ge=0, le=1)
    diabetes: Optional[int] = Field(None, ge=0, le=1)
    bp_meds: Optional[int] = Field(None, ge=0, le=1)


class ShapFactor(BaseModel):
    feature: str
    value: str
    contribution: float
    direction: str


class CVDRiskResponse(BaseModel):
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

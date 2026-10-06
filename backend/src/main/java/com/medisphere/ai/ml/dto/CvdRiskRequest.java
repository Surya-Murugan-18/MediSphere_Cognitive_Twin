package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for POST /predict/cvd-risk.
 *
 * Matches CVDRiskRequest Pydantic schema exactly.
 * All clinical fields are Optional (null → FastAPI pipeline imputes).
 * Java MUST NOT duplicate Python preprocessing — only pass raw domain values.
 *
 * Feature sources (MediSphere domain):
 *   patient_id       → Patient.id
 *   male             → Patient.gender ("Male" → 1, else → 0)
 *   age              → Patient.dob → computed age in years
 *   sys_bp           → VitalsSnapshot.bloodPressure (systolic part)
 *   dia_bp           → VitalsSnapshot.bloodPressure (diastolic part)
 *   heart_rate       → VitalsSnapshot.heartRate
 *   tot_chol         → LabResult where test="Total Cholesterol"
 *   glucose          → LabResult where test="Fasting Glucose"
 *   prevalent_hyp    → Patient.conditions contains "hypertension" → 1
 *   prevalent_stroke → Patient.conditions contains "stroke" → 1
 *   diabetes         → Patient.conditions contains "diabetes" → 1
 *   bp_meds          → CarePlanRecommendation.intervention keyword proxy
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CvdRiskRequest(
        @JsonProperty("patient_id")       String patientId,
        @JsonProperty("male")             Integer male,
        @JsonProperty("age")              Integer age,
        @JsonProperty("sys_bp")           Double sysBp,
        @JsonProperty("dia_bp")           Double diaBp,
        @JsonProperty("heart_rate")       Integer heartRate,
        @JsonProperty("tot_chol")         Double totChol,
        @JsonProperty("glucose")          Double glucose,
        @JsonProperty("prevalent_hyp")    Integer prevalentHyp,
        @JsonProperty("prevalent_stroke") Integer prevalentStroke,
        @JsonProperty("diabetes")         Integer diabetes,
        @JsonProperty("bp_meds")          Integer bpMeds
) {}

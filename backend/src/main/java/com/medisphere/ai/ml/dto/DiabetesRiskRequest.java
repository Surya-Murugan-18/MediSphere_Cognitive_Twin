package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for POST /predict/diabetes-risk.
 *
 * Matches DiabetesRiskRequest Pydantic schema exactly.
 * The FastAPI service performs the age_years → BRFSS bracket conversion internally
 * (via age_years_to_bracket). Java sends raw age_years — do NOT pre-convert.
 *
 * Feature sources:
 *   patient_id                 → Patient.id
 *   age_years                  → Patient.dob → computed age in years
 *   sex_male                   → Patient.gender ("Male" → 1, else → 0)
 *   high_bp                    → Patient.conditions contains "hypertension" → 1
 *   high_chol                  → LabResult where test="Total Cholesterol" and status="High" → 1
 *   chol_check                 → LabResult for Total Cholesterol exists → 1
 *   stroke                     → Patient.conditions contains "stroke" → 1
 *   heart_disease_or_attack    → Patient.conditions contains "heart" or "coronary" or "cad" → 1
 *   phys_hlth_alert_count_30d  → count of alerts in last 30 days (any severity)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DiabetesRiskRequest(
        @JsonProperty("patient_id")                  String patientId,
        @JsonProperty("age_years")                   Integer ageYears,
        @JsonProperty("sex_male")                    Integer sexMale,
        @JsonProperty("high_bp")                     Integer highBp,
        @JsonProperty("high_chol")                   Integer highChol,
        @JsonProperty("chol_check")                  Integer cholCheck,
        @JsonProperty("stroke")                      Integer stroke,
        @JsonProperty("heart_disease_or_attack")     Integer heartDiseaseOrAttack,
        @JsonProperty("phys_hlth_alert_count_30d")   Integer physHlthAlertCount30d
) {}

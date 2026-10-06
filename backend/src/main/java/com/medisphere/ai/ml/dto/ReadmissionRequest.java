package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for POST /predict/readmission-30d.
 *
 * Matches Readmission30DRequest Pydantic schema exactly.
 *
 * Feature sources:
 *   patient_id                   → Patient.id
 *   gender_male                  → Patient.gender ("Male" → 1, else → 0)
 *   age_years                    → Patient.dob → computed age (FastAPI converts to bracket midpoint)
 *   number_diagnoses             → Patient.conditions.size()
 *   diag_group_primary           → ConditionMapper.conditionNameToGroup(conditions[0])
 *   diag_group_secondary         → ConditionMapper.conditionNameToGroup(conditions[1])
 *   diag_group_tertiary          → ConditionMapper.conditionNameToGroup(conditions[2])
 *   number_high_alerts_prior_year → AlertRepository: HIGH alerts in last 365 days
 *   diabetes_med                 → ConditionMapper.extractDiabetesMed(conditions, carePlanRecs)
 *   care_plan_changed_30d        → CarePlan with updatedAt in last 30 days → 1
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReadmissionRequest(
        @JsonProperty("patient_id")                    String patientId,
        @JsonProperty("gender_male")                   Integer genderMale,
        @JsonProperty("age_years")                     Integer ageYears,
        @JsonProperty("number_diagnoses")              Integer numberDiagnoses,
        @JsonProperty("diag_group_primary")            String diagGroupPrimary,
        @JsonProperty("diag_group_secondary")          String diagGroupSecondary,
        @JsonProperty("diag_group_tertiary")           String diagGroupTertiary,
        @JsonProperty("number_high_alerts_prior_year") Integer numberHighAlertsPriorYear,
        @JsonProperty("diabetes_med")                  Integer diabetesMed,
        @JsonProperty("care_plan_changed_30d")         Integer carePlanChanged30d
) {}

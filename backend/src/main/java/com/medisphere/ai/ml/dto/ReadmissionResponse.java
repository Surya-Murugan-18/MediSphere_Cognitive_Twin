package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for POST /predict/readmission-30d.
 *
 * Matches Readmission30DResponse Pydantic schema exactly.
 * prediction_time_assumption is always "early_hospitalization" from the service.
 */
public record ReadmissionResponse(
        @JsonProperty("patient_id")               String patientId,
        @JsonProperty("model_id")                 String modelId,
        @JsonProperty("model_version")            String modelVersion,
        @JsonProperty("probability_score")        double probabilityScore,
        @JsonProperty("risk_category")            String riskCategory,
        @JsonProperty("prediction_binary")        int predictionBinary,
        @JsonProperty("threshold_used")           double thresholdUsed,
        @JsonProperty("shap_factors")             List<MlShapFactor> shapFactors,
        @JsonProperty("imputed_fields")           List<String> imputedFields,
        @JsonProperty("prediction_time_assumption") String predictionTimeAssumption,
        @JsonProperty("inference_timestamp")      String inferenceTimestamp
) {}

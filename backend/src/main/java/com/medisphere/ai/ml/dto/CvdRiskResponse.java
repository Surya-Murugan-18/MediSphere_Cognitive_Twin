package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for POST /predict/cvd-risk.
 *
 * Matches CVDRiskResponse Pydantic schema exactly.
 * probability_score is in [0,1] — multiply by 100 for the Prediction.value field.
 */
public record CvdRiskResponse(
        @JsonProperty("patient_id")       String patientId,
        @JsonProperty("model_id")         String modelId,
        @JsonProperty("model_version")    String modelVersion,
        @JsonProperty("probability_score") double probabilityScore,
        @JsonProperty("risk_category")    String riskCategory,
        @JsonProperty("prediction_binary") int predictionBinary,
        @JsonProperty("threshold_used")   double thresholdUsed,
        @JsonProperty("shap_factors")     List<MlShapFactor> shapFactors,
        @JsonProperty("imputed_fields")   List<String> imputedFields,
        @JsonProperty("inference_timestamp") String inferenceTimestamp
) {}

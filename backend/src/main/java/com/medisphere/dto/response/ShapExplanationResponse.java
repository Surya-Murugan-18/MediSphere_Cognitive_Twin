package com.medisphere.dto.response;

import com.medisphere.domain.Prediction;

import java.time.Instant;
import java.util.List;

/**
 * Full SHAP explainability response.
 * Returned by GET /api/explainability/{predictionId}.
 *
 * Includes: prediction details, SHAP factors, clinical evidence,
 * patient summary, and a natural-language explanation string.
 */
public record ShapExplanationResponse(
        String predictionId,
        String patientId,
        String patientName,
        String model,
        String label,
        double value,
        String category,
        int federatedRound,
        int confidence,
        String calibration,
        List<PredictionResponse.ShapFactorResponse> shapFactors,
        List<PredictionResponse.ClinicalEvidenceResponse> clinicalEvidence,
        String naturalLanguageSummary,
        Instant createdAt
) {
    public static ShapExplanationResponse from(Prediction prediction,
                                                String patientName,
                                                String naturalLanguageSummary) {
        return new ShapExplanationResponse(
                prediction.getId(),
                prediction.getPatientId(),
                patientName,
                prediction.getModel(),
                prediction.getLabel(),
                prediction.getValue(),
                prediction.getCategory(),
                prediction.getFederatedRound(),
                prediction.getConfidence(),
                prediction.getCalibration(),
                prediction.getShapFactors() == null ? List.of() :
                        prediction.getShapFactors().stream()
                                .map(PredictionResponse.ShapFactorResponse::from)
                                .toList(),
                prediction.getClinicalEvidence() == null ? List.of() :
                        prediction.getClinicalEvidence().stream()
                                .map(PredictionResponse.ClinicalEvidenceResponse::from)
                                .toList(),
                naturalLanguageSummary,
                prediction.getCreatedAt()
        );
    }
}

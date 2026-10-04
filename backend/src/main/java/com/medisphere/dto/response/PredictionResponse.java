package com.medisphere.dto.response;

import com.medisphere.domain.Prediction;

import java.time.Instant;
import java.util.List;

/**
 * AI risk prediction response DTO.
 * Returned by GET /api/predictions, GET /api/predictions/{id}, POST /api/predictions/run,
 * and GET /api/patients/{id}/predictions.
 */
public record PredictionResponse(
        String id,
        String patientId,
        String model,
        String label,
        double value,
        String category,
        int federatedRound,
        int confidence,
        String calibration,
        List<ShapFactorResponse> shapFactors,
        List<ClinicalEvidenceResponse> clinicalEvidence,
        Instant createdAt
) {
    public static PredictionResponse from(Prediction p) {
        return new PredictionResponse(
                p.getId(),
                p.getPatientId(),
                p.getModel(),
                p.getLabel(),
                p.getValue(),
                p.getCategory(),
                p.getFederatedRound(),
                p.getConfidence(),
                p.getCalibration(),
                p.getShapFactors() == null ? List.of() :
                        p.getShapFactors().stream().map(ShapFactorResponse::from).toList(),
                p.getClinicalEvidence() == null ? List.of() :
                        p.getClinicalEvidence().stream().map(ClinicalEvidenceResponse::from).toList(),
                p.getCreatedAt()
        );
    }

    public record ShapFactorResponse(
            String feature,
            double contribution,
            String value,
            String direction
    ) {
        public static ShapFactorResponse from(Prediction.ShapFactor f) {
            return new ShapFactorResponse(f.getFeature(), f.getContribution(),
                    f.getValue(), f.getDirection());
        }
    }

    public record ClinicalEvidenceResponse(String guideline, String detail) {
        public static ClinicalEvidenceResponse from(Prediction.ClinicalEvidence e) {
            return new ClinicalEvidenceResponse(e.getGuideline(), e.getDetail());
        }
    }
}

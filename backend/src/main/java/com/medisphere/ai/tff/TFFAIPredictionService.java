package com.medisphere.ai.tff;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;

import java.util.List;

/**
 * Production placeholder for TensorFlow Federated AI prediction.
 *
 * Activated when AI_PREDICTION_MODE=tff.
 *
 * This is a placeholder — it throws UnsupportedOperationException until the real
 * TFF integration is implemented. Switching from stub to TFF requires only a
 * configuration change (AI_PREDICTION_MODE=tff + TFF_ENDPOINT).
 *
 * Pattern mirrors LiveFHIRClient from Phase 3.
 */
public class TFFAIPredictionService implements AIPredictionService {

    private static final String MSG =
            "TFFAIPredictionService is not yet implemented. " +
            "Configure AI_PREDICTION_MODE=stub for development, " +
            "or implement this class with the real TFF endpoint.";

    @Override
    public Prediction predictCVDRisk(Patient patient, List<LabResult> labs, VitalsSnapshot vitals) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public Prediction predictDiabetesComplication(Patient patient, List<LabResult> labs) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public Prediction predictReadmission(Patient patient, List<String> recentAlerts) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public List<Prediction.ShapFactor> explainPrediction(String predictionId, Patient patient,
                                                          List<LabResult> labs, VitalsSnapshot vitals) {
        throw new UnsupportedOperationException(MSG);
    }
}

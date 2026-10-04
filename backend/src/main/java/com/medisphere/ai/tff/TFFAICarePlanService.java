package com.medisphere.ai.tff;

import com.medisphere.ai.AICarePlanService;
import com.medisphere.domain.CarePlanRecommendation;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.SafetyCheck;

import java.util.List;

/**
 * Production placeholder for TensorFlow Federated care plan generation.
 *
 * Activated when AI_CAREPLAN_MODE=tff.
 * Throws UnsupportedOperationException until the real TFF integration is implemented.
 */
public class TFFAICarePlanService implements AICarePlanService {

    private static final String MSG =
            "TFFAICarePlanService is not yet implemented. " +
            "Configure AI_CAREPLAN_MODE=stub for development.";

    @Override
    public List<CarePlanRecommendation> generateRecommendations(Patient patient,
                                                                  Prediction latestPrediction,
                                                                  List<LabResult> labs) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public List<SafetyCheck> runSafetyChecks(Patient patient,
                                              List<CarePlanRecommendation> recommendations) {
        throw new UnsupportedOperationException(MSG);
    }
}

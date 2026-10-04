package com.medisphere.ai;

import com.medisphere.domain.CarePlanRecommendation;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.SafetyCheck;

import java.util.List;

/**
 * AI Care Plan Service abstraction — the ONLY way to generate care plan recommendations.
 *
 * All implementations must be deterministic for the same patient state.
 *
 * NOTE: This interface is DEFINED in Phase 4 but USED in Phase 6.
 * The stub and TFF placeholder are created here so the abstraction is complete.
 * CarePlanService (Phase 6) will inject this interface.
 *
 * Implementations:
 *   StubAICarePlanService  — active when AI_CAREPLAN_MODE=stub (default in development)
 *   TFFAICarePlanService   — active when AI_CAREPLAN_MODE=tff (production)
 *
 * Per design.md §6.4
 */
public interface AICarePlanService {

    /**
     * Generate personalised care plan recommendations for a patient.
     *
     * @param patient            the patient domain object
     * @param latestPrediction   the patient's latest risk prediction (CVD or highest-risk)
     * @param labs               the patient's current lab results
     * @return ordered list of care plan recommendations (not yet persisted)
     */
    List<CarePlanRecommendation> generateRecommendations(Patient patient,
                                                          Prediction latestPrediction,
                                                          List<LabResult> labs);

    /**
     * Run safety checks against a proposed set of recommendations.
     * Checks include: drug interactions, renal dose adjustment, allergy flags,
     * hypoglycaemia risk.
     *
     * @param patient          the patient domain object
     * @param recommendations  the recommendations to validate
     * @return list of safety check results
     */
    List<SafetyCheck> runSafetyChecks(Patient patient,
                                       List<CarePlanRecommendation> recommendations);
}

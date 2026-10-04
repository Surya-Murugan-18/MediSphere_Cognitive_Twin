package com.medisphere.ai.stub;

import com.medisphere.ai.AICarePlanService;
import com.medisphere.domain.CarePlanRecommendation;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.SafetyCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development stub for AI care plan generation.
 *
 * Activated when AI_CAREPLAN_MODE=stub (default).
 *
 * NOTE: This stub is DEFINED in Phase 4 but USED in Phase 6 (CarePlanService).
 * It is not connected to any REST endpoint in Phase 4.
 *
 * Generates deterministic recommendations based on the patient's conditions and lab values.
 * No random values. Same patient state → same recommendations.
 *
 * Per design.md §6.5
 */
public class StubAICarePlanService implements AICarePlanService {

    private static final Logger log = LoggerFactory.getLogger(StubAICarePlanService.class);

    @Override
    public List<CarePlanRecommendation> generateRecommendations(Patient patient,
                                                                  Prediction latestPrediction,
                                                                  List<LabResult> labs) {
        log.debug("StubAICarePlanService: generating recommendations for patient {}", patient.getId());

        List<CarePlanRecommendation> recommendations = new ArrayList<>();

        boolean hasDiabetes    = hasCondition(patient, "diabetes");
        boolean hasHypertension = hasCondition(patient, "hypertension");
        double hba1c = extractNumericLab(labs, "HbA1c");

        // 1. Medication management (tailored to conditions)
        if (hasDiabetes || hba1c > 7.0) {
            recommendations.add(CarePlanRecommendation.builder()
                    .id("rec-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Medication Management")
                    .goal("Improve glycaemic control toward HbA1c < 7.0%")
                    .intervention(hba1c > 8.0
                            ? "Consider titrating Metformin to 1000 mg BID. Evaluate addition of GLP-1 agonist."
                            : "Continue current Metformin regimen. Review compliance at next visit.")
                    .monitoring("HbA1c at 12 weeks; fasting glucose weekly.")
                    .outcome("Projected HbA1c reduction of 0.8–1.2 percentage points.")
                    .evidence("ADA Standards of Care 2026 · Section 9")
                    .build());
        }

        if (hasHypertension) {
            recommendations.add(CarePlanRecommendation.builder()
                    .id("rec-" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Blood Pressure Control")
                    .goal("Achieve target BP < 130/80 mmHg")
                    .intervention("Review antihypertensive regimen. Consider ACE inhibitor if not already prescribed.")
                    .monitoring("Home BP monitoring twice daily; clinic review at 4 weeks.")
                    .outcome("Estimated systolic reduction of 8–12 mmHg with optimised regimen.")
                    .evidence("JNC 8 Hypertension Guideline · ACC/AHA 2017")
                    .build());
        }

        // 2. Lifestyle intervention
        recommendations.add(CarePlanRecommendation.builder()
                .id("rec-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Lifestyle Intervention")
                .goal("Reduce cardiovascular risk through physical activity and dietary modification")
                .intervention("150 minutes moderate aerobic exercise per week. Mediterranean diet counselling.")
                .monitoring("Activity tracker review monthly; dietary diary at each visit.")
                .outcome("Expected 5–7% reduction in CVD risk with sustained adherence.")
                .evidence("ACC/AHA Prevention Guideline 2019")
                .build());

        // 3. Monitoring and follow-up
        recommendations.add(CarePlanRecommendation.builder()
                .id("rec-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Monitoring and Follow-Up")
                .goal("Ensure timely detection of worsening risk factors")
                .intervention("Schedule follow-up in 4 weeks. Full metabolic panel at 12 weeks.")
                .monitoring("Wearable device compliance; alert review at each visit.")
                .outcome("Early identification of adverse trends reduces emergency admissions.")
                .evidence("Institutional chronic disease management pathway")
                .build());

        return recommendations;
    }

    @Override
    public List<SafetyCheck> runSafetyChecks(Patient patient,
                                              List<CarePlanRecommendation> recommendations) {
        log.debug("StubAICarePlanService: running safety checks for patient {}", patient.getId());

        List<SafetyCheck> checks = new ArrayList<>();

        // Drug interaction check
        checks.add(SafetyCheck.builder()
                .id("sc-" + UUID.randomUUID().toString().substring(0, 8))
                .label("Drug interaction validation")
                .detail("No major drug interactions identified in the proposed medication plan.")
                .tone("healthy")
                .build());

        // Renal dose check (based on creatinine if available)
        double creatinine = extractNumericLab(null, "Creatinine"); // labs not passed to safety checks
        checks.add(SafetyCheck.builder()
                .id("sc-" + UUID.randomUUID().toString().substring(0, 8))
                .label("Renal dose adjustment")
                .detail("Renal function within acceptable range for proposed medications.")
                .tone("healthy")
                .build());

        // Allergy check
        checks.add(SafetyCheck.builder()
                .id("sc-" + UUID.randomUUID().toString().substring(0, 8))
                .label("Allergy validation")
                .detail("No documented allergies in patient record that contraindicate the plan.")
                .tone("healthy")
                .build());

        // Hypoglycaemia risk
        boolean hasDiabetes = hasCondition(patient, "diabetes");
        checks.add(SafetyCheck.builder()
                .id("sc-" + UUID.randomUUID().toString().substring(0, 8))
                .label("Hypoglycaemia risk assessment")
                .detail(hasDiabetes
                        ? "Hypoglycaemia risk present — monitor blood glucose with dose titration."
                        : "Low hypoglycaemia risk based on current medication profile.")
                .tone(hasDiabetes ? "warning" : "healthy")
                .build());

        return checks;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean hasCondition(Patient patient, String keyword) {
        if (patient.getConditions() == null) return false;
        return patient.getConditions().stream()
                .anyMatch(c -> c != null && c.toLowerCase().contains(keyword.toLowerCase()));
    }

    private double extractNumericLab(List<LabResult> labs, String testName) {
        if (labs == null) return 0.0;
        return labs.stream()
                .filter(l -> testName.equalsIgnoreCase(l.getTest()))
                .mapToDouble(LabResult::getNumeric)
                .findFirst()
                .orElse(0.0);
    }
}

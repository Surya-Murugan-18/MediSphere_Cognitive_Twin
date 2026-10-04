package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.dto.response.ShapExplanationResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.PredictionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the full SHAP explainability response for a given prediction.
 *
 * SHAP values are deterministic — they were computed and embedded in the
 * Prediction document at creation time by StubAIPredictionService.
 * This service simply assembles the response; it does not re-compute anything.
 *
 * Per tasks.md B4.4.
 */
@Service
public class ExplainabilityService {

    private static final String MODULE = "Explainability";

    private final PredictionRepository predictionRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public ExplainabilityService(PredictionRepository predictionRepository,
                                  PatientRepository patientRepository,
                                  AuditService auditService) {
        this.predictionRepository = predictionRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    /**
     * Get the full explanation for a prediction.
     * Loads prediction + patient summary, assembles SHAP factors and clinical evidence,
     * and generates a natural-language summary string.
     *
     * @param predictionId          the prediction to explain
     * @param requestingProviderId  the provider requesting this explanation (for audit)
     * @return full explanation response
     */
    public ShapExplanationResponse getExplanation(String predictionId,
                                                   String requestingProviderId) {
        Prediction prediction = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prediction", predictionId));

        Patient patient = patientRepository.findById(prediction.getPatientId())
                .orElse(null);
        String patientName = patient != null ? patient.getName() : prediction.getPatientId();

        String summary = buildNaturalLanguageSummary(prediction, patientName);

        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Viewed SHAP Explanation")
                .module(MODULE)
                .patientId(prediction.getPatientId())
                .patientName(patientName)
                .status("Success")
                .build());

        return ShapExplanationResponse.from(prediction, patientName, summary);
    }

    /**
     * Build a natural-language explanation from the prediction and SHAP factors.
     * Deterministic: same inputs → same summary text.
     */
    private String buildNaturalLanguageSummary(Prediction prediction, String patientName) {
        List<Prediction.ShapFactor> factors = prediction.getShapFactors();
        if (factors == null || factors.isEmpty()) {
            return String.format(
                "The model estimated a %s of %.1f%% for %s. " +
                "Detailed feature contributions are not available for this prediction.",
                prediction.getLabel().toLowerCase(), prediction.getValue(), patientName);
        }

        List<Prediction.ShapFactor> positives = factors.stream()
                .filter(f -> f.getContribution() > 0)
                .sorted((a, b) -> Double.compare(b.getContribution(), a.getContribution()))
                .collect(Collectors.toList());

        List<Prediction.ShapFactor> negatives = factors.stream()
                .filter(f -> f.getContribution() < 0)
                .collect(Collectors.toList());

        String topPositives = positives.stream()
                .limit(3)
                .map(f -> String.format("%s (%s)", f.getFeature(), f.getValue()))
                .collect(Collectors.joining(", "));

        String topNegatives = negatives.stream()
                .limit(2)
                .map(f -> f.getFeature().toLowerCase())
                .collect(Collectors.joining(" and "));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "The model estimated a %s of %.1f%% for this patient. ",
                prediction.getLabel().toLowerCase(), prediction.getValue()));

        if (!topPositives.isEmpty()) {
            sb.append(String.format(
                    "Risk was driven upward mainly by %s. ", topPositives));
        }

        if (!topNegatives.isEmpty()) {
            sb.append(String.format(
                    "Contributions of %s partly offset the prediction, " +
                    "indicating that these factors are moderating this patient's risk profile. ",
                    topNegatives));
        }

        sb.append("All contributions are computed locally on hospital data. " +
                  "No patient-level features leave this institution during federated training.");

        return sb.toString();
    }
}

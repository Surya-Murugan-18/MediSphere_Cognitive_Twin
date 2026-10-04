package com.medisphere.service;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.PredictionResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.LabResultRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.PredictionRepository;
import com.medisphere.repository.VitalsSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Orchestrates AI risk prediction workflow.
 *
 * Delegates all prediction computation to AIPredictionService (never implements formulas here).
 * Persists results to MongoDB predictions collection.
 * Updates patient riskLevel after predictions run.
 * Logs audit entries per design.md requirements.
 *
 * Phase 4 note: readmission risk uses empty alert list — Phase 5 will supply real alerts.
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);
    private static final String MODULE = "Predictions";

    private final AIPredictionService aiPredictionService;
    private final PredictionRepository predictionRepository;
    private final PatientRepository patientRepository;
    private final LabResultRepository labResultRepository;
    private final VitalsSnapshotRepository vitalsSnapshotRepository;
    private final TwinService twinService;
    private final AuditService auditService;

    public PredictionService(AIPredictionService aiPredictionService,
                              PredictionRepository predictionRepository,
                              PatientRepository patientRepository,
                              LabResultRepository labResultRepository,
                              VitalsSnapshotRepository vitalsSnapshotRepository,
                              TwinService twinService,
                              AuditService auditService) {
        this.aiPredictionService = aiPredictionService;
        this.predictionRepository = predictionRepository;
        this.patientRepository = patientRepository;
        this.labResultRepository = labResultRepository;
        this.vitalsSnapshotRepository = vitalsSnapshotRepository;
        this.twinService = twinService;
        this.auditService = auditService;
    }

    // ── Run all 3 models for a patient (called async on patient create) ────

    /**
     * Run all three risk prediction models for a patient.
     * Loads fresh patient, labs, and vitals from repositories — does not accept
     * potentially stale objects from the caller.
     *
     * Marked @Async so patient creation returns immediately.
     * Failures are caught and logged — they must never propagate to the create flow.
     *
     * Per tasks.md B4.4 and B4.5.
     */
    @Async("predictionTaskExecutor")
    public void runPredictions(String patientId) {
        try {
            log.debug("Running predictions for patient {}", patientId);

            Patient patient = patientRepository.findById(patientId).orElse(null);
            if (patient == null) {
                log.warn("runPredictions: patient {} not found — skipping", patientId);
                return;
            }

            List<LabResult> labs = labResultRepository.findByPatientIdOrderByDateDesc(patientId);
            VitalsSnapshot vitals = vitalsSnapshotRepository.findByPatientId(patientId)
                    .orElse(null);

            List<Prediction> results = new ArrayList<>();

            // 1. CVD Risk
            try {
                Prediction cvd = aiPredictionService.predictCVDRisk(patient, labs, vitals);
                results.add(predictionRepository.save(cvd));
                log.debug("CVD prediction for {}: {}% ({})", patientId, cvd.getValue(), cvd.getCategory());
            } catch (Exception e) {
                log.error("CVD prediction failed for patient {}: {}", patientId, e.getMessage());
            }

            // 2. Diabetes Complication Risk
            try {
                Prediction dm = aiPredictionService.predictDiabetesComplication(patient, labs);
                results.add(predictionRepository.save(dm));
                log.debug("DM complication prediction for {}: {}% ({})", patientId, dm.getValue(), dm.getCategory());
            } catch (Exception e) {
                log.error("DM prediction failed for patient {}: {}", patientId, e.getMessage());
            }

            // 3. Readmission Risk (empty alert list — Phase 5 will supply real alerts)
            try {
                Prediction readmission = aiPredictionService.predictReadmission(patient, List.of());
                results.add(predictionRepository.save(readmission));
                log.debug("Readmission prediction for {}: {}% ({})", patientId, readmission.getValue(), readmission.getCategory());
            } catch (Exception e) {
                log.error("Readmission prediction failed for patient {}: {}", patientId, e.getMessage());
            }

            if (!results.isEmpty()) {
                // Update patient riskLevel based on highest prediction category
                updatePatientRiskLevel(patientId, results);

                // Add timeline event to twin
                updateTwinWithPredictions(patientId, patient.getTwinId(), results);

                // Audit — per tasks.md B4.4: runPredictions must log audit
                // userId is "system" for async background runs; patientId is recorded
                auditService.log(AuditContext.builder()
                        .userId("system")
                        .action("AI predictions updated")
                        .module(MODULE)
                        .patientId(patientId)
                        .patientName(patient.getName())
                        .status("Success")
                        .build());

                log.info("Predictions complete for patient {}: {} model(s) ran", patientId, results.size());
            }

        } catch (Exception e) {
            // Prediction failures must never crash the patient create flow
            log.error("Unexpected error running predictions for patient {}: {}", patientId, e.getMessage(), e);
        }
    }

    /**
     * Run a specific model (or ALL) for a patient synchronously (API-triggered).
     * Returns list of new predictions.
     */
    public List<PredictionResponse> runPredictionsSync(String patientId, String model,
                                                        String requestingProviderId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        List<LabResult> labs = labResultRepository.findByPatientIdOrderByDateDesc(patientId);
        VitalsSnapshot vitals = vitalsSnapshotRepository.findByPatientId(patientId).orElse(null);

        List<Prediction> results = new ArrayList<>();

        boolean runAll = "ALL".equalsIgnoreCase(model);

        if (runAll || "CVD-Risk-v3.2".equals(model)) {
            results.add(predictionRepository.save(
                    aiPredictionService.predictCVDRisk(patient, labs, vitals)));
        }
        if (runAll || "DM-Complication-v2.4".equals(model)) {
            results.add(predictionRepository.save(
                    aiPredictionService.predictDiabetesComplication(patient, labs)));
        }
        if (runAll || "Readmit-30d-v1.8".equals(model)) {
            results.add(predictionRepository.save(
                    aiPredictionService.predictReadmission(patient, List.of())));
        }

        if (!results.isEmpty()) {
            updatePatientRiskLevel(patientId, results);
            updateTwinWithPredictions(patientId, patient.getTwinId(), results);
        }

        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Triggered prediction run")
                .module(MODULE)
                .patientId(patientId)
                .patientName(patient.getName())
                .status("Success")
                .build());

        return results.stream().map(PredictionResponse::from).toList();
    }

    // ── Read operations ───────────────────────────────────────────────────

    public PredictionResponse getPrediction(String predictionId, String requestingProviderId) {
        Prediction p = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prediction", predictionId));

        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Viewed Prediction")
                .module(MODULE)
                .patientId(p.getPatientId())
                .status("Success")
                .build());

        return PredictionResponse.from(p);
    }

    public List<PredictionResponse> getPatientPredictions(String patientId,
                                                           String requestingProviderId) {
        // Validate patient exists
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", patientId);
        }

        List<Prediction> predictions =
                predictionRepository.findByPatientIdOrderByCreatedAtDesc(patientId);

        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Viewed Patient Predictions")
                .module(MODULE)
                .patientId(patientId)
                .status("Success")
                .build());

        return predictions.stream().map(PredictionResponse::from).toList();
    }

    /**
     * Paginated, filtered prediction list for the Predictions page table.
     */
    public PageResponse<PredictionResponse> getPredictions(String patientId, String category,
                                                            String model, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Prediction> result;

        boolean hasPatient   = StringUtils.hasText(patientId);
        boolean hasCategory  = StringUtils.hasText(category)
                               && !category.equalsIgnoreCase("All");
        boolean hasModel     = StringUtils.hasText(model);

        if (hasPatient && hasCategory) {
            result = predictionRepository.findByPatientIdAndCategoryOrderByCreatedAtDesc(
                    patientId, category, pageable);
        } else if (hasPatient) {
            result = predictionRepository.findByPatientIdOrderByCreatedAtDesc(patientId, pageable);
        } else if (hasCategory) {
            result = predictionRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
        } else if (hasModel) {
            result = predictionRepository.findByModelOrderByCreatedAtDesc(model, pageable);
        } else {
            result = predictionRepository.findAll(pageable);
        }

        return PageResponse.from(result, PredictionResponse::from);
    }

    /**
     * Risk category distribution for the donut chart on the Predictions page.
     * Returns counts for High, Medium, Low.
     */
    public List<Map<String, Object>> getRiskDistribution() {
        long high   = predictionRepository.countByCategory("High");
        long medium = predictionRepository.countByCategory("Medium");
        long low    = predictionRepository.countByCategory("Low");

        // Use LinkedHashMap to preserve insertion order for the frontend chart
        List<Map<String, Object>> distribution = new ArrayList<>();
        distribution.add(mapOf("name", "High",   "value", high));
        distribution.add(mapOf("name", "Medium",  "value", medium));
        distribution.add(mapOf("name", "Low",     "value", low));
        return distribution;
    }

    /**
     * Summary stats for the KPI cards on the Predictions page.
     */
    public Map<String, Object> getStats() {
        long total = predictionRepository.count();
        long highRiskCount = predictionRepository.countByCategory("High");

        // Average confidence — derived from all predictions
        double avgConfidence = predictionRepository.findAll().stream()
                .mapToInt(Prediction::getConfidence)
                .average()
                .orElse(0.0);
        avgConfidence = Math.round(avgConfidence * 10.0) / 10.0;

        // Latest federated round from most recent prediction
        int latestRound = predictionRepository.findAll().stream()
                .mapToInt(Prediction::getFederatedRound)
                .max()
                .orElse(47); // fallback to stub round 47

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total",         total);
        stats.put("avgAccuracy",   avgConfidence);
        stats.put("highRiskCount", highRiskCount);
        stats.put("latestRound",   latestRound);
        return stats;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Update patient.riskLevel to the highest category from the new predictions.
     */
    private void updatePatientRiskLevel(String patientId, List<Prediction> predictions) {
        try {
            String highest = predictions.stream()
                    .map(Prediction::getCategory)
                    .reduce("Low", (a, b) -> {
                        if ("High".equals(a) || "High".equals(b)) return "High";
                        if ("Medium".equals(a) || "Medium".equals(b)) return "Medium";
                        return "Low";
                    });

            patientRepository.findById(patientId).ifPresent(patient -> {
                patient.setRiskLevel(highest);
                patientRepository.save(patient);
                log.debug("Updated patient {} riskLevel to {}", patientId, highest);
            });
        } catch (Exception e) {
            log.warn("Failed to update riskLevel for patient {}: {}", patientId, e.getMessage());
        }
    }

    /**
     * Add a timeline event to the health twin after predictions complete.
     */
    private void updateTwinWithPredictions(String patientId, String twinId,
                                            List<Prediction> predictions) {
        try {
            String resolvedTwinId = twinId;
            if (!StringUtils.hasText(resolvedTwinId)) {
                resolvedTwinId = twinService.getTwinByPatientId(patientId).getId();
            }
            if (!StringUtils.hasText(resolvedTwinId)) return;

            // Find highest risk prediction for the summary
            Prediction highest = predictions.stream()
                    .max(java.util.Comparator.comparingDouble(Prediction::getValue))
                    .orElse(null);

            if (highest != null) {
                String tone = "High".equals(highest.getCategory()) ? "critical"
                            : "Medium".equals(highest.getCategory()) ? "warning" : "healthy";
                String detail = String.format("AI predictions updated — highest risk: %s at %.1f%% (%s).",
                        highest.getLabel(), highest.getValue(), highest.getCategory());
                twinService.addTimelineEvent(resolvedTwinId, "AI predictions updated", detail, tone);
            }
        } catch (Exception e) {
            log.warn("Failed to update twin timeline after predictions for patient {}: {}",
                    patientId, e.getMessage());
        }
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}

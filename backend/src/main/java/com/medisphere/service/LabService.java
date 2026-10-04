package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.dto.response.LabResultResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.FHIRResourceMapper;
import com.medisphere.fhir.model.FHIRObservation;
import com.medisphere.repository.LabResultRepository;
import com.medisphere.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Laboratory result service.
 *
 * Responsibilities:
 *  - Retrieve and filter lab results (server-side)
 *  - Ingest lab results from the FHIR integration layer
 *  - Compute trend against previous result (deterministic)
 *  - Deduplicate FHIR observations (by fhirObservationId)
 *
 * Phase 3 FR-AUD-05 compliance:
 *  - {@link #getLabResults} emits one audit entry per invocation so that
 *    every provider-initiated lab-result view is recorded in audit_logs.
 */
@Service
public class LabService {

    private static final Logger log = LoggerFactory.getLogger(LabService.class);

    /** Audit module name — consistent with existing Phase 2–7 module labels. */
    private static final String MODULE = "Labs";

    private final LabResultRepository labResultRepository;
    private final PatientRepository patientRepository;
    private final FHIRClient fhirClient;
    private final FHIRResourceMapper fhirMapper;
    private final AuditService auditService;

    public LabService(LabResultRepository labResultRepository,
                      PatientRepository patientRepository,
                      FHIRClient fhirClient,
                      FHIRResourceMapper fhirMapper,
                      AuditService auditService) {
        this.labResultRepository = labResultRepository;
        this.patientRepository = patientRepository;
        this.fhirClient = fhirClient;
        this.fhirMapper = fhirMapper;
        this.auditService = auditService;
    }

    // ── Retrieval ─────────────────────────────────────────────────────────

    /**
     * Get paginated, filtered lab results for a patient.
     *
     * Phase 3 FR-AUD-05: emits "Viewed Lab Results" audit entry on every call.
     * The audit contains the requesting provider's identity and the patient ID.
     *
     * @param patientId             the patient whose lab results are requested
     * @param category              optional category filter
     * @param status                optional status filter
     * @param dateFrom              optional date range lower bound (inclusive, ISO-8601)
     * @param dateTo                optional date range upper bound (inclusive, ISO-8601)
     * @param page                  0-indexed page number
     * @param size                  page size (already capped by the controller)
     * @param requestingProviderId  the authenticated provider's ID (for audit)
     */
    public PageResponse<LabResultResponse> getLabResults(String patientId,
                                                          String category,
                                                          String status,
                                                          String dateFrom,
                                                          String dateTo,
                                                          int page,
                                                          int size,
                                                          String requestingProviderId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "date"));

        boolean hasCategory = StringUtils.hasText(category) && !"All types".equalsIgnoreCase(category);
        boolean hasStatus   = StringUtils.hasText(status)   && !"All statuses".equalsIgnoreCase(status);
        boolean hasFrom     = StringUtils.hasText(dateFrom);
        boolean hasTo       = StringUtils.hasText(dateTo);
        boolean hasDateRange = hasFrom && hasTo;

        Page<LabResult> resultPage;

        if (hasCategory && hasStatus && hasDateRange) {
            resultPage = labResultRepository.findByPatientIdAndCategoryAndStatusAndDateBetween(
                    patientId, category, status, dateFrom, dateTo, pageable);
        } else if (hasCategory && hasStatus) {
            resultPage = labResultRepository.findByPatientIdAndCategoryAndStatus(
                    patientId, category, status, pageable);
        } else if (hasCategory && hasDateRange) {
            resultPage = labResultRepository.findByPatientIdAndCategoryAndDateBetween(
                    patientId, category, dateFrom, dateTo, pageable);
        } else if (hasStatus && hasDateRange) {
            resultPage = labResultRepository.findByPatientIdAndStatusAndDateBetween(
                    patientId, status, dateFrom, dateTo, pageable);
        } else if (hasCategory) {
            resultPage = labResultRepository.findByPatientIdAndCategory(
                    patientId, category, pageable);
        } else if (hasStatus) {
            resultPage = labResultRepository.findByPatientIdAndStatus(
                    patientId, status, pageable);
        } else if (hasDateRange) {
            resultPage = labResultRepository.findByPatientIdAndDateBetween(
                    patientId, dateFrom, dateTo, pageable);
        } else {
            resultPage = labResultRepository.findByPatientId(patientId, pageable);
        }

        // FR-AUD-05 — Phase 3: emit exactly one audit entry per lab-result view.
        // Placement in the service (not the controller) follows the same convention
        // as PatientService.getPatient() and ExplainabilityService.explain().
        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Viewed Lab Results")
                .module(MODULE)
                .patientId(patientId)
                .patientName(patient.getName())
                .status("Success")
                .build());

        return PageResponse.from(resultPage, LabResultResponse::from);
    }

    /**
     * Get the top N most recent lab results for a patient (used in overview tabs).
     * This is a supporting summary endpoint — audit is intentionally not emitted here
     * to avoid duplicate entries when Patient360 loads both tabs simultaneously.
     * The primary audit is captured by {@link #getLabResults}.
     */
    public List<LabResultResponse> getRecentLabResults(String patientId, int limit) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        return labResultRepository.findByPatientIdOrderByDateDesc(patientId)
                .stream()
                .limit(limit)
                .map(LabResultResponse::from)
                .toList();
    }

    // ── FHIR ingestion ────────────────────────────────────────────────────

    /**
     * Ingest laboratory results from FHIR for the given patient.
     *
     * Flow:
     *  1. Resolve patient → get fhirId
     *  2. Fetch DiagnosticReports via FHIRClient
     *  3. Fetch Observations via FHIRClient
     *  4. Map each Observation to a LabResult via FHIRResourceMapper
     *  5. Skip duplicates (by fhirObservationId)
     *  6. Compute trend for each new result
     *  7. Persist
     *
     * Idempotent — safe to call multiple times; duplicates are skipped.
     */
    public int ingestFromFHIR(String patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        String fhirId = patient.getFhirId();
        if (!StringUtils.hasText(fhirId)) {
            log.debug("Patient {} has no fhirId — skipping FHIR ingestion", patientId);
            return 0;
        }

        int ingested = 0;

        try {
            // Fetch all observations (DiagnosticReport provides context; Observations carry data)
            fhirClient.fetchDiagnosticReports(fhirId); // validates FHIR connectivity
            List<FHIRObservation> observations = fhirClient.fetchObservations(fhirId, null, null, null);

            for (FHIRObservation obs : observations) {
                // Deduplication check
                if (labResultRepository.existsByFhirObservationId(obs.getId())) {
                    log.debug("Observation {} already ingested — skipping", obs.getId());
                    continue;
                }

                LabResult labResult = fhirMapper.mapObservationToLabResult(obs, patientId);
                if (labResult == null) continue;

                // Compute trend against previous result for same test
                computeTrend(labResult, patientId);

                labResultRepository.save(labResult);
                ingested++;
                log.debug("Ingested lab result: {} for patient {}", obs.getDisplayName(), patientId);
            }

            log.info("FHIR lab ingestion complete for patient {}: {} new results", patientId, ingested);

        } catch (Exception e) {
            // FHIR ingestion failure must not corrupt the patient/twin creation flow
            log.error("FHIR lab ingestion failed for patient {}: {}", patientId, e.getMessage(), e);
        }

        return ingested;
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /**
     * Persist a single lab result, computing its trend first.
     */
    public LabResult saveLabResult(LabResult result) {
        computeTrend(result, result.getPatientId());
        return labResultRepository.save(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Compute trend (up | down | flat) by comparing the new result's numeric value
     * to the most recent previous result for the same patient + test name.
     * Deterministic — no AI, no random values.
     */
    private void computeTrend(LabResult result, String patientId) {
        Optional<LabResult> previous = labResultRepository
                .findFirstByPatientIdAndTestOrderByDateDesc(patientId, result.getTest());

        if (previous.isPresent()) {
            double prev = previous.get().getNumeric();
            double curr = result.getNumeric();
            result.setPrevious(previous.get().getResult());

            double delta = curr - prev;
            if (Math.abs(delta) < 0.01) {
                result.setTrend("flat");
            } else {
                result.setTrend(delta > 0 ? "up" : "down");
            }
        } else {
            result.setTrend("flat");
            result.setPrevious("");
        }
    }

}

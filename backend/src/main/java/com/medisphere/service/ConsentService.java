package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.Consent;
import com.medisphere.domain.Patient;
import com.medisphere.dto.request.UpdateConsentRequest;
import com.medisphere.dto.response.ConsentHistoryEntryResponse;
import com.medisphere.dto.response.ConsentResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 7 — B7.1 Consent Service
 *
 * Manages consent records per patient. Consent is initialised during
 * patient creation (Phase 2). This service adds full lifecycle:
 * retrieve, update with history appended, and history retrieval.
 *
 * Per FR-CON-01 through FR-CON-05.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;
    private final TwinService twinService;

    private static final String MODULE = "Consent";

    // ── Get consent ───────────────────────────────────────────────────────

    public ConsentResponse getConsent(String patientId) {
        Consent consent = findConsentOrThrow(patientId);
        return ConsentResponse.from(consent);
    }

    // ── Update consent ────────────────────────────────────────────────────

    public ConsentResponse updateConsent(String patientId,
                                         UpdateConsentRequest request,
                                         String providerId,
                                         String providerName) {
        // Verify patient exists
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        Consent consent = findConsentOrThrow(patientId);
        Instant now = Instant.now();

        // Append a history entry for each field that is actually changing
        if (request.ehr() != null && request.ehr() != consent.isEhr()) {
            appendHistory(consent, "EHR Data Access",
                    request.ehr() ? "Granted" : "Declined", providerName, now);
        }
        if (request.wearable() != null && request.wearable() != consent.isWearable()) {
            appendHistory(consent, "Wearable Data Access",
                    request.wearable() ? "Granted" : "Declined", providerName, now);
        }
        if (request.ai() != null && request.ai() != consent.isAi()) {
            appendHistory(consent, "AI Risk Analysis",
                    request.ai() ? "Granted" : "Declined", providerName, now);
        }

        // Apply changes
        if (request.ehr() != null)      consent.setEhr(request.ehr());
        if (request.wearable() != null) consent.setWearable(request.wearable());
        if (request.ai() != null)       consent.setAi(request.ai());

        consent.setUpdatedAt(now);
        consent.setUpdatedBy(providerName);

        Consent saved = consentRepository.save(consent);

        // FR-CON-05: propagate EHR/AI withdrawal to twin data sources
        if (patient.getTwinId() != null) {
            try {
                if (request.ehr() != null) {
                    twinService.updateDataSource(patient.getTwinId(), "ehr",
                            saved.isEhr(), now);
                }
                if (request.ai() != null) {
                    // AI consent affects the kafka/streaming data source flag
                    twinService.updateDataSource(patient.getTwinId(), "kafka",
                            saved.isAi(), now);
                }
            } catch (Exception e) {
                log.warn("Could not update twin data sources for patient {} after consent change: {}",
                        patientId, e.getMessage());
            }
        }

        // Update patient consentComplete flag
        boolean complete = saved.isEhr() && saved.isAi();
        patient.setConsentComplete(complete);
        patientRepository.save(patient);

        // Audit
        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Updated Patient Consent")
                .module(MODULE)
                .patientId(patientId)
                .patientName(patient.getName())
                .status("Success")
                .build());

        log.info("Consent updated for patient {} by {}", patientId, providerId);
        return ConsentResponse.from(saved);
    }

    // ── Get consent history ───────────────────────────────────────────────

    public List<ConsentHistoryEntryResponse> getConsentHistory(String patientId) {
        Consent consent = findConsentOrThrow(patientId);

        return consent.getHistory().stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate())) // newest first
                .map(ConsentHistoryEntryResponse::from)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Consent findConsentOrThrow(String patientId) {
        return consentRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Consent for patient", patientId));
    }

    private void appendHistory(Consent consent, String type,
                                String status, String updatedBy, Instant date) {
        Consent.ConsentHistoryEntry entry = Consent.ConsentHistoryEntry.builder()
                .id("CH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .date(date)
                .type(type)
                .status(status)
                .updatedBy(updatedBy)
                .build();
        consent.getHistory().add(entry);
    }
}

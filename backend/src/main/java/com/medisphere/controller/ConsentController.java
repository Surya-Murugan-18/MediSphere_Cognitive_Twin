package com.medisphere.controller;

import com.medisphere.dto.request.UpdateConsentRequest;
import com.medisphere.dto.response.ConsentHistoryEntryResponse;
import com.medisphere.dto.response.ConsentResponse;
import com.medisphere.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase 7 — B7.1 Consent Controller
 *
 * Per design.md §10 and tasks.md B7.1:
 *
 *   GET /api/patients/{id}/consent         — any authenticated
 *   PUT /api/patients/{id}/consent         — CLINICIAN, ADMIN, NURSE (FR-RBAC-04)
 *   GET /api/patients/{id}/consent/history — any authenticated
 */
@RestController
@RequestMapping("/api/patients/{patientId}/consent")
@Tag(name = "Consent", description = "Patient consent lifecycle management")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get patient consent", description = "Returns current consent state for a patient")
    public ResponseEntity<ConsentResponse> getConsent(@PathVariable String patientId) {
        return ResponseEntity.ok(consentService.getConsent(patientId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN','NURSE')")
    @Operation(summary = "Update patient consent",
               description = "Updates consent flags. Each changed field appends a history entry. Logs audit.")
    public ResponseEntity<ConsentResponse> updateConsent(
            @PathVariable String patientId,
            @RequestBody UpdateConsentRequest request,
            Authentication auth) {

        String providerId   = (String) auth.getPrincipal();
        String providerName = auth.getDetails() instanceof String s ? s : providerId;

        return ResponseEntity.ok(
                consentService.updateConsent(patientId, request, providerId, providerName));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get consent history",
               description = "Returns the immutable consent change history for a patient, newest first")
    public ResponseEntity<List<ConsentHistoryEntryResponse>> getConsentHistory(
            @PathVariable String patientId) {
        return ResponseEntity.ok(consentService.getConsentHistory(patientId));
    }
}

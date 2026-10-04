package com.medisphere.controller;

import com.medisphere.domain.CarePlanStatus;
import com.medisphere.dto.request.*;
import com.medisphere.dto.response.*;
import com.medisphere.service.AdherenceService;
import com.medisphere.service.CarePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for care plan lifecycle.
 *
 * Per design.md §10 and tasks.md B6.3:
 *
 *   POST /api/care-plans/generate              — CLINICIAN, ADMIN
 *   GET  /api/care-plans                       — authenticated
 *   GET  /api/care-plans/stats                 — authenticated
 *   GET  /api/care-plans/{id}                  — authenticated
 *   PUT  /api/care-plans/{id}                  — CLINICIAN, ADMIN
 *   POST /api/care-plans/{id}/approve          — CLINICIAN, ADMIN
 *   POST /api/care-plans/{id}/reject           — CLINICIAN, ADMIN
 *   GET  /api/care-plans/{id}/timeline         — authenticated
 *   GET  /api/care-plans/{id}/adherence        — authenticated
 *   GET  /api/care-plans/{id}/adherence/trend  — authenticated
 *   POST /api/care-plans/{id}/adherence        — CLINICIAN, ADMIN, NURSE
 *   GET  /api/care-plans/{id}/outcomes         — authenticated
 *
 * Active plan for a patient:
 *   GET /api/patients/{id}/care-plan           — in PatientController
 */
@RestController
@RequestMapping("/api/care-plans")
@Tag(name = "Care Plans", description = "AI-assisted care plan generation and approval workflow")
public class CarePlanController {

    private final CarePlanService carePlanService;
    private final AdherenceService adherenceService;

    public CarePlanController(CarePlanService carePlanService, AdherenceService adherenceService) {
        this.carePlanService = carePlanService;
        this.adherenceService = adherenceService;
    }

    // ── POST /generate ────────────────────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Generate AI care plan",
               description = "Generates a Draft care plan using the AI stub. CLINICIAN or ADMIN only.")
    public ResponseEntity<CarePlanResponse> generateCarePlan(
            @Valid @RequestBody GenerateCarePlanRequest request,
            Authentication auth) {
        String providerId   = auth.getName();
        String providerName = extractProviderName(auth);
        return ResponseEntity.ok(carePlanService.generateCarePlan(request, providerId, providerName));
    }

    // ── GET /  ────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List care plans", description = "Paginated, filtered care plan list")
    public ResponseEntity<PageResponse<CarePlanResponse>> getCarePlans(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String risk,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        CarePlanStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = CarePlanStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // unknown status — treat as no filter
            }
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(carePlanService.getCarePlans(patientId, statusEnum, risk, pageable));
    }

    // ── GET /stats ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Care plan statistics")
    public ResponseEntity<CarePlanStatsResponse> getStats() {
        return ResponseEntity.ok(carePlanService.getCarePlanStats());
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get care plan by ID")
    public ResponseEntity<CarePlanResponse> getCarePlan(@PathVariable String id) {
        return ResponseEntity.ok(carePlanService.getCarePlan(id));
    }

    // ── PUT /{id} ─────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Update care plan", description = "Edit mode — updates goal and riskLevel only.")
    public ResponseEntity<CarePlanResponse> updateCarePlan(
            @PathVariable String id,
            @Valid @RequestBody UpdateCarePlanRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                carePlanService.updateCarePlan(id, request, auth.getName(), extractProviderName(auth)));
    }

    // ── POST /{id}/approve ────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Approve care plan",
               description = "Sets status to ACTIVE. Publishes careplan.approved event. CLINICIAN or ADMIN only.")
    public ResponseEntity<CarePlanResponse> approveCarePlan(
            @PathVariable String id,
            @RequestBody(required = false) ApproveCarePlanRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                carePlanService.approveCarePlan(id, request, auth.getName(), extractProviderName(auth)));
    }

    // ── POST /{id}/reject ─────────────────────────────────────────────────

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Reject care plan",
               description = "Sets status to REJECTED. Reason is mandatory. CLINICIAN or ADMIN only.")
    public ResponseEntity<CarePlanResponse> rejectCarePlan(
            @PathVariable String id,
            @Valid @RequestBody RejectCarePlanRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                carePlanService.rejectCarePlan(id, request, auth.getName(), extractProviderName(auth)));
    }

    // ── GET /{id}/timeline ────────────────────────────────────────────────

    @GetMapping("/{id}/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Care plan timeline")
    public ResponseEntity<List<TimelineEventResponse>> getTimeline(@PathVariable String id) {
        return ResponseEntity.ok(carePlanService.getCarePlanTimeline(id));
    }

    // ── Adherence nested endpoints ────────────────────────────────────────

    @GetMapping("/{id}/adherence")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get adherence for plan")
    public ResponseEntity<AdherenceResponse> getAdherence(@PathVariable String id) {
        return ResponseEntity.ok(adherenceService.getAdherence(id));
    }

    @GetMapping("/{id}/adherence/trend")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get adherence trend")
    public ResponseEntity<List<AdherenceTrendPoint>> getAdherenceTrend(
            @PathVariable String id,
            @RequestParam(defaultValue = "6") int weeks) {
        return ResponseEntity.ok(adherenceService.getAdherenceTrend(id, weeks));
    }

    @PostMapping("/{id}/adherence")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN','NURSE')")
    @Operation(summary = "Record weekly adherence")
    public ResponseEntity<Void> recordAdherence(
            @PathVariable String id,
            @Valid @RequestBody RecordAdherenceRequest request) {
        adherenceService.recordAdherence(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/outcomes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get outcomes for plan")
    public ResponseEntity<List<OutcomeResponse>> getOutcomes(@PathVariable String id) {
        return ResponseEntity.ok(adherenceService.getOutcomes(id));
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Extract provider display name from the JWT Authentication object.
     * JwtAuthenticationFilter stores the provider name in auth.getDetails() as a String.
     * Fallback: use auth.getName() (the provider ID).
     * Follows the same pattern as PatientController.extractName().
     */
    private String extractProviderName(Authentication auth) {
        if (auth.getDetails() instanceof String s && !s.isBlank()) {
            return s;
        }
        return auth.getName();
    }
}

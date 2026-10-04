package com.medisphere.controller;

import com.medisphere.audit.Auditable;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.dto.request.UpdatePatientRequest;
import com.medisphere.dto.response.AlertResponse;
import com.medisphere.dto.response.FilterOptionsResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.PatientResponse;
import com.medisphere.dto.response.PatientSummaryResponse;
import com.medisphere.dto.response.PredictionResponse;
import com.medisphere.dto.response.TwinResponse;
import com.medisphere.service.AlertService;
import com.medisphere.service.CarePlanService;
import com.medisphere.service.PatientService;
import com.medisphere.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Patient CRUD and patient-scoped sub-resources")
public class PatientController {

    private final PatientService patientService;
    private final PredictionService predictionService;
    private final AlertService alertService;
    private final CarePlanService carePlanService;

    public PatientController(PatientService patientService,
                              PredictionService predictionService,
                              AlertService alertService,
                              CarePlanService carePlanService) {
        this.patientService = patientService;
        this.predictionService = predictionService;
        this.alertService = alertService;
        this.carePlanService = carePlanService;
    }

    // ── GET /api/patients ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List patients", description = "Paginated, server-side filtered patient list")
    public ResponseEntity<PageResponse<PatientSummaryResponse>> getPatients(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String providerName,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        // Guard: max page size to prevent memory abuse
        int safeSize = Math.min(size, 100);

        return ResponseEntity.ok(patientService.getPatients(
                search, riskLevel, condition, status, providerName, page, safeSize));
    }

    // ── POST /api/patients ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Create patient", description = "Register a new patient; auto-creates digital health twin and consent record")
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            Authentication auth) {

        String providerId   = (String) auth.getPrincipal();
        String providerName = extractName(auth);

        PatientResponse response = patientService.createPatient(request, providerId, providerName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/patients/next-id ─────────────────────────────────────────

    @GetMapping("/next-id")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Preview next patient ID", description = "Returns the next patient ID without consuming it")
    public ResponseEntity<NextIdResponse> getNextId() {
        return ResponseEntity.ok(new NextIdResponse(patientService.getNextId()));
    }

    // ── GET /api/patients/filter-options ─────────────────────────────────

    @GetMapping("/filter-options")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Filter options", description = "Returns available values for filter dropdowns")
    public ResponseEntity<FilterOptionsResponse> getFilterOptions() {
        return ResponseEntity.ok(patientService.getFilterOptions());
    }

    // ── GET /api/patients/{id} ────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "Viewed Patient Record", module = "Patients", patientParam = "#id")
    @Operation(summary = "Get patient", description = "Full patient record including twin status")
    public ResponseEntity<PatientResponse> getPatient(
            @PathVariable String id,
            Authentication auth) {

        String providerId = (String) auth.getPrincipal();
        return ResponseEntity.ok(patientService.getPatient(id, providerId));
    }

    // ── PUT /api/patients/{id} ────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Auditable(action = "Updated patient", module = "Patients", patientParam = "#id")
    @Operation(summary = "Update patient", description = "Partial update — only provided fields are changed")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable String id,
            @Valid @RequestBody UpdatePatientRequest request,
            Authentication auth) {

        String providerId   = (String) auth.getPrincipal();
        String providerName = extractName(auth);
        return ResponseEntity.ok(patientService.updatePatient(id, request, providerId, providerName));
    }

    // ── GET /api/patients/{id}/timeline ──────────────────────────────────

    @GetMapping("/{id}/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Patient timeline", description = "Twin state timeline for the patient")
    public ResponseEntity<List<TwinResponse.TimelineEventResponse>> getTimeline(
            @PathVariable String id) {
        return ResponseEntity.ok(patientService.getTimeline(id));
    }

    // ── GET /api/patients/{id}/predictions ────────────────────────────────

    @GetMapping("/{id}/predictions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Patient predictions",
               description = "All AI risk predictions for a patient, newest first")
    public ResponseEntity<List<PredictionResponse>> getPatientPredictions(
            @PathVariable String id,
            Authentication auth) {
        return ResponseEntity.ok(
                predictionService.getPatientPredictions(id, (String) auth.getPrincipal()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    // ── GET /api/patients/{id}/alerts ─────────────────────────────────────

    @GetMapping("/{id}/alerts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get patient alerts")
    public ResponseEntity<List<AlertResponse>> getPatientAlerts(@PathVariable String id) {
        return ResponseEntity.ok(alertService.getPatientAlerts(id));
    }

    // ── GET /api/patients/{id}/care-plan ──────────────────────────────────

    @GetMapping("/{id}/care-plan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active care plan",
               description = "Returns the ACTIVE care plan for a patient. 404 if no active plan exists.")
    public ResponseEntity<com.medisphere.dto.response.CarePlanResponse> getActiveCarePlan(
            @PathVariable String id) {
        return ResponseEntity.ok(carePlanService.getActiveCarePlan(id));
    }

    private String extractName(Authentication auth) {
        if (auth.getDetails() instanceof String s) return s;
        return auth.getPrincipal().toString();
    }

    /** Simple next-id response wrapper */
    public record NextIdResponse(String nextId) {}
}

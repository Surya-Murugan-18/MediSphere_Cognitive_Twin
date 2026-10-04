package com.medisphere.controller;

import com.medisphere.audit.Auditable;
import com.medisphere.dto.request.AcknowledgeAlertRequest;
import com.medisphere.dto.request.EscalateAlertRequest;
import com.medisphere.dto.request.ResolveAlertRequest;
import com.medisphere.dto.response.AlertCountResponse;
import com.medisphere.dto.response.AlertResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for clinical alert lifecycle.
 *
 * Per design.md §10 API contract and tasks.md B5.4:
 *
 *   GET    /api/alerts
 *   GET    /api/alerts/count
 *   GET    /api/alerts/{id}
 *   PATCH  /api/alerts/{id}/acknowledge   — NURSE, CLINICIAN, ADMIN
 *   POST   /api/alerts/{id}/escalate      — CLINICIAN, ADMIN
 *   POST   /api/alerts/{id}/resolve       — CLINICIAN, ADMIN
 *
 * Patient-scoped endpoint is in PatientController:
 *   GET    /api/patients/{id}/alerts
 */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Clinical alert lifecycle management")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // ── GET /api/alerts ───────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List alerts", description = "Server-side filtered, paginated alert list")
    public ResponseEntity<PageResponse<AlertResponse>> getAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, 100);
        return ResponseEntity.ok(
                alertService.getAlerts(severity, status, patientId, type, from, to, page, safeSize));
    }

    // ── GET /api/alerts/count ─────────────────────────────────────────────

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Alert count", description = "Count alerts by status (e.g. status=Unacknowledged)")
    public ResponseEntity<AlertCountResponse> getAlertCount(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(alertService.getAlertCount(status));
    }

    // ── GET /api/alerts/{id} ──────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "Viewed Alert", module = "Alerts")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable String id) {
        return ResponseEntity.ok(alertService.getAlert(id));
    }

    // ── PATCH /api/alerts/{id}/acknowledge ────────────────────────────────

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('NURSE','CLINICIAN','ADMIN')")
    @Operation(summary = "Acknowledge alert", description = "Allowed: NURSE, CLINICIAN, ADMIN")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable String id,
            @RequestBody(required = false) AcknowledgeAlertRequest req,
            Authentication auth) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(id, req, auth));
    }

    // ── POST /api/alerts/{id}/escalate ────────────────────────────────────

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Escalate alert", description = "Allowed: CLINICIAN, ADMIN")
    public ResponseEntity<AlertResponse> escalateAlert(
            @PathVariable String id,
            @Valid @RequestBody EscalateAlertRequest req,
            Authentication auth) {
        return ResponseEntity.ok(alertService.escalateAlert(id, req, auth));
    }

    // ── POST /api/alerts/{id}/resolve ─────────────────────────────────────

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Resolve alert", description = "Allowed: CLINICIAN, ADMIN")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable String id,
            @Valid @RequestBody ResolveAlertRequest req,
            Authentication auth) {
        return ResponseEntity.ok(alertService.resolveAlert(id, req, auth));
    }
}

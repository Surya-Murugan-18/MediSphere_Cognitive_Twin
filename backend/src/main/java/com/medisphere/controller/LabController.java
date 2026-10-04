package com.medisphere.controller;

import com.medisphere.dto.response.LabResultResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.service.LabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients/{id}/labs")
@Tag(name = "Laboratory Results", description = "FHIR-ingested laboratory results per patient")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    /**
     * GET /api/patients/{id}/labs
     * Filtered, paginated laboratory results for a patient.
     *
     * Query params:
     *   category  — All types | Metabolic | Lipids | Cardiac | Hematology
     *   status    — All statuses | High | Low | Normal | Pending
     *   dateFrom  — ISO-8601 date (inclusive)
     *   dateTo    — ISO-8601 date (inclusive)
     *   page      — 0-indexed page number (default 0)
     *   size      — page size (default 20, max 100)
     *
     * Phase 3 FR-AUD-05: Authentication is required so the requesting provider's
     * identity can be recorded in the audit log.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get lab results", description = "Paginated filtered laboratory results for a patient")
    public ResponseEntity<PageResponse<LabResultResponse>> getLabResults(
            @PathVariable String id,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        int safeSize = Math.min(size, 100);
        String providerId = (String) auth.getPrincipal();
        return ResponseEntity.ok(
                labService.getLabResults(id, category, status, dateFrom, dateTo, page, safeSize, providerId));
    }

    /**
     * GET /api/patients/{id}/labs/recent?limit=3
     * Top N most recent results — used by Patient360 overview and labs tab.
     */
    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Recent lab results", description = "Top N most recent laboratory results for overview panels")
    public ResponseEntity<List<LabResultResponse>> getRecentLabResults(
            @PathVariable String id,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(labService.getRecentLabResults(id, Math.min(limit, 10)));
    }
}

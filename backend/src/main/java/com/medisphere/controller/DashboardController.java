package com.medisphere.controller;

import com.medisphere.dto.response.DashboardStatsResponse;
import com.medisphere.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 7 — B7.8 Dashboard Controller
 *
 * Per design.md §10 and tasks.md B7.8:
 *
 *   GET /api/dashboard/stats — returns all 8 KPI values in a single response
 *
 * Authorization: any authenticated user (ADMIN, CLINICIAN, NURSE, ANALYST).
 * No PHI in the response — only aggregate counts and averages.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Clinical operations dashboard KPI aggregation")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get dashboard statistics",
               description = "Returns all 8 KPI card values aggregated from patients, alerts, " +
                             "wearables, care plans, and FHIR resources in a single API call.")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}

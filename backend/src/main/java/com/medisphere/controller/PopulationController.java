package com.medisphere.controller;

import com.medisphere.dto.response.*;
import com.medisphere.service.PopulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Phase 7 — B7.3 Population Health Controller
 *
 * Per design.md §10 and tasks.md B7.3 / FR-POP-01 through FR-POP-06:
 *
 *   GET /api/population/stats                  — aggregate KPIs
 *   GET /api/population/risk-distribution      — risk donut data
 *   GET /api/population/condition-distribution — condition bar chart data
 *   GET /api/population/hospitals              — per-hospital table
 *   GET /api/population/alert-trend            — 7-day alert volume
 *   GET /api/population/categories             — cardiovascular/diabetes/readmission cohorts
 *
 * Authorization: ANALYST, CLINICIAN, ADMIN (FR-POP-06)
 * No individual patient PHI in any response.
 */
@RestController
@RequestMapping("/api/population")
@Tag(name = "Population Health", description = "Cohort-level risk, condition prevalence and alert analytics")
public class PopulationController {

    private final PopulationService populationService;

    public PopulationController(PopulationService populationService) {
        this.populationService = populationService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Population statistics",
               description = "Total patient count, risk counts, avg adherence, active alerts and care plans")
    public ResponseEntity<PopulationStatsResponse> getStats() {
        return ResponseEntity.ok(populationService.getStats());
    }

    @GetMapping("/risk-distribution")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Risk distribution",
               description = "Patient counts grouped by risk level (High/Medium/Low)")
    public ResponseEntity<List<Map<String, Object>>> getRiskDistribution() {
        return ResponseEntity.ok(populationService.getRiskDistribution());
    }

    @GetMapping("/condition-distribution")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Condition distribution",
               description = "Top 10 primary documented conditions by patient count")
    public ResponseEntity<List<Map<String, Object>>> getConditionDistribution() {
        return ResponseEntity.ok(populationService.getConditionDistribution());
    }

    @GetMapping("/hospitals")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Hospital overview",
               description = "Per-hospital patient count, high-risk count, adherence, and alert volume")
    public ResponseEntity<List<HospitalSummaryResponse>> getHospitalOverview() {
        return ResponseEntity.ok(populationService.getHospitalOverview());
    }

    @GetMapping("/alert-trend")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Alert trend",
               description = "Daily alert volume for the past 7 days")
    public ResponseEntity<List<AlertTrendPointResponse>> getAlertTrend() {
        return ResponseEntity.ok(populationService.getAlertTrend());
    }

    @GetMapping("/adherence-trend")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Population adherence trend",
               description = "Weekly average adherence across active care plans")
    public ResponseEntity<List<AdherenceTrendPoint>> getAdherenceTrend() {
        return ResponseEntity.ok(populationService.getAdherenceTrend());
    }

    @GetMapping("/risk-trend")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Population risk trend",
               description = "Monthly high and medium risk cohorts from persisted prediction history")
    public ResponseEntity<List<PopulationRiskTrendPointResponse>> getRiskTrend() {
        return ResponseEntity.ok(populationService.getRiskTrend());
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ANALYST','CLINICIAN','ADMIN')")
    @Operation(summary = "Population categories",
               description = "Cardiovascular, diabetes, and readmission cohort counts and trends")
    public ResponseEntity<List<PopulationCategoryResponse>> getCategories() {
        return ResponseEntity.ok(populationService.getCategories());
    }
}

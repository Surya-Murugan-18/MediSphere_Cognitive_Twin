package com.medisphere.controller;

import com.medisphere.dto.response.ReportCardResponse;
import com.medisphere.dto.response.ReportJobStatusResponse;
import com.medisphere.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Phase 7 — B7.4 Report Controller
 *
 * Per design.md §10 and tasks.md B7.4:
 *
 *   GET  /api/reports                   → report cards (CLINICIAN, ADMIN)
 *   POST /api/reports/{id}/generate     → create job + start async (CLINICIAN, ADMIN)
 *   GET  /api/reports/{id}/status       → job status by jobId param (CLINICIAN, ADMIN)
 *   GET  /api/reports/{id}/export       → stream PDF or CSV (CLINICIAN, ADMIN)
 *
 * ANALYST is excluded from report access per FR-REP-05.
 * WebSocket notification sent to /topic/reports.{reportId} on completion.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Clinical report generation and export")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "List reports",
               description = "Returns all report card metadata with last-run status from report_jobs")
    public ResponseEntity<List<ReportCardResponse>> getReports() {
        return ResponseEntity.ok(reportService.getReports());
    }

    @PostMapping("/{reportId}/generate")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Generate report",
               description = "Creates a ReportJob and starts async generation. Returns jobId for WebSocket tracking.")
    public ResponseEntity<Map<String, String>> generateReport(
            @PathVariable String reportId,
            Authentication auth) {
        String providerId = (String) auth.getPrincipal();
        return ResponseEntity.ok(reportService.generateReport(reportId, providerId));
    }

    @GetMapping("/{reportId}/status")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Get report job status",
               description = "Returns current status of a report generation job by jobId")
    public ResponseEntity<ReportJobStatusResponse> getReportStatus(
            @PathVariable String reportId,
            @RequestParam String jobId) {
        return ResponseEntity.ok(reportService.getReportStatus(jobId));
    }

    @GetMapping("/{reportId}/export")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Export report",
               description = "Streams PDF (default) or CSV export of a completed report")
    public void exportReport(
            @PathVariable String reportId,
            @RequestParam(defaultValue = "pdf") String format,
            Authentication auth,
            HttpServletResponse response) throws IOException {
        String providerId = (String) auth.getPrincipal();
        reportService.exportReport(reportId, format, providerId, response);
    }
}

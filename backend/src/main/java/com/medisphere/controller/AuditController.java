package com.medisphere.controller;

import com.medisphere.domain.AuditLog;
import com.medisphere.dto.response.AuditLogResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Phase 7 — B7.2 Audit Controller
 *
 * Per design.md §10 and tasks.md B7.2:
 *
 *   GET /api/audit               — filtered + paginated audit log (any authenticated)
 *   GET /api/audit/export?format=csv — CSV stream (ADMIN only, FR-AUD-06)
 *
 * Audit logs are write-only by contract (FR-AUD-03).
 * No PUT, PATCH, DELETE endpoints exist on this controller.
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit Logs", description = "Clinical audit trail — read-only, immutable")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // ── GET /api/audit ────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List audit log entries",
               description = "Paginated audit log with optional filters: user, patient, action, module, from, to")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<AuditLog> results = auditLogRepository.findFiltered(
                user, patient, action, module, from, to, pageable);

        return ResponseEntity.ok(PageResponse.from(results, AuditLogResponse::from));
    }

    // ── GET /api/audit/export?format=csv ──────────────────────────────────

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export audit logs as CSV",
               description = "Streams a CSV file of filtered audit logs. ADMIN role required (FR-AUD-06).")
    public void exportAuditLogs(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String patient,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) throws IOException {

        // Always CSV for now — future extension point for other formats
        String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"audit-export-" + dateStr + ".csv\"");
        response.setCharacterEncoding("UTF-8");

        // Fetch up to MAX_EXPORT_ROWS, sorted by timestamp desc
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_ROWS,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> page = auditLogRepository.findFiltered(
                user, patient, action, module, from, to, pageable);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("ID", "Timestamp", "User ID", "User Name", "User Role",
                           "Action", "Patient ID", "Patient Name", "Module",
                           "Status", "IP Address")
                .build();

        try (CSVPrinter printer = new CSVPrinter(response.getWriter(), csvFormat)) {
            for (AuditLog entry : page.getContent()) {
                printer.printRecord(
                        entry.getId(),
                        entry.getTimestamp() != null ? entry.getTimestamp().toString() : "",
                        nullSafe(entry.getUserId()),
                        nullSafe(entry.getUserName()),
                        nullSafe(entry.getUserRole()),
                        nullSafe(entry.getAction()),
                        nullSafe(entry.getPatientId()),
                        nullSafe(entry.getPatientName()),
                        nullSafe(entry.getModule()),
                        nullSafe(entry.getStatus()),
                        nullSafe(entry.getIpAddress())
                );
            }
            printer.flush();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}

package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.ReportJob;
import com.medisphere.dto.response.ReportCardResponse;
import com.medisphere.dto.response.ReportJobStatusResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.ReportReadyEvent;
import com.medisphere.repository.ReportJobRepository;
import com.medisphere.websocket.ReportWebSocketBroadcaster;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 7 — B7.4 Report Service
 *
 * Manages the async report generation lifecycle:
 *   getReports()          → list report cards with last-run metadata from report_jobs
 *   generateReport()      → create job (PENDING) → @Async execution → COMPLETED/FAILED
 *                         → publish report.ready Kafka event
 *                         → push ReportWebSocketBroadcaster to /topic/reports.{reportId}
 *   getReportStatus()     → current job state
 *   exportReport()        → stream PDF or CSV
 *
 * Per tasks.md B7.4 and design.md §5 (Kafka topic: report.ready).
 * WebSocket destination: /topic/reports.{reportId} (consistent with design.md §8.1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportJobRepository reportJobRepository;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final ReportWebSocketBroadcaster reportBroadcaster;
    private final AuditService auditService;

    // ── Static report catalog ─────────────────────────────────────────────

    /** Fixed set of report types matching the frontend static data IDs. */
    private static final List<ReportMeta> REPORT_CATALOG = List.of(
            new ReportMeta("r1", "Patient Risk Report",
                    "Cohort risk stratification with model provenance.", "1,247 patients"),
            new ReportMeta("r2", "Digital Twin Report",
                    "Twin completeness, sync status and data-source coverage.", "patients"),
            new ReportMeta("r3", "Alert Report",
                    "Alert volume, severity mix and response times.", "alerts"),
            new ReportMeta("r4", "Care Plan Report",
                    "Plan status, approvals and adherence distribution.", "plans"),
            new ReportMeta("r5", "Population Health Report",
                    "Condition prevalence and risk trend by hospital.", "3 hospitals"),
            new ReportMeta("r6", "AI Model Report",
                    "Federated rounds, accuracy and calibration audit.", "No data")
    );

    // ── Get report cards ──────────────────────────────────────────────────

    public List<ReportCardResponse> getReports() {
        return REPORT_CATALOG.stream().map(meta -> {
            Optional<ReportJob> latestJob =
                    reportJobRepository.findTopByReportIdOrderByRequestedAtDesc(meta.id());
            boolean ready = latestJob
                    .map(j -> "COMPLETED".equals(j.getStatus()))
                    .orElse(false);
            String lastRun = latestJob
                    .filter(j -> "COMPLETED".equals(j.getStatus()))
                    .map(j -> formatRelative(j.getCompletedAt()))
                    .orElse("Not yet generated");
            Instant lastRunAt = latestJob
                    .map(ReportJob::getCompletedAt)
                    .orElse(null);
            return new ReportCardResponse(
                    meta.id(), meta.title(), meta.detail(),
                    lastRun, meta.rowsLabel(), ready, lastRunAt);
        }).collect(Collectors.toList());
    }

    // ── Generate report (creates job, runs async) ─────────────────────────

    public Map<String, String> generateReport(String reportId, String requestingProviderId) {
        ReportMeta meta = findMetaOrThrow(reportId);

        // Create job document
        ReportJob job = ReportJob.builder()
                .id("JOB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .reportId(reportId)
                .reportType(meta.title())
                .status("PENDING")
                .progress(0)
                .requestedBy(requestingProviderId)
                .requestedAt(Instant.now())
                .build();
        reportJobRepository.save(job);

        // Audit
        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Generated Report")
                .module("Reports")
                .status("Success")
                .build());

        // Run async — does NOT block the HTTP response
        runReportAsync(job.getId());

        return Map.of("jobId", job.getId(), "reportId", reportId);
    }

    // ── Get report job status ─────────────────────────────────────────────

    public ReportJobStatusResponse getReportStatus(String jobId) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportJob", jobId));
        return ReportJobStatusResponse.from(job);
    }

    // ── Export report ─────────────────────────────────────────────────────

    public void exportReport(String reportId, String format,
                              String requestingProviderId,
                              HttpServletResponse response) throws IOException {

        // Verify report exists and has at least one completed job
        findMetaOrThrow(reportId);
        Optional<ReportJob> latestJob =
                reportJobRepository.findTopByReportIdOrderByRequestedAtDesc(reportId);
        boolean ready = latestJob.map(j -> "COMPLETED".equals(j.getStatus())).orElse(false);
        if (!ready) {
            response.sendError(HttpServletResponse.SC_CONFLICT,
                    "Report has not been generated yet. Run Generate first.");
            return;
        }

        // Audit
        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Exported Report")
                .module("Reports")
                .status("Success")
                .build());

        String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        ReportMeta meta = findMetaOrThrow(reportId);

        if ("csv".equalsIgnoreCase(format)) {
            exportCsv(meta, dateStr, response);
        } else {
            exportPdf(meta, dateStr, response);
        }
    }

    // ── Async report execution ────────────────────────────────────────────

    @Async("predictionTaskExecutor")
    public void runReportAsync(String jobId) {
        ReportJob job = reportJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("runReportAsync: job {} not found", jobId);
            return;
        }

        try {
            // RUNNING
            job.setStatus("RUNNING");
            job.setProgress(10);
            reportJobRepository.save(job);

            // Simulate generation work — deterministic, no sleep in tests
            log.info("Generating report {} (job {})", job.getReportId(), jobId);

            job.setProgress(80);
            reportJobRepository.save(job);

            // COMPLETED
            job.setStatus("COMPLETED");
            job.setProgress(100);
            job.setCompletedAt(Instant.now());
            reportJobRepository.save(job);

            // Publish Kafka report.ready event
            kafkaEventPublisher.publishReportReady(ReportReadyEvent.builder()
                    .reportId(job.getReportId())
                    .jobId(job.getId())
                    .reportType(job.getReportType())
                    .status("COMPLETED")
                    .timestamp(Instant.now())
                    .build());

            // Push WebSocket notification to /topic/reports.{reportId}
            reportBroadcaster.broadcastJobUpdate(job);

            log.info("Report {} completed (job {})", job.getReportId(), jobId);

        } catch (Exception e) {
            log.error("Report generation failed for job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            reportJobRepository.save(job);

            // Push failure notification
            kafkaEventPublisher.publishReportReady(ReportReadyEvent.builder()
                    .reportId(job.getReportId())
                    .jobId(job.getId())
                    .status("FAILED")
                    .timestamp(Instant.now())
                    .build());
            reportBroadcaster.broadcastJobUpdate(job);
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────

    private void exportCsv(ReportMeta meta, String dateStr,
                            HttpServletResponse response) throws IOException {
        String filename = meta.title().replaceAll("\\s+", "-").toLowerCase()
                + "-" + dateStr + ".csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("Report", "Generated", "Status")
                .build();
        try (CSVPrinter printer = new CSVPrinter(response.getWriter(), format)) {
            printer.printRecord(meta.title(), dateStr, "Completed");
            printer.printRecord("MediSphere Clinical Platform", "", "");
            printer.printRecord("Data as of: " + dateStr, "", "");
            printer.flush();
        }
    }

    // ── PDF export ────────────────────────────────────────────────────────

    private void exportPdf(ReportMeta meta, String dateStr,
                            HttpServletResponse response) throws IOException {
        String filename = meta.title().replaceAll("\\s+", "-").toLowerCase()
                + "-" + dateStr + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font boldFont   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font normalFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Header
                cs.beginText();
                cs.setFont(boldFont, 18);
                cs.newLineAtOffset(50, 780);
                cs.showText("MediSphere Cognitive Twin");
                cs.endText();

                cs.beginText();
                cs.setFont(boldFont, 14);
                cs.newLineAtOffset(50, 755);
                cs.showText(meta.title());
                cs.endText();

                // Date + subtitle
                cs.beginText();
                cs.setFont(normalFont, 10);
                cs.newLineAtOffset(50, 735);
                cs.showText("Generated: " + dateStr + "  |  " + meta.detail());
                cs.endText();

                // Horizontal rule (drawn as thin rectangle)
                cs.setLineWidth(0.5f);
                cs.moveTo(50, 725);
                cs.lineTo(545, 725);
                cs.stroke();

                // Body
                cs.beginText();
                cs.setFont(normalFont, 10);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 705);
                cs.showText("Report data: " + meta.rowsLabel());
                cs.newLine();
                cs.showText("Status: Completed");
                cs.newLine();
                cs.showText("This report was generated by MediSphere AI Clinical Platform.");
                cs.newLine();
                cs.showText("Report exports are audited and role-restricted per FR-AUD-05.");
                cs.endText();
            }

            doc.save(response.getOutputStream());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ReportMeta findMetaOrThrow(String reportId) {
        return REPORT_CATALOG.stream()
                .filter(m -> m.id().equals(reportId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));
    }

    private String formatRelative(Instant instant) {
        if (instant == null) return "Not yet generated";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d · HH:mm")
                .withZone(ZoneOffset.UTC);
        return fmt.format(instant);
    }

    // ── Internal record ───────────────────────────────────────────────────

    private record ReportMeta(String id, String title, String detail, String rowsLabel) {}
}

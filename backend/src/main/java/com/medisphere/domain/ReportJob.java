package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Async report generation job document.
 * Collection: report_jobs
 *
 * Lifecycle: PENDING → RUNNING → COMPLETED | FAILED
 * One job document is created per generateReport() call.
 */
@Document(collection = "report_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportJob {

    @Id
    private String id;          // jobId — "JOB-{uuid}"

    @Indexed
    private String reportId;    // e.g. "r1", "r2" — the logical report type

    private String reportType;  // e.g. "Patient Risk Report"

    /** PENDING | RUNNING | COMPLETED | FAILED */
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private int progress = 0;   // 0–100

    private String requestedBy; // providerId

    private Instant requestedAt;

    private Instant completedAt;

    private String errorMessage;
}

package com.medisphere.dto.response;

import java.time.Instant;

/**
 * Report card metadata returned by GET /api/reports
 *
 * Maps the static report types to their last-run metadata from report_jobs.
 */
public record ReportCardResponse(
        String id,
        String title,
        String detail,
        String lastRun,
        String rows,
        boolean ready,
        Instant lastRunAt
) {}

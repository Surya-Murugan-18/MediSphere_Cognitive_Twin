package com.medisphere.dto.response;

import com.medisphere.domain.ReportJob;

import java.time.Instant;

/**
 * Report job status response for GET /api/reports/{id}/status?jobId=
 */
public record ReportJobStatusResponse(
        String jobId,
        String reportId,
        /** PENDING | RUNNING | COMPLETED | FAILED */
        String status,
        int progress,
        Instant requestedAt,
        Instant completedAt,
        String errorMessage
) {
    public static ReportJobStatusResponse from(ReportJob job) {
        return new ReportJobStatusResponse(
                job.getId(),
                job.getReportId(),
                job.getStatus(),
                job.getProgress(),
                job.getRequestedAt(),
                job.getCompletedAt(),
                job.getErrorMessage()
        );
    }
}

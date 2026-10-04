package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event published when an async report generation job completes.
 * Topic: report.ready (KafkaTopics.REPORT_READY)
 *
 * Per design.md §5 and tasks.md B7.4.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportReadyEvent {
    private String reportId;
    private String jobId;
    private String reportType;
    /** COMPLETED | FAILED */
    private String status;
    private Instant timestamp;
}

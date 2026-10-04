package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for the {@code vitals.anomaly} Kafka topic.
 *
 * Published by: AnomalyDetectionService when a clinical rule fires.
 * Consumed by: AnomalyKafkaConsumer (future phase / WS broadcaster)
 *
 * Per design.md §5:
 * { patientId, rule, score, currentValue, previousValue, timestamp }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsAnomalyEvent {
    private String patientId;
    private String alertId;
    /** Rule code: HR_SPIKE_P95 | SPO2_LOW | BP_ELEVATED | DEVICE_OFFLINE */
    private String rule;
    /** Confidence/anomaly score 0.0–1.0 */
    private double score;
    private String currentValue;
    private String previousValue;
    private Instant timestamp;
}

package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for the {@code vitals.raw} Kafka topic.
 *
 * Published by: WearableSimulator (dev) or external wearable gateway.
 * Consumed by: VitalsKafkaConsumer
 *
 * Per design.md §5 event schema:
 * { patientId, deviceId, timestamp, type, value, unit }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsRawEvent {
    private String patientId;
    private String deviceId;
    private Instant timestamp;
    /** Vital type: heartRate | bloodPressureSystolic | bloodPressureDiastolic | spo2 | temperature | respiratoryRate */
    private String type;
    private double value;
    private String unit;
}

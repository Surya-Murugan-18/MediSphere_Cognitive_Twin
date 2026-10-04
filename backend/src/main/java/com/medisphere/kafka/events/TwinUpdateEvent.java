package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for the {@code twin.update} Kafka topic.
 *
 * Published by: TwinService on every state change.
 * Consumed by: TwinWebSocketBroadcaster (Phase 7)
 *
 * Per design.md §5:
 * { twinId, patientId, stateVersion, completeness, timestamp }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinUpdateEvent {
    private String twinId;
    private String patientId;
    private long stateVersion;
    private int completeness;
    private Instant timestamp;
}

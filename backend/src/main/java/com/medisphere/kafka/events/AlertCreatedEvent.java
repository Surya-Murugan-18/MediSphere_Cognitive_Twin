package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for the {@code alert.created} Kafka topic.
 *
 * Published by: AlertService.createAlert()
 * Consumed by: AlertWebSocketBroadcaster
 *
 * Contains the full alert summary required for real-time UI push.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertCreatedEvent {
    private String alertId;
    private String severity;
    private String patientId;
    private String patientName;
    private String event;
    private String type;
    private String status;
    private String assignedProvider;
    private double confidence;
    private Instant detectedAt;
}

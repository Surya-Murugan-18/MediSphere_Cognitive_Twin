package com.medisphere.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload for the careplan.approved Kafka topic.
 *
 * Published when a provider approves a care plan.
 * Consumed by TwinService (twin update trigger) and future integrations.
 *
 * Per design.md §5 Kafka Topics table.
 * KafkaTopics.CAREPLAN_APPROVED = "careplan.approved"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlanApprovedEvent {

    private String planId;
    private String patientId;
    private String providerId;
    private Instant timestamp;
}

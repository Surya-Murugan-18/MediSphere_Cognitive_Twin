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
 * Platform-level system event document.
 * Collection: system_events
 *
 * Written by services at key milestones:
 *   - FHIR bundle ingested
 *   - Anomaly detected
 *   - Federated round completed
 *   - Wearable devices offline threshold triggered
 */
@Document(collection = "system_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemEvent {

    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    /** Human-readable event description */
    private String text;

    /** neutral | healthy | warning | critical */
    @Builder.Default
    private String tone = "neutral";

    /** e.g. "FHIR", "Kafka", "Anomaly", "Federated", "Wearable" */
    private String category;
}

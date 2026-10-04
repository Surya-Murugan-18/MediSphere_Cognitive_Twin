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
 * Wearable device registration document.
 * Collection: wearable_devices
 */
@Document(collection = "wearable_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WearableDevice {

    @Id
    private String id;                  // DEV-001, DEV-002, …

    @Indexed
    private String patientId;

    /** Display name e.g. "Smart Watch · SW-1044" */
    private String displayName;

    private String deviceType;          // Smartwatch | Patch | Glucometer

    private String manufacturer;

    private String kafkaTopic;

    private String deviceKey;           // e.g. "SW-1044"

    /** Online | Offline */
    @Builder.Default
    private String status = "Offline";

    private Instant lastSeen;

    private Instant registeredAt;
}

package com.medisphere.dto.response;

import com.medisphere.domain.WearableDevice;

import java.time.Instant;

/**
 * Wearable device response — used by GET /api/devices/by-patient/{patientId}
 */
public record WearableDeviceResponse(
        String id,
        String patientId,
        String displayName,
        String deviceType,
        String manufacturer,
        String kafkaTopic,
        String deviceKey,
        String status,
        Instant lastSeen,
        Instant registeredAt
) {
    public static WearableDeviceResponse from(WearableDevice d) {
        return new WearableDeviceResponse(
                d.getId(),
                d.getPatientId(),
                d.getDisplayName(),
                d.getDeviceType(),
                d.getManufacturer(),
                d.getKafkaTopic(),
                d.getDeviceKey(),
                d.getStatus(),
                d.getLastSeen(),
                d.getRegisteredAt()
        );
    }
}

package com.medisphere.dto.response;

import com.medisphere.domain.VitalsSnapshot;

import java.time.Instant;

/**
 * Current vital sign snapshot response — used by GET /api/patients/{id}/vitals/current
 */
public record VitalsSnapshotResponse(
        String patientId,
        int heartRate,
        String bloodPressure,
        double spo2,
        double temperature,
        int respiratoryRate,
        String source,
        String deviceId,
        Instant updatedAt
) {
    public static VitalsSnapshotResponse from(VitalsSnapshot s) {
        return new VitalsSnapshotResponse(
                s.getPatientId(),
                s.getHeartRate(),
                s.getBloodPressure(),
                s.getSpo2(),
                s.getTemperature(),
                s.getRespiratoryRate(),
                s.getSource(),
                s.getDeviceId(),
                s.getUpdatedAt()
        );
    }
}

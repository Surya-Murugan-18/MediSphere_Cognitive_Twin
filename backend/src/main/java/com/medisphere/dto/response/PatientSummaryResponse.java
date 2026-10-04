package com.medisphere.dto.response;

import com.medisphere.domain.HealthTwin;
import com.medisphere.domain.Patient;

import java.time.Instant;
import java.util.List;

/**
 * Compact patient representation used in the paginated list endpoint.
 * Contains only the fields required for the Patients table.
 */
public record PatientSummaryResponse(
        String id,
        String name,
        String dob,
        String gender,
        List<String> conditions,
        String riskLevel,
        String status,
        String twinId,
        String twinStatus,
        int twinCompleteness,
        boolean fhirConnected,
        boolean consentComplete,
        String wearableStatus,
        String providerName,
        Instant updatedAt
) {
    public static PatientSummaryResponse from(Patient patient, HealthTwin twin) {
        String twinStatus = "Not Created";
        int twinCompleteness = 0;

        if (twin != null) {
            twinStatus = twin.getStatus();
            twinCompleteness = twin.getCompleteness();
        }

        return new PatientSummaryResponse(
                patient.getId(),
                patient.getName(),
                patient.getDob(),
                patient.getGender(),
                patient.getConditions() != null ? patient.getConditions() : List.of(),
                patient.getRiskLevel(),
                patient.getStatus(),
                patient.getTwinId(),
                twinStatus,
                twinCompleteness,
                patient.isFhirConnected(),
                patient.isConsentComplete(),
                patient.getWearableStatus(),
                patient.getProviderName(),
                patient.getUpdatedAt()
        );
    }
}

package com.medisphere.dto.response;

import com.medisphere.domain.HealthTwin;
import com.medisphere.domain.Patient;

import java.time.Instant;
import java.util.List;

/**
 * Full patient response — used in GET /api/patients/{id} and POST /api/patients.
 * Includes all fields needed by Patient360 and HealthTwin pages.
 */
public record PatientResponse(
        String id,
        String fhirId,
        String name,
        int age,
        String dob,
        String gender,
        String phone,
        String email,
        List<String> conditions,
        String riskLevel,
        String status,
        String twinId,
        String twinStatus,
        int twinCompleteness,
        String consentId,
        boolean fhirConnected,
        boolean consentComplete,
        String wearableStatus,
        String providerName,
        String providerId,
        String ehrSystem,
        String healthStatus,
        int adherence,
        Instant createdAt,
        Instant updatedAt,
        // Phase 3+ fields — null until those phases are wired
        VitalSnapshot vitals,
        List<MedicationEntry> medications
) {
    public static PatientResponse from(Patient patient, HealthTwin twin) {
        String twinStatus = "Not Created";
        int twinCompleteness = 0;
        if (twin != null) {
            twinStatus = twin.getStatus();
            twinCompleteness = twin.getCompleteness();
        }

        return new PatientResponse(
                patient.getId(),
                patient.getFhirId(),
                patient.getName(),
                computeAge(patient.getDob()),
                patient.getDob(),
                patient.getGender(),
                patient.getContact() != null ? patient.getContact().getPhone() : null,
                patient.getContact() != null ? patient.getContact().getEmail() : null,
                patient.getConditions() != null ? patient.getConditions() : List.of(),
                patient.getRiskLevel(),
                patient.getStatus(),
                patient.getTwinId(),
                twinStatus,
                twinCompleteness,
                patient.getConsentId(),
                patient.isFhirConnected(),
                patient.isConsentComplete(),
                patient.getWearableStatus(),
                patient.getProviderName(),
                patient.getProviderId(),
                patient.getEhrSystem(),
                patient.getHealthStatus(),
                patient.getAdherence(),
                patient.getCreatedAt(),
                patient.getUpdatedAt(),
                null,   // vitals — Phase 3
                null    // medications — Phase 3
        );
    }

    // ── Age helper ─────────────────────────────────────────────────────────

    private static int computeAge(String dob) {
        if (dob == null || dob.isBlank()) return 0;
        try {
            java.time.LocalDate birthDate = java.time.LocalDate.parse(dob);
            return java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Placeholder embedded types (populated in Phase 3) ─────────────────

    public record VitalSnapshot(
            int heartRate,
            String bloodPressure,
            double spo2,
            double temperature,
            int respiratoryRate
    ) {}

    public record MedicationEntry(
            String name,
            String dose,
            String frequency,
            String startedOn
    ) {}
}

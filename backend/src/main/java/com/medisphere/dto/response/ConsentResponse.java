package com.medisphere.dto.response;

import com.medisphere.domain.Consent;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for GET/PUT /api/patients/{id}/consent
 */
public record ConsentResponse(
        String id,
        String patientId,
        boolean ehr,
        boolean wearable,
        boolean ai,
        Instant updatedAt,
        String updatedBy
) {
    public static ConsentResponse from(Consent consent) {
        return new ConsentResponse(
                consent.getId(),
                consent.getPatientId(),
                consent.isEhr(),
                consent.isWearable(),
                consent.isAi(),
                consent.getUpdatedAt(),
                consent.getUpdatedBy()
        );
    }
}

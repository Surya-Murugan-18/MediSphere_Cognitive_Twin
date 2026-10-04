package com.medisphere.dto.response;

import com.medisphere.domain.Consent;

import java.time.Instant;

/**
 * Response DTO for GET /api/patients/{id}/consent/history entries
 */
public record ConsentHistoryEntryResponse(
        String id,
        Instant date,
        String type,
        String status,
        String updatedBy
) {
    public static ConsentHistoryEntryResponse from(Consent.ConsentHistoryEntry entry) {
        return new ConsentHistoryEntryResponse(
                entry.getId(),
                entry.getDate(),
                entry.getType(),
                entry.getStatus(),
                entry.getUpdatedBy()
        );
    }
}

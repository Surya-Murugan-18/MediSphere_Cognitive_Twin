package com.medisphere.dto.response;

import com.medisphere.domain.FhirConfiguration;

import java.time.Instant;

/**
 * Response DTO for GET/PUT /api/settings/fhir (ADMIN only)
 *
 * SECURITY: smartClientId and smartClientSecret are intentionally OMITTED.
 * They remain environment-variable-only and are never exposed via API.
 */
public record FhirConfigResponse(
        String mode,
        String baseUrl,
        String version,
        String authType,
        int syncIntervalMinutes,
        String updatedBy,
        Instant updatedAt
) {
    public static FhirConfigResponse from(FhirConfiguration config) {
        return new FhirConfigResponse(
                config.getMode(),
                config.getBaseUrl(),
                config.getVersion(),
                config.getAuthType(),
                config.getSyncIntervalMinutes(),
                config.getUpdatedBy(),
                config.getUpdatedAt()
        );
    }
}

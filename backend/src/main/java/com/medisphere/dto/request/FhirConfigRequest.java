package com.medisphere.dto.request;

/**
 * Request body for PUT /api/settings/fhir (ADMIN only)
 *
 * SECURITY: credentials (smartClientId, smartClientSecret) are NOT accepted here.
 * They remain environment-variable-only and are never stored in MongoDB.
 */
public record FhirConfigRequest(
        /** "mock" | "live" */
        String mode,
        String baseUrl,
        /** "R4" | "R4B" */
        String version,
        /** "SMART on FHIR" | "OAuth 2.0 client credentials" */
        String authType,
        int syncIntervalMinutes
) {}

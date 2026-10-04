package com.medisphere.fhir;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FHIR integration configuration properties.
 *
 * Active mode is controlled by FHIR_MODE environment variable (default: mock).
 * Credentials must be supplied via environment variables — never hardcoded.
 */
@ConfigurationProperties(prefix = "medisphere.fhir")
public record FHIRProperties(
        /** "mock" | "live" */
        String mode,
        /** Base URL for the live FHIR server (used only when mode=live) */
        String baseUrl,
        /** SMART on FHIR OAuth2 client ID (used only when mode=live) */
        String smartClientId,
        /** SMART on FHIR OAuth2 client secret (used only when mode=live) */
        String smartClientSecret
) {
    public boolean isMock() {
        return mode == null || "mock".equalsIgnoreCase(mode);
    }
}

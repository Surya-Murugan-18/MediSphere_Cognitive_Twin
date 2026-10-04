package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persisted FHIR configuration document.
 * Collection: fhir_config
 *
 * Singleton document — always id = "default".
 * Seeded on first startup from FHIRProperties env values.
 *
 * SECURITY: smartClientSecret is NEVER stored in this document.
 * Credentials remain environment-variable-only.
 * This document only stores non-sensitive configuration.
 */
@Document(collection = "fhir_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirConfiguration {

    /** Singleton ID — always "default" */
    @Id
    @Builder.Default
    private String id = "default";

    /** "mock" | "live" */
    @Builder.Default
    private String mode = "mock";

    /** FHIR server base URL (used only when mode=live) */
    @Builder.Default
    private String baseUrl = "";

    /** FHIR R4 version: "R4" | "R4B" */
    @Builder.Default
    private String version = "R4";

    /** "SMART on FHIR" | "OAuth 2.0 client credentials" */
    @Builder.Default
    private String authType = "SMART on FHIR";

    /** Sync interval in minutes (e.g. 5) */
    @Builder.Default
    private int syncIntervalMinutes = 5;

    private String updatedBy;

    @LastModifiedDate
    private Instant updatedAt;
}

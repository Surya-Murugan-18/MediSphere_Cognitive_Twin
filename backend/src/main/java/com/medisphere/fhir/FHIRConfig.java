package com.medisphere.fhir;

import com.medisphere.fhir.live.LiveFHIRClient;
import com.medisphere.fhir.mock.MockFHIRClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active FHIRClient bean based on the medisphere.fhir.mode property.
 *
 * FHIR_MODE=mock  (default) → MockFHIRClient (no external calls, development safe)
 * FHIR_MODE=live            → LiveFHIRClient (requires real credentials)
 *
 * Switching from mock to live requires ONLY a configuration change.
 * No frontend changes and no changes outside the fhir/ package are needed.
 */
@Configuration
public class FHIRConfig {

    private static final Logger log = LoggerFactory.getLogger(FHIRConfig.class);

    /**
     * Mock FHIR client — active by default when mode is "mock" or not set.
     */
    @Bean
    @ConditionalOnProperty(
            name  = "medisphere.fhir.mode",
            havingValue = "mock",
            matchIfMissing = true   // default to mock when property is absent
    )
    public FHIRClient mockFHIRClient() {
        log.info("FHIR integration: MockFHIRClient active (FHIR_MODE=mock). " +
                 "No external FHIR server required.");
        return new MockFHIRClient();
    }

    /**
     * Live FHIR client — active only when mode is explicitly set to "live".
     */
    @Bean
    @ConditionalOnProperty(
            name  = "medisphere.fhir.mode",
            havingValue = "live"
    )
    public FHIRClient liveFHIRClient(FHIRProperties fhirProperties) {
        log.info("FHIR integration: LiveFHIRClient selected (FHIR_MODE=live). " +
                 "External FHIR server: {}", fhirProperties.baseUrl());
        return new LiveFHIRClient();
    }
}

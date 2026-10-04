package com.medisphere.federated;

import com.medisphere.federated.live.LiveFederatedOrchestrator;
import com.medisphere.federated.stub.StubFederatedOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active FederatedLearningOrchestrator bean based on configuration.
 *
 * FEDERATED_MODE=stub  (default) → StubFederatedOrchestrator (in-memory, deterministic)
 * FEDERATED_MODE=live            → LiveFederatedOrchestrator  (production)
 *
 * Switching requires only a configuration change — no code changes needed.
 * Mirrors the FHIRConfig pattern from Phase 3.
 */
@Configuration
public class FederatedConfig {

    private static final Logger log = LoggerFactory.getLogger(FederatedConfig.class);

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.federated.mode",
            havingValue = "stub",
            matchIfMissing = true
    )
    public FederatedLearningOrchestrator stubFederatedOrchestrator() {
        log.info("Federated Learning: StubFederatedOrchestrator active (FEDERATED_MODE=stub). " +
                 "Simulating 3 hospital nodes, rounds 43–47.");
        return new StubFederatedOrchestrator();
    }

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.federated.mode",
            havingValue = "live"
    )
    public FederatedLearningOrchestrator liveFederatedOrchestrator() {
        log.info("Federated Learning: LiveFederatedOrchestrator selected (FEDERATED_MODE=live).");
        return new LiveFederatedOrchestrator();
    }
}

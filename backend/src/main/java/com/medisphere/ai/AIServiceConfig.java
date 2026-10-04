package com.medisphere.ai;

import com.medisphere.ai.stub.StubAICarePlanService;
import com.medisphere.ai.stub.StubAIPredictionService;
import com.medisphere.ai.tff.TFFAICarePlanService;
import com.medisphere.ai.tff.TFFAIPredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active AI service beans based on configuration properties.
 *
 * AI_PREDICTION_MODE=stub  (default) → StubAIPredictionService (deterministic, no external calls)
 * AI_PREDICTION_MODE=tff             → TFFAIPredictionService  (production TFF endpoint)
 *
 * AI_CAREPLAN_MODE=stub    (default) → StubAICarePlanService
 * AI_CAREPLAN_MODE=tff               → TFFAICarePlanService
 *
 * Switching from stub to TFF requires ONLY a configuration change.
 * No frontend changes and no changes outside the ai/ package are needed.
 *
 * Mirrors the FHIRConfig pattern from Phase 3.
 */
@Configuration
public class AIServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(AIServiceConfig.class);

    // ── Prediction service bean selection ─────────────────────────────────

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.prediction.mode",
            havingValue = "stub",
            matchIfMissing = true   // default to stub when property is absent
    )
    public AIPredictionService stubAIPredictionService() {
        log.info("AI Prediction: StubAIPredictionService active (AI_PREDICTION_MODE=stub). " +
                 "Deterministic development mode — no external AI endpoint required.");
        return new StubAIPredictionService();
    }

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.prediction.mode",
            havingValue = "tff"
    )
    public AIPredictionService tffAIPredictionService() {
        log.info("AI Prediction: TFFAIPredictionService selected (AI_PREDICTION_MODE=tff). " +
                 "Requires TFF_ENDPOINT to be configured.");
        return new TFFAIPredictionService();
    }

    // ── Care plan service bean selection ──────────────────────────────────

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.careplan.mode",
            havingValue = "stub",
            matchIfMissing = true
    )
    public AICarePlanService stubAICarePlanService() {
        log.info("AI CarePlan: StubAICarePlanService active (AI_CAREPLAN_MODE=stub).");
        return new StubAICarePlanService();
    }

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.careplan.mode",
            havingValue = "tff"
    )
    public AICarePlanService tffAICarePlanService() {
        log.info("AI CarePlan: TFFAICarePlanService selected (AI_CAREPLAN_MODE=tff).");
        return new TFFAICarePlanService();
    }
}

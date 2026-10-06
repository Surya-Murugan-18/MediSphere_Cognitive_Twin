package com.medisphere.ai;

import com.medisphere.ai.ml.MLServiceClient;
import com.medisphere.ai.stub.StubAICarePlanService;
import com.medisphere.ai.stub.StubAIPredictionService;
import com.medisphere.ai.tff.TFFAICarePlanService;
import com.medisphere.ai.tff.TFFAIPredictionService;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.CarePlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Selects the active AI service beans based on configuration properties.
 *
 * AI_PREDICTION_MODE=stub (default) → StubAIPredictionService
 * AI_PREDICTION_MODE=ml             → TFFAIPredictionService backed by FastAPI ML service
 *
 * AI_CAREPLAN_MODE=stub    (default) → StubAICarePlanService
 * AI_CAREPLAN_MODE=tff               → TFFAICarePlanService
 *
 * The "tff" alias is also accepted for prediction mode for backwards compatibility.
 */
@Configuration
public class AIServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(AIServiceConfig.class);

    @Value("${medisphere.ai.ml.service-url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${medisphere.ai.ml.internal-token:dev-token}")
    private String mlInternalToken;

    // ── Shared RestTemplate for ML service calls ──────────────────────────

    /**
     * Dedicated RestTemplate for ML service calls.
     * 5-second connect timeout, 30-second read timeout.
     * Timeouts are conservative — SHAP computation can take a few seconds.
     */
    @Bean(name = "mlRestTemplate")
    public RestTemplate mlRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    // ── Prediction service bean selection ─────────────────────────────────

    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.prediction.mode",
            havingValue = "stub",
            matchIfMissing = true   // default to stub when property is absent
    )
    public AIPredictionService stubAIPredictionService() {
        log.info("AI Prediction: StubAIPredictionService active (AI_PREDICTION_MODE=stub). "
                + "Deterministic development mode — no external AI endpoint required.");
        return new StubAIPredictionService();
    }

    /**
     * Real ML prediction service — activated by AI_PREDICTION_MODE=ml.
     * Also accepts "tff" for backwards compatibility with earlier configuration.
     */
    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.prediction.mode",
            havingValue = "ml"
    )
    public AIPredictionService mlAIPredictionService(
            RestTemplate mlRestTemplate,
            CarePlanRepository carePlanRepository,
            AlertRepository alertRepository) {
        log.info("AI Prediction: TFFAIPredictionService (ML) active — connecting to ML service at {}",
                mlServiceUrl);
        MLServiceClient client = new MLServiceClient(mlServiceUrl, mlInternalToken, mlRestTemplate);
        return new TFFAIPredictionService(client, carePlanRepository, alertRepository);
    }

    /**
     * Backwards-compatible alias: AI_PREDICTION_MODE=tff also activates the real ML service.
     */
    @Bean
    @ConditionalOnProperty(
            name = "medisphere.ai.prediction.mode",
            havingValue = "tff"
    )
    public AIPredictionService tffAIPredictionService(
            RestTemplate mlRestTemplate,
            CarePlanRepository carePlanRepository,
            AlertRepository alertRepository) {
        log.info("AI Prediction: TFFAIPredictionService active (AI_PREDICTION_MODE=tff) — "
                + "connecting to ML service at {}", mlServiceUrl);
        MLServiceClient client = new MLServiceClient(mlServiceUrl, mlInternalToken, mlRestTemplate);
        return new TFFAIPredictionService(client, carePlanRepository, alertRepository);
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

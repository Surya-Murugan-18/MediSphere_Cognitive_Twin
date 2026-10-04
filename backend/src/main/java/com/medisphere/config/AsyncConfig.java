package com.medisphere.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async task executor configuration.
 *
 * Enables Spring @Async support for background tasks:
 *   - AI prediction runs triggered on patient creation (Phase 4)
 *   - Audit log writes (Phase 1 — AuditService already uses @Async)
 *
 * Uses a bounded thread pool to prevent resource exhaustion.
 * The "predictionTaskExecutor" bean is the executor for PredictionService.runPredictions.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated thread pool for AI prediction tasks.
     *
     * Core pool size 2 / max pool size 4 keeps prediction workload
     * isolated from the main request-serving threads.
     * Queue capacity 25 ensures patient creation never blocks even
     * under burst load (25 simultaneous new patient registrations).
     */
    @Bean(name = "predictionTaskExecutor")
    public Executor predictionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("prediction-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

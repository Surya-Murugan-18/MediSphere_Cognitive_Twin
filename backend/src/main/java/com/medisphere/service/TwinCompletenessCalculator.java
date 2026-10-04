package com.medisphere.service;

import com.medisphere.domain.HealthTwin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Computes the twin completeness score (0–100%) from data source freshness.
 *
 * Scoring rules (each source worth 25 points):
 *   - EHR:      connected + synced within 24 hours  → +25
 *   - Lab:      connected + synced within 7 days    → +25
 *   - Wearable: connected + synced within 1 hour    → +25
 *   - Kafka:    connected + synced within 5 minutes → +25
 *
 * A source that has never synced contributes 0 regardless of connected flag.
 */
@Component
public class TwinCompletenessCalculator {

    private static final Duration EHR_FRESHNESS      = Duration.ofHours(24);
    private static final Duration LAB_FRESHNESS       = Duration.ofDays(7);
    private static final Duration WEARABLE_FRESHNESS  = Duration.ofHours(1);
    private static final Duration KAFKA_FRESHNESS     = Duration.ofMinutes(5);
    private static final int      POINTS_PER_SOURCE   = 25;

    public int compute(HealthTwin.DataSources sources) {
        if (sources == null) return 0;

        Instant now = Instant.now();
        int score = 0;

        score += scoreSource(sources.getEhr(),      EHR_FRESHNESS,     now);
        score += scoreSource(sources.getLab(),       LAB_FRESHNESS,     now);
        score += scoreSource(sources.getWearable(),  WEARABLE_FRESHNESS, now);
        score += scoreSource(sources.getKafka(),     KAFKA_FRESHNESS,   now);

        return score;
    }

    private int scoreSource(HealthTwin.DataSourceEntry source, Duration freshness, Instant now) {
        if (source == null || !source.isConnected() || source.getLastSync() == null) return 0;
        Duration age = Duration.between(source.getLastSync(), now);
        return age.compareTo(freshness) <= 0 ? POINTS_PER_SOURCE : 0;
    }
}

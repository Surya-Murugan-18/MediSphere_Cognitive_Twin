package com.medisphere.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for anomaly detection thresholds.
 *
 * All clinical rule thresholds are read from application.yml under
 * {@code medisphere.anomaly.*} so they can be overridden via environment
 * variables without code changes.
 */
@Component
@ConfigurationProperties(prefix = "medisphere.anomaly")
@Data
public class AnomalyProperties {

    /** Percentile rank for HR spike detection (default 95). */
    private int hrSpikePercentile = 95;

    /** Number of past readings to use when computing the HR baseline (default 50). */
    private int hrHistoryWindow = 50;

    /** Minimum number of HR readings required before the HR_SPIKE_P95 rule can fire. */
    private int hrMinSamples = 5;

    /** SpO₂ low threshold (< this value triggers SPO2_LOW). */
    private double spo2LowThreshold = 94.0;

    /** Systolic BP threshold (> this value triggers BP_ELEVATED). */
    private int bpSystolicThreshold = 140;

    /** Diastolic BP threshold (> this value triggers BP_ELEVATED). */
    private int bpDiastolicThreshold = 90;
}

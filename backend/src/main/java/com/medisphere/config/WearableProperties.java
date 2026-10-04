package com.medisphere.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for wearable device behaviour.
 *
 * Bound from application.yml under {@code medisphere.wearable.*}.
 * Used by AnomalyDetectionService for the DEVICE_OFFLINE rule.
 */
@Component
@ConfigurationProperties(prefix = "medisphere.wearable")
@Data
public class WearableProperties {

    /**
     * Minutes without a vital reading before a device is considered offline
     * and a DEVICE_OFFLINE alert is raised.
     *
     * Default: 30. Override via env var WEARABLE_OFFLINE_THRESHOLD.
     */
    private long offlineThresholdMinutes = 30L;
}

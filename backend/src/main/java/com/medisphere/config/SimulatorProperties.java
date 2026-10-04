package com.medisphere.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the development wearable simulator.
 *
 * Read from application.yml under {@code medisphere.simulator.*}.
 */
@Component
@ConfigurationProperties(prefix = "medisphere.simulator")
@Data
public class SimulatorProperties {

    /** Master on/off switch (default true in dev profile). */
    private boolean enabled = true;

    /** Seconds between synthetic vitals.raw publish cycles. */
    private int intervalSeconds = 5;
}

package com.medisphere.dto.response;

import java.time.Instant;

/**
 * Federated learning node response.
 * Represents one participating hospital in the federated network.
 */
public record FederatedNodeResponse(
        String id,
        String hospitalName,
        String status,
        int patients,
        Instant lastSync,
        int contribution      // percentage weight in federated aggregation
) {}

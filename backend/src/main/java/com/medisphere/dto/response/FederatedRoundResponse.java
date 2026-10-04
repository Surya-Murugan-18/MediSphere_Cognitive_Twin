package com.medisphere.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Federated learning round response.
 * Represents one completed or in-progress federated training round.
 */
public record FederatedRoundResponse(
        int round,
        String model,
        double accuracy,
        int durationMinutes,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<NodeContribution> nodeContributions
) {
    public record NodeContribution(
            String nodeId,
            int contribution,
            String status
    ) {}
}

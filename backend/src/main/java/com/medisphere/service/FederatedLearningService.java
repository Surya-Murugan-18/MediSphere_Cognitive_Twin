package com.medisphere.service;

import com.medisphere.dto.response.FederatedNodeResponse;
import com.medisphere.dto.response.FederatedRoundResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.federated.FederatedLearningOrchestrator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin delegation service for federated learning orchestration.
 *
 * Delegates all work to FederatedLearningOrchestrator (stub or live).
 * Does not implement any actual federated training.
 * No Kafka. No WebSocket (those belong to Phase 5/7).
 *
 * Per tasks.md B4.4.
 */
@Service
public class FederatedLearningService {

    private final FederatedLearningOrchestrator orchestrator;

    public FederatedLearningService(FederatedLearningOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public FederatedRoundResponse getCurrentRound() {
        return orchestrator.getCurrentRound();
    }

    public PageResponse<FederatedRoundResponse> getRounds(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return PageResponse.from(orchestrator.getRounds(pageable), r -> r);
    }

    public List<FederatedNodeResponse> getNodes() {
        return orchestrator.getNodes();
    }
}

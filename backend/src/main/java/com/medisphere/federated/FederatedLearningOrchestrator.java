package com.medisphere.federated;

import com.medisphere.dto.response.FederatedNodeResponse;
import com.medisphere.dto.response.FederatedRoundResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Federated Learning Orchestrator abstraction — the ONLY way to access federated
 * learning state in MediSphere.
 *
 * Patient data NEVER leaves the hospital boundary. Only model weight updates are
 * exchanged between nodes.
 *
 * Implementations:
 *   StubFederatedOrchestrator  — active when FEDERATED_MODE=stub (default in development)
 *   LiveFederatedOrchestrator  — active when FEDERATED_MODE=live (production)
 *
 * Per design.md (FR-FL section)
 */
public interface FederatedLearningOrchestrator {

    /**
     * Get the current (most recently completed or in-progress) federated round.
     */
    FederatedRoundResponse getCurrentRound();

    /**
     * Get paginated history of federated rounds, ordered newest first.
     */
    Page<FederatedRoundResponse> getRounds(Pageable pageable);

    /**
     * Get all registered federated hospital nodes and their current status.
     */
    List<FederatedNodeResponse> getNodes();
}

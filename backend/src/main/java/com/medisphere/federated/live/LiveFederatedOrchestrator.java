package com.medisphere.federated.live;

import com.medisphere.dto.response.FederatedNodeResponse;
import com.medisphere.dto.response.FederatedRoundResponse;
import com.medisphere.federated.FederatedLearningOrchestrator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Production placeholder for live federated learning orchestration.
 *
 * Activated when FEDERATED_MODE=live.
 * Throws UnsupportedOperationException until real federated infrastructure is implemented.
 *
 * Patient data NEVER leaves the hospital. Only model weight updates are exchanged.
 */
public class LiveFederatedOrchestrator implements FederatedLearningOrchestrator {

    private static final String MSG =
            "LiveFederatedOrchestrator is not yet implemented. " +
            "Configure FEDERATED_MODE=stub for development.";

    @Override
    public FederatedRoundResponse getCurrentRound() {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public Page<FederatedRoundResponse> getRounds(Pageable pageable) {
        throw new UnsupportedOperationException(MSG);
    }

    @Override
    public List<FederatedNodeResponse> getNodes() {
        throw new UnsupportedOperationException(MSG);
    }
}

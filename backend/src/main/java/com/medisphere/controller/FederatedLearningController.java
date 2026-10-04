package com.medisphere.controller;

import com.medisphere.dto.response.FederatedNodeResponse;
import com.medisphere.dto.response.FederatedRoundResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.service.FederatedLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for federated learning state.
 * Serves the FederatedLearning page.
 *
 * Phase 4: backed by StubFederatedOrchestrator (in-memory, deterministic).
 * No Kafka, no WebSocket, no actual federated training in Phase 4.
 */
@RestController
@RequestMapping("/api/federated")
@Tag(name = "Federated Learning", description = "Federated learning nodes and round history")
public class FederatedLearningController {

    private final FederatedLearningService federatedLearningService;

    public FederatedLearningController(FederatedLearningService federatedLearningService) {
        this.federatedLearningService = federatedLearningService;
    }

    // ── GET /api/federated/nodes ──────────────────────────────────────────

    @GetMapping("/nodes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get federated nodes",
               description = "Returns all participating hospital nodes and their current status")
    public ResponseEntity<List<FederatedNodeResponse>> getNodes() {
        return ResponseEntity.ok(federatedLearningService.getNodes());
    }

    // ── GET /api/federated/rounds/current ─────────────────────────────────

    @GetMapping("/rounds/current")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current federated round",
               description = "Returns the most recently completed or in-progress round")
    public ResponseEntity<FederatedRoundResponse> getCurrentRound() {
        return ResponseEntity.ok(federatedLearningService.getCurrentRound());
    }

    // ── GET /api/federated/rounds ─────────────────────────────────────────

    @GetMapping("/rounds")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get federated round history",
               description = "Paginated round history, newest first")
    public ResponseEntity<PageResponse<FederatedRoundResponse>> getRounds(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(federatedLearningService.getRounds(page, size));
    }
}

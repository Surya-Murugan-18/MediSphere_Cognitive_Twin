package com.medisphere.federated.stub;

import com.medisphere.dto.response.FederatedNodeResponse;
import com.medisphere.dto.response.FederatedRoundResponse;
import com.medisphere.federated.FederatedLearningOrchestrator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Development stub for federated learning orchestration.
 *
 * Activated when FEDERATED_MODE=stub (default).
 *
 * Simulates 3 connected hospitals, rounds 43–47, accuracy 89.1%→91.4%.
 * All data is deterministic. No random values. No actual federated training.
 * No Kafka. No WebSocket.
 *
 * Per tasks.md B4.2 and the static data values in src/data/predictions.ts.
 */
public class StubFederatedOrchestrator implements FederatedLearningOrchestrator {

    // ── Reference "now" for timestamps — relative so they age gracefully ──────
    private static final Instant BASE = Instant.parse("2026-09-21T11:00:00Z");

    // ── Node definitions — match src/data/predictions.ts exactly ─────────────
    private static final List<FederatedNodeResponse> NODES = List.of(
        new FederatedNodeResponse(
            "HOSP-A",
            "Hospital A — Northside General",
            "Connected",
            512,
            BASE.plus(5 * 60 + 17, ChronoUnit.SECONDS),  // 5 h 17 m after base
            41
        ),
        new FederatedNodeResponse(
            "HOSP-B",
            "Hospital B — Lakeview Medical",
            "Connected",
            438,
            BASE.plus(5 * 60 + 16, ChronoUnit.SECONDS),
            35
        ),
        new FederatedNodeResponse(
            "HOSP-C",
            "Hospital C — Riverbend Clinic",
            "Connected",
            297,
            BASE.plus(5 * 60 + 14, ChronoUnit.SECONDS),
            24
        )
    );

    // ── Round history — rounds 43–47, accuracy 89.1→91.4 ─────────────────────
    private static final List<FederatedRoundResponse> ROUNDS = List.of(
        round(43, "CVD-Risk-v3.0", 89.1, 14, BASE),
        round(44, "CVD-Risk-v3.1", 89.8, 13, BASE.plus(3 * 60, ChronoUnit.SECONDS)),
        round(45, "CVD-Risk-v3.1", 90.3, 15, BASE.plus(6 * 60, ChronoUnit.SECONDS)),
        round(46, "CVD-Risk-v3.2", 90.9, 12, BASE.plus(9 * 60, ChronoUnit.SECONDS)),
        round(47, "CVD-Risk-v3.2", 91.4, 13, BASE.plus(12 * 60, ChronoUnit.SECONDS))
    );

    // ── Interface implementation ──────────────────────────────────────────────

    @Override
    public FederatedRoundResponse getCurrentRound() {
        // Round 47 is the latest completed round
        return ROUNDS.get(ROUNDS.size() - 1);
    }

    @Override
    public Page<FederatedRoundResponse> getRounds(Pageable pageable) {
        // Return newest-first
        List<FederatedRoundResponse> reversed = new java.util.ArrayList<>(ROUNDS);
        java.util.Collections.reverse(reversed);

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), reversed.size());

        if (start >= reversed.size()) {
            return new PageImpl<>(List.of(), pageable, reversed.size());
        }
        return new PageImpl<>(reversed.subList(start, end), pageable, reversed.size());
    }

    @Override
    public List<FederatedNodeResponse> getNodes() {
        return NODES;
    }

    // ── Round builder helper ──────────────────────────────────────────────────

    private static FederatedRoundResponse round(int round, String model, double accuracy,
                                                  int durationMinutes, Instant startedAt) {
        Instant completedAt = startedAt.plus(durationMinutes, ChronoUnit.MINUTES);
        List<FederatedRoundResponse.NodeContribution> contributions = List.of(
            new FederatedRoundResponse.NodeContribution("HOSP-A", 41, "Completed"),
            new FederatedRoundResponse.NodeContribution("HOSP-B", 35, "Completed"),
            new FederatedRoundResponse.NodeContribution("HOSP-C", 24, "Completed")
        );
        return new FederatedRoundResponse(
            round, model, accuracy, durationMinutes, "Completed",
            startedAt, completedAt, contributions
        );
    }
}

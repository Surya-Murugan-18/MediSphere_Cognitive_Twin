package com.medisphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/care-plans/stats
 * Per design.md §10.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlanStatsResponse {

    /** Total number of ACTIVE care plans. */
    private long activeCount;

    /** Rolling average adherence across all active plans (0–100). */
    private double avgAdherence;

    /** Percentage reduction in hospitalizations versus pre-intervention baseline. */
    private double hospitalizationReduction;

    /** Number of plans currently awaiting approval (DRAFT). */
    private long draftCount;

    /** Number of plans rejected. */
    private long rejectedCount;
}

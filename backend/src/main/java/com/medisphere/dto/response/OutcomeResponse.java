package com.medisphere.dto.response;

import com.medisphere.domain.Outcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for outcomes per care plan.
 * Used by GET /api/care-plans/{id}/outcomes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeResponse {

    private String id;
    private String planId;
    private String metric;
    private double baseline;
    private double current;
    private double goal;
    private String unit;
    private String trend;
    private Instant updatedAt;

    public static OutcomeResponse from(Outcome outcome) {
        return OutcomeResponse.builder()
                .id(outcome.getId())
                .planId(outcome.getPlanId())
                .metric(outcome.getMetric())
                .baseline(outcome.getBaseline())
                .current(outcome.getCurrent())
                .goal(outcome.getGoal())
                .unit(outcome.getUnit())
                .trend(outcome.getTrend())
                .updatedAt(outcome.getUpdatedAt())
                .build();
    }
}

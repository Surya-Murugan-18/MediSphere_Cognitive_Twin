package com.medisphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for GET /api/care-plans/{id}/adherence
 * Per tasks.md B6.3 AdherenceService.getAdherence().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdherenceResponse {

    private String planId;

    /** Overall adherence percentage (equal-weighted average of the three dimensions). */
    private int overall;

    /** Per-dimension breakdown for display in ProgressBar components. */
    private List<BreakdownItem> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakdownItem {
        /** Label shown in the UI, e.g. "Medication", "Monitoring", "Follow-up" */
        private String label;
        /** 0–100 percentage. */
        private int value;
    }
}

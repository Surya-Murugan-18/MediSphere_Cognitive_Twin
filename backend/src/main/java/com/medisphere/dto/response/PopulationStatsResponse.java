package com.medisphere.dto.response;

import java.util.List;

/**
 * Top-level population health statistics response.
 * Used by GET /api/population/stats
 */
public record PopulationStatsResponse(
        long totalPatients,
        long highRiskCount,
        long mediumRiskCount,
        long lowRiskCount,
        double avgAdherence,
        long activeAlerts,
        long activePlans,
        /** Hospitalisation reduction rate as a percentage string, e.g. "12.4%" */
        String hospitalizationReduction
) {}

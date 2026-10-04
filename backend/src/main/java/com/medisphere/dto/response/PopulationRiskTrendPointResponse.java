package com.medisphere.dto.response;

/**
 * Monthly population risk snapshot derived from persisted prediction history.
 */
public record PopulationRiskTrendPointResponse(
        String t,
        long high,
        long medium
) {}

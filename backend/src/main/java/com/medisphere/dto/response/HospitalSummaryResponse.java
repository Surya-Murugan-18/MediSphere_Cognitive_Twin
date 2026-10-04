package com.medisphere.dto.response;

/**
 * Per-hospital summary row for the population health hospital table.
 * Used by GET /api/population/hospitals
 */
public record HospitalSummaryResponse(
        String name,
        long patients,
        long highRisk,
        double adherence,
        long alerts
) {}

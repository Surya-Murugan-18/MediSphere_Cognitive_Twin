package com.medisphere.dto.response;

/**
 * Single data point in the 7-day alert volume trend.
 * Used by GET /api/population/alert-trend
 */
public record AlertTrendPointResponse(
        /** Day label, e.g. "Mon", "Tue" */
        String t,
        long alerts,
        long critical
) {}

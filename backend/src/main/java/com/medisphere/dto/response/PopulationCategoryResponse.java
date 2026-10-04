package com.medisphere.dto.response;

/**
 * Cohort category summary (cardiovascular, diabetes, readmission).
 * Used by GET /api/population/categories
 */
public record PopulationCategoryResponse(
        String name,
        long cohort,
        long highRisk,
        /** Trend label, e.g. "-2.1%" */
        String trend,
        /** healthy | warning | neutral */
        String tone
) {}

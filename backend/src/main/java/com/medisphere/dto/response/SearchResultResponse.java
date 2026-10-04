package com.medisphere.dto.response;

import java.util.List;

/**
 * Search result response — grouped by entity category.
 * Used by GET /api/search
 *
 * Per FR-SCH-01 through FR-SCH-04:
 *   - Results grouped by type
 *   - Max 4 per group
 *   - ANALYST receives no patient-scoped PHI (patient/alert/careplan/prediction excluded)
 */
public record SearchResultResponse(
        String category,
        List<SearchResultItem> results
) {
    public record SearchResultItem(
            String id,
            String label,
            String detail,
            String to
    ) {}
}

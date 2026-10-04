package com.medisphere.dto.response;

import java.util.List;

/**
 * Filter options for the patient list page dropdowns.
 * Returned by GET /api/patients/filter-options
 */
public record FilterOptionsResponse(
        List<String> conditions,
        List<String> providers,
        List<String> statuses,
        List<String> riskLevels
) {
    public static FilterOptionsResponse of(List<String> conditions, List<String> providers) {
        return new FilterOptionsResponse(
                conditions,
                providers,
                List.of("Active", "Inactive", "Pending Consent"),
                List.of("High", "Medium", "Low")
        );
    }
}

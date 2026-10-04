package com.medisphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single data point in a weekly adherence trend chart.
 * Used by GET /api/care-plans/{id}/adherence/trend
 *
 * The 't' field matches the xKey used in TrendChart on the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdherenceTrendPoint {

    /** Week label shown on the X axis, e.g. "Week 1" */
    private String t;

    /** Overall adherence percentage for that week. */
    private int value;
}

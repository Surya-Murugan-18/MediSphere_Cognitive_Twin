package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded care plan recommendation.
 * Persisted inside the care_plans document (not a separate collection).
 *
 * NOTE: Defined in Phase 4 as part of the AI service abstraction.
 * The care plan persistence + REST endpoints belong to Phase 6.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlanRecommendation {

    private String id;
    private String title;
    private String goal;
    private String intervention;
    private String monitoring;
    private String outcome;
    private String evidence;
}

package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded care plan safety check result.
 * Persisted inside the care_plans document (not a separate collection).
 *
 * NOTE: Defined in Phase 4 as part of the AI service abstraction.
 * The care plan persistence + REST endpoints belong to Phase 6.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyCheck {

    private String id;

    /** Human-readable check label e.g. "Drug interaction validation" */
    private String label;

    /** Detail text describing the check result */
    private String detail;

    /** Visual tone: healthy | warning | critical */
    private String tone;
}

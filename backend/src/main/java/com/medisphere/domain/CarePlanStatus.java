package com.medisphere.domain;

/**
 * Care plan status lifecycle.
 *
 * Valid transitions:
 *   DRAFT  → ACTIVE    (provider approval)
 *   DRAFT  → REJECTED  (provider rejection)
 *
 * No other transitions are permitted.
 * Per design.md §4.9 and tasks.md B6.1.
 */
public enum CarePlanStatus {

    /** AI-generated plan awaiting provider review. */
    DRAFT,

    /** Provider-approved; interventions are live. */
    ACTIVE,

    /** Provider-rejected; no interventions started. */
    REJECTED
}

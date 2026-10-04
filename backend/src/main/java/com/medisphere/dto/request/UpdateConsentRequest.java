package com.medisphere.dto.request;

/**
 * Request body for PUT /api/patients/{id}/consent
 *
 * All fields are nullable — only non-null fields are updated.
 * This allows partial updates (e.g. toggling only EHR consent).
 */
public record UpdateConsentRequest(
        Boolean ehr,
        Boolean wearable,
        Boolean ai
) {}

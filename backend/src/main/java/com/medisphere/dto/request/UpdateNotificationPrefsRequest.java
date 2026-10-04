package com.medisphere.dto.request;

/**
 * Request body for PUT /api/providers/me/notifications
 *
 * All fields are nullable — only non-null values are updated.
 */
public record UpdateNotificationPrefsRequest(
        Boolean critical,
        Boolean risk,
        Boolean approvals,
        Boolean system
) {}

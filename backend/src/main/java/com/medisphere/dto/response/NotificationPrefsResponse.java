package com.medisphere.dto.response;

import com.medisphere.domain.NotificationPrefs;

/**
 * Response DTO for GET/PUT /api/providers/me/notifications
 */
public record NotificationPrefsResponse(
        boolean critical,
        boolean risk,
        boolean approvals,
        boolean system
) {
    public static NotificationPrefsResponse from(NotificationPrefs prefs) {
        return new NotificationPrefsResponse(
                prefs.isCritical(),
                prefs.isRisk(),
                prefs.isApprovals(),
                prefs.isSystem()
        );
    }
}

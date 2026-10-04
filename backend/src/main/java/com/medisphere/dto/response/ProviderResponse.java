package com.medisphere.dto.response;

import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;

import java.time.Instant;

/**
 * Public-facing provider DTO — never includes passwordHash.
 */
public record ProviderResponse(
        String id,
        String name,
        String email,
        ProviderRole role,
        String specialty,
        String facility,
        String npi,
        NotificationPrefs notificationPrefs,
        Instant lastSignIn,
        boolean active
) {
    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
                provider.getId(),
                provider.getName(),
                provider.getEmail(),
                provider.getRole(),
                provider.getSpecialty(),
                provider.getFacility(),
                provider.getNpi(),
                provider.getNotificationPrefs(),
                provider.getLastSignIn(),
                provider.isActive()
        );
    }
}

package com.medisphere.dto.response;

/**
 * Response body for POST /api/auth/login and POST /api/auth/refresh.
 * The refresh token is NOT included here — it is set as an httpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        ProviderResponse provider
) {
}

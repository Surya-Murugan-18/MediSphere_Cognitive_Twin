package com.medisphere.controller;

import com.medisphere.dto.request.LoginRequest;
import com.medisphere.dto.response.AuthResponse;
import com.medisphere.dto.response.ProviderResponse;
import com.medisphere.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Provider authentication and session management")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final int COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // 30 days

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     * Authenticates a provider and issues access + refresh tokens.
     * The refresh token is set as an httpOnly, SameSite=Strict cookie.
     */
    @PostMapping("/login")
    @SecurityRequirements  // No auth required on this endpoint
    @Operation(summary = "Provider login", description = "Authenticate with email and password")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ipAddress = extractIpAddress(httpRequest);
        AuthService.LoginResult result = authService.login(request, ipAddress);

        setRefreshTokenCookie(httpResponse, result.rawRefreshToken());

        return ResponseEntity.ok(result.authResponse());
    }

    /**
     * POST /api/auth/refresh
     * Issues a new access token using the refresh token cookie.
     * Returns 401 if the cookie is missing, expired, or revoked.
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Refresh access token", description = "Exchange refresh token cookie for new access token")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String rawRefreshToken = extractRefreshCookie(httpRequest);
        if (rawRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            AuthResponse response = authService.refresh(rawRefreshToken);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            // Clear the stale cookie
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * POST /api/auth/logout
     * Revokes all refresh tokens for the authenticated provider and clears the cookie.
     */
    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Logout", description = "Revoke refresh tokens and clear session")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            Authentication authentication) {

        if (authentication != null && authentication.isAuthenticated()) {
            String providerId = (String) authentication.getPrincipal();
            authService.logout(providerId, extractIpAddress(httpRequest));
        }

        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me
     * Returns the authenticated provider's profile.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current provider", description = "Returns the authenticated provider profile")
    public ResponseEntity<ProviderResponse> getMe(Authentication authentication) {
        String providerId = (String) authentication.getPrincipal();
        ProviderResponse response = authService.getMe(providerId);
        return ResponseEntity.ok(response);
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);       // Set to true in production (HTTPS)
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        // SameSite=Strict is not directly supported by jakarta.servlet.http.Cookie
        // — set via response header for proper browser enforcement
        response.addCookie(cookie);
        response.addHeader("Set-Cookie",
                REFRESH_COOKIE_NAME + "=" + rawToken
                        + "; Path=/api/auth/refresh"
                        + "; HttpOnly"
                        + "; SameSite=Strict"
                        + "; Max-Age=" + COOKIE_MAX_AGE_SECONDS);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

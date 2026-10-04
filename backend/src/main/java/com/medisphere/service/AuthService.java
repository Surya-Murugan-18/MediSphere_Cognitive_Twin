package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.Provider;
import com.medisphere.domain.RefreshToken;
import com.medisphere.dto.request.LoginRequest;
import com.medisphere.dto.response.AuthResponse;
import com.medisphere.dto.response.ProviderResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.ProviderRepository;
import com.medisphere.repository.RefreshTokenRepository;
import com.medisphere.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MODULE = "Authentication";

    private final ProviderRepository providerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final com.medisphere.security.JwtProperties jwtProperties;

    public AuthService(ProviderRepository providerRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       com.medisphere.security.JwtProperties jwtProperties) {
        this.providerRepository = providerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Authenticate a provider by email + password.
     * Returns an AuthResponse containing the access token and provider details.
     * The caller (AuthController) is responsible for setting the refresh token cookie.
     *
     * @param request    login credentials
     * @param ipAddress  client IP for audit log
     * @return pair of [AuthResponse, rawRefreshToken]
     */
    public LoginResult login(LoginRequest request, String ipAddress) {
        Provider provider = providerRepository.findByEmail(request.email())
                .filter(Provider::isActive)
                .orElse(null);

        // Use constant-time comparison even when provider not found to prevent timing attacks
        boolean credentialsValid = provider != null
                && passwordEncoder.matches(request.password(), provider.getPasswordHash());

        if (!credentialsValid) {
            // Log failed login attempt without exposing whether the email exists
            auditService.log(AuditContext.builder()
                    .userId(request.email())
                    .userName(request.email())
                    .userRole("UNKNOWN")
                    .action("Login failed")
                    .module(MODULE)
                    .status("Denied")
                    .ipAddress(ipAddress)
                    .build());
            throw new BadCredentialsException("Invalid credentials");
        }

        // Update last sign-in timestamp
        provider.setLastSignIn(Instant.now());
        providerRepository.save(provider);

        // Issue tokens
        String accessToken = jwtTokenProvider.generateAccessToken(provider);
        String rawRefreshToken = generateRawRefreshToken();
        persistRefreshToken(rawRefreshToken, provider.getId());

        // Audit successful login
        auditService.log(AuditContext.builder()
                .userId(provider.getId())
                .userName(provider.getName())
                .userRole(provider.getRole().name())
                .action("Login successful")
                .module(MODULE)
                .status("Success")
                .ipAddress(ipAddress)
                .build());

        log.info("Provider '{}' logged in successfully", provider.getId());

        AuthResponse response = new AuthResponse(accessToken, ProviderResponse.from(provider));
        return new LoginResult(response, rawRefreshToken);
    }

    /**
     * Validate a refresh token and issue a new access token.
     *
     * @param rawRefreshToken the token value from the httpOnly cookie
     * @return new AuthResponse with fresh access token
     */
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!storedToken.isValid()) {
            // Token expired or revoked — clean it up
            refreshTokenRepository.delete(storedToken);
            throw new BadCredentialsException("Refresh token has expired or been revoked");
        }

        Provider provider = providerRepository.findByIdAndActiveTrue(storedToken.getProviderId())
                .orElseThrow(() -> new BadCredentialsException("Provider account not found or inactive"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(provider);
        return new AuthResponse(newAccessToken, ProviderResponse.from(provider));
    }

    /**
     * Revoke all refresh tokens for the given provider (logout).
     */
    public void logout(String providerId, String ipAddress) {
        refreshTokenRepository.deleteAllByProviderId(providerId);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .action("Logout")
                .module(MODULE)
                .status("Success")
                .ipAddress(ipAddress)
                .build());

        log.info("Provider '{}' logged out — refresh tokens revoked", providerId);
    }

    /**
     * Return the provider profile for the authenticated provider ID.
     */
    public ProviderResponse getMe(String providerId) {
        Provider provider = providerRepository.findByIdAndActiveTrue(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", providerId));
        return ProviderResponse.from(provider);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Generate a cryptographically random opaque refresh token. */
    private String generateRawRefreshToken() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** SHA-256 hash of the raw refresh token for storage. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void persistRefreshToken(String rawToken, String providerId) {
        RefreshToken token = RefreshToken.builder()
                .tokenHash(hashToken(rawToken))
                .providerId(providerId)
                .expiresAt(Instant.now().plusSeconds(jwtProperties.refreshTokenExpirySeconds()))
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(token);
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record LoginResult(AuthResponse authResponse, String rawRefreshToken) {
    }
}

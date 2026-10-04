package com.medisphere.security;

import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Creates and validates JWT access tokens.
 *
 * Token claims:
 *   sub  — provider ID (e.g. PROV-001)
 *   name — provider display name
 *   role — ProviderRole enum name
 *   spec — specialty
 *   fac  — facility
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_SPECIALTY = "spec";
    static final String CLAIM_FACILITY = "fac";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // Key must be at least 32 chars to meet HMAC-SHA256 requirements
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a signed JWT access token for the given provider.
     */
    public String generateAccessToken(Provider provider) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.accessTokenExpirySeconds());

        return Jwts.builder()
                .subject(provider.getId())
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_NAME, provider.getName())
                .claim(CLAIM_ROLE, provider.getRole().name())
                .claim(CLAIM_SPECIALTY, provider.getSpecialty())
                .claim(CLAIM_FACILITY, provider.getFacility())
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate the token signature, expiry, and issuer.
     * Returns true only if the token is completely valid.
     */
    public boolean validateAccessToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** Extract the provider ID (subject) from a valid token. */
    public String extractProviderId(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extract the role from a valid token. */
    public ProviderRole extractRole(String token) {
        String roleStr = parseClaims(token).get(CLAIM_ROLE, String.class);
        return ProviderRole.valueOf(roleStr);
    }

    /** Extract all claims from a valid token. */
    public Claims extractClaims(String token) {
        return parseClaims(token);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

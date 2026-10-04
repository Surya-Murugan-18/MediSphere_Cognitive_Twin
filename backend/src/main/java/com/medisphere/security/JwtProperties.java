package com.medisphere.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medisphere.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirySeconds,
        long refreshTokenExpirySeconds,
        String issuer
) {
}

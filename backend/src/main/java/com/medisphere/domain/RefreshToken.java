package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Opaque refresh token document.
 * The token value stored here is the SHA-256 hash of the actual token sent to the client.
 * This prevents token theft from DB compromise.
 *
 * Collection: refresh_tokens
 */
@Document(collection = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private String id;

    /**
     * SHA-256 hash of the actual token string sent to the client as an httpOnly cookie.
     */
    @Indexed
    private String tokenHash;

    @Indexed
    private String providerId;

    private Instant expiresAt;

    @Builder.Default
    private boolean revoked = false;

    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}

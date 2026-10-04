package com.medisphere.repository;

import com.medisphere.domain.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke all tokens for a provider (used on logout). */
    void deleteAllByProviderId(String providerId);

    long countByProviderIdAndRevokedFalse(String providerId);
}

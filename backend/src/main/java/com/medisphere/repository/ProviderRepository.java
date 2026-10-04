package com.medisphere.repository;

import com.medisphere.domain.Provider;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends MongoRepository<Provider, String> {

    Optional<Provider> findByEmail(String email);

    Optional<Provider> findByIdAndActiveTrue(String id);

    boolean existsByEmail(String email);
}

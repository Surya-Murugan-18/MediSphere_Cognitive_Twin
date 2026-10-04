package com.medisphere.repository;

import com.medisphere.domain.FhirConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FhirConfigurationRepository extends MongoRepository<FhirConfiguration, String> {
    // Singleton pattern — always use findById("default")
}

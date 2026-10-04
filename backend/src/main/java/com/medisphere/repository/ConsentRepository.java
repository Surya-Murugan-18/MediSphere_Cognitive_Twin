package com.medisphere.repository;

import com.medisphere.domain.Consent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsentRepository extends MongoRepository<Consent, String> {

    Optional<Consent> findByPatientId(String patientId);

    boolean existsByPatientId(String patientId);
}

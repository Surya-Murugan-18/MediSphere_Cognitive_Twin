package com.medisphere.repository;

import com.medisphere.domain.Outcome;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for care plan outcome measurements.
 * Collection: outcomes
 *
 * Per tasks.md B6.2.
 */
@Repository
public interface OutcomeRepository extends MongoRepository<Outcome, String> {

    List<Outcome> findByPlanId(String planId);

    Optional<Outcome> findFirstByPlanIdOrderByCreatedAtDesc(String planId);

    long countByPlanId(String planId);
}

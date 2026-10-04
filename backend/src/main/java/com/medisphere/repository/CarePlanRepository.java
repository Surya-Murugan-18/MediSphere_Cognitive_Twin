package com.medisphere.repository;

import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for care plan documents.
 * Collection: care_plans
 *
 * Per tasks.md B6.1.
 */
@Repository
public interface CarePlanRepository
        extends MongoRepository<CarePlan, String>, CarePlanRepositoryCustom {

    // ── Active plan lookup ────────────────────────────────────────────────

    /**
     * Find the active care plan for a patient (there should be at most one).
     * Used by getActiveCarePlan() — returns first ACTIVE plan found.
     */
    Optional<CarePlan> findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
            String patientId, CarePlanStatus status);

    // ── Status-filtered queries ───────────────────────────────────────────

    List<CarePlan> findByPatientIdOrderByCreatedAtDesc(String patientId);

    Page<CarePlan> findByStatusOrderByCreatedAtDesc(CarePlanStatus status, Pageable pageable);

    Page<CarePlan> findByPatientIdOrderByCreatedAtDesc(String patientId, Pageable pageable);

    // ── Count queries ─────────────────────────────────────────────────────

    long countByStatus(CarePlanStatus status);

    long countByPatientId(String patientId);
}

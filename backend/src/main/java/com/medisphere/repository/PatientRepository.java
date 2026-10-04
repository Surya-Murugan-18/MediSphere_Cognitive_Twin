package com.medisphere.repository;

import com.medisphere.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository
        extends MongoRepository<Patient, String>, PatientRepositoryCustom {

    // ── Find by field ──────────────────────────────────────────────────────

    Optional<Patient> findByFhirId(String fhirId);

    List<Patient> findByStatus(String status);

    List<Patient> findByRiskLevel(String riskLevel);

    List<Patient> findByProviderId(String providerId);

    // ── Paginated queries with filters ────────────────────────────────────

    Page<Patient> findByStatus(String status, Pageable pageable);

    Page<Patient> findByRiskLevel(String riskLevel, Pageable pageable);

    Page<Patient> findByProviderId(String providerId, Pageable pageable);

    Page<Patient> findByStatusAndRiskLevel(String status, String riskLevel, Pageable pageable);

    Page<Patient> findByConditionsContaining(String condition, Pageable pageable);

    Page<Patient> findByStatusAndConditionsContaining(String status, String condition, Pageable pageable);

    // ── Count queries ──────────────────────────────────────────────────────

    long countByStatus(String status);

    long countByRiskLevel(String riskLevel);

    // ── Text search ────────────────────────────────────────────────────────

    /** Full-text search over the text index (name + fhirId) */
    @Query("{ $text: { $search: ?0 } }")
    Page<Patient> findByTextSearch(String query, Pageable pageable);

    /** Full-text search filtered by status */
    @Query("{ $text: { $search: ?0 }, status: ?1 }")
    Page<Patient> findByTextSearchAndStatus(String query, String status, Pageable pageable);

    /** Full-text search filtered by riskLevel */
    @Query("{ $text: { $search: ?0 }, riskLevel: ?1 }")
    Page<Patient> findByTextSearchAndRiskLevel(String query, String riskLevel, Pageable pageable);

    // ── ID generation helpers ──────────────────────────────────────────────

    /**
     * Find all patient IDs to allow PatientIdGenerator to compute the next one.
     * Returns only the _id field to minimise payload.
     */
    @Query(value = "{}", fields = "{ '_id': 1 }")
    List<Patient> findAllIds();

    // ── Existence checks ───────────────────────────────────────────────────

    boolean existsByFhirId(String fhirId);

    // ── Distinct field values (for filter-options endpoint) ───────────────

    @Query(value = "{}", fields = "{ 'conditions': 1, '_id': 0 }")
    List<Patient> findAllConditions();

    @Query(value = "{}", fields = "{ 'providerName': 1, '_id': 0 }")
    List<Patient> findAllProviders();
}

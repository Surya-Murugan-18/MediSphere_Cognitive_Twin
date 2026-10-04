package com.medisphere.repository;

import com.medisphere.domain.LabResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabResultRepository extends MongoRepository<LabResult, String> {

    // ── Basic retrieval ───────────────────────────────────────────────────

    Page<LabResult> findByPatientId(String patientId, Pageable pageable);

    List<LabResult> findByPatientIdOrderByDateDesc(String patientId);

    // ── Filtered queries ──────────────────────────────────────────────────

    Page<LabResult> findByPatientIdAndCategory(
            String patientId, String category, Pageable pageable);

    Page<LabResult> findByPatientIdAndStatus(
            String patientId, String status, Pageable pageable);

    Page<LabResult> findByPatientIdAndCategoryAndStatus(
            String patientId, String category, String status, Pageable pageable);

    Page<LabResult> findByPatientIdAndDateBetween(
            String patientId, String dateFrom, String dateTo, Pageable pageable);

    Page<LabResult> findByPatientIdAndCategoryAndDateBetween(
            String patientId, String category, String dateFrom, String dateTo, Pageable pageable);

    Page<LabResult> findByPatientIdAndStatusAndDateBetween(
            String patientId, String status, String dateFrom, String dateTo, Pageable pageable);

    Page<LabResult> findByPatientIdAndCategoryAndStatusAndDateBetween(
            String patientId, String category, String status,
            String dateFrom, String dateTo, Pageable pageable);

    // ── Previous result for trend calculation ─────────────────────────────

    /** Find the most recent result for the same patient and test name (for trend) */
    Optional<LabResult> findFirstByPatientIdAndTestOrderByDateDesc(
            String patientId, String test);

    // ── Deduplication ─────────────────────────────────────────────────────

    boolean existsByFhirObservationId(String fhirObservationId);

    Optional<LabResult> findByFhirObservationId(String fhirObservationId);
}

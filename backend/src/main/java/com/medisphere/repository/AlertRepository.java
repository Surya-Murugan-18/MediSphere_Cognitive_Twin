package com.medisphere.repository;

import com.medisphere.domain.Alert;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data MongoDB repository for Alert documents.
 *
 * Uses:
 * - Spring Data derived queries for simple operations
 * - AlertRepositoryCustom for dynamic filtered queries
 */
public interface AlertRepository
        extends MongoRepository<Alert, String>, AlertRepositoryCustom {

    long countByStatus(String status);

    List<Alert> findByPatientIdOrderByDetectedAtDesc(String patientId);

    /** Used by MonitoringService to count today's alerts. */
    long countByDetectedAtAfter(Instant since);

    /** Find the most recently detected alerts regardless of status. */
    List<Alert> findTop10ByOrderByDetectedAtDesc();

    /** Latest alerts by patient with specific statuses. */
    List<Alert> findByPatientIdAndStatusInOrderByDetectedAtDesc(
            String patientId,
            List<String> statuses
    );
}
package com.medisphere.repository;

import com.medisphere.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Audit log repository — write-only in practice.
 * No delete methods are exposed. No update methods exist on AuditLog.
 *
 * Phase 7 extends with AuditLogRepositoryCustom for multi-field filtered queries.
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String>,
        AuditLogRepositoryCustom {

    Page<AuditLog> findByUserIdContainingIgnoreCase(String userId, Pageable pageable);

    Page<AuditLog> findByPatientIdContainingIgnoreCase(String patientId, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}

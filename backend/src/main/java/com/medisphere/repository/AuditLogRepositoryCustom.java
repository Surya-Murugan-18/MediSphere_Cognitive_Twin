package com.medisphere.repository;

import com.medisphere.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Custom audit log queries using MongoTemplate for multi-field filtering.
 *
 * Extends the basic MongoRepository so all filter combinations can be
 * expressed as Criteria without combinatorial derived-query proliferation.
 */
public interface AuditLogRepositoryCustom {

    /**
     * Filter audit logs by any combination of user, patient, action, module,
     * and date range. All parameters are optional (null = no filter for that field).
     */
    Page<AuditLog> findFiltered(
            String userId,
            String patientId,
            String action,
            String module,
            Instant from,
            Instant to,
            Pageable pageable
    );
}

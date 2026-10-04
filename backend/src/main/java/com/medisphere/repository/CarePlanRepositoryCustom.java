package com.medisphere.repository;

import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Custom care plan query interface for dynamic filtered queries.
 * Implemented by CarePlanRepositoryCustomImpl using MongoTemplate.
 *
 * Per tasks.md B6.1 — paginated filtered queries.
 */
public interface CarePlanRepositoryCustom {

    Page<CarePlan> findFiltered(
            String patientId,
            CarePlanStatus status,
            String riskLevel,
            Pageable pageable
    );
}

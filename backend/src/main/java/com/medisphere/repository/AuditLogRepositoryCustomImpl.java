package com.medisphere.repository;

import com.medisphere.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoTemplate-based implementation of multi-filter audit log queries.
 *
 * Uses Criteria composition so any combination of optional filters is supported
 * without creating an exponential number of derived query methods.
 *
 * Per FR-AUD-04: filterable by user, patient, action, module, and date range
 * with server-side pagination.
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryCustomImpl implements AuditLogRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<AuditLog> findFiltered(
            String userId,
            String patientId,
            String action,
            String module,
            Instant from,
            Instant to,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();

        if (StringUtils.hasText(userId)) {
            // Case-insensitive substring match — matches partial provider IDs or names
            criteriaList.add(Criteria.where("userId").regex(userId, "i"));
        }
        if (StringUtils.hasText(patientId)) {
            criteriaList.add(Criteria.where("patientId").regex(patientId, "i"));
        }
        if (StringUtils.hasText(action)) {
            criteriaList.add(Criteria.where("action").regex(action, "i"));
        }
        if (StringUtils.hasText(module)) {
            criteriaList.add(Criteria.where("module").regex(module, "i"));
        }

        // Date range — both from and to are optional independently
        if (from != null || to != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (from != null) timeCriteria = timeCriteria.gte(from);
            if (to   != null) timeCriteria = timeCriteria.lte(to);
            criteriaList.add(timeCriteria);
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(
                    criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, AuditLog.class);

        query.with(pageable);
        List<AuditLog> results = mongoTemplate.find(query, AuditLog.class);

        return new PageImpl<>(results, pageable, total);
    }
}

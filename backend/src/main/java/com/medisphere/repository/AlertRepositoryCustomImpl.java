package com.medisphere.repository;

import com.medisphere.domain.Alert;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AlertRepositoryCustomImpl implements AlertRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Alert> findFiltered(
            String severity,
            String status,
            String patientId,
            String type,
            Instant from,
            Instant to,
            Pageable pageable
    ) {

        List<Criteria> criteriaList = new ArrayList<>();

        /*
         * Only add a filter when the value is actually provided.
         * This avoids the old query's problematic $or / $exists logic.
         */

        if (severity != null && !severity.isBlank()) {
            criteriaList.add(Criteria.where("severity").is(severity));
        }

        if (status != null && !status.isBlank()) {
            criteriaList.add(Criteria.where("status").is(status));
        }

        if (patientId != null && !patientId.isBlank()) {
            criteriaList.add(Criteria.where("patientId").is(patientId));
        }

        if (type != null && !type.isBlank()) {
            criteriaList.add(Criteria.where("type").is(type));
        }

        if (from != null || to != null) {
            Criteria detectedAtCriteria = Criteria.where("detectedAt");

            if (from != null) {
                detectedAtCriteria = detectedAtCriteria.gte(from);
            }

            if (to != null) {
                detectedAtCriteria = detectedAtCriteria.lte(to);
            }

            criteriaList.add(detectedAtCriteria);
        }

        Query query = new Query();

        if (!criteriaList.isEmpty()) {
            query.addCriteria(
                    new Criteria().andOperator(
                            criteriaList.toArray(new Criteria[0])
                    )
            );
        }

        /*
         * Keep newest alerts first.
         */
        query.with(pageable);

        List<Alert> alerts = mongoTemplate.find(query, Alert.class);

        /*
         * Count without pagination.
         */
        Query countQuery = Query.of(query)
                .limit(0)
                .skip(0);

        long total = mongoTemplate.count(countQuery, Alert.class);

        return new PageImpl<>(
                alerts,
                pageable,
                total
        );
    }
}
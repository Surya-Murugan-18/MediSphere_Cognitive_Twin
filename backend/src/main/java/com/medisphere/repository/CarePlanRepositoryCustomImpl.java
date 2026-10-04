package com.medisphere.repository;

import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filtered care plan queries using MongoTemplate.
 * Follows the AlertRepositoryCustomImpl pattern from Phase 5.
 */
@Repository
@RequiredArgsConstructor
public class CarePlanRepositoryCustomImpl implements CarePlanRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<CarePlan> findFiltered(
            String patientId,
            CarePlanStatus status,
            String riskLevel,
            Pageable pageable
    ) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (StringUtils.hasText(patientId)) {
            criteriaList.add(Criteria.where("patientId").is(patientId));
        }

        if (status != null) {
            criteriaList.add(Criteria.where("status").is(status));
        }

        if (StringUtils.hasText(riskLevel)) {
            criteriaList.add(Criteria.where("riskLevel").is(riskLevel));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        // Newest first
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);

        List<CarePlan> plans = mongoTemplate.find(query, CarePlan.class);

        Query countQuery = Query.of(query).limit(0).skip(0);
        long total = mongoTemplate.count(countQuery, CarePlan.class);

        return new PageImpl<>(plans, pageable, total);
    }
}

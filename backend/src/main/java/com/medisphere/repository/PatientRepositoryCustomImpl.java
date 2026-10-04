package com.medisphere.repository;

import com.medisphere.domain.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class PatientRepositoryCustomImpl implements PatientRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Patient> searchPatients(
            String search,
            String riskLevel,
            String condition,
            String status,
            String providerName,
            Pageable pageable) {

        List<Criteria> criteriaList = new ArrayList<>();

        /*
         * SEARCH
         *
         * Search across:
         * - patient ID
         * - patient name
         * - FHIR ID
         *
         * Uses case-insensitive substring matching so:
         *
         * "s"      -> Surya M
         * "sur"    -> Surya M
         * "P001"   -> P001
         * "fhir"   -> matching FHIR ID
         */
        if (StringUtils.hasText(search)) {

            String escapedSearch = Pattern.quote(search.trim());

            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("_id")
                            .regex(escapedSearch, "i"),

                    Criteria.where("name")
                            .regex(escapedSearch, "i"),

                    Criteria.where("fhirId")
                            .regex(escapedSearch, "i")
            );

            criteriaList.add(searchCriteria);
        }

        /*
         * RISK
         */
        if (StringUtils.hasText(riskLevel)
                && !"All risk".equalsIgnoreCase(riskLevel)) {

            criteriaList.add(
                    Criteria.where("riskLevel")
                            .regex("^" + Pattern.quote(riskLevel.trim()) + "$", "i")
            );
        }

        /*
         * CONDITION
         *
         * conditions is an array in MongoDB.
         * This matches any condition containing the selected value.
         */
        if (StringUtils.hasText(condition)
                && !"All conditions".equalsIgnoreCase(condition)) {

            criteriaList.add(
                    Criteria.where("conditions")
                            .regex(Pattern.quote(condition.trim()), "i")
            );
        }

        /*
         * STATUS
         */
        if (StringUtils.hasText(status)
                && !"All statuses".equalsIgnoreCase(status)) {

            criteriaList.add(
                    Criteria.where("status")
                            .regex("^" + Pattern.quote(status.trim()) + "$", "i")
            );
        }

        /*
         * PROVIDER
         */
        if (StringUtils.hasText(providerName)
                && !"All providers".equalsIgnoreCase(providerName)) {

            criteriaList.add(
                    Criteria.where("providerName")
                            .regex("^" + Pattern.quote(providerName.trim()) + "$", "i")
            );
        }

        Query query = new Query();

        /*
         * Combine every active filter using AND.
         */
        if (!criteriaList.isEmpty()) {
            query.addCriteria(
                    new Criteria().andOperator(
                            criteriaList.toArray(new Criteria[0])
                    )
            );
        }

        /*
         * Total count BEFORE pagination.
         */
        long total = mongoTemplate.count(query, Patient.class);

        /*
         * Apply sorting + pagination.
         */
        query.with(pageable);

        List<Patient> patients =
                mongoTemplate.find(query, Patient.class);

        return new PageImpl<>(
                patients,
                pageable,
                total
        );
    }
}
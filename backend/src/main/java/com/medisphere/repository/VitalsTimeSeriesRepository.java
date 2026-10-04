package com.medisphere.repository;

import com.medisphere.domain.VitalsTimeSeries;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VitalsTimeSeriesRepository extends MongoRepository<VitalsTimeSeries, String> {

    List<VitalsTimeSeries> findByPatientIdAndTypeAndTimestampBetween(
            String patientId, String type, Instant from, Instant to, Sort sort);

    List<VitalsTimeSeries> findByPatientIdAndTimestampBetween(
            String patientId, Instant from, Instant to, Sort sort);

    /** Count readings for a patient and type — used to check if data exists */
    long countByPatientIdAndType(String patientId, String type);

    /** Latest reading across all vital types for source freshness checks. */
    java.util.Optional<VitalsTimeSeries> findFirstByPatientIdOrderByTimestampDesc(String patientId);
}

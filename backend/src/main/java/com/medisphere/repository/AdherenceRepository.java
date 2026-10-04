package com.medisphere.repository;

import com.medisphere.domain.AdherenceRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for weekly adherence records.
 * Collection: adherence_records
 *
 * Per tasks.md B6.2.
 */
@Repository
public interface AdherenceRepository extends MongoRepository<AdherenceRecord, String> {

    /** All adherence records for a plan, sorted oldest-first (for trend charts). */
    List<AdherenceRecord> findByPlanIdOrderByWeekNumberAsc(String planId);

    /** Last N weeks of adherence for a plan, sorted newest-first. */
    List<AdherenceRecord> findTop6ByPlanIdOrderByWeekNumberDesc(String planId);

    /** Custom N-week query — fetches top N sorted newest-first; caller reverses for chart. */
    List<AdherenceRecord> findByPlanIdOrderByWeekNumberDesc(String planId);

    /** Count records per plan (used to determine next week number). */
    long countByPlanId(String planId);
}

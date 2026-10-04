package com.medisphere.repository;

import com.medisphere.domain.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for AI prediction documents.
 * Collection: predictions
 * Compound index: { patientId: 1, model: 1, createdAt: -1 } — defined on the domain class.
 */
@Repository
public interface PredictionRepository extends MongoRepository<Prediction, String> {

    /** All predictions for a patient, newest first. */
    List<Prediction> findByPatientIdOrderByCreatedAtDesc(String patientId);

    /** All predictions for a patient and model, newest first. */
    List<Prediction> findByPatientIdAndModelOrderByCreatedAtDesc(String patientId, String model);

    /** Most recent prediction for a patient and model. */
    java.util.Optional<Prediction> findFirstByPatientIdAndModelOrderByCreatedAtDesc(
            String patientId, String model);

    /** Paginated predictions filtered by category (High / Medium / Low). */
    Page<Prediction> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    /** Paginated predictions filtered by patient. */
    Page<Prediction> findByPatientIdOrderByCreatedAtDesc(String patientId, Pageable pageable);

    /** Paginated predictions filtered by patient and category. */
    Page<Prediction> findByPatientIdAndCategoryOrderByCreatedAtDesc(
            String patientId, String category, Pageable pageable);

    /** Paginated predictions filtered by model. */
    Page<Prediction> findByModelOrderByCreatedAtDesc(String model, Pageable pageable);

    /** Count predictions by risk category (for stats/distribution). */
    long countByCategory(String category);

    /** Check if a patient already has a prediction from a given model (most-recent). */
    boolean existsByPatientIdAndModel(String patientId, String model);
}

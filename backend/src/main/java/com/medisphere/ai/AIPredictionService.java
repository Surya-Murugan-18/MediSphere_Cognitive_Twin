package com.medisphere.ai;

import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;

import java.util.List;

/**
 * AI Prediction Service abstraction — the ONLY way to generate risk predictions.
 *
 * All implementations must be deterministic for the same patient state.
 * No implementation should generate random values.
 *
 * Implementations:
 *   StubAIPredictionService  — active when AI_PREDICTION_MODE=stub (default in development)
 *   TFFAIPredictionService   — active when AI_PREDICTION_MODE=tff  (production TensorFlow Federated)
 *
 * Per design.md §6.1
 */
public interface AIPredictionService {

    /**
     * Predict 10-year cardiovascular risk for a patient.
     *
     * @param patient  the patient domain object
     * @param labs     the patient's lab results (used for HbA1c, cholesterol etc.)
     * @param vitals   the patient's current vitals snapshot (used for blood pressure)
     * @return a Prediction document (not yet persisted — PredictionService persists it)
     */
    Prediction predictCVDRisk(Patient patient, List<LabResult> labs, VitalsSnapshot vitals);

    /**
     * Predict 12-month diabetes complication risk for a patient.
     *
     * @param patient  the patient domain object
     * @param labs     the patient's lab results (used for HbA1c, glucose trends)
     * @return a Prediction document (not yet persisted)
     */
    Prediction predictDiabetesComplication(Patient patient, List<LabResult> labs);

    /**
     * Predict 30-day hospital readmission risk for a patient.
     *
     * @param patient       the patient domain object
     * @param recentAlerts  recent HIGH-severity alerts for this patient (Phase 5+)
     *                      Pass an empty list in Phase 4 (alerts not yet implemented).
     * @return a Prediction document (not yet persisted)
     */
    Prediction predictReadmission(Patient patient, List<String> recentAlerts);

    /**
     * Generate SHAP feature contribution factors for an existing prediction.
     * The factors must be deterministically derived from the same patient attributes
     * that produced the prediction value — not random.
     *
     * @param predictionId  the ID of the persisted prediction to explain
     * @param patient       the patient whose data was used in the prediction
     * @param labs          the lab results used during prediction
     * @return list of SHAP factors (same order for same inputs)
     */
    List<Prediction.ShapFactor> explainPrediction(String predictionId, Patient patient,
                                                   List<LabResult> labs, VitalsSnapshot vitals);
}

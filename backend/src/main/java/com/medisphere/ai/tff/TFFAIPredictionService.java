package com.medisphere.ai.tff;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.ai.ml.ConditionMapper;
import com.medisphere.ai.ml.MLServiceClient;
import com.medisphere.ai.ml.dto.*;
import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanRecommendation;
import com.medisphere.domain.CarePlanStatus;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.CarePlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production ML prediction service backed by the FastAPI ML inference service.
 *
 * Activated when AI_PREDICTION_MODE=ml.
 *
 * Responsibilities:
 *   1. Extract raw domain features from Patient / LabResult / VitalsSnapshot / CarePlan.
 *   2. Assemble the ML request DTO.
 *   3. Call MLServiceClient (POST to FastAPI).
 *   4. Map the response to the existing Prediction domain object.
 *
 * Invariants:
 *   - NEVER fabricates predictions when the ML service is unavailable.
 *   - NEVER duplicates Python preprocessing (sklearn pipeline is authoritative).
 *   - Does NOT add features absent from the trained model feature set.
 *   - All probability values are stored as percentages (0–100) per Prediction.value convention.
 *   - Genuine SHAP values from the ML response are mapped 1:1 to Prediction.ShapFactor.
 */
public class TFFAIPredictionService implements AIPredictionService {

    private static final Logger log = LoggerFactory.getLogger(TFFAIPredictionService.class);

    // Model labels shown in the UI — must match PredictionService.runPredictionsSync selectors
    static final String MODEL_CVD         = "CVD-Risk-v3.2";
    static final String MODEL_DIABETES    = "DM-Complication-v2.4";
    static final String MODEL_READMISSION = "Readmit-30d-v1.8";

    static final String LABEL_CVD         = "10-Year Cardiovascular Risk";
    static final String LABEL_DIABETES    = "Diabetes Complication Risk (12 mo)";
    static final String LABEL_READMISSION = "30-Day Readmission Risk";

    private final MLServiceClient mlClient;
    private final CarePlanRepository carePlanRepository;
    private final AlertRepository alertRepository;

    public TFFAIPredictionService(MLServiceClient mlClient,
                                   CarePlanRepository carePlanRepository,
                                   AlertRepository alertRepository) {
        this.mlClient            = mlClient;
        this.carePlanRepository  = carePlanRepository;
        this.alertRepository     = alertRepository;
    }

    // ── CVD-10Y ───────────────────────────────────────────────────────────

    @Override
    public Prediction predictCVDRisk(Patient patient, List<LabResult> labs, VitalsSnapshot vitals) {
        log.info("TFFAIPredictionService: CVD-Risk prediction for patient {}", patient.getId());

        CvdRiskRequest request = buildCvdRequest(patient, labs, vitals);
        CvdRiskResponse mlResp = mlClient.predictCvd(request);

        if (mlResp == null) {
            throw new MLServiceUnavailableException(
                    "CVD-Risk ML prediction failed for patient " + patient.getId()
                    + " — ML service unavailable or returned an error. "
                    + "Check ML service health at /health/ready.");
        }

        return mapToPredict(mlResp.patientId(), MODEL_CVD, LABEL_CVD,
                mlResp.probabilityScore(), mlResp.riskCategory(),
                mlResp.modelId(), mlResp.modelVersion(),
                mlResp.shapFactors(), mlResp.imputedFields());
    }

    // ── Diabetes-Risk ─────────────────────────────────────────────────────

    @Override
    public Prediction predictDiabetesComplication(Patient patient, List<LabResult> labs) {
        log.info("TFFAIPredictionService: Diabetes-Risk prediction for patient {}", patient.getId());

        DiabetesRiskRequest request = buildDiabetesRequest(patient, labs);
        DiabetesRiskResponse mlResp = mlClient.predictDiabetes(request);

        if (mlResp == null) {
            throw new MLServiceUnavailableException(
                    "Diabetes-Risk ML prediction failed for patient " + patient.getId()
                    + " — ML service unavailable or returned an error.");
        }

        return mapToPredict(mlResp.patientId(), MODEL_DIABETES, LABEL_DIABETES,
                mlResp.probabilityScore(), mlResp.riskCategory(),
                mlResp.modelId(), mlResp.modelVersion(),
                mlResp.shapFactors(), mlResp.imputedFields());
    }

    // ── Readmission-30D ───────────────────────────────────────────────────

    /**
     * @param recentAlerts list provided by PredictionService — each entry represents
     *                     one HIGH-severity alert in the previous 365 days.
     *                     list.size() is used as number_high_alerts_prior_year.
     */
    @Override
    public Prediction predictReadmission(Patient patient, List<String> recentAlerts) {
        log.info("TFFAIPredictionService: Readmission-30D prediction for patient {}", patient.getId());

        ReadmissionRequest request = buildReadmissionRequest(patient, recentAlerts);
        ReadmissionResponse mlResp = mlClient.predictReadmission(request);

        if (mlResp == null) {
            throw new MLServiceUnavailableException(
                    "Readmission-30D ML prediction failed for patient " + patient.getId()
                    + " — ML service unavailable or returned an error.");
        }

        return mapToPredict(mlResp.patientId(), MODEL_READMISSION, LABEL_READMISSION,
                mlResp.probabilityScore(), mlResp.riskCategory(),
                mlResp.modelId(), mlResp.modelVersion(),
                mlResp.shapFactors(), mlResp.imputedFields());
    }

    // ── SHAP explanation (on-demand via existing prediction) ──────────────

    /**
     * SHAP factors are already embedded in the prediction document when it is created.
     * For the ML integration, this method returns an empty list — callers should read
     * shapFactors directly from the Prediction document.
     * If on-demand re-explanation is required in a future phase, call /explain endpoints here.
     */
    @Override
    public List<Prediction.ShapFactor> explainPrediction(String predictionId, Patient patient,
                                                          List<LabResult> labs, VitalsSnapshot vitals) {
        return List.of();
    }

    // ── CVD feature extraction ────────────────────────────────────────────

    /**
     * Build CVD request from domain objects.
     *
     * Feature mapping:
     *   patient_id       → Patient.id
     *   male             → Patient.gender "Male" → 1, else → 0
     *   age              → computed from Patient.dob (integer years)
     *   sys_bp           → VitalsSnapshot.bloodPressure systolic component (Double)
     *   dia_bp           → VitalsSnapshot.bloodPressure diastolic component (Double)
     *   heart_rate       → VitalsSnapshot.heartRate (0 → null, so pipeline can impute)
     *   tot_chol         → LabResult test="Total Cholesterol" numeric value
     *   glucose          → LabResult test="Fasting Glucose" numeric value
     *   prevalent_hyp    → Patient.conditions contains "hypertension" → 1
     *   prevalent_stroke → Patient.conditions contains "stroke" → 1
     *   diabetes         → Patient.conditions contains "diabetes" → 1
     *   bp_meds          → ConditionMapper.extractBpMeds() from active CarePlan recommendations
     *
     * DO NOT include: smoking, currentSmoker, cigsPerDay, BMI, education, pulse_pressure
     * (pulse_pressure is computed by the Python pipeline from sysBP and diaBP)
     */
    CvdRiskRequest buildCvdRequest(Patient patient, List<LabResult> labs, VitalsSnapshot vitals) {
        Integer male    = genderToMale(patient.getGender());
        Integer age     = computeAge(patient.getDob());
        Double  sysBp   = extractSystolic(vitals);
        Double  diaBp   = extractDiastolic(vitals);
        Integer hr      = extractHeartRate(vitals);
        Double  totChol = extractLabNumeric(labs, "Total Cholesterol");
        Double  glucose = extractLabNumeric(labs, "Fasting Glucose");
        Integer prevHyp    = conditionFlag(patient, "hypertension");
        Integer prevStroke = conditionFlag(patient, "stroke");
        Integer diabetes   = conditionFlag(patient, "diabetes");
        Integer bpMeds     = extractBpMedsFromCarePlan(patient.getId());

        return new CvdRiskRequest(
                patient.getId(), male, age, sysBp, diaBp, hr,
                totChol, glucose, prevHyp, prevStroke, diabetes, bpMeds);
    }

    // ── Diabetes feature extraction ───────────────────────────────────────

    /**
     * Build Diabetes request from domain objects.
     *
     * Feature mapping:
     *   patient_id               → Patient.id
     *   age_years                → computed from Patient.dob (FastAPI converts to BRFSS bracket)
     *   sex_male                 → Patient.gender "Male" → 1
     *   high_bp                  → Patient.conditions contains "hypertension" → 1
     *   high_chol                → LabResult "Total Cholesterol" status="High" → 1
     *   chol_check               → any Total Cholesterol LabResult exists → 1
     *   stroke                   → Patient.conditions contains "stroke" → 1
     *   heart_disease_or_attack  → conditions contain "heart" | "coronary" | "cad" | "myocardial" | "infarction" → 1
     *   phys_hlth_alert_count_30d → count of HIGH+MEDIUM severity alerts in last 30 days, capped at 30
     *                               Per metadata.json proxy_features and ML_IMPLEMENTATION_DESIGN.md §B.2.2 row 8:
     *                               severity IN ('HIGH','MEDIUM'), window = today−30d, fallback = 0
     *
     * DO NOT include: BMI, GenHlth, Smoker, HbA1c or any unavailable BRFSS-only field.
     */
    DiabetesRiskRequest buildDiabetesRequest(Patient patient, List<LabResult> labs) {
        Integer ageYears              = computeAge(patient.getDob());
        Integer sexMale               = genderToMale(patient.getGender());
        Integer highBp                = conditionFlag(patient, "hypertension");
        Integer highChol              = cholesterolHighFlag(labs);
        Integer cholCheck             = cholesterolCheckFlag(labs);
        Integer stroke                = conditionFlag(patient, "stroke");
        Integer heartDiseaseOrAttack  = heartDiseaseFlag(patient);
        // PhysHlth proxy: HIGH+MEDIUM alert count in last 30 days, capped at 30.
        // Per metadata.json proxy_features and ML_IMPLEMENTATION_DESIGN.md §B.2.2:
        //   "Count of HIGH/MEDIUM Alert records in past 30 days (capped at 30)"
        //   No alerts in window → 0 (patient had no detected health events — semantically correct;
        //   do NOT send null here as the design says fallback = 0, not median imputation).
        Integer physHlth = loadAlertCount30d(patient.getId());

        return new DiabetesRiskRequest(
                patient.getId(), ageYears, sexMale, highBp, highChol,
                cholCheck, stroke, heartDiseaseOrAttack, physHlth);
    }

    // ── Readmission feature extraction ────────────────────────────────────

    /**
     * Build Readmission request from domain objects.
     *
     * Feature mapping:
     *   patient_id                   → Patient.id
     *   gender_male                  → Patient.gender "Male" → 1
     *   age_years                    → computed from Patient.dob (FastAPI maps to bracket midpoint)
     *   number_diagnoses             → Patient.conditions.size()
     *   diag_group_primary           → ConditionMapper.conditionNameToGroup(conditions[0])
     *   diag_group_secondary         → ConditionMapper.conditionNameToGroup(conditions[1])
     *   diag_group_tertiary          → ConditionMapper.conditionNameToGroup(conditions[2])
     *   number_high_alerts_prior_year → recentAlerts.size() (HIGH alerts from last 365d)
     *   diabetes_med                 → ConditionMapper.extractDiabetesMed() proxy
     *   care_plan_changed_30d        → ACTIVE CarePlan updated in last 30 days → 1
     *
     * DO NOT include: number_inpatient, number_outpatient, admission_type_id, race,
     *   medical_specialty, BMI, smoking, or any unavailable field.
     */
    ReadmissionRequest buildReadmissionRequest(Patient patient, List<String> recentAlerts) {
        Integer genderMale   = genderToMale(patient.getGender());
        Integer ageYears     = computeAge(patient.getDob());
        List<String> conds   = patient.getConditions() != null ? patient.getConditions() : List.of();
        Integer numDiagnoses = conds.size();

        String[] diagGroups  = ConditionMapper.conditionsToDigGroups(conds);

        Integer numHighAlerts = recentAlerts != null ? recentAlerts.size() : 0;

        // diabetes_med proxy via ConditionMapper
        List<String> interventions = getActiveCarePlanInterventions(patient.getId());
        List<String> titles        = getActiveCarePlanTitles(patient.getId());
        Integer diabetesMed = ConditionMapper.extractDiabetesMed(conds, interventions, titles);

        // care_plan_changed_30d: any ACTIVE care plan updated in last 30 days
        Integer carePlanChanged = carePlanChangedLast30Days(patient.getId());

        return new ReadmissionRequest(
                patient.getId(), genderMale, ageYears, numDiagnoses,
                diagGroups[0], diagGroups[1], diagGroups[2],
                numHighAlerts, diabetesMed, carePlanChanged);
    }

    // ── ML response → Prediction domain mapping ───────────────────────────

    /**
     * Map a FastAPI ML response to the existing Prediction domain object.
     *
     * probability_score from FastAPI is [0,1]; Prediction.value is stored as
     * a percentage [0,100] to match the existing stub convention and frontend contract.
     */
    private Prediction mapToPredict(String patientId,
                                     String modelName, String label,
                                     double probabilityScore, String riskCategory,
                                     String mlModelId, String mlModelVersion,
                                     List<MlShapFactor> mlShapFactors,
                                     List<String> imputedFields) {
        // Convert probability [0,1] → percentage [0,100], rounded to 2 dp
        double valuePercent = Math.round(probabilityScore * 100.0 * 100.0) / 100.0;

        // Map SHAP factors — genuine values from the ML service
        List<Prediction.ShapFactor> shapFactors = mapShapFactors(mlShapFactors);

        // Build clinical evidence note including model metadata and imputation info
        List<Prediction.ClinicalEvidence> clinicalEvidence =
                buildClinicalEvidence(mlModelId, mlModelVersion, imputedFields);

        return Prediction.builder()
                .id("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patientId)
                .model(modelName)
                .label(label)
                .value(valuePercent)
                .category(normaliseCategory(riskCategory))
                .federatedRound(0)   // Not applicable for non-federated ML service
                .confidence(0)       // FastAPI does not return a separate confidence score
                .calibration("Calibrated")   // Pipeline uses isotonic calibration
                .shapFactors(shapFactors)
                .clinicalEvidence(clinicalEvidence)
                .build();
    }

    // ── SHAP mapping ──────────────────────────────────────────────────────

    private List<Prediction.ShapFactor> mapShapFactors(List<MlShapFactor> mlFactors) {
        if (mlFactors == null || mlFactors.isEmpty()) return List.of();
        List<Prediction.ShapFactor> result = new ArrayList<>(mlFactors.size());
        for (MlShapFactor mf : mlFactors) {
            result.add(Prediction.ShapFactor.builder()
                    .feature(mf.feature())
                    .value(mf.value())
                    .contribution(mf.contribution())
                    .direction(normaliseDirection(mf.direction()))
                    .build());
        }
        return result;
    }

    // ── Clinical evidence builder ─────────────────────────────────────────

    private List<Prediction.ClinicalEvidence> buildClinicalEvidence(
            String mlModelId, String mlModelVersion, List<String> imputedFields) {

        List<Prediction.ClinicalEvidence> evidence = new ArrayList<>();

        evidence.add(new Prediction.ClinicalEvidence(
                "MediSphere ML Service — " + mlModelId + " v" + mlModelVersion,
                "Prediction generated by serialized scikit-learn/XGBoost pipeline. "
                + "For clinical decision support only — requires clinical oversight."));

        if (imputedFields != null && !imputedFields.isEmpty()) {
            evidence.add(new Prediction.ClinicalEvidence(
                    "Pipeline imputation applied",
                    "The following features were missing and imputed by the model pipeline: "
                    + String.join(", ", imputedFields) + "."));
        }

        return evidence;
    }

    // ── Feature extraction helpers ────────────────────────────────────────

    /** Convert gender string to binary 0/1. null if gender unknown. */
    static Integer genderToMale(String gender) {
        if (gender == null || gender.isBlank()) return null;
        return "male".equalsIgnoreCase(gender.trim()) ? 1 : 0;
    }

    /** Compute age in years from ISO-8601 dob string. null if unavailable. */
    static Integer computeAge(String dob) {
        if (dob == null || dob.isBlank()) return null;
        try {
            return Period.between(LocalDate.parse(dob.trim()), LocalDate.now(ZoneOffset.UTC)).getYears();
        } catch (Exception e) {
            log.debug("Could not parse dob '{}': {}", dob, e.getMessage());
            return null;
        }
    }

    /**
     * Extract systolic BP from VitalsSnapshot.bloodPressure ("systolic/diastolic").
     * Returns null if unavailable or unparseable (pipeline will impute).
     */
    static Double extractSystolic(VitalsSnapshot vitals) {
        if (vitals == null) return null;
        String bp = vitals.getBloodPressure();
        if (bp == null || bp.isBlank() || "—".equals(bp)) return null;
        try {
            return Double.parseDouble(bp.split("/")[0].trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract diastolic BP. Returns null if unavailable. */
    static Double extractDiastolic(VitalsSnapshot vitals) {
        if (vitals == null) return null;
        String bp = vitals.getBloodPressure();
        if (bp == null || bp.isBlank() || "—".equals(bp)) return null;
        try {
            String[] parts = bp.split("/");
            return parts.length > 1 ? Double.parseDouble(parts[1].trim()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract heart rate. Returns null if 0 (not yet measured) so the pipeline imputes.
     */
    static Integer extractHeartRate(VitalsSnapshot vitals) {
        if (vitals == null || vitals.getHeartRate() == 0) return null;
        return vitals.getHeartRate();
    }

    /**
     * Extract a numeric lab value by test name (case-insensitive, most recent first).
     * Returns null if no matching lab result — pipeline will impute.
     */
    static Double extractLabNumeric(List<LabResult> labs, String testName) {
        if (labs == null || labs.isEmpty()) return null;
        return labs.stream()
                .filter(l -> testName.equalsIgnoreCase(l.getTest()))
                .mapToDouble(LabResult::getNumeric)
                .boxed()
                .findFirst()
                .map(v -> v == 0.0 ? null : v)  // 0.0 means "not recorded" → let pipeline impute
                .orElse(null);
    }

    /**
     * Return 1 if Patient.conditions contains a string with the keyword, else 0.
     * null only if conditions list is null (pipeline will impute).
     */
    static Integer conditionFlag(Patient patient, String keyword) {
        if (patient.getConditions() == null) return null;
        boolean found = patient.getConditions().stream()
                .anyMatch(c -> c != null && c.toLowerCase().contains(keyword.toLowerCase()));
        return found ? 1 : 0;
    }

    /**
     * Heart disease or attack flag:
     * 1 if conditions contain any of: heart, coronary, cad, myocardial, infarction, angina.
     */
    static Integer heartDiseaseFlag(Patient patient) {
        if (patient.getConditions() == null) return null;
        String[] heartKeywords = {"heart", "coronary", "cad", "myocardial", "infarction", "angina"};
        for (String cond : patient.getConditions()) {
            if (cond == null) continue;
            String lower = cond.toLowerCase();
            for (String kw : heartKeywords) {
                if (lower.contains(kw)) return 1;
            }
        }
        return 0;
    }

    /**
     * 1 if Total Cholesterol lab result has status="High", else 0.
     * null if no cholesterol result found.
     */
    static Integer cholesterolHighFlag(List<LabResult> labs) {
        if (labs == null || labs.isEmpty()) return null;
        Optional<LabResult> cholLab = labs.stream()
                .filter(l -> "Total Cholesterol".equalsIgnoreCase(l.getTest()))
                .findFirst();
        return cholLab.map(l -> "High".equalsIgnoreCase(l.getStatus()) ? 1 : 0).orElse(null);
    }

    /**
     * 1 if any Total Cholesterol lab result exists (chol_check proxy), else 0.
     */
    static Integer cholesterolCheckFlag(List<LabResult> labs) {
        if (labs == null || labs.isEmpty()) return 0;
        boolean exists = labs.stream()
                .anyMatch(l -> "Total Cholesterol".equalsIgnoreCase(l.getTest()));
        return exists ? 1 : 0;
    }

    /**
     * bp_meds proxy: check the active care plan's recommendations for antihypertensive keywords.
     * Returns 0 if no active care plan.
     */
    private Integer extractBpMedsFromCarePlan(String patientId) {
        try {
            Optional<CarePlan> activePlan = carePlanRepository
                    .findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
                            patientId, CarePlanStatus.ACTIVE);
            if (activePlan.isEmpty() || activePlan.get().getRecommendations() == null) return 0;

            List<String> interventions = new ArrayList<>();
            List<String> titles        = new ArrayList<>();
            for (CarePlanRecommendation rec : activePlan.get().getRecommendations()) {
                interventions.add(rec.getIntervention() != null ? rec.getIntervention() : "");
                titles.add(rec.getTitle() != null ? rec.getTitle() : "");
            }
            return ConditionMapper.extractBpMeds(interventions, titles);
        } catch (Exception e) {
            log.debug("Could not load care plan for bp_meds proxy for patient {}: {}",
                    patientId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get intervention strings from the active care plan.
     */
    private List<String> getActiveCarePlanInterventions(String patientId) {
        try {
            return carePlanRepository
                    .findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
                            patientId, CarePlanStatus.ACTIVE)
                    .map(plan -> {
                        if (plan.getRecommendations() == null) return List.<String>of();
                        return plan.getRecommendations().stream()
                                .map(r -> r.getIntervention() != null ? r.getIntervention() : "")
                                .toList();
                    })
                    .orElse(List.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Get title strings from the active care plan.
     */
    private List<String> getActiveCarePlanTitles(String patientId) {
        try {
            return carePlanRepository
                    .findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
                            patientId, CarePlanStatus.ACTIVE)
                    .map(plan -> {
                        if (plan.getRecommendations() == null) return List.<String>of();
                        return plan.getRecommendations().stream()
                                .map(r -> r.getTitle() != null ? r.getTitle() : "")
                                .toList();
                    })
                    .orElse(List.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * care_plan_changed_30d: 1 if the ACTIVE care plan's updatedAt is within the last 30 days.
     */
    private Integer carePlanChangedLast30Days(String patientId) {
        try {
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            return carePlanRepository
                    .findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
                            patientId, CarePlanStatus.ACTIVE)
                    .map(plan -> {
                        // updatedAt set by @LastModifiedDate — checks whether plan was recently changed
                        Instant updatedAt = plan.getUpdatedAt();
                        if (updatedAt == null) updatedAt = plan.getCreatedAt();
                        return (updatedAt != null && updatedAt.isAfter(thirtyDaysAgo)) ? 1 : 0;
                    })
                    .orElse(0);
        } catch (Exception e) {
            log.debug("Could not determine care_plan_changed_30d for patient {}: {}",
                    patientId, e.getMessage());
            return 0;
        }
    }

    /**
     * PhysHlth proxy: count of HIGH and MEDIUM severity alerts in the past 30 days,
     * capped at 30 per the FastAPI schema (phys_hlth_alert_count_30d field: ge=0, le=30).
     *
     * Per metadata.json proxy_features:
     *   "PhysHlth": "Count of HIGH/MEDIUM Alert records in past 30 days (capped at 30)"
     *
     * Per ML_IMPLEMENTATION_DESIGN.md §B.2.2 row 8:
     *   severity IN ('HIGH','MEDIUM'), window = today−30d
     *   No alerts in window → 0   (not null — the design says 0 is semantically correct:
     *   "patient had no detected health events — directionally correct")
     *
     * Returns 0 (not null) on repository failure so the field is always populated.
     */
    Integer loadAlertCount30d(String patientId) {
        try {
            Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
            long highCount   = alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    patientId, "HIGH",   thirtyDaysAgo);
            long mediumCount = alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    patientId, "MEDIUM", thirtyDaysAgo);
            long total = highCount + mediumCount;
            // Cap at 30 — FastAPI schema enforces ge=0, le=30 for phys_hlth_alert_count_30d
            return (int) Math.min(total, 30);
        } catch (Exception e) {
            log.debug("Could not load alert count for phys_hlth proxy for patient {}: {}",
                    patientId, e.getMessage());
            return 0;  // fallback: 0 = no detected health events (semantically correct per design)
        }
    }

    // ── Value normalization ───────────────────────────────────────────────

    /** Ensure category is exactly "High", "Medium", or "Low". */
    private String normaliseCategory(String riskCategory) {
        if (riskCategory == null) return "Low";
        return switch (riskCategory.trim()) {
            case "High"   -> "High";
            case "Medium" -> "Medium";
            default       -> "Low";
        };
    }

    /** Ensure direction is exactly "increases" or "decreases". */
    private String normaliseDirection(String direction) {
        if (direction == null) return "increases";
        return direction.trim().toLowerCase().startsWith("decr") ? "decreases" : "increases";
    }
}

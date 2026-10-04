package com.medisphere.ai.stub;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development stub for AI risk prediction.
 *
 * Activated when AI_PREDICTION_MODE=stub (default).
 *
 * All formulas are deterministic: same patient state → same output every time.
 * No Math.random(), no external AI calls, no neural network.
 * Formulas follow design.md §6.2 exactly.
 *
 * CVD risk:
 *   base = 15%
 *   +3% per diabetes condition
 *   +2% per hypertension condition
 *   +1% per year of age over 50
 *   +5% if HbA1c > 8.0
 *   scaled/capped to 100%
 *
 * Diabetes complication risk:
 *   base = 10%
 *   +4% if HbA1c > 8.0
 *   +2% if fasting glucose above normal range (> 100 mg/dL)
 *
 * Readmission risk:
 *   base = 8%
 *   +3% per HIGH alert in last 30 days (alerts arrive in Phase 5; list is empty in Phase 4)
 */
public class StubAIPredictionService implements AIPredictionService {

    private static final Logger log = LoggerFactory.getLogger(StubAIPredictionService.class);

    // ── CVD Risk ──────────────────────────────────────────────────────────

    @Override
    public Prediction predictCVDRisk(Patient patient, List<LabResult> labs, VitalsSnapshot vitals) {
        log.debug("StubAIPredictionService: computing CVD risk for patient {}", patient.getId());

        double risk = 15.0; // base

        // +3% per diabetes condition
        long diabetesCount = countCondition(patient, "diabetes");
        risk += diabetesCount * 3.0;

        // +2% per hypertension condition
        long htCount = countCondition(patient, "hypertension");
        risk += htCount * 2.0;

        // +1% per year over 50
        int age = computeAge(patient.getDob());
        if (age > 50) {
            risk += (age - 50) * 1.0;
        }

        // +5% if HbA1c > 8.0
        double hba1c = extractNumericLab(labs, "HbA1c");
        if (hba1c > 8.0) {
            risk += 5.0;
        }

        // Cap to 100
        risk = Math.min(risk, 100.0);
        risk = Math.round(risk * 10.0) / 10.0;

        String category = riskCategory(risk);
        int confidence = computeConfidence(patient, labs, 91);

        List<Prediction.ShapFactor> shap = buildCvdShapFactors(patient, labs, vitals, age, hba1c, htCount);
        List<Prediction.ClinicalEvidence> evidence = cvdClinicalEvidence();

        return Prediction.builder()
                .id("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .model("CVD-Risk-v3.2")
                .label("10-Year Cardiovascular Risk")
                .value(risk)
                .category(category)
                .federatedRound(47)
                .confidence(confidence)
                .calibration("Calibrated")
                .shapFactors(shap)
                .clinicalEvidence(evidence)
                .build();
    }

    // ── Diabetes Complication Risk ────────────────────────────────────────

    @Override
    public Prediction predictDiabetesComplication(Patient patient, List<LabResult> labs) {
        log.debug("StubAIPredictionService: computing DM complication risk for patient {}", patient.getId());

        double risk = 10.0; // base

        double hba1c = extractNumericLab(labs, "HbA1c");
        if (hba1c > 8.0) {
            risk += 4.0;
        }

        double glucose = extractNumericLab(labs, "Fasting Glucose");
        if (glucose > 100.0) {
            risk += 2.0;
        }

        risk = Math.min(risk, 100.0);
        risk = Math.round(risk * 10.0) / 10.0;

        String category = riskCategory(risk);
        int confidence = computeConfidence(patient, labs, 88);

        List<Prediction.ShapFactor> shap = buildDmShapFactors(patient, labs, hba1c, glucose);
        List<Prediction.ClinicalEvidence> evidence = dmClinicalEvidence();

        return Prediction.builder()
                .id("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .model("DM-Complication-v2.4")
                .label("Diabetes Complication Risk (12 mo)")
                .value(risk)
                .category(category)
                .federatedRound(47)
                .confidence(confidence)
                .calibration("Calibrated")
                .shapFactors(shap)
                .clinicalEvidence(evidence)
                .build();
    }

    // ── Readmission Risk ─────────────────────────────────────────────────

    @Override
    public Prediction predictReadmission(Patient patient, List<String> recentAlerts) {
        log.debug("StubAIPredictionService: computing readmission risk for patient {}", patient.getId());

        double risk = 8.0; // base

        // +3% per HIGH alert in last 30 days
        // In Phase 4 recentAlerts will always be empty; Phase 5 supplies real alerts
        risk += recentAlerts.size() * 3.0;

        risk = Math.min(risk, 100.0);
        risk = Math.round(risk * 10.0) / 10.0;

        String category = riskCategory(risk);

        // Confidence slightly lower for readmission model
        int confidence = computeConfidence(patient, List.of(), 84);

        // Determine calibration based on risk level
        String calibration = recentAlerts.isEmpty() ? "Calibrated" : "Recalibration Due";

        List<Prediction.ShapFactor> shap = buildReadmissionShapFactors(patient, recentAlerts);
        List<Prediction.ClinicalEvidence> evidence = readmissionClinicalEvidence();

        return Prediction.builder()
                .id("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patient.getId())
                .model("Readmit-30d-v1.8")
                .label("30-Day Readmission Risk")
                .value(risk)
                .category(category)
                .federatedRound(46)
                .confidence(confidence)
                .calibration(calibration)
                .shapFactors(shap)
                .clinicalEvidence(evidence)
                .build();
    }

    // ── SHAP Explanation ──────────────────────────────────────────────────

    @Override
    public List<Prediction.ShapFactor> explainPrediction(String predictionId, Patient patient,
                                                          List<LabResult> labs, VitalsSnapshot vitals) {
        // SHAP factors are already embedded in the prediction document at creation time.
        // This method exists as a hook for the TFF implementation to compute SHAP on-demand.
        // For the stub, the factors were already computed during prediction — return an empty list
        // (callers should read from the embedded shapFactors on the Prediction document).
        return List.of();
    }

    // ── SHAP factor builders ──────────────────────────────────────────────

    private List<Prediction.ShapFactor> buildCvdShapFactors(Patient patient, List<LabResult> labs,
                                                              VitalsSnapshot vitals, int age,
                                                              double hba1c, long htCount) {
        List<Prediction.ShapFactor> factors = new ArrayList<>();

        // HbA1c contribution — increases risk when elevated
        if (hba1c > 0) {
            double contrib = hba1c > 8.0 ? 5.0 + (hba1c - 8.0) * 0.5 : (hba1c - 5.7) * 0.8;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("HbA1c", contrib,
                    String.format("%.1f%%", hba1c), contrib > 0 ? "increases" : "decreases"));
        }

        // Blood pressure contribution
        if (vitals != null && vitals.getBloodPressure() != null && !vitals.getBloodPressure().equals("—")) {
            int systolic = parseSystolic(vitals.getBloodPressure());
            double contrib = systolic > 130 ? (systolic - 120) * 0.12 : -(120 - systolic) * 0.05;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Blood Pressure", contrib,
                    vitals.getBloodPressure() + " mmHg", contrib > 0 ? "increases" : "decreases"));
        }

        // Age contribution
        if (age > 50) {
            double contrib = Math.round((age - 50) * 0.8 * 10.0) / 10.0;
            factors.add(shapFactor("Age", contrib, age + " years", "increases"));
        } else if (age > 0) {
            factors.add(shapFactor("Age", 0.5, age + " years", "increases"));
        }

        // Total Cholesterol
        double cholesterol = extractNumericLab(labs, "Total Cholesterol");
        if (cholesterol > 0) {
            double contrib = cholesterol > 200 ? (cholesterol - 200) * 0.015 : -0.5;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Total Cholesterol", contrib,
                    String.format("%.0f mg/dL", cholesterol), contrib > 0 ? "increases" : "decreases"));
        }

        // Hypertension condition
        if (htCount > 0) {
            factors.add(shapFactor("Hypertension", 2.0, "Diagnosed", "increases"));
        }

        // Medication adherence (from patient adherence score)
        int adherence = patient.getAdherence();
        if (adherence > 0) {
            double contrib = -(adherence / 100.0) * 2.5;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Medication Adherence", contrib,
                    adherence + "% (30-day)", "decreases"));
        }

        return factors;
    }

    private List<Prediction.ShapFactor> buildDmShapFactors(Patient patient, List<LabResult> labs,
                                                             double hba1c, double glucose) {
        List<Prediction.ShapFactor> factors = new ArrayList<>();

        if (hba1c > 0) {
            double contrib = hba1c > 8.0 ? 4.0 + (hba1c - 8.0) : 2.0 * (hba1c - 5.7) / 3.0;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("HbA1c", contrib,
                    String.format("%.1f%%", hba1c), "increases"));
        }

        if (glucose > 0) {
            double contrib = glucose > 100.0 ? (glucose - 100.0) * 0.03 : -0.5;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Fasting Glucose", contrib,
                    String.format("%.0f mg/dL", glucose), contrib > 0 ? "increases" : "decreases"));
        }

        long diabetesCount = countCondition(patient, "diabetes");
        if (diabetesCount > 0) {
            factors.add(shapFactor("Diabetes Diagnosis", 3.0, "Diagnosed", "increases"));
        }

        int adherence = patient.getAdherence();
        if (adherence > 0) {
            double contrib = -(adherence / 100.0) * 1.5;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Medication Adherence", contrib,
                    adherence + "%", "decreases"));
        }

        return factors;
    }

    private List<Prediction.ShapFactor> buildReadmissionShapFactors(Patient patient,
                                                                      List<String> recentAlerts) {
        List<Prediction.ShapFactor> factors = new ArrayList<>();

        factors.add(shapFactor("Prior Hospitalizations", 3.0, "Baseline risk factor", "increases"));

        int alertCount = recentAlerts.size();
        if (alertCount > 0) {
            factors.add(shapFactor("Recent HIGH Alerts", alertCount * 3.0,
                    alertCount + " alert(s)", "increases"));
        }

        long conditionCount = patient.getConditions() != null ? patient.getConditions().size() : 0;
        if (conditionCount > 1) {
            double contrib = (conditionCount - 1) * 1.5;
            factors.add(shapFactor("Comorbidity Count", contrib,
                    conditionCount + " conditions", "increases"));
        }

        int adherence = patient.getAdherence();
        if (adherence > 0) {
            double contrib = -(adherence / 100.0) * 2.0;
            contrib = Math.round(contrib * 10.0) / 10.0;
            factors.add(shapFactor("Care Plan Adherence", contrib,
                    adherence + "%", "decreases"));
        }

        return factors;
    }

    // ── Clinical evidence builders ────────────────────────────────────────

    private List<Prediction.ClinicalEvidence> cvdClinicalEvidence() {
        return List.of(
            new Prediction.ClinicalEvidence(
                "ACC/AHA CVD Risk Calculator",
                "Validated against Pooled Cohort Equations (PCE). Age, cholesterol, and blood pressure are established risk contributors."),
            new Prediction.ClinicalEvidence(
                "ADA Standards of Care 2026",
                "HbA1c above 8% is associated with accelerated macrovascular risk."),
            new Prediction.ClinicalEvidence(
                "Institutional cardiology pathway",
                "Patients above 20% 10-year risk warrant intensified prevention review.")
        );
    }

    private List<Prediction.ClinicalEvidence> dmClinicalEvidence() {
        return List.of(
            new Prediction.ClinicalEvidence(
                "ADA Standards of Care 2026",
                "HbA1c and fasting glucose are primary indicators of glycaemic control and complication risk."),
            new Prediction.ClinicalEvidence(
                "UKPDS Risk Engine",
                "UK Prospective Diabetes Study equations for diabetes complication risk stratification.")
        );
    }

    private List<Prediction.ClinicalEvidence> readmissionClinicalEvidence() {
        return List.of(
            new Prediction.ClinicalEvidence(
                "CMS Hospital Readmissions Reduction Program",
                "Identifies patients at elevated 30-day readmission risk for proactive intervention."),
            new Prediction.ClinicalEvidence(
                "LACE Index",
                "Length of stay, acuity, comorbidities, and emergency department use contribute to readmission risk.")
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String riskCategory(double value) {
        if (value >= 20.0) return "High";
        if (value >= 10.0) return "Medium";
        return "Low";
    }

    private long countCondition(Patient patient, String keyword) {
        if (patient.getConditions() == null) return 0;
        return patient.getConditions().stream()
                .filter(c -> c != null && c.toLowerCase().contains(keyword.toLowerCase()))
                .count();
    }

    private int computeAge(String dob) {
        if (dob == null || dob.isBlank()) return 0;
        try {
            return Period.between(LocalDate.parse(dob), LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    private double extractNumericLab(List<LabResult> labs, String testName) {
        if (labs == null) return 0.0;
        return labs.stream()
                .filter(l -> testName.equalsIgnoreCase(l.getTest()))
                .mapToDouble(LabResult::getNumeric)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * Confidence is deterministic: derived from available data completeness,
     * not random. Higher data completeness → higher confidence.
     */
    private int computeConfidence(Patient patient, List<LabResult> labs, int base) {
        int bonus = 0;
        if (patient.isFhirConnected()) bonus += 2;
        if (labs != null && labs.size() >= 3) bonus += 2;
        if (patient.getConditions() != null && !patient.getConditions().isEmpty()) bonus += 1;
        return Math.min(base + bonus, 98);
    }

    private int parseSystolic(String bloodPressure) {
        try {
            return Integer.parseInt(bloodPressure.split("/")[0].trim());
        } catch (Exception e) {
            return 120;
        }
    }

    private Prediction.ShapFactor shapFactor(String feature, double contribution,
                                              String value, String direction) {
        return Prediction.ShapFactor.builder()
                .feature(feature)
                .contribution(Math.round(contribution * 10.0) / 10.0)
                .value(value)
                .direction(direction)
                .build();
    }
}

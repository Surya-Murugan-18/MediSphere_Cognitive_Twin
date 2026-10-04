package com.medisphere.service.phase4;

import com.medisphere.ai.stub.StubAIPredictionService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StubAIPredictionService.
 *
 * Verifies:
 *   - All 3 models produce non-null, valid predictions
 *   - Predictions are deterministic (same input → same output)
 *   - CVD formula: conditions, age, HbA1c drive the expected risk
 *   - DM formula: HbA1c and glucose drive the expected risk
 *   - Readmission formula: base + alert count
 *   - SHAP factors are non-empty and deterministic
 *   - No random values are generated
 */
@DisplayName("StubAIPredictionService — Deterministic AI Stub Tests")
class StubAIPredictionServiceTest {

    private StubAIPredictionService service;

    @BeforeEach
    void setUp() {
        service = new StubAIPredictionService();
    }

    // ── CVD Risk ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("CVD: high-risk patient (diabetes + hypertension + age 58 + HbA1c 8.2) → value > 20 and category High")
    void cvd_highRiskPatient_expectedCategory() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes", "Hypertension"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2));
        VitalsSnapshot vitals = buildVitals("142/91");

        Prediction result = service.predictCVDRisk(patient, labs, vitals);

        assertThat(result).isNotNull();
        assertThat(result.getValue()).isGreaterThan(20.0); // High risk threshold
        assertThat(result.getCategory()).isEqualTo("High");
        assertThat(result.getModel()).isEqualTo("CVD-Risk-v3.2");
        assertThat(result.getLabel()).isEqualTo("10-Year Cardiovascular Risk");
        assertThat(result.getFederatedRound()).isEqualTo(47);
        assertThat(result.getConfidence()).isGreaterThan(0).isLessThanOrEqualTo(100);
        assertThat(result.getCalibration()).isEqualTo("Calibrated");
        assertThat(result.getShapFactors()).isNotEmpty();
        assertThat(result.getClinicalEvidence()).isNotEmpty();
    }

    @Test
    @DisplayName("CVD: low-risk young patient with no conditions and normal labs → value < 20")
    void cvd_lowRiskPatient_lowValue() {
        Patient patient = buildPatient("P002", "1995-01-01",
                List.of(), 0);
        List<LabResult> labs = List.of(hba1cLab(5.2));
        VitalsSnapshot vitals = buildVitals("118/76");

        Prediction result = service.predictCVDRisk(patient, labs, vitals);

        assertThat(result).isNotNull();
        assertThat(result.getValue()).isLessThan(20.0);
        assertThat(result.getModel()).isEqualTo("CVD-Risk-v3.2");
    }

    @Test
    @DisplayName("CVD: same input twice → same risk value (determinism)")
    void cvd_sameInput_samePrediction() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes", "Hypertension"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2));
        VitalsSnapshot vitals = buildVitals("142/91");

        Prediction first  = service.predictCVDRisk(patient, labs, vitals);
        Prediction second = service.predictCVDRisk(patient, labs, vitals);

        // Risk value must be identical — no randomness
        assertThat(first.getValue()).isEqualTo(second.getValue());
        assertThat(first.getCategory()).isEqualTo(second.getCategory());
        assertThat(first.getConfidence()).isEqualTo(second.getConfidence());
    }

    @Test
    @DisplayName("CVD: +5% applied when HbA1c > 8.0")
    void cvd_hba1c_above_8_adds5Percent() {
        Patient base = buildPatient("P003", "1975-06-15", List.of(), 0);
        VitalsSnapshot vitals = buildVitals("120/80");

        Prediction withNormalHba1c = service.predictCVDRisk(
                base, List.of(hba1cLab(7.5)), vitals);
        Prediction withHighHba1c   = service.predictCVDRisk(
                base, List.of(hba1cLab(8.5)), vitals);

        // High HbA1c version should be at least 5% higher
        assertThat(withHighHba1c.getValue())
                .isGreaterThanOrEqualTo(withNormalHba1c.getValue() + 4.5);
    }

    @Test
    @DisplayName("CVD: risk is capped at 100%")
    void cvd_riskCappedAt100() {
        // Extreme patient: 80 years old, all conditions, very high HbA1c
        Patient patient = buildPatient("P004", "1946-01-01",
                List.of("Diabetes", "Hypertension", "Diabetes Type 2"), 50);
        List<LabResult> labs = List.of(hba1cLab(14.0));
        VitalsSnapshot vitals = buildVitals("180/110");

        Prediction result = service.predictCVDRisk(patient, labs, vitals);

        assertThat(result.getValue()).isLessThanOrEqualTo(100.0);
    }

    // ── Diabetes Complication Risk ────────────────────────────────────────

    @Test
    @DisplayName("DM: base risk is 10%; +4% for HbA1c > 8.0; +2% for glucose > 100")
    void dm_formulaMatchesDesignSpec() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of("Diabetes"), 0);
        List<LabResult> labs = List.of(hba1cLab(8.2), glucoseLab(126.0));

        Prediction result = service.predictDiabetesComplication(patient, labs);

        // base 10 + 4 (HbA1c>8) + 2 (glucose>100) = 16%
        assertThat(result.getValue()).isEqualTo(16.0);
        // 16% is in range [10, 20) → Medium category
        assertThat(result.getCategory()).isEqualTo("Medium");
        assertThat(result.getModel()).isEqualTo("DM-Complication-v2.4");
        assertThat(result.getLabel()).isEqualTo("Diabetes Complication Risk (12 mo)");
        assertThat(result.getShapFactors()).isNotEmpty();
    }

    @Test
    @DisplayName("DM: base risk only (no elevated HbA1c or glucose) → 10%")
    void dm_baseRiskOnly_10Percent() {
        Patient patient = buildPatient("P002", "1990-01-01", List.of(), 0);
        List<LabResult> labs = List.of(hba1cLab(5.5), glucoseLab(85.0));

        Prediction result = service.predictDiabetesComplication(patient, labs);

        assertThat(result.getValue()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("DM: same input twice → same value (determinism)")
    void dm_sameInput_samePrediction() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of("Diabetes"), 0);
        List<LabResult> labs = List.of(hba1cLab(8.2), glucoseLab(126.0));

        Prediction first  = service.predictDiabetesComplication(patient, labs);
        Prediction second = service.predictDiabetesComplication(patient, labs);

        assertThat(first.getValue()).isEqualTo(second.getValue());
    }

    // ── Readmission Risk ─────────────────────────────────────────────────

    @Test
    @DisplayName("Readmission: base risk is 8% with empty alert list (Phase 4 expected)")
    void readmission_noAlerts_8Percent() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of("Diabetes"), 0);

        Prediction result = service.predictReadmission(patient, List.of());

        assertThat(result.getValue()).isEqualTo(8.0);
        assertThat(result.getModel()).isEqualTo("Readmit-30d-v1.8");
        assertThat(result.getLabel()).isEqualTo("30-Day Readmission Risk");
        assertThat(result.getShapFactors()).isNotEmpty();
    }

    @Test
    @DisplayName("Readmission: +3% per HIGH alert entry")
    void readmission_withAlerts_addsPerAlert() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of(), 0);

        Prediction withTwo = service.predictReadmission(patient,
                List.of("A-001", "A-002"));

        // base 8 + 3*2 = 14%
        assertThat(withTwo.getValue()).isEqualTo(14.0);
    }

    @Test
    @DisplayName("Readmission: same input twice → same value (determinism)")
    void readmission_sameInput_samePrediction() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of(), 0);

        Prediction first  = service.predictReadmission(patient, List.of());
        Prediction second = service.predictReadmission(patient, List.of());

        assertThat(first.getValue()).isEqualTo(second.getValue());
    }

    // ── SHAP Factors ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CVD SHAP: factors list is non-empty and each entry has non-blank fields")
    void cvd_shapFactors_nonEmpty_validFields() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes", "Hypertension"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2));
        VitalsSnapshot vitals = buildVitals("142/91");

        Prediction result = service.predictCVDRisk(patient, labs, vitals);

        assertThat(result.getShapFactors()).isNotEmpty();
        result.getShapFactors().forEach(f -> {
            assertThat(f.getFeature()).isNotBlank();
            assertThat(f.getValue()).isNotBlank();
            assertThat(f.getDirection()).isIn("increases", "decreases");
        });
    }

    @Test
    @DisplayName("CVD SHAP: positive contributions have direction=increases, negative=decreases")
    void cvd_shapFactors_directionConsistent() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes", "Hypertension"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2));

        Prediction result = service.predictCVDRisk(patient, labs, buildVitals("142/91"));

        result.getShapFactors().forEach(f -> {
            if (f.getContribution() > 0) {
                assertThat(f.getDirection()).isEqualTo("increases");
            } else if (f.getContribution() < 0) {
                assertThat(f.getDirection()).isEqualTo("decreases");
            }
        });
    }

    @Test
    @DisplayName("CVD SHAP: same patient state → same SHAP contributions (determinism)")
    void cvd_shapFactors_deterministic() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2));
        VitalsSnapshot vitals = buildVitals("140/90");

        Prediction first  = service.predictCVDRisk(patient, labs, vitals);
        Prediction second = service.predictCVDRisk(patient, labs, vitals);

        assertThat(first.getShapFactors()).hasSize(second.getShapFactors().size());
        for (int i = 0; i < first.getShapFactors().size(); i++) {
            assertThat(first.getShapFactors().get(i).getContribution())
                    .isEqualTo(second.getShapFactors().get(i).getContribution());
        }
    }

    // ── Clinical Evidence ────────────────────────────────────────────────

    @Test
    @DisplayName("CVD: clinical evidence list is non-empty")
    void cvd_clinicalEvidence_nonEmpty() {
        Patient patient = buildPatient("P001", "1968-04-12", List.of("Diabetes"), 0);
        Prediction result = service.predictCVDRisk(patient, List.of(), buildVitals("120/80"));

        assertThat(result.getClinicalEvidence()).isNotEmpty();
        result.getClinicalEvidence().forEach(e -> {
            assertThat(e.getGuideline()).isNotBlank();
            assertThat(e.getDetail()).isNotBlank();
        });
    }

    // ── Output boundaries ────────────────────────────────────────────────

    @Test
    @DisplayName("All 3 models: values are between 0 and 100 inclusive")
    void allModels_valuesInValidRange() {
        Patient patient = buildPatient("P001", "1968-04-12",
                List.of("Diabetes", "Hypertension"), 78);
        List<LabResult> labs = List.of(hba1cLab(8.2), glucoseLab(130.0));
        VitalsSnapshot vitals = buildVitals("142/91");

        Prediction cvd  = service.predictCVDRisk(patient, labs, vitals);
        Prediction dm   = service.predictDiabetesComplication(patient, labs);
        Prediction read = service.predictReadmission(patient, List.of());

        for (Prediction p : List.of(cvd, dm, read)) {
            assertThat(p.getValue()).isBetween(0.0, 100.0);
            assertThat(p.getCategory()).isIn("High", "Medium", "Low");
            assertThat(p.getConfidence()).isBetween(0, 100);
            assertThat(p.getId()).isNotBlank();
            assertThat(p.getPatientId()).isEqualTo("P001");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(String id, String dob, List<String> conditions, int adherence) {
        return Patient.builder()
                .id(id)
                .name("Test Patient")
                .dob(dob)
                .conditions(new ArrayList<>(conditions))
                .riskLevel("Low")
                .status("Active")
                .providerId("PROV-001")
                .fhirConnected(true)
                .adherence(adherence)
                .wearableStatus("Online")
                .build();
    }

    private LabResult hba1cLab(double numeric) {
        return LabResult.builder()
                .id("LAB-TEST-HBA1C")
                .patientId("P001")
                .test("HbA1c")
                .loinc("4548-4")
                .numeric(numeric)
                .result(numeric + " %")
                .unit("%")
                .status(numeric > 5.7 ? "High" : "Normal")
                .category("Metabolic")
                .date("2026-09-15")
                .build();
    }

    private LabResult glucoseLab(double numeric) {
        return LabResult.builder()
                .id("LAB-TEST-GLUCOSE")
                .patientId("P001")
                .test("Fasting Glucose")
                .loinc("2345-7")
                .numeric(numeric)
                .result(numeric + " mg/dL")
                .unit("mg/dL")
                .status(numeric > 100 ? "High" : "Normal")
                .category("Metabolic")
                .date("2026-09-15")
                .build();
    }

    private VitalsSnapshot buildVitals(String bloodPressure) {
        return VitalsSnapshot.builder()
                .patientId("P001")
                .heartRate(85)
                .bloodPressure(bloodPressure)
                .spo2(98.0)
                .temperature(36.8)
                .respiratoryRate(16)
                .build();
    }
}

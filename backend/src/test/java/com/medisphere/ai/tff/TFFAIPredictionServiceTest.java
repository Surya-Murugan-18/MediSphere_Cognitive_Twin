package com.medisphere.ai.tff;

import com.medisphere.ai.ml.MLServiceClient;
import com.medisphere.ai.ml.dto.*;
import com.medisphere.domain.*;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.CarePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TFFAIPredictionService.
 *
 * Verifies:
 *   B.1  CVD feature extraction
 *   B.2  Diabetes feature extraction
 *   B.3  Readmission feature extraction
 *   B.4  ML response → Prediction domain mapping
 *   B.5  SHAP mapping — genuine values from ML response
 *   B.6  Imputed field propagation
 *   B.7  MLServiceUnavailableException thrown when client returns null
 *   B.8  No unavailable features are sent (smoke/BMI etc.)
 *   B.9  probability_score [0,1] → value [0,100]
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TFFAIPredictionService — Unit Tests")
class TFFAIPredictionServiceTest {

    @Mock private MLServiceClient mlClient;
    @Mock private CarePlanRepository carePlanRepository;
    @Mock private AlertRepository alertRepository;

    private TFFAIPredictionService service;

    @BeforeEach
    void setUp() {
        service = new TFFAIPredictionService(mlClient, carePlanRepository, alertRepository);
        // Default: no active care plan
        when(carePlanRepository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc(
                any(), eq(CarePlanStatus.ACTIVE))).thenReturn(Optional.empty());
        // Default: 0 HIGH and 0 MEDIUM alerts in last 30 days (per design: fallback = 0)
        lenient().when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), eq("HIGH"),   any(Instant.class))).thenReturn(0L);
        lenient().when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), eq("MEDIUM"), any(Instant.class))).thenReturn(0L);
    }

    // ── B.1: CVD feature extraction ───────────────────────────────────────

    @Nested
    @DisplayName("CVD feature extraction")
    class CvdFeatureExtraction {

        @Test
        @DisplayName("CVD: correct features extracted from patient, labs, vitals")
        void cvd_correctFeaturesExtracted() {
            Patient patient = buildPatient("P001", "Male", "1968-04-12",
                    List.of("Type 2 Diabetes", "Hypertension"));
            VitalsSnapshot vitals = buildVitals("135/85", 72);
            List<LabResult> labs = List.of(
                    buildLab("Total Cholesterol", 210.0, "High"),
                    buildLab("Fasting Glucose", 110.0, "High"));

            CvdRiskResponse mockResp = buildCvdResponse("P001", 0.24);
            when(mlClient.predictCvd(any())).thenReturn(mockResp);

            service.predictCVDRisk(patient, labs, vitals);

            ArgumentCaptor<CvdRiskRequest> captor = ArgumentCaptor.forClass(CvdRiskRequest.class);
            verify(mlClient).predictCvd(captor.capture());
            CvdRiskRequest req = captor.getValue();

            assertThat(req.patientId()).isEqualTo("P001");
            assertThat(req.male()).isEqualTo(1);                // Male → 1
            assertThat(req.age()).isBetween(55, 60);            // Born 1968, ~58 years in 2026
            assertThat(req.sysBp()).isEqualTo(135.0);
            assertThat(req.diaBp()).isEqualTo(85.0);
            assertThat(req.heartRate()).isEqualTo(72);
            assertThat(req.totChol()).isEqualTo(210.0);
            assertThat(req.glucose()).isEqualTo(110.0);
            assertThat(req.prevalentHyp()).isEqualTo(1);        // Hypertension in conditions
            assertThat(req.diabetes()).isEqualTo(1);            // Diabetes in conditions
            assertThat(req.prevalentStroke()).isEqualTo(0);     // No stroke

            // NO smoking, BMI, education or pulse_pressure fields exist in the DTO
            // (compile-time guarantee — CvdRiskRequest only has the 12 allowed fields)
        }

        @Test
        @DisplayName("CVD: missing vitals → null sys_bp, dia_bp, heart_rate (pipeline imputes)")
        void cvd_missingVitals_nullFields() {
            Patient patient = buildPatient("P001", "Female", "1970-01-01", List.of());
            when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.15));

            service.predictCVDRisk(patient, List.of(), null);

            ArgumentCaptor<CvdRiskRequest> captor = ArgumentCaptor.forClass(CvdRiskRequest.class);
            verify(mlClient).predictCvd(captor.capture());
            CvdRiskRequest req = captor.getValue();

            assertThat(req.sysBp()).isNull();
            assertThat(req.diaBp()).isNull();
            assertThat(req.heartRate()).isNull();
            assertThat(req.male()).isEqualTo(0);    // Female → 0
        }

        @Test
        @DisplayName("CVD: bloodPressure '—' → null systolic and diastolic")
        void cvd_dashBP_nullSystolicDiastolic() {
            Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
            VitalsSnapshot vitals = buildVitals("—", 0);
            when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.15));

            service.predictCVDRisk(patient, List.of(), vitals);

            ArgumentCaptor<CvdRiskRequest> captor = ArgumentCaptor.forClass(CvdRiskRequest.class);
            verify(mlClient).predictCvd(captor.capture());
            assertThat(captor.getValue().sysBp()).isNull();
            assertThat(captor.getValue().diaBp()).isNull();
            assertThat(captor.getValue().heartRate()).isNull(); // 0 → null
        }

        @Test
        @DisplayName("CVD: Total Cholesterol lab numeric 0.0 → null (not a real value)")
        void cvd_zeroCholesterol_sendsNull() {
            Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
            List<LabResult> labs = List.of(buildLab("Total Cholesterol", 0.0, "Normal"));
            when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.15));

            service.predictCVDRisk(patient, labs, null);

            ArgumentCaptor<CvdRiskRequest> captor = ArgumentCaptor.forClass(CvdRiskRequest.class);
            verify(mlClient).predictCvd(captor.capture());
            assertThat(captor.getValue().totChol()).isNull();
        }
    }

    // ── B.2: Diabetes feature extraction ─────────────────────────────────

    @Nested
    @DisplayName("Diabetes feature extraction")
    class DiabetesFeatureExtraction {

        @Test
        @DisplayName("Diabetes: correct features extracted")
        void diabetes_correctFeaturesExtracted() {
            Patient patient = buildPatient("P002", "Female", "1975-06-15",
                    List.of("Hypertension", "Stroke"));
            List<LabResult> labs = List.of(
                    buildLab("Total Cholesterol", 240.0, "High"));

            DiabetesRiskResponse mockResp = buildDiabetesResponse("P002", 0.35);
            when(mlClient.predictDiabetes(any())).thenReturn(mockResp);

            service.predictDiabetesComplication(patient, labs);

            ArgumentCaptor<DiabetesRiskRequest> captor = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(captor.capture());
            DiabetesRiskRequest req = captor.getValue();

            assertThat(req.patientId()).isEqualTo("P002");
            assertThat(req.sexMale()).isEqualTo(0);              // Female → 0
            assertThat(req.ageYears()).isBetween(49, 53);        // Born 1975
            assertThat(req.highBp()).isEqualTo(1);               // Hypertension
            assertThat(req.highChol()).isEqualTo(1);             // Cholesterol status=High
            assertThat(req.cholCheck()).isEqualTo(1);            // Cholesterol lab exists
            assertThat(req.stroke()).isEqualTo(1);               // Stroke in conditions
            assertThat(req.heartDiseaseOrAttack()).isEqualTo(0); // No heart conditions
        }

        @Test
        @DisplayName("Diabetes: no cholesterol lab → highChol null, cholCheck 0")
        void diabetes_noChol_nullHighChol() {
            Patient patient = buildPatient("P002", "Male", "1975-06-15", List.of());
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P002", 0.2));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<DiabetesRiskRequest> captor = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(captor.capture());
            assertThat(captor.getValue().highChol()).isNull();
            assertThat(captor.getValue().cholCheck()).isEqualTo(0);
        }

        @Test
        @DisplayName("Diabetes: heart disease condition detected correctly")
        void diabetes_heartDiseaseFlag() {
            Patient patient = buildPatient("P003", "Male", "1960-01-01",
                    List.of("Coronary artery disease"));
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P003", 0.2));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<DiabetesRiskRequest> captor = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(captor.capture());
            assertThat(captor.getValue().heartDiseaseOrAttack()).isEqualTo(1);
        }
    }

    // ── B.2b: PhysHlth proxy (per metadata.json + ML_IMPLEMENTATION_DESIGN §B.2.2) ──

    @Nested
    @DisplayName("PhysHlth proxy — HIGH+MEDIUM alert count last 30 days")
    class PhysHlthProxy {

        @Test
        @DisplayName("0 HIGH and 0 MEDIUM alerts → phys_hlth_alert_count_30d = 0 (not null)")
        void phys_hlth_noAlerts_sendsZero() {
            // Default setUp already stubs 0 for both severities
            Patient patient = buildPatient("P002", "Female", "1975-06-15", List.of());
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P002", 0.2));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<DiabetesRiskRequest> cap = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(cap.capture());
            // Must be 0, NOT null — the design says "No alerts → 0 (semantically correct)"
            assertThat(cap.getValue().physHlthAlertCount30d()).isEqualTo(0);
        }

        @Test
        @DisplayName("3 HIGH + 2 MEDIUM alerts → phys_hlth_alert_count_30d = 5")
        void phys_hlth_highPlusMedium_summed() {
            Patient patient = buildPatient("P003", "Male", "1968-04-12", List.of());

            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P003"), eq("HIGH"),   any(Instant.class))).thenReturn(3L);
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P003"), eq("MEDIUM"), any(Instant.class))).thenReturn(2L);
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P003", 0.2));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<DiabetesRiskRequest> cap = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(cap.capture());
            assertThat(cap.getValue().physHlthAlertCount30d()).isEqualTo(5);
        }

        @Test
        @DisplayName("35 total alerts → capped at 30 (FastAPI schema le=30)")
        void phys_hlth_cappedAt30() {
            Patient patient = buildPatient("P004", "Male", "1968-04-12", List.of());

            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P004"), eq("HIGH"),   any(Instant.class))).thenReturn(20L);
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P004"), eq("MEDIUM"), any(Instant.class))).thenReturn(15L);
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P004", 0.3));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<DiabetesRiskRequest> cap = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(cap.capture());
            assertThat(cap.getValue().physHlthAlertCount30d()).isEqualTo(30);
        }

        @Test
        @DisplayName("LOW alerts are NOT counted — only HIGH and MEDIUM")
        void phys_hlth_lowAlertsNotCounted() {
            Patient patient = buildPatient("P005", "Male", "1968-04-12", List.of());

            // HIGH=0, MEDIUM=0, LOW would be 10 but must not be queried
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P005"), eq("HIGH"),   any(Instant.class))).thenReturn(0L);
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P005"), eq("MEDIUM"), any(Instant.class))).thenReturn(0L);
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P005", 0.1));

            service.predictDiabetesComplication(patient, List.of());

            // Verify LOW is never queried
            verify(alertRepository, never()).countByPatientIdAndSeverityAndDetectedAtAfter(
                    any(), eq("LOW"), any(Instant.class));

            ArgumentCaptor<DiabetesRiskRequest> cap = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(cap.capture());
            assertThat(cap.getValue().physHlthAlertCount30d()).isEqualTo(0);
        }

        @Test
        @DisplayName("AlertRepository throws → graceful degradation, sends 0 (not null)")
        void phys_hlth_repositoryThrows_gracefulDegradation() {
            Patient patient = buildPatient("P006", "Male", "1968-04-12", List.of());

            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P006"), any(), any(Instant.class)))
                    .thenThrow(new RuntimeException("DB timeout"));
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P006", 0.1));

            // Must not throw
            assertThatCode(() ->
                    service.predictDiabetesComplication(patient, List.of()))
                    .doesNotThrowAnyException();

            ArgumentCaptor<DiabetesRiskRequest> cap = ArgumentCaptor.forClass(DiabetesRiskRequest.class);
            verify(mlClient).predictDiabetes(cap.capture());
            // Falls back to 0, not null — 0 is the design-specified fallback value
            assertThat(cap.getValue().physHlthAlertCount30d()).isEqualTo(0);
        }

        @Test
        @DisplayName("AlertRepository is queried with cutoff ~30 days ago")
        void phys_hlth_cutoffIs30DaysAgo() {
            Patient patient = buildPatient("P007", "Male", "1968-04-12", List.of());
            when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P007", 0.1));

            service.predictDiabetesComplication(patient, List.of());

            ArgumentCaptor<Instant> cutoffCap = ArgumentCaptor.forClass(Instant.class);
            // Capture for HIGH query (MEDIUM uses same cutoff but captured separately)
            verify(alertRepository, atLeastOnce()).countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P007"), any(), cutoffCap.capture());

            Instant captured = cutoffCap.getAllValues().get(0);
            Instant expectedMin = Instant.now().minus(31, ChronoUnit.DAYS);
            Instant expectedMax = Instant.now().minus(29, ChronoUnit.DAYS);
            assertThat(captured).isBetween(expectedMin, expectedMax);
        }

        @Test
        @DisplayName("loadAlertCount30d: exactly 30 HIGH + 0 MEDIUM → returns 30 (at cap)")
        void loadAlertCount30d_exactlyAtCap() {
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P_CAP"), eq("HIGH"),   any())).thenReturn(30L);
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P_CAP"), eq("MEDIUM"), any())).thenReturn(0L);

            assertThat(service.loadAlertCount30d("P_CAP")).isEqualTo(30);
        }

        @Test
        @DisplayName("loadAlertCount30d: 0 HIGH + 0 MEDIUM → returns 0")
        void loadAlertCount30d_noAlerts_zero() {
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P_ZERO"), eq("HIGH"),   any())).thenReturn(0L);
            when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                    eq("P_ZERO"), eq("MEDIUM"), any())).thenReturn(0L);

            assertThat(service.loadAlertCount30d("P_ZERO")).isEqualTo(0);
        }
    }

    // ── B.3: Readmission feature extraction ──────────────────────────────

    @Nested
    @DisplayName("Readmission feature extraction")
    class ReadmissionFeatureExtraction {

        @Test
        @DisplayName("Readmission: diag groups mapped via ConditionMapper")
        void readmission_diagGroupsMapped() {
            Patient patient = buildPatient("P003", "Male", "1955-03-01",
                    List.of("Hypertension", "Type 2 Diabetes", "Asthma"));

            ReadmissionResponse mockResp = buildReadmissionResponse("P003", 0.22);
            when(mlClient.predictReadmission(any())).thenReturn(mockResp);

            service.predictReadmission(patient, List.of("HIGH", "HIGH", "HIGH")); // 3 HIGH alerts

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            ReadmissionRequest req = captor.getValue();

            assertThat(req.patientId()).isEqualTo("P003");
            assertThat(req.genderMale()).isEqualTo(1);
            assertThat(req.numberDiagnoses()).isEqualTo(3);
            assertThat(req.diagGroupPrimary()).isEqualTo("Circulatory");       // Hypertension
            assertThat(req.diagGroupSecondary()).isEqualTo("Metabolic/Endocrine"); // Diabetes
            assertThat(req.diagGroupTertiary()).isEqualTo("Respiratory");      // Asthma
            assertThat(req.numberHighAlertsPriorYear()).isEqualTo(3);
        }

        @Test
        @DisplayName("Readmission: empty alerts → numberHighAlerts = 0")
        void readmission_emptyAlerts_zero() {
            Patient patient = buildPatient("P003", "Female", "1960-01-01",
                    List.of("Hypertension"));
            when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P003", 0.18));

            service.predictReadmission(patient, List.of());

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            assertThat(captor.getValue().numberHighAlertsPriorYear()).isEqualTo(0);
        }

        @Test
        @DisplayName("Readmission: null alerts → numberHighAlerts = 0")
        void readmission_nullAlerts_zero() {
            Patient patient = buildPatient("P003", "Male", "1960-01-01", List.of());
            when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P003", 0.1));

            service.predictReadmission(patient, null);

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            assertThat(captor.getValue().numberHighAlertsPriorYear()).isEqualTo(0);
        }

        @Test
        @DisplayName("Readmission: no conditions → 0 diagnoses, all groups Unknown")
        void readmission_noConditions_unknownGroups() {
            Patient patient = buildPatient("P004", "Male", "1960-01-01", null);
            when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P004", 0.1));

            service.predictReadmission(patient, List.of());

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            ReadmissionRequest req = captor.getValue();

            assertThat(req.numberDiagnoses()).isEqualTo(0);
            assertThat(req.diagGroupPrimary()).isEqualTo("Unknown");
            assertThat(req.diagGroupSecondary()).isEqualTo("Unknown");
            assertThat(req.diagGroupTertiary()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Readmission: diabetic patient without care plan → diabetesMed = 1")
        void readmission_diabeticNoCareplan_diabetesMed1() {
            Patient patient = buildPatient("P005", "Male", "1960-01-01",
                    List.of("Diabetes mellitus"));

            when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P005", 0.2));

            service.predictReadmission(patient, List.of());

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            assertThat(captor.getValue().diabetesMed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Readmission: non-diabetic patient → diabetesMed = 0")
        void readmission_nonDiabetic_diabetesMed0() {
            Patient patient = buildPatient("P006", "Female", "1970-01-01",
                    List.of("Hypertension"));
            when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P006", 0.1));

            service.predictReadmission(patient, List.of());

            ArgumentCaptor<ReadmissionRequest> captor = ArgumentCaptor.forClass(ReadmissionRequest.class);
            verify(mlClient).predictReadmission(captor.capture());
            assertThat(captor.getValue().diabetesMed()).isEqualTo(0);
        }
    }

    // ── B.4: ML response → Prediction mapping ────────────────────────────

    @Test
    @DisplayName("predictCVDRisk: probability 0.24 → Prediction.value 24.0%")
    void cvd_probabilityMappedToPercent() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.24));

        Prediction result = service.predictCVDRisk(patient, List.of(), null);

        assertThat(result.getValue()).isEqualTo(24.0);
    }

    @Test
    @DisplayName("predictCVDRisk: model name is CVD-Risk-v3.2")
    void cvd_modelNameCorrect() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.18));

        Prediction result = service.predictCVDRisk(patient, List.of(), null);

        assertThat(result.getModel()).isEqualTo("CVD-Risk-v3.2");
        assertThat(result.getLabel()).isEqualTo("10-Year Cardiovascular Risk");
    }

    @Test
    @DisplayName("predictDiabetesComplication: model name is DM-Complication-v2.4")
    void diabetes_modelNameCorrect() {
        Patient patient = buildPatient("P002", "Male", "1975-06-15", List.of());
        when(mlClient.predictDiabetes(any())).thenReturn(buildDiabetesResponse("P002", 0.31));

        Prediction result = service.predictDiabetesComplication(patient, List.of());

        assertThat(result.getModel()).isEqualTo("DM-Complication-v2.4");
        assertThat(result.getLabel()).isEqualTo("Diabetes Complication Risk (12 mo)");
    }

    @Test
    @DisplayName("predictReadmission: model name is Readmit-30d-v1.8")
    void readmission_modelNameCorrect() {
        Patient patient = buildPatient("P003", "Male", "1960-01-01", List.of());
        when(mlClient.predictReadmission(any())).thenReturn(buildReadmissionResponse("P003", 0.22));

        Prediction result = service.predictReadmission(patient, List.of());

        assertThat(result.getModel()).isEqualTo("Readmit-30d-v1.8");
        assertThat(result.getLabel()).isEqualTo("30-Day Readmission Risk");
    }

    @Test
    @DisplayName("risk category 'High' from ML is preserved as 'High'")
    void cvd_riskCategoryHigh_preserved() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        CvdRiskResponse resp = new CvdRiskResponse("P001", "CVD-10Y", "1.0.0",
                0.30, "High", 1, 0.2157, List.of(), List.of(), "2026-10-06T10:00:00Z");
        when(mlClient.predictCvd(any())).thenReturn(resp);

        Prediction result = service.predictCVDRisk(patient, List.of(), null);
        assertThat(result.getCategory()).isEqualTo("High");
    }

    @Test
    @DisplayName("prediction id is generated and prefixed PR-")
    void prediction_idGenerated() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.18));

        Prediction result = service.predictCVDRisk(patient, List.of(), null);

        assertThat(result.getId()).startsWith("PR-");
        assertThat(result.getId()).hasSize(11); // "PR-" + 8 chars
    }

    // ── B.5: SHAP mapping ─────────────────────────────────────────────────

    @Test
    @DisplayName("SHAP: genuine ML factors are mapped 1:1 to Prediction.ShapFactor")
    void cvd_shapFactors_mappedFromMlResponse() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        CvdRiskResponse resp = new CvdRiskResponse(
                "P001", "CVD-10Y", "1.0.0", 0.24, "High", 1, 0.2157,
                List.of(
                    new MlShapFactor("age",   "55",     0.085, "increases"),
                    new MlShapFactor("sysBP", "135.0",  0.042, "increases"),
                    new MlShapFactor("glucose","110.0", 0.031, "increases"),
                    new MlShapFactor("BPMeds","0.0",   -0.011, "decreases"),
                    new MlShapFactor("totChol","210.0", 0.028, "increases")
                ),
                List.of(),
                "2026-10-06T10:00:00Z");

        when(mlClient.predictCvd(any())).thenReturn(resp);

        Prediction result = service.predictCVDRisk(patient, List.of(), null);

        assertThat(result.getShapFactors()).hasSize(5);
        Prediction.ShapFactor first = result.getShapFactors().get(0);
        assertThat(first.getFeature()).isEqualTo("age");
        assertThat(first.getValue()).isEqualTo("55");
        assertThat(first.getContribution()).isEqualTo(0.085);
        assertThat(first.getDirection()).isEqualTo("increases");

        Prediction.ShapFactor fourth = result.getShapFactors().get(3);
        assertThat(fourth.getDirection()).isEqualTo("decreases");
    }

    @Test
    @DisplayName("SHAP: empty ML factors → empty Prediction.shapFactors")
    void cvd_emptyShap_emptyFactors() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(buildCvdResponse("P001", 0.18));

        Prediction result = service.predictCVDRisk(patient, List.of(), null);
        assertThat(result.getShapFactors()).isEmpty();
    }

    // ── B.6: Imputed fields propagated to clinical evidence ───────────────

    @Test
    @DisplayName("Imputed fields appear in clinical evidence note")
    void cvd_imputedFields_inClinicalEvidence() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        CvdRiskResponse resp = new CvdRiskResponse(
                "P001", "CVD-10Y", "1.0.0", 0.18, "Low", 0, 0.2157,
                List.of(),
                List.of("glucose", "heartRate"),
                "2026-10-06T10:00:00Z");
        when(mlClient.predictCvd(any())).thenReturn(resp);

        Prediction result = service.predictCVDRisk(patient, List.of(), null);

        assertThat(result.getClinicalEvidence()).isNotEmpty();
        boolean hasImputationNote = result.getClinicalEvidence().stream()
                .anyMatch(e -> e.getDetail() != null
                        && e.getDetail().contains("glucose")
                        && e.getDetail().contains("heartRate"));
        assertThat(hasImputationNote).isTrue();
    }

    // ── B.7: MLServiceUnavailableException on null response ───────────────

    @Test
    @DisplayName("throws MLServiceUnavailableException when CVD client returns null")
    void cvd_clientReturnsNull_throwsException() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(null);

        assertThatThrownBy(() ->
                service.predictCVDRisk(patient, List.of(), null))
                .isInstanceOf(MLServiceUnavailableException.class)
                .hasMessageContaining("CVD-Risk");
    }

    @Test
    @DisplayName("throws MLServiceUnavailableException when Diabetes client returns null")
    void diabetes_clientReturnsNull_throwsException() {
        Patient patient = buildPatient("P002", "Male", "1975-06-15", List.of());
        when(mlClient.predictDiabetes(any())).thenReturn(null);

        assertThatThrownBy(() ->
                service.predictDiabetesComplication(patient, List.of()))
                .isInstanceOf(MLServiceUnavailableException.class)
                .hasMessageContaining("Diabetes-Risk");
    }

    @Test
    @DisplayName("throws MLServiceUnavailableException when Readmission client returns null")
    void readmission_clientReturnsNull_throwsException() {
        Patient patient = buildPatient("P003", "Male", "1960-01-01", List.of());
        when(mlClient.predictReadmission(any())).thenReturn(null);

        assertThatThrownBy(() ->
                service.predictReadmission(patient, List.of()))
                .isInstanceOf(MLServiceUnavailableException.class)
                .hasMessageContaining("Readmission-30D");
    }

    // ── B.9: probability → percent conversion ─────────────────────────────

    @Test
    @DisplayName("probability 0.0 → value 0.0%, probability 1.0 → value 100.0%")
    void probabilityBoundaries() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());

        when(mlClient.predictCvd(any())).thenReturn(
                new CvdRiskResponse("P001","CVD-10Y","1.0.0",0.0,"Low",0,0.2157,
                        List.of(),List.of(),"2026-10-06T10:00:00Z"));
        Prediction low = service.predictCVDRisk(patient, List.of(), null);
        assertThat(low.getValue()).isEqualTo(0.0);

        when(mlClient.predictCvd(any())).thenReturn(
                new CvdRiskResponse("P001","CVD-10Y","1.0.0",1.0,"High",1,0.2157,
                        List.of(),List.of(),"2026-10-06T10:00:00Z"));
        Prediction high = service.predictCVDRisk(patient, List.of(), null);
        assertThat(high.getValue()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("probability 0.1234 → value 12.34%")
    void probabilityRoundedCorrectly() {
        Patient patient = buildPatient("P001", "Male", "1968-04-12", List.of());
        when(mlClient.predictCvd(any())).thenReturn(
                new CvdRiskResponse("P001","CVD-10Y","1.0.0",0.1234,"Low",0,0.2157,
                        List.of(),List.of(),"2026-10-06T10:00:00Z"));

        Prediction result = service.predictCVDRisk(patient, List.of(), null);
        assertThat(result.getValue()).isEqualTo(12.34);
    }

    // ── Static helper method unit tests ───────────────────────────────────

    @Test
    @DisplayName("genderToMale: 'Male' → 1, 'Female' → 0, null → null, blank → null")
    void genderToMale() {
        assertThat(TFFAIPredictionService.genderToMale("Male")).isEqualTo(1);
        assertThat(TFFAIPredictionService.genderToMale("male")).isEqualTo(1);
        assertThat(TFFAIPredictionService.genderToMale("MALE")).isEqualTo(1);
        assertThat(TFFAIPredictionService.genderToMale("Female")).isEqualTo(0);
        assertThat(TFFAIPredictionService.genderToMale("Other")).isEqualTo(0);
        assertThat(TFFAIPredictionService.genderToMale(null)).isNull();
        assertThat(TFFAIPredictionService.genderToMale("")).isNull();
        assertThat(TFFAIPredictionService.genderToMale("   ")).isNull();
    }

    @Test
    @DisplayName("computeAge: known dob produces correct age")
    void computeAge() {
        // Born 1968-04-12 → ~58 years in 2026
        assertThat(TFFAIPredictionService.computeAge("1968-04-12")).isBetween(55, 62);
        assertThat(TFFAIPredictionService.computeAge(null)).isNull();
        assertThat(TFFAIPredictionService.computeAge("")).isNull();
        assertThat(TFFAIPredictionService.computeAge("not-a-date")).isNull();
    }

    @Test
    @DisplayName("extractSystolic: '135/85' → 135.0")
    void extractSystolic() {
        VitalsSnapshot v = buildVitals("135/85", 72);
        assertThat(TFFAIPredictionService.extractSystolic(v)).isEqualTo(135.0);
        assertThat(TFFAIPredictionService.extractSystolic(null)).isNull();
        assertThat(TFFAIPredictionService.extractSystolic(buildVitals("—", 0))).isNull();
    }

    @Test
    @DisplayName("extractDiastolic: '135/85' → 85.0")
    void extractDiastolic() {
        VitalsSnapshot v = buildVitals("135/85", 72);
        assertThat(TFFAIPredictionService.extractDiastolic(v)).isEqualTo(85.0);
        assertThat(TFFAIPredictionService.extractDiastolic(null)).isNull();
    }

    @Test
    @DisplayName("extractHeartRate: 0 → null (not yet measured)")
    void extractHeartRate() {
        assertThat(TFFAIPredictionService.extractHeartRate(buildVitals("120/80", 72))).isEqualTo(72);
        assertThat(TFFAIPredictionService.extractHeartRate(buildVitals("120/80", 0))).isNull();
        assertThat(TFFAIPredictionService.extractHeartRate(null)).isNull();
    }

    @Test
    @DisplayName("conditionFlag: finds keyword case-insensitively")
    void conditionFlag() {
        Patient p = buildPatient("P1", "Male", "1970-01-01", List.of("Type 2 Diabetes", "Hypertension"));
        assertThat(TFFAIPredictionService.conditionFlag(p, "diabetes")).isEqualTo(1);
        assertThat(TFFAIPredictionService.conditionFlag(p, "stroke")).isEqualTo(0);

        Patient noConditions = buildPatient("P2", "Male", "1970-01-01", null);
        assertThat(TFFAIPredictionService.conditionFlag(noConditions, "diabetes")).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(String id, String gender, String dob, List<String> conditions) {
        return Patient.builder()
                .id(id).name("Test Patient").dob(dob).gender(gender)
                .conditions(conditions != null ? new ArrayList<>(conditions) : null)
                .riskLevel("Low").status("Active").providerId("PROV-001")
                .fhirConnected(false).adherence(0).wearableStatus("Offline")
                .build();
    }

    private VitalsSnapshot buildVitals(String bp, int hr) {
        return VitalsSnapshot.builder()
                .patientId("P001").bloodPressure(bp).heartRate(hr)
                .spo2(98.0).temperature(36.6).respiratoryRate(16).build();
    }

    private LabResult buildLab(String test, double numeric, String status) {
        return LabResult.builder()
                .id("LAB-001").patientId("P001").test(test)
                .numeric(numeric).result(numeric + "").status(status)
                .category("Lipids").date("2026-09-01").build();
    }

    private CvdRiskResponse buildCvdResponse(String patientId, double prob) {
        return new CvdRiskResponse(patientId, "CVD-10Y", "1.0.0", prob,
                prob >= 0.20 ? "High" : prob >= 0.10 ? "Medium" : "Low",
                prob >= 0.2157 ? 1 : 0, 0.2157, List.of(), List.of(),
                "2026-10-06T10:00:00Z");
    }

    private DiabetesRiskResponse buildDiabetesResponse(String patientId, double prob) {
        return new DiabetesRiskResponse(patientId, "diabetes-risk", "1.0.0", prob,
                prob >= 0.40 ? "High" : "Low",
                prob >= 0.40 ? 1 : 0, 0.40, List.of(), List.of(),
                "2026-10-06T10:00:00Z");
    }

    private ReadmissionResponse buildReadmissionResponse(String patientId, double prob) {
        return new ReadmissionResponse(patientId, "readmission-30d", "1.0.0", prob,
                prob >= 0.30 ? "High" : prob >= 0.15 ? "Medium" : "Low",
                prob >= 0.30 ? 1 : 0, 0.30, List.of(), List.of(),
                "early_hospitalization", "2026-10-06T10:00:00Z");
    }
}

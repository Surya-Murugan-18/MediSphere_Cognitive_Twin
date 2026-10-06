package com.medisphere.service.phase4;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.HealthTwin;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.domain.VitalsSnapshot;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.PredictionResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.LabResultRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.PredictionRepository;
import com.medisphere.repository.VitalsSnapshotRepository;
import com.medisphere.service.PredictionService;
import com.medisphere.service.TwinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PredictionService Unit Tests")
class PredictionServiceTest {

    @Mock private AIPredictionService aiPredictionService;
    @Mock private PredictionRepository predictionRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private VitalsSnapshotRepository vitalsSnapshotRepository;
    @Mock private TwinService twinService;
    @Mock private AuditService auditService;
    @Mock private AlertRepository alertRepository;

    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        // Default: AlertRepository returns 0 HIGH alerts so existing tests are unaffected
        lenient().when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), any(), any())).thenReturn(0L);
        predictionService = new PredictionService(
                aiPredictionService, predictionRepository,
                patientRepository, labResultRepository,
                vitalsSnapshotRepository, twinService, auditService, alertRepository);
    }

    // ── runPredictionsSync ────────────────────────────────────────────────

    @Test
    @DisplayName("runPredictionsSync(ALL): calls all 3 models, persists 3 predictions")
    void runPredictionsSync_all_callsAllThreeModels() {
        Patient patient = buildPatient("P001");
        // lenient: findById called by runPredictionsSync AND by updatePatientRiskLevel internally
        lenient().when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001")).thenReturn(List.of());
        when(vitalsSnapshotRepository.findByPatientId("P001")).thenReturn(Optional.empty());

        Prediction cvd  = buildPrediction("PR-CVD",  "CVD-Risk-v3.2",       "P001", 24.3, "High");
        Prediction dm   = buildPrediction("PR-DM",   "DM-Complication-v2.4","P001", 16.0, "Medium");
        Prediction read = buildPrediction("PR-READ", "Readmit-30d-v1.8",    "P001", 8.0,  "Low");

        when(aiPredictionService.predictCVDRisk(any(), any(), any())).thenReturn(cvd);
        when(aiPredictionService.predictDiabetesComplication(any(), any())).thenReturn(dm);
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(read);
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // For twin update
        when(twinService.getTwinByPatientId("P001"))
                .thenReturn(HealthTwin.builder().id("HT-001").build());
        when(twinService.addTimelineEvent(any(), any(), any(), any()))
                .thenReturn(HealthTwin.builder().id("HT-001").build());

        List<PredictionResponse> results =
                predictionService.runPredictionsSync("P001", "ALL", "PROV-001");

        assertThat(results).hasSize(3);
        verify(aiPredictionService, times(1)).predictCVDRisk(any(), any(), any());
        verify(aiPredictionService, times(1)).predictDiabetesComplication(any(), any());
        verify(aiPredictionService, times(1)).predictReadmission(any(), any());
        verify(predictionRepository, times(3)).save(any(Prediction.class));
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("runPredictionsSync(CVD only): only CVD model called, 1 prediction saved")
    void runPredictionsSync_cvdOnly_callsOnlyCvd() {
        Patient patient = buildPatient("P001");
        // lenient: findById called by both initial lookup and updatePatientRiskLevel
        lenient().when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001")).thenReturn(List.of());
        when(vitalsSnapshotRepository.findByPatientId("P001")).thenReturn(Optional.empty());

        Prediction cvd = buildPrediction("PR-CVD", "CVD-Risk-v3.2", "P001", 24.3, "High");
        when(aiPredictionService.predictCVDRisk(any(), any(), any())).thenReturn(cvd);
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twinService.getTwinByPatientId("P001"))
                .thenReturn(HealthTwin.builder().id("HT-001").build());
        when(twinService.addTimelineEvent(any(), any(), any(), any()))
                .thenReturn(HealthTwin.builder().id("HT-001").build());

        List<PredictionResponse> results =
                predictionService.runPredictionsSync("P001", "CVD-Risk-v3.2", "PROV-001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).model()).isEqualTo("CVD-Risk-v3.2");
        verify(aiPredictionService, times(1)).predictCVDRisk(any(), any(), any());
        verify(aiPredictionService, never()).predictDiabetesComplication(any(), any());
        verify(aiPredictionService, never()).predictReadmission(any(), any());
        verify(predictionRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("runPredictionsSync: audit entry logged with patientId and action")
    void runPredictionsSync_logsAuditEntry() {
        Patient patient = buildPatient("P001");
        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001")).thenReturn(List.of());
        when(vitalsSnapshotRepository.findByPatientId("P001")).thenReturn(Optional.empty());

        Prediction cvd = buildPrediction("PR-CVD", "CVD-Risk-v3.2", "P001", 24.3, "High");
        when(aiPredictionService.predictCVDRisk(any(), any(), any())).thenReturn(cvd);
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twinService.getTwinByPatientId("P001"))
                .thenReturn(HealthTwin.builder().id("HT-001").build());
        when(twinService.addTimelineEvent(any(), any(), any(), any()))
                .thenReturn(HealthTwin.builder().id("HT-001").build());

        predictionService.runPredictionsSync("P001", "CVD-Risk-v3.2", "PROV-001");

        // runPredictionsSync logs: "Triggered prediction run" (for the API call)
        verify(auditService, atLeastOnce()).log(argThat(ctx ->
                ctx.patientId() != null && ctx.patientId().equals("P001")
        ));
    }

    @Test
    @DisplayName("getStats: stats map uses 'avgAccuracy' key (design.md API contract)")
    void getStats_usesAvgAccuracyKey() {
        when(predictionRepository.count()).thenReturn(1L);
        when(predictionRepository.countByCategory("High")).thenReturn(1L);
        when(predictionRepository.findAll()).thenReturn(List.of(
                buildPrediction("PR-1", "CVD-Risk-v3.2", "P001", 24.3, "High")
        ));
        Map<String, Object> stats = predictionService.getStats();
        // design.md API contract specifies "avgAccuracy" as the JSON field name
        assertThat(stats).containsKey("avgAccuracy");
        assertThat(stats).doesNotContainKey("avgConfidence");
    }

    // ── runPredictionsSync: throws 404 ────────────────────────────────────
    void runPredictionsSync_patientNotFound_throws404() {
        when(patientRepository.findById("P999")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                predictionService.runPredictionsSync("P999", "ALL", "PROV-001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("P999");

        verify(aiPredictionService, never()).predictCVDRisk(any(), any(), any());
        verify(predictionRepository, never()).save(any());
    }

    // ── getPrediction ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getPrediction: returns response and logs audit")
    void getPrediction_found_returnsAndAudits() {
        Prediction pred = buildPrediction("PR-9001", "CVD-Risk-v3.2", "P001", 24.3, "High");
        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(pred));

        PredictionResponse result = predictionService.getPrediction("PR-9001", "PROV-001");

        assertThat(result.id()).isEqualTo("PR-9001");
        assertThat(result.value()).isEqualTo(24.3);
        assertThat(result.category()).isEqualTo("High");
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("getPrediction: throws 404 for unknown prediction")
    void getPrediction_notFound_throws404() {
        when(predictionRepository.findById("PR-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                predictionService.getPrediction("PR-UNKNOWN", "PROV-001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PR-UNKNOWN");
    }

    // ── getPatientPredictions ─────────────────────────────────────────────

    @Test
    @DisplayName("getPatientPredictions: returns all predictions for patient")
    void getPatientPredictions_returnsList() {
        when(patientRepository.existsById("P001")).thenReturn(true);
        List<Prediction> preds = List.of(
                buildPrediction("PR-1", "CVD-Risk-v3.2",        "P001", 24.3, "High"),
                buildPrediction("PR-2", "DM-Complication-v2.4", "P001", 16.0, "Low"),
                buildPrediction("PR-3", "Readmit-30d-v1.8",     "P001",  8.0, "Low")
        );
        when(predictionRepository.findByPatientIdOrderByCreatedAtDesc("P001")).thenReturn(preds);

        List<PredictionResponse> results =
                predictionService.getPatientPredictions("P001", "PROV-001");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).model()).isEqualTo("CVD-Risk-v3.2");
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("getPatientPredictions: throws 404 when patient does not exist")
    void getPatientPredictions_patientNotFound_throws404() {
        when(patientRepository.existsById("P999")).thenReturn(false);

        assertThatThrownBy(() ->
                predictionService.getPatientPredictions("P999", "PROV-001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getRiskDistribution ───────────────────────────────────────────────

    @Test
    @DisplayName("getRiskDistribution: returns High/Medium/Low counts")
    void getRiskDistribution_returnsCorrectCounts() {
        when(predictionRepository.countByCategory("High")).thenReturn(23L);
        when(predictionRepository.countByCategory("Medium")).thenReturn(12L);
        when(predictionRepository.countByCategory("Low")).thenReturn(5L);

        List<Map<String, Object>> dist = predictionService.getRiskDistribution();

        assertThat(dist).hasSize(3);
        assertThat(dist.get(0).get("name")).isEqualTo("High");
        assertThat(dist.get(0).get("value")).isEqualTo(23L);
        assertThat(dist.get(1).get("name")).isEqualTo("Medium");
        assertThat(dist.get(2).get("name")).isEqualTo("Low");
    }

    // ── getStats ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats: returns total, highRiskCount, latestRound")
    void getStats_returnsCorrectValues() {
        List<Prediction> preds = List.of(
                buildPrediction("PR-1", "CVD-Risk-v3.2", "P001", 24.3, "High"),
                buildPrediction("PR-2", "DM-Complication-v2.4", "P001", 16.0, "Low")
        );
        when(predictionRepository.count()).thenReturn(2L);
        when(predictionRepository.countByCategory("High")).thenReturn(1L);
        when(predictionRepository.findAll()).thenReturn(preds);

        Map<String, Object> stats = predictionService.getStats();

        assertThat(stats.get("total")).isEqualTo(2L);
        assertThat(stats.get("highRiskCount")).isEqualTo(1L);
        assertThat(stats.get("latestRound")).isEqualTo(47);
        assertThat(stats).containsKey("avgAccuracy");
    }

    // ── getPredictions (filtered list) ────────────────────────────────────

    @Test
    @DisplayName("getPredictions: no filters returns all predictions paginated")
    void getPredictions_noFilters_returnsPaged() {
        Prediction p = buildPrediction("PR-1", "CVD-Risk-v3.2", "P001", 24.3, "High");
        Page<Prediction> page = new PageImpl<>(List.of(p));
        when(predictionRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<PredictionResponse> result =
                predictionService.getPredictions(null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(String id) {
        return Patient.builder()
                .id(id).name("Test Patient").dob("1968-04-12")
                .conditions(new ArrayList<>(List.of("Diabetes")))
                .riskLevel("Low").status("Active")
                .providerId("PROV-001").fhirConnected(true)
                .adherence(78).wearableStatus("Online")
                .twinId("HT-001").build();
    }

    private Prediction buildPrediction(String id, String model, String patientId,
                                        double value, String category) {
        return Prediction.builder()
                .id(id)
                .patientId(patientId)
                .model(model)
                .label("Test Label")
                .value(value)
                .category(category)
                .federatedRound(47)
                .confidence(91)
                .calibration("Calibrated")
                .shapFactors(List.of(
                        Prediction.ShapFactor.builder()
                                .feature("HbA1c").contribution(5.0)
                                .value("8.2%").direction("increases").build()
                ))
                .clinicalEvidence(List.of(
                        new Prediction.ClinicalEvidence("ACC/AHA", "Test evidence.")
                ))
                .createdAt(Instant.now())
                .build();
    }
}

package com.medisphere.service.phase9;

import com.medisphere.ai.AIPredictionService;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.repository.*;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * D. Readmission alert tests (per task spec §17.D)
 *
 * Verifies:
 *   D.1  HIGH alerts in previous 365 days are counted
 *   D.2  The cutoff Instant is ~365 days ago
 *   D.3  Only "HIGH" severity is queried (not MEDIUM or LOW)
 *   D.4  Empty alert history → empty list passed to predictReadmission
 *   D.5  AlertRepository failure degrades gracefully (empty list, no crash)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PredictionService — Readmission HIGH Alert Feature Tests")
class ReadmissionAlertFeatureTest {

    @Mock private AIPredictionService   aiPredictionService;
    @Mock private PredictionRepository  predictionRepository;
    @Mock private PatientRepository     patientRepository;
    @Mock private LabResultRepository   labResultRepository;
    @Mock private VitalsSnapshotRepository vitalsSnapshotRepository;
    @Mock private TwinService           twinService;
    @Mock private AuditService          auditService;
    @Mock private AlertRepository       alertRepository;

    private PredictionService predictionService;

    private static final String PATIENT_ID  = "P001";
    private static final String PROVIDER_ID = "PROV-001";

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(
                aiPredictionService, predictionRepository, patientRepository,
                labResultRepository, vitalsSnapshotRepository,
                twinService, auditService, alertRepository);

        Patient patient = Patient.builder()
                .id(PATIENT_ID).name("Test Patient").dob("1960-01-01").gender("Male")
                .conditions(List.of("Hypertension")).riskLevel("Low")
                .status("Active").providerId(PROVIDER_ID)
                .fhirConnected(false).adherence(0).wearableStatus("Offline")
                .twinId("HT-001").build();

        lenient().when(patientRepository.findById(PATIENT_ID))
                .thenReturn(Optional.of(patient));
        lenient().when(labResultRepository.findByPatientIdOrderByDateDesc(PATIENT_ID))
                .thenReturn(List.of());
        lenient().when(vitalsSnapshotRepository.findByPatientId(PATIENT_ID))
                .thenReturn(Optional.empty());
        lenient().when(predictionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        // Twin failure is fine — it is swallowed
        lenient().when(twinService.getTwinByPatientId(PATIENT_ID))
                .thenThrow(new RuntimeException("No twin"));
    }

    // ── D.1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D.1: 3 HIGH alerts in past year → list.size()=3 forwarded to predictReadmission")
    void d1_threeHighAlerts_forwardedAsListSize3() {
        when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                eq(PATIENT_ID), eq("HIGH"), any(Instant.class))).thenReturn(3L);

        Prediction readmission = stub("Readmit-30d-v1.8");
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(readmission);

        predictionService.runPredictionsSync(PATIENT_ID, "Readmit-30d-v1.8", PROVIDER_ID);

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(aiPredictionService).predictReadmission(any(Patient.class), cap.capture());
        assertThat(cap.getValue()).hasSize(3);
    }

    // ── D.2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D.2: cutoff Instant passed to repository is ~365 days ago")
    void d2_cutoffIs365DaysAgo() {
        when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), any(), any())).thenReturn(0L);
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(stub("Readmit-30d-v1.8"));

        predictionService.runPredictionsSync(PATIENT_ID, "Readmit-30d-v1.8", PROVIDER_ID);

        ArgumentCaptor<Instant> cutoffCap = ArgumentCaptor.forClass(Instant.class);
        verify(alertRepository).countByPatientIdAndSeverityAndDetectedAtAfter(
                eq(PATIENT_ID), eq("HIGH"), cutoffCap.capture());

        Instant captured = cutoffCap.getValue();
        Instant min = Instant.now().minus(366, ChronoUnit.DAYS);
        Instant max = Instant.now().minus(364, ChronoUnit.DAYS);
        assertThat(captured).isBetween(min, max);
    }

    // ── D.3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D.3: only severity='HIGH' queried — MEDIUM and LOW never queried")
    void d3_onlyHighSeverityQueried() {
        when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), any(), any())).thenReturn(0L);
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(stub("Readmit-30d-v1.8"));

        predictionService.runPredictionsSync(PATIENT_ID, "Readmit-30d-v1.8", PROVIDER_ID);

        verify(alertRepository).countByPatientIdAndSeverityAndDetectedAtAfter(
                eq(PATIENT_ID), eq("HIGH"), any(Instant.class));
        verify(alertRepository, never()).countByPatientIdAndSeverityAndDetectedAtAfter(
                eq(PATIENT_ID), eq("MEDIUM"), any(Instant.class));
        verify(alertRepository, never()).countByPatientIdAndSeverityAndDetectedAtAfter(
                eq(PATIENT_ID), eq("LOW"), any(Instant.class));
    }

    // ── D.4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D.4: no HIGH alerts → empty list passed to predictReadmission")
    void d4_noAlerts_emptyList() {
        when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), any(), any())).thenReturn(0L);
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(stub("Readmit-30d-v1.8"));

        predictionService.runPredictionsSync(PATIENT_ID, "Readmit-30d-v1.8", PROVIDER_ID);

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(aiPredictionService).predictReadmission(any(), cap.capture());
        assertThat(cap.getValue()).isEmpty();
    }

    // ── D.5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D.5: AlertRepository throws → graceful degradation (empty list, no crash)")
    void d5_repositoryThrows_gracefulDegradation() {
        when(alertRepository.countByPatientIdAndSeverityAndDetectedAtAfter(
                any(), any(), any())).thenThrow(new RuntimeException("DB timeout"));
        when(aiPredictionService.predictReadmission(any(), any())).thenReturn(stub("Readmit-30d-v1.8"));

        assertThatCode(() ->
                predictionService.runPredictionsSync(PATIENT_ID, "Readmit-30d-v1.8", PROVIDER_ID))
                .doesNotThrowAnyException();

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(aiPredictionService).predictReadmission(any(), cap.capture());
        assertThat(cap.getValue()).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Prediction stub(String model) {
        return Prediction.builder()
                .id("PR-STUB001").patientId(PATIENT_ID).model(model)
                .label("Test").value(18.0).category("Low")
                .federatedRound(0).confidence(0).calibration("Calibrated")
                .shapFactors(List.of()).clinicalEvidence(List.of())
                .createdAt(Instant.now()).build();
    }
}

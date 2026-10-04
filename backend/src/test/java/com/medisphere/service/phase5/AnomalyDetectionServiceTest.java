package com.medisphere.service.phase5;

import com.medisphere.config.AnomalyProperties;
import com.medisphere.config.WearableProperties;
import com.medisphere.domain.Alert;
import com.medisphere.domain.Patient;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.VitalsTimeSeriesRepository;
import com.medisphere.repository.WearableDeviceRepository;
import com.medisphere.service.AlertService;
import com.medisphere.service.AnomalyDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.medisphere.service.AnomalyDetectionService.percentile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnomalyDetectionService.
 *
 * All collaborators are mocked — no Spring context, no MongoDB, no Kafka broker needed.
 *
 * Covers all 4 rules:
 *   HR_SPIKE_P95, SPO2_LOW, BP_ELEVATED, DEVICE_OFFLINE
 */
@DisplayName("AnomalyDetectionService — Clinical Rule Tests")
class AnomalyDetectionServiceTest {

    private AnomalyProperties props;
    private WearableProperties wearableProps;
    private VitalsTimeSeriesRepository timeSeriesRepo;
    private PatientRepository patientRepo;
    private WearableDeviceRepository wearableRepo;
    private AlertService alertService;
    private KafkaEventPublisher publisher;
    private AnomalyDetectionService service;

    private static final String PATIENT_ID = "P001";

    @BeforeEach
    void setUp() {
        props = new AnomalyProperties();
        // Default thresholds from spec
        props.setHrSpikePercentile(95);
        props.setHrHistoryWindow(50);
        props.setHrMinSamples(5);
        props.setSpo2LowThreshold(94.0);
        props.setBpSystolicThreshold(140);
        props.setBpDiastolicThreshold(90);

        wearableProps = new WearableProperties();
        wearableProps.setOfflineThresholdMinutes(30L); // default

        timeSeriesRepo = mock(VitalsTimeSeriesRepository.class);
        patientRepo    = mock(PatientRepository.class);
        wearableRepo   = mock(WearableDeviceRepository.class);
        alertService   = mock(AlertService.class);
        publisher      = mock(KafkaEventPublisher.class);

        service = new AnomalyDetectionService(
                props, wearableProps, timeSeriesRepo, patientRepo, wearableRepo, alertService, publisher);

        // Default: patient exists
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setName("John Doe");
        when(patientRepo.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        // Default: createAlert returns a non-null alert (so getId() doesn't NPE)
        Alert stubAlert = Alert.builder().id("A-STUB001").patientId(PATIENT_ID)
                .severity("HIGH").status("Unacknowledged")
                .auditTrail(new ArrayList<>()).build();
        when(alertService.createAlert(any())).thenReturn(stubAlert);
    }

    // ── percentile() utility ──────────────────────────────────────────────

    @Test
    @DisplayName("percentile: P95 of [1..20] = 19")
    void percentile_p95_basic() {
        List<Double> values = new ArrayList<>();
        for (int i = 1; i <= 20; i++) values.add((double) i);
        assertThat(percentile(values, 95)).isEqualTo(19.0);
    }

    @Test
    @DisplayName("percentile: empty list returns 0.0")
    void percentile_empty() {
        assertThat(percentile(new ArrayList<>(), 95)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("percentile: single element always returns that element")
    void percentile_single() {
        assertThat(percentile(new ArrayList<>(List.of(72.0)), 95)).isEqualTo(72.0);
    }

    // ── HR_SPIKE_P95 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HR_SPIKE_P95: HR well above P95 baseline fires alert")
    void hrSpike_firesAlert_whenAboveP95() {
        // Baseline: 10 readings of ~70 BPM  → P95 ≈ 70
        stubHrHistory(PATIENT_ID, buildHistory(70.0, 10));

        // Incoming reading = 145 BPM → above P95 (70)
        service.checkVitals(PATIENT_ID, "heartRate", 145.0, "145 BPM");

        ArgumentCaptor<AlertService.CreateAlertCommand> captor =
                ArgumentCaptor.forClass(AlertService.CreateAlertCommand.class);
        verify(alertService).createAlert(captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("HR_SPIKE_P95");
        assertThat(captor.getValue().severity()).isEqualTo("HIGH");
        assertThat(captor.getValue().patientId()).isEqualTo(PATIENT_ID);
        verify(publisher).publishVitalsAnomaly(any());
    }

    @Test
    @DisplayName("HR_SPIKE_P95: HR within normal range does NOT fire alert")
    void hrSpike_noAlert_whenNormal() {
        stubHrHistory(PATIENT_ID, buildHistory(70.0, 10));

        // 72 BPM is at P95 = 70 — not strictly above
        service.checkVitals(PATIENT_ID, "heartRate", 70.0, "70 BPM");

        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("HR_SPIKE_P95: insufficient history does NOT fire alert")
    void hrSpike_noAlert_insufficientHistory() {
        // Only 3 readings — below hrMinSamples=5
        stubHrHistory(PATIENT_ID, buildHistory(70.0, 3));

        service.checkVitals(PATIENT_ID, "heartRate", 200.0, "200 BPM");

        verify(alertService, never()).createAlert(any());
    }

    // ── SPO2_LOW ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SPO2_LOW: SpO2 below threshold fires alert")
    void spo2Low_firesAlert_belowThreshold() {
        // No previous SpO2 history needed for this rule
        when(timeSeriesRepo.findByPatientIdAndTypeAndTimestampBetween(
                eq(PATIENT_ID), eq("spo2"), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        // 93% < 94% threshold
        service.checkVitals(PATIENT_ID, "spo2", 93.0, "93%");

        ArgumentCaptor<AlertService.CreateAlertCommand> captor =
                ArgumentCaptor.forClass(AlertService.CreateAlertCommand.class);
        verify(alertService).createAlert(captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("SPO2_LOW");
        assertThat(captor.getValue().severity()).isEqualTo("HIGH");
    }

    @ParameterizedTest
    @ValueSource(doubles = {94.0, 95.0, 98.0, 100.0})
    @DisplayName("SPO2_LOW: SpO2 at or above threshold does NOT fire alert")
    void spo2Low_noAlert_aboveThreshold(double spo2) {
        service.checkVitals(PATIENT_ID, "spo2", spo2, spo2 + "%");
        verify(alertService, never()).createAlert(any());
    }

    // ── BP_ELEVATED ───────────────────────────────────────────────────────

    @Test
    @DisplayName("BP_ELEVATED: systolic above 140 fires alert")
    void bpElevated_systolic_firesAlert() {
        service.checkVitals(PATIENT_ID, "bloodPressureSystolic", 145.0, "145 mmHg");

        ArgumentCaptor<AlertService.CreateAlertCommand> captor =
                ArgumentCaptor.forClass(AlertService.CreateAlertCommand.class);
        verify(alertService).createAlert(captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("BP_ELEVATED");
        assertThat(captor.getValue().severity()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("BP_ELEVATED: systolic at threshold (140) does NOT fire alert")
    void bpElevated_systolicAtThreshold_noAlert() {
        service.checkVitals(PATIENT_ID, "bloodPressureSystolic", 140.0, "140 mmHg");
        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("BP_ELEVATED: diastolic above 90 fires alert")
    void bpElevated_diastolic_firesAlert() {
        service.checkVitals(PATIENT_ID, "bloodPressureDiastolic", 95.0, "95 mmHg");

        ArgumentCaptor<AlertService.CreateAlertCommand> captor =
                ArgumentCaptor.forClass(AlertService.CreateAlertCommand.class);
        verify(alertService).createAlert(captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("BP_ELEVATED");
    }

    @Test
    @DisplayName("BP_ELEVATED: diastolic at threshold (90) does NOT fire alert")
    void bpElevated_diastolicAtThreshold_noAlert() {
        service.checkVitals(PATIENT_ID, "bloodPressureDiastolic", 90.0, "90 mmHg");
        verify(alertService, never()).createAlert(any());
    }

    // ── Non-anomaly readings ──────────────────────────────────────────────

    @Test
    @DisplayName("Normal temperature vital: no alert fired")
    void normalVital_noAlert() {
        service.checkVitals(PATIENT_ID, "temperature", 36.8, "36.8°C");
        verify(alertService, never()).createAlert(any());
    }

    @Test
    @DisplayName("Unknown patient: no exception, no alert")
    void unknownPatient_noException() {
        when(patientRepo.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThatCode(() -> service.checkVitals("UNKNOWN", "heartRate", 200.0, "200 BPM"))
                .doesNotThrowAnyException();
        verify(alertService, never()).createAlert(any());
    }

    // ── Determinism ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Same input always produces same rule decision (determinism)")
    void determinism_sameInputSameOutput() {
        stubHrHistory(PATIENT_ID, buildHistory(70.0, 10));

        // Call twice with the same input
        service.checkVitals(PATIENT_ID, "heartRate", 145.0, "145 BPM");
        service.checkVitals(PATIENT_ID, "heartRate", 145.0, "145 BPM");

        // Both calls should fire (same input, same decision)
        verify(alertService, times(2)).createAlert(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void stubHrHistory(String patientId, List<com.medisphere.domain.VitalsTimeSeries> history) {
        when(timeSeriesRepo.findByPatientIdAndTypeAndTimestampBetween(
                eq(patientId), eq("heartRate"), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(history);
    }

    private List<com.medisphere.domain.VitalsTimeSeries> buildHistory(double value, int count) {
        List<com.medisphere.domain.VitalsTimeSeries> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            com.medisphere.domain.VitalsTimeSeries ts =
                    com.medisphere.domain.VitalsTimeSeries.builder()
                            .id("ts-" + i)
                            .patientId(PATIENT_ID)
                            .type("heartRate")
                            .value(value)
                            .timestamp(Instant.now().minusSeconds(i * 10L))
                            .build();
            list.add(ts);
        }
        return list;
    }

    private List<Double> buildDoubleHistory(double value, int count) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(value);
        return result;
    }

    // ── DEVICE_OFFLINE — configured threshold ─────────────────────────────

    /**
     * Verifies that the DEVICE_OFFLINE rule uses the configured offline threshold
     * from WearableProperties rather than a hardcoded value.
     *
     * The test sets threshold = 10 minutes and creates a device whose lastSeen
     * is 11 minutes ago → should fire DEVICE_OFFLINE.
     *
     * Then sets threshold = 60 minutes for the same device (lastSeen 11 min ago)
     * → should NOT fire.
     */
    @Test
    @DisplayName("DEVICE_OFFLINE: uses configured threshold — fires when device exceeds configured minutes")
    void deviceOffline_firesWhenDeviceExceedsConfiguredThreshold() {
        com.medisphere.domain.WearableDevice device = new com.medisphere.domain.WearableDevice();
        device.setId("DEV-001");
        device.setPatientId(PATIENT_ID);
        device.setStatus("Online");
        // Last seen 11 minutes ago
        device.setLastSeen(Instant.now().minusSeconds(11 * 60));

        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setName("John Doe");
        when(patientRepo.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(wearableRepo.findAll()).thenReturn(List.of(device));
        when(wearableRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Configure threshold = 10 minutes → device (11 min ago) SHOULD be offline
        wearableProps.setOfflineThresholdMinutes(10L);

        service.checkDeviceOffline();

        // Alert must have been created with DEVICE_OFFLINE rule
        ArgumentCaptor<AlertService.CreateAlertCommand> captor =
                ArgumentCaptor.forClass(AlertService.CreateAlertCommand.class);
        verify(alertService).createAlert(captor.capture());
        assertThat(captor.getValue().ruleCode()).isEqualTo("DEVICE_OFFLINE");
        assertThat(captor.getValue().severity()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("DEVICE_OFFLINE: uses configured threshold — does NOT fire when device within threshold")
    void deviceOffline_doesNotFire_whenDeviceWithinConfiguredThreshold() {
        com.medisphere.domain.WearableDevice device = new com.medisphere.domain.WearableDevice();
        device.setId("DEV-002");
        device.setPatientId(PATIENT_ID);
        device.setStatus("Online");
        // Last seen 11 minutes ago
        device.setLastSeen(Instant.now().minusSeconds(11 * 60));

        when(patientRepo.findById(PATIENT_ID)).thenReturn(Optional.of(new Patient()));
        when(wearableRepo.findAll()).thenReturn(List.of(device));

        // Configure threshold = 60 minutes → device (11 min ago) is still within threshold
        wearableProps.setOfflineThresholdMinutes(60L);

        service.checkDeviceOffline();

        // No alert should fire
        verify(alertService, never()).createAlert(any());
    }
}

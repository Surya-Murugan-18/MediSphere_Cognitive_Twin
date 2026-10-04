package com.medisphere.service.phase3;

import com.medisphere.domain.VitalsSnapshot;
import com.medisphere.domain.VitalsTimeSeries;
import com.medisphere.dto.response.VitalsHistoryResponse;
import com.medisphere.dto.response.VitalsSnapshotResponse;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.VitalsSnapshotRepository;
import com.medisphere.repository.VitalsTimeSeriesRepository;
import com.medisphere.repository.WearableDeviceRepository;
import com.medisphere.service.VitalsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VitalsService Unit Tests (Phase 3)")
class VitalsServiceTest {

    @Mock private VitalsSnapshotRepository snapshotRepository;
    @Mock private VitalsTimeSeriesRepository timeSeriesRepository;
    @Mock private WearableDeviceRepository wearableRepository;
    @Mock private PatientRepository patientRepository;

    private VitalsService vitalsService;

    @BeforeEach
    void setUp() {
        vitalsService = new VitalsService(
                snapshotRepository, timeSeriesRepository,
                wearableRepository, patientRepository);
    }

    // ── getCurrentVitals ──────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentVitals returns snapshot when one exists")
    void getCurrentVitals_existingSnapshot_returnsIt() {
        when(patientRepository.existsById("P001")).thenReturn(true);
        VitalsSnapshot snap = VitalsSnapshot.builder()
                .patientId("P001").heartRate(72).bloodPressure("118/78")
                .spo2(98.0).temperature(36.6).respiratoryRate(16)
                .source("wearable").build();
        when(snapshotRepository.findByPatientId("P001")).thenReturn(Optional.of(snap));

        VitalsSnapshotResponse result = vitalsService.getCurrentVitals("P001");

        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.heartRate()).isEqualTo(72);
        assertThat(result.bloodPressure()).isEqualTo("118/78");
        assertThat(result.spo2()).isEqualTo(98.0);
    }

    @Test
    @DisplayName("getCurrentVitals seeds snapshot when none exists")
    void getCurrentVitals_noSnapshot_seeds() {
        when(patientRepository.existsById("P001")).thenReturn(true);
        when(snapshotRepository.findByPatientId("P001")).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(VitalsSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        VitalsSnapshotResponse result = vitalsService.getCurrentVitals("P001");

        assertThat(result).isNotNull();
        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.heartRate()).isGreaterThan(0);
        verify(snapshotRepository, times(1)).save(any(VitalsSnapshot.class));
    }

    @Test
    @DisplayName("getCurrentVitals throws 404 when patient not found")
    void getCurrentVitals_unknownPatient_throws404() {
        when(patientRepository.existsById("P999")).thenReturn(false);
        assertThatThrownBy(() -> vitalsService.getCurrentVitals("P999"))
                .isInstanceOf(com.medisphere.exception.ResourceNotFoundException.class)
                .hasMessageContaining("P999");
    }

    // ── getVitalsHistory ──────────────────────────────────────────────────

    @Test
    @DisplayName("getVitalsHistory returns existing data when records are present")
    void getVitalsHistory_existingData_returns() {
        when(patientRepository.existsById("P001")).thenReturn(true);

        List<VitalsTimeSeries> mockData = List.of(
                VitalsTimeSeries.builder().patientId("P001").type("heartRate")
                        .value(72.0).unit("BPM").timestamp(Instant.now()).source("wearable").build(),
                VitalsTimeSeries.builder().patientId("P001").type("heartRate")
                        .value(75.0).unit("BPM").timestamp(Instant.now()).source("wearable").build()
        );
        when(timeSeriesRepository.findByPatientIdAndTypeAndTimestampBetween(
                eq("P001"), eq("heartRate"), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(mockData);

        VitalsHistoryResponse result = vitalsService.getVitalsHistory("P001", "heartRate", "24h");

        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.type()).isEqualTo("heartRate");
        assertThat(result.period()).isEqualTo("24h");
        assertThat(result.data()).hasSize(2);
    }

    @Test
    @DisplayName("getVitalsHistory seeds data when no records exist, then returns seeded data")
    void getVitalsHistory_noData_seeds() {
        when(patientRepository.existsById("P001")).thenReturn(true);
        when(timeSeriesRepository.countByPatientIdAndType("P001", "heartRate")).thenReturn(0L);

        // First call returns empty (before seed), second call returns seeded data
        List<VitalsTimeSeries> seeded = List.of(
                VitalsTimeSeries.builder().patientId("P001").type("heartRate")
                        .value(70.0).unit("BPM").timestamp(Instant.now()).build()
        );
        when(timeSeriesRepository.findByPatientIdAndTypeAndTimestampBetween(
                eq("P001"), eq("heartRate"), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(List.of())   // first call — triggers seed
                .thenReturn(seeded);     // second call — after seed
        when(timeSeriesRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        VitalsHistoryResponse result = vitalsService.getVitalsHistory("P001", "heartRate", "24h");

        assertThat(result.data()).hasSize(1);
        verify(timeSeriesRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("getVitalsHistory uses 7d period for bloodPressureSystolic")
    void getVitalsHistory_7dPeriod_queriesCorrectRange() {
        when(patientRepository.existsById("P001")).thenReturn(true);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);

        when(timeSeriesRepository.findByPatientIdAndTypeAndTimestampBetween(
                eq("P001"), eq("bloodPressureSystolic"),
                fromCaptor.capture(), toCaptor.capture(), any(Sort.class)))
                .thenReturn(List.of());
        when(timeSeriesRepository.countByPatientIdAndType(anyString(), anyString())).thenReturn(0L);
        when(timeSeriesRepository.saveAll(anyList())).thenReturn(List.of());

        vitalsService.getVitalsHistory("P001", "bloodPressureSystolic", "7d");

        Instant from = fromCaptor.getValue();
        Instant to   = toCaptor.getValue();
        // 7d period: 'from' should be approximately 7 days before 'to'
        long diffHours = java.time.Duration.between(from, to).toHours();
        assertThat(diffHours).isBetween(167L, 169L); // ~168 hours (7 days)
    }

    // ── saveVitalReading ──────────────────────────────────────────────────

    @Test
    @DisplayName("saveVitalReading persists timeseries entry and upserts snapshot")
    void saveVitalReading_persistsBoth() {
        when(snapshotRepository.findByPatientId("P001")).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(VitalsSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(timeSeriesRepository.save(any(VitalsTimeSeries.class))).thenAnswer(i -> i.getArgument(0));

        vitalsService.saveVitalReading("P001", "heartRate", 85.0, "BPM", "wearable", "DEV-001");

        verify(timeSeriesRepository, times(1)).save(any(VitalsTimeSeries.class));
        verify(snapshotRepository, times(1)).save(any(VitalsSnapshot.class));
    }

    @Test
    @DisplayName("saveVitalReading updates heartRate in existing snapshot")
    void saveVitalReading_updatesExistingSnapshot() {
        VitalsSnapshot existing = VitalsSnapshot.builder()
                .patientId("P001").heartRate(70).build();
        when(snapshotRepository.findByPatientId("P001")).thenReturn(Optional.of(existing));
        when(snapshotRepository.save(any(VitalsSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(timeSeriesRepository.save(any(VitalsTimeSeries.class))).thenAnswer(i -> i.getArgument(0));

        vitalsService.saveVitalReading("P001", "heartRate", 92.0, "BPM", "kafka", "DEV-001");

        ArgumentCaptor<VitalsSnapshot> cap = ArgumentCaptor.forClass(VitalsSnapshot.class);
        verify(snapshotRepository).save(cap.capture());
        assertThat(cap.getValue().getHeartRate()).isEqualTo(92);
        assertThat(cap.getValue().getSource()).isEqualTo("kafka");
    }

    // ── seedVitalsForPatient ──────────────────────────────────────────────

    @Test
    @DisplayName("seedVitalsForPatient skips if snapshot already exists (idempotent)")
    void seedVitalsForPatient_existingSnapshot_skips() {
        VitalsSnapshot existing = VitalsSnapshot.builder().patientId("P001").heartRate(72).build();
        when(snapshotRepository.findByPatientId("P001")).thenReturn(Optional.of(existing));

        vitalsService.seedVitalsForPatient("P001");

        // Should not save a new snapshot
        verify(snapshotRepository, never()).save(any(VitalsSnapshot.class));
    }
}

package com.medisphere.service;

import com.medisphere.domain.HealthTwin;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.HealthTwinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TwinService Unit Tests")
class TwinServiceTest {

    @Mock private HealthTwinRepository twinRepository;

    private TwinCompletenessCalculator completenessCalculator;
    private TwinService twinService;

    @BeforeEach
    void setUp() {
        completenessCalculator = new TwinCompletenessCalculator();
        twinService = new TwinService(twinRepository, completenessCalculator);
    }

    // ── initTwin ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("initTwin creates new twin when none exists for patient")
    void initTwin_noExistingTwin_createsNew() {
        when(twinRepository.findByPatientId("P001")).thenReturn(Optional.empty());
        when(twinRepository.findAll()).thenReturn(new ArrayList<>());
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.initTwin("P001");

        assertThat(result.getPatientId()).isEqualTo("P001");
        assertThat(result.getStatus()).isEqualTo("Syncing");
        assertThat(result.getCompleteness()).isEqualTo(0);
        assertThat(result.getTimeline()).hasSize(1);
        assertThat(result.getTimeline().get(0).getTitle()).isEqualTo("Twin created");
        assertThat(result.getBodyRegions()).hasSize(5);
        verify(twinRepository, times(1)).save(any(HealthTwin.class));
    }

    @Test
    @DisplayName("initTwin returns existing twin when one already exists — idempotent")
    void initTwin_existingTwin_returnsExisting() {
        HealthTwin existing = HealthTwin.builder()
                .id("HT-001").patientId("P001").status("Synchronized").completeness(100)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources()).build();
        when(twinRepository.findByPatientId("P001")).thenReturn(Optional.of(existing));

        HealthTwin result = twinService.initTwin("P001");

        assertThat(result.getId()).isEqualTo("HT-001");
        verify(twinRepository, never()).save(any());
    }

    // ── getTwin ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTwin returns twin when found")
    void getTwin_found_returnsTwin() {
        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));

        HealthTwin result = twinService.getTwin("HT-001");
        assertThat(result.getId()).isEqualTo("HT-001");
    }

    @Test
    @DisplayName("getTwin throws 404 when twin not found")
    void getTwin_notFound_throws404() {
        when(twinRepository.findById("HT-999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> twinService.getTwin("HT-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("HT-999");
    }

    // ── updateCompleteness ────────────────────────────────────────────────

    @Test
    @DisplayName("updateCompleteness with no connected sources scores 0 and status=Stale")
    void updateCompleteness_noSources_zero_stale() {
        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .stateVersion(1L).completeness(50)
                .dataSources(HealthTwin.DataSources.defaultSources())
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.updateCompleteness("HT-001");

        assertThat(result.getCompleteness()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo("Stale");
        assertThat(result.getStateVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateCompleteness with all fresh sources scores 100 and status=Synchronized")
    void updateCompleteness_allFreshSources_hundred_synchronized() {
        Instant now = Instant.now();
        HealthTwin.DataSources ds = HealthTwin.DataSources.builder()
                .ehr(new HealthTwin.DataSourceEntry(true, now))
                .lab(new HealthTwin.DataSourceEntry(true, now))
                .wearable(new HealthTwin.DataSourceEntry(true, now))
                .kafka(new HealthTwin.DataSourceEntry(true, now))
                .build();

        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .stateVersion(1L).completeness(0).dataSources(ds)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.updateCompleteness("HT-001");

        assertThat(result.getCompleteness()).isEqualTo(100);
        assertThat(result.getStatus()).isEqualTo("Synchronized");
    }

    @Test
    @DisplayName("updateCompleteness with 2 fresh sources scores 50 and status=Syncing")
    void updateCompleteness_twoSources_fifty_syncing() {
        Instant now = Instant.now();
        HealthTwin.DataSources ds = HealthTwin.DataSources.builder()
                .ehr(new HealthTwin.DataSourceEntry(true, now))
                .lab(new HealthTwin.DataSourceEntry(true, now))
                .wearable(new HealthTwin.DataSourceEntry(false, null))
                .kafka(new HealthTwin.DataSourceEntry(false, null))
                .build();

        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .stateVersion(1L).completeness(0).dataSources(ds)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.updateCompleteness("HT-001");

        assertThat(result.getCompleteness()).isEqualTo(50);
        assertThat(result.getStatus()).isEqualTo("Syncing");
    }

    // ── addTimelineEvent ──────────────────────────────────────────────────

    @Test
    @DisplayName("addTimelineEvent appends event to timeline")
    void addTimelineEvent_appendsEvent() {
        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .stateVersion(1L).timeline(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources())
                .bodyRegions(new ArrayList<>()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.addTimelineEvent("HT-001", "Test event", "Details", "info");

        assertThat(result.getTimeline()).hasSize(1);
        assertThat(result.getTimeline().get(0).getTitle()).isEqualTo("Test event");
        assertThat(result.getTimeline().get(0).getTone()).isEqualTo("info");
    }

    // ── updateDataSource ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateDataSource updates ehr source and recomputes completeness")
    void updateDataSource_ehr_updatesAndRecomputes() {
        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .stateVersion(1L).completeness(0)
                .dataSources(HealthTwin.DataSources.defaultSources())
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>()).build();
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));
        when(twinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin result = twinService.updateDataSource("HT-001", "ehr", true, Instant.now());

        assertThat(result.getDataSources().getEhr().isConnected()).isTrue();
        assertThat(result.getCompleteness()).isEqualTo(25); // only EHR connected
        assertThat(result.getStateVersion()).isEqualTo(2L);
    }
}

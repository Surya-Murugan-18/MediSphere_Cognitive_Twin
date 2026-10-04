package com.medisphere.service.phase6;

import com.medisphere.domain.AdherenceRecord;
import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanStatus;
import com.medisphere.domain.Outcome;
import com.medisphere.dto.request.RecordAdherenceRequest;
import com.medisphere.dto.response.AdherenceResponse;
import com.medisphere.dto.response.AdherenceTrendPoint;
import com.medisphere.dto.response.OutcomeResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.AdherenceRepository;
import com.medisphere.repository.CarePlanRepository;
import com.medisphere.repository.OutcomeRepository;
import com.medisphere.service.AdherenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AdherenceService.
 *
 * Verifies:
 *  - overall adherence computation from breakdown
 *  - trend data returned in correct order
 *  - outcomes returned per plan
 *  - 404 when plan not found
 *
 * Per tasks.md B6.4.
 */
@ExtendWith(MockitoExtension.class)
class AdherenceServiceTest {

    @Mock AdherenceRepository adherenceRepository;
    @Mock OutcomeRepository outcomeRepository;
    @Mock CarePlanRepository carePlanRepository;

    AdherenceService service;

    @BeforeEach
    void setUp() {
        service = new AdherenceService(adherenceRepository, outcomeRepository, carePlanRepository);
    }

    // ── computeOverall ────────────────────────────────────────────────────

    @Test
    void computeOverall_equalWeighting() {
        // (80 + 90 + 70) / 3 = 80
        assertThat(AdherenceService.computeOverall(80, 90, 70)).isEqualTo(80);
    }

    @Test
    void computeOverall_roundsCorrectly() {
        // (79 + 80 + 80) / 3 = 79.67 → rounds to 80
        assertThat(AdherenceService.computeOverall(79, 80, 80)).isEqualTo(80);
    }

    @Test
    void computeOverall_zeroValues() {
        assertThat(AdherenceService.computeOverall(0, 0, 0)).isEqualTo(0);
    }

    @Test
    void computeOverall_perfectValues() {
        assertThat(AdherenceService.computeOverall(100, 100, 100)).isEqualTo(100);
    }

    // ── getAdherence ──────────────────────────────────────────────────────

    @Test
    void getAdherence_withRecords_returnsComputedValues() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);

        AdherenceRecord rec = AdherenceRecord.builder()
                .planId("CP-001").weekNumber(1)
                .medicationAdherence(80).monitoringAdherence(90).followUpAdherence(70)
                .overallAdherence(80).build();

        when(adherenceRepository.findByPlanIdOrderByWeekNumberAsc("CP-001")).thenReturn(List.of(rec));

        AdherenceResponse response = service.getAdherence("CP-001");

        assertThat(response.getOverall()).isEqualTo(80);
        assertThat(response.getBreakdown()).hasSize(3);
        assertThat(response.getBreakdown()).anyMatch(b -> "Medication".equals(b.getLabel()) && b.getValue() == 80);
        assertThat(response.getBreakdown()).anyMatch(b -> "Monitoring".equals(b.getLabel()) && b.getValue() == 90);
        assertThat(response.getBreakdown()).anyMatch(b -> "Follow-up".equals(b.getLabel()) && b.getValue() == 70);
    }

    @Test
    void getAdherence_noRecords_returnsDefault() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);
        when(adherenceRepository.findByPlanIdOrderByWeekNumberAsc("CP-001")).thenReturn(List.of());

        AdherenceResponse response = service.getAdherence("CP-001");

        assertThat(response.getOverall()).isEqualTo(78);
        assertThat(response.getBreakdown()).hasSize(3);
    }

    @Test
    void getAdherence_planNotFound_throws404() {
        when(carePlanRepository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> service.getAdherence("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAdherenceTrend ──────────────────────────────────────────────────

    @Test
    void getAdherenceTrend_returnsWeeksInOrder() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);

        List<AdherenceRecord> records = List.of(
                buildRecord("CP-001", 1, 60),
                buildRecord("CP-001", 2, 65),
                buildRecord("CP-001", 3, 70)
        );
        when(adherenceRepository.findByPlanIdOrderByWeekNumberAsc("CP-001")).thenReturn(records);

        List<AdherenceTrendPoint> trend = service.getAdherenceTrend("CP-001", 3);

        assertThat(trend).hasSize(3);
        assertThat(trend.get(0).getT()).isEqualTo("Week 1");
        assertThat(trend.get(0).getValue()).isEqualTo(60);
        assertThat(trend.get(2).getT()).isEqualTo("Week 3");
        assertThat(trend.get(2).getValue()).isEqualTo(70);
    }

    @Test
    void getAdherenceTrend_noRecords_returnsDefault() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);
        when(adherenceRepository.findByPlanIdOrderByWeekNumberAsc("CP-001")).thenReturn(List.of());

        List<AdherenceTrendPoint> trend = service.getAdherenceTrend("CP-001", 6);

        assertThat(trend).hasSize(6);
        assertThat(trend.get(0).getT()).isEqualTo("Week 1");
    }

    // ── getOutcomes ───────────────────────────────────────────────────────

    @Test
    void getOutcomes_withRecords_returnsOutcomes() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);

        Outcome o = Outcome.builder().id("OUT-1").planId("CP-001")
                .metric("HbA1c").baseline(8.2).current(7.6).goal(7.0).unit("%")
                .trend("improving").updatedAt(Instant.now()).build();

        when(outcomeRepository.findByPlanId("CP-001")).thenReturn(List.of(o));

        List<OutcomeResponse> outcomes = service.getOutcomes("CP-001");

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).getMetric()).isEqualTo("HbA1c");
        assertThat(outcomes.get(0).getCurrent()).isEqualTo(7.6);
    }

    @Test
    void getOutcomes_noRecords_returnsDefaultOutcome() {
        when(carePlanRepository.existsById("CP-001")).thenReturn(true);
        when(outcomeRepository.findByPlanId("CP-001")).thenReturn(List.of());
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.empty());

        List<OutcomeResponse> outcomes = service.getOutcomes("CP-001");

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).getMetric()).isEqualTo("HbA1c");
    }

    // ── recordAdherence ───────────────────────────────────────────────────

    @Test
    void recordAdherence_computesOverallAndPersists() {
        CarePlan plan = CarePlan.builder().id("CP-001").patientId("P001")
                .status(CarePlanStatus.ACTIVE).adherence(0).build();

        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));
        when(adherenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecordAdherenceRequest req = new RecordAdherenceRequest(1, 80, 90, 70);
        AdherenceRecord saved = service.recordAdherence("CP-001", req);

        assertThat(saved.getOverallAdherence()).isEqualTo(80); // (80+90+70)/3 = 80
        verify(adherenceRepository).save(any(AdherenceRecord.class));
        verify(carePlanRepository).save(plan);
        assertThat(plan.getAdherence()).isEqualTo(80);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private AdherenceRecord buildRecord(String planId, int week, int overall) {
        return AdherenceRecord.builder()
                .planId(planId).weekNumber(week)
                .medicationAdherence(overall).monitoringAdherence(overall).followUpAdherence(overall)
                .overallAdherence(overall).build();
    }
}

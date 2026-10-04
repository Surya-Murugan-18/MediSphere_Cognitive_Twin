package com.medisphere.service.phase7;

import com.medisphere.domain.CarePlanStatus;
import com.medisphere.dto.response.PopulationStatsResponse;
import com.medisphere.repository.*;
import com.medisphere.service.PopulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 — B7.9 PopulationService unit tests.
 * Per tasks.md: aggregation pipeline correctness.
 *
 * Uses lenient strictness because MongoTemplate is mocked broadly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PopulationService — Phase 7 Tests")
class PopulationServiceTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private PatientRepository patientRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private CarePlanRepository carePlanRepository;
    @Mock private ProviderRepository providerRepository;

    private PopulationService populationService;

    @BeforeEach
    void setUp() {
        populationService = new PopulationService(
                mongoTemplate, patientRepository, alertRepository,
                carePlanRepository, providerRepository);
    }

    // ── getStats ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats returns correct counts from repositories")
    void getStats_returnsCorrectCounts() {
        when(patientRepository.count()).thenReturn(100L);
        when(patientRepository.countByRiskLevel("High")).thenReturn(10L);
        when(patientRepository.countByRiskLevel("Medium")).thenReturn(40L);
        when(patientRepository.countByRiskLevel("Low")).thenReturn(50L);
        when(alertRepository.countByStatus("Unacknowledged")).thenReturn(5L);
        when(carePlanRepository.countByStatus(CarePlanStatus.ACTIVE)).thenReturn(75L);

        // Mock avgAdherence aggregation
        AggregationResults<Map> mockAggResult = mock(AggregationResults.class);
        when(mockAggResult.getMappedResults()).thenReturn(
                List.of(Map.of("avg", 78.5)));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("patients"), eq(Map.class)))
                .thenReturn(mockAggResult);

        PopulationStatsResponse stats = populationService.getStats();

        assertThat(stats.totalPatients()).isEqualTo(100L);
        assertThat(stats.highRiskCount()).isEqualTo(10L);
        assertThat(stats.mediumRiskCount()).isEqualTo(40L);
        assertThat(stats.lowRiskCount()).isEqualTo(50L);
        assertThat(stats.activeAlerts()).isEqualTo(5L);
        assertThat(stats.activePlans()).isEqualTo(75L);
        assertThat(stats.avgAdherence()).isEqualTo(78.5);
    }

    @Test
    @DisplayName("getStats handles empty collections gracefully")
    void getStats_emptyCollections_returnsZeros() {
        when(patientRepository.count()).thenReturn(0L);
        when(patientRepository.countByRiskLevel(any())).thenReturn(0L);
        when(alertRepository.countByStatus(any())).thenReturn(0L);
        when(carePlanRepository.countByStatus(any())).thenReturn(0L);

        // Empty aggregation result
        AggregationResults<Map> emptyResult = mock(AggregationResults.class);
        when(emptyResult.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("patients"), eq(Map.class)))
                .thenReturn(emptyResult);

        PopulationStatsResponse stats = populationService.getStats();

        assertThat(stats.totalPatients()).isEqualTo(0L);
        assertThat(stats.highRiskCount()).isEqualTo(0L);
        assertThat(stats.activeAlerts()).isEqualTo(0L);
        // Default adherence when no data
        assertThat(stats.avgAdherence()).isEqualTo(78.0);
    }

    @Test
    @DisplayName("getStats risk counts sum to total patients")
    void getStats_riskCountsSumToTotal() {
        long total = 1247L;
        long high = 23L, medium = 412L, low = 812L;

        when(patientRepository.count()).thenReturn(total);
        when(patientRepository.countByRiskLevel("High")).thenReturn(high);
        when(patientRepository.countByRiskLevel("Medium")).thenReturn(medium);
        when(patientRepository.countByRiskLevel("Low")).thenReturn(low);
        when(alertRepository.countByStatus(any())).thenReturn(0L);
        when(carePlanRepository.countByStatus(any())).thenReturn(0L);

        AggregationResults<Map> mockAgg = mock(AggregationResults.class);
        when(mockAgg.getMappedResults()).thenReturn(List.of(Map.of("avg", 78.0)));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("patients"), eq(Map.class)))
                .thenReturn(mockAgg);

        PopulationStatsResponse stats = populationService.getStats();

        assertThat(stats.highRiskCount() + stats.mediumRiskCount() + stats.lowRiskCount())
                .isEqualTo(stats.totalPatients());
    }
}

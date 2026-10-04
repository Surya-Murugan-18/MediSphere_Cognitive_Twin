package com.medisphere.service.phase7;

import com.medisphere.domain.SystemEvent;
import com.medisphere.dto.response.SystemEventResponse;
import com.medisphere.dto.response.SystemServiceResponse;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.repository.SystemEventRepository;
import com.medisphere.repository.WearableDeviceRepository;
import com.medisphere.service.SystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 — B7.9 SystemService unit tests.
 * Per tasks.md: health-check aggregation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SystemService — Phase 7 Tests")
class SystemServiceTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private KafkaAdmin kafkaAdmin;
    @Mock private FHIRClient fhirClient;
    @Mock private WearableDeviceRepository wearableDeviceRepository;
    @Mock private SystemEventRepository systemEventRepository;

    private SystemService systemService;

    @BeforeEach
    void setUp() {
        systemService = new SystemService(
                mongoTemplate, kafkaAdmin, fhirClient,
                wearableDeviceRepository, systemEventRepository);
    }

    // ── getServices — full list ───────────────────────────────────────────

    @Test
    @DisplayName("getServices returns 7 integration entries")
    void getServices_returns7Integrations() {
        // MongoDB ping succeeds
        var mockDb = mock(com.mongodb.client.MongoDatabase.class);
        when(mongoTemplate.getDb()).thenReturn(mockDb);
        when(mockDb.runCommand(any())).thenReturn(new org.bson.Document("ok", 1));
        var mockCollection = mock(com.mongodb.client.MongoCollection.class);
        when(mockDb.getCollection("health_twins")).thenReturn(mockCollection);
        when(mockCollection.countDocuments()).thenReturn(5L);

        // Kafka succeeds
        when(kafkaAdmin.describeTopics(anyString())).thenReturn(Map.of());

        // FHIR mock — no-op
        doNothing().when(fhirClient).validatePatientId(any());

        // Wearables
        when(wearableDeviceRepository.countByStatus("Online")).thenReturn(10L);
        when(wearableDeviceRepository.countByStatus("Offline")).thenReturn(2L);

        // System events (for FHIR sync worker)
        when(systemEventRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(List.of());

        List<SystemServiceResponse> services = systemService.getServices();

        assertThat(services).hasSize(7);
    }

    @Test
    @DisplayName("getServices returns degraded status for unavailable MongoDB — not 500")
    void getServices_mongoUnavailable_returnsDegradedNotException() {
        when(mongoTemplate.getDb()).thenThrow(new RuntimeException("Connection refused"));

        // Kafka, FHIR, Wearables — mock minimal to not throw
        when(kafkaAdmin.describeTopics(anyString())).thenReturn(Map.of());
        doNothing().when(fhirClient).validatePatientId(any());
        when(wearableDeviceRepository.countByStatus(any())).thenReturn(0L);
        when(systemEventRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(List.of());

        List<SystemServiceResponse> services = systemService.getServices();

        // Should not throw — should contain MongoDB entry with critical tone
        assertThat(services).isNotEmpty();
        SystemServiceResponse mongo = services.stream()
                .filter(s -> s.name().contains("MongoDB"))
                .findFirst()
                .orElseThrow();
        assertThat(mongo.tone()).isEqualTo("critical");
        assertThat(mongo.state()).isEqualTo("Unavailable");
    }

    @Test
    @DisplayName("getServices returns degraded status for unavailable Kafka — not 500")
    void getServices_kafkaUnavailable_returnsDegradedNotException() {
        // MongoDB succeeds
        var mockDb = mock(com.mongodb.client.MongoDatabase.class);
        when(mongoTemplate.getDb()).thenReturn(mockDb);
        when(mockDb.runCommand(any())).thenReturn(new org.bson.Document("ok", 1));
        var mockCollection = mock(com.mongodb.client.MongoCollection.class);
        when(mockDb.getCollection("health_twins")).thenReturn(mockCollection);
        when(mockCollection.countDocuments()).thenReturn(0L);

        // Kafka unavailable
        when(kafkaAdmin.describeTopics(anyString()))
                .thenThrow(new RuntimeException("Broker unreachable"));

        doNothing().when(fhirClient).validatePatientId(any());
        when(wearableDeviceRepository.countByStatus(any())).thenReturn(0L);
        when(systemEventRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(List.of());

        List<SystemServiceResponse> services = systemService.getServices();

        SystemServiceResponse kafka = services.stream()
                .filter(s -> s.name().contains("Kafka"))
                .findFirst()
                .orElseThrow();
        assertThat(kafka.tone()).isEqualTo("critical");
        assertThat(kafka.state()).isEqualTo("Unavailable");
    }

    // ── getEvents ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvents returns system events sorted newest first")
    void getEvents_returnsSortedEvents() {
        SystemEvent e1 = SystemEvent.builder()
                .id("SE-001").timestamp(Instant.now().minusSeconds(60))
                .text("Older event").tone("neutral").category("FHIR").build();
        SystemEvent e2 = SystemEvent.builder()
                .id("SE-002").timestamp(Instant.now())
                .text("Newer event").tone("healthy").category("Kafka").build();

        when(systemEventRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(List.of(e2, e1)); // already sorted by repository

        List<SystemEventResponse> events = systemService.getEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).id()).isEqualTo("SE-002"); // newest first
    }

    @Test
    @DisplayName("getEvents returns empty list when no events exist")
    void getEvents_empty_returnsEmptyList() {
        when(systemEventRepository.findAllByOrderByTimestampDesc(any(Pageable.class)))
                .thenReturn(List.of());

        List<SystemEventResponse> events = systemService.getEvents();

        assertThat(events).isEmpty();
    }
}

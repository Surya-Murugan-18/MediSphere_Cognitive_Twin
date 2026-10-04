package com.medisphere.service.phase5;

import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.VitalsKafkaConsumer;
import com.medisphere.service.AnomalyDetectionService;
import com.medisphere.service.MonitoringService;
import com.medisphere.service.TwinService;
import com.medisphere.service.VitalsService;
import com.medisphere.websocket.KafkaEventBroadcaster;
import com.medisphere.websocket.VitalsWebSocketBroadcaster;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VitalsKafkaConsumer.
 *
 * Verifies the full Phase 5 consumer chain:
 *   Kafka message → persist → WS push → anomaly check → twin update
 *
 * All dependencies mocked — no broker required.
 */
@DisplayName("VitalsKafkaConsumer — Consumer Chain Tests")
class VitalsKafkaConsumerTest {

    private VitalsService vitalsService;
    private VitalsWebSocketBroadcaster vitalsBroadcaster;
    private AnomalyDetectionService anomalyService;
    private TwinService twinService;
    private KafkaEventPublisher publisher;
    private KafkaEventBroadcaster eventBroadcaster;
    private MonitoringService monitoringService;
    private VitalsKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        vitalsService     = mock(VitalsService.class);
        vitalsBroadcaster = mock(VitalsWebSocketBroadcaster.class);
        anomalyService    = mock(AnomalyDetectionService.class);
        twinService       = mock(TwinService.class);
        publisher         = mock(KafkaEventPublisher.class);
        eventBroadcaster  = mock(KafkaEventBroadcaster.class);
        monitoringService = mock(MonitoringService.class);

        consumer = new VitalsKafkaConsumer(
                vitalsService, vitalsBroadcaster, anomalyService,
                twinService, publisher, eventBroadcaster, monitoringService);

        // Default: no twin found
        when(twinService.findTwinByPatientId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("valid heartRate event: persists to MongoDB via VitalsService")
    void validEvent_persistsVitals() {
        ConsumerRecord<String, Object> record = buildRecord("P001", "heartRate", 85.0, "bpm");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(vitalsService).saveVitalReading("P001", "heartRate", 85.0, "bpm", "wearable", "DEV-001");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("valid event: pushes snapshot via VitalsWebSocketBroadcaster")
    void validEvent_pushesWebSocket() {
        ConsumerRecord<String, Object> record = buildRecord("P001", "spo2", 97.0, "%");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(vitalsBroadcaster).pushByPatientId("P001");
    }

    @Test
    @DisplayName("valid event: invokes anomaly detection")
    void validEvent_checksAnomaly() {
        ConsumerRecord<String, Object> record = buildRecord("P002", "heartRate", 145.0, "bpm");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(anomalyService).checkVitals(eq("P002"), eq("heartRate"), eq(145.0), any());
    }

    @Test
    @DisplayName("valid event: records Kafka event in MonitoringService")
    void validEvent_recordsMonitoringEvent() {
        ConsumerRecord<String, Object> record = buildRecord("P001", "heartRate", 72.0, "bpm");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(monitoringService).recordEvent(eq("vitals.raw"), eq("P001"), anyString());
    }

    @Test
    @DisplayName("valid event: twin found → publishes twin.update event")
    void validEvent_twinFound_publishesTwinUpdate() {
        com.medisphere.domain.HealthTwin twin =
                com.medisphere.domain.HealthTwin.builder()
                        .id("HT-001").patientId("P001").stateVersion(100L).completeness(90)
                        .build();
        when(twinService.findTwinByPatientId("P001")).thenReturn(Optional.of(twin));

        ConsumerRecord<String, Object> record = buildRecord("P001", "spo2", 98.0, "%");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(publisher).publishTwinUpdate(any());
    }

    @Test
    @DisplayName("message with missing patientId: acknowledged without processing")
    void missingPatientId_acknowledgedCleanly() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "heartRate");
        payload.put("value", 80.0);
        // no patientId
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("vitals.raw", 0, 0L, "key", payload);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(vitalsService, never()).saveVitalReading(any(), any(), anyDouble(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("exception during processing: message still acknowledged (no poison pill)")
    void exceptionDuringProcessing_stillAcknowledges() {
        doThrow(new RuntimeException("DB error"))
                .when(vitalsService).saveVitalReading(any(), any(), anyDouble(), any(), any(), any());

        ConsumerRecord<String, Object> record = buildRecord("P001", "heartRate", 80.0, "bpm");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onVitalsRaw(record, ack);

        verify(ack).acknowledge();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static ConsumerRecord<String, Object> buildRecord(
            String patientId, String type, double value, String unit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patientId", patientId);
        payload.put("deviceId",  "DEV-001");
        payload.put("type",      type);
        payload.put("value",     value);
        payload.put("unit",      unit);
        return new ConsumerRecord<>("vitals.raw", 0, 0L, patientId, payload);
    }
}

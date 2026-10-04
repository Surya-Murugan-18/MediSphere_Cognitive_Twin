package com.medisphere.kafka;

import com.medisphere.kafka.events.TwinUpdateEvent;
import com.medisphere.service.AnomalyDetectionService;
import com.medisphere.service.MonitoringService;
import com.medisphere.service.TwinService;
import com.medisphere.service.VitalsService;
import com.medisphere.websocket.KafkaEventBroadcaster;
import com.medisphere.websocket.VitalsWebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka consumer for the {@code vitals.raw} topic.
 *
 * Phase 5 flow (per tasks.md B5.2 and design.md §8.3):
 *
 *   vitals.raw event
 *     → VitalsService.saveVitalReading()     (persist to MongoDB)
 *     → VitalsWebSocketBroadcaster.push()    (push snapshot to UI)
 *     → AnomalyDetectionService.checkVitals() (evaluate rules)
 *     → KafkaEventPublisher.publishTwinUpdate() (twin state event)
 *
 * The consumer deserialises the payload as a generic Map<String,Object>
 * (configured in KafkaConfig) to avoid tight coupling to specific POJOs
 * for the raw topic, which may be produced by external wearable gateways.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VitalsKafkaConsumer {

    private final VitalsService vitalsService;
    private final VitalsWebSocketBroadcaster vitalsWebSocketBroadcaster;
    private final AnomalyDetectionService anomalyDetectionService;
    private final TwinService twinService;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final KafkaEventBroadcaster kafkaEventBroadcaster;
    private final MonitoringService monitoringService;

    @KafkaListener(
            topics     = KafkaTopics.VITALS_RAW,
            groupId    = "${spring.kafka.consumer.group-id:medisphere-backend}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVitalsRaw(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) record.value();

            String patientId = getString(payload, "patientId");
            String deviceId  = getString(payload, "deviceId");
            String type      = getString(payload, "type");
            double value     = getDouble(payload, "value");
            String unit      = getString(payload, "unit");

            if (patientId == null || type == null) {
                log.warn("vitals.raw message missing required fields — offset={}", record.offset());
                ack.acknowledge();
                return;
            }

            log.debug("vitals.raw — patient={} type={} value={} device={}",
                    patientId, type, value, deviceId);

            // 1. Persist time-series + upsert snapshot
            vitalsService.saveVitalReading(patientId, type, value, unit != null ? unit : "", "wearable", deviceId);

            // 1b. Mark the wearable and Kafka sources as freshly connected.
            // This drives Digital Twin completeness and source-status cards.
            twinService.findTwinByPatientId(patientId).ifPresent(twin -> {
                twinService.updateDataSource(twin.getId(), "wearable", true, Instant.now());
                twinService.updateDataSource(twin.getId(), "kafka", true, Instant.now());
            });

            // 2. Build a human-readable snapshot string for the anomaly/alert message
            String snapshotStr = formatSnapshotValue(type, value, unit);

            // 3. Push current snapshot to connected WebSocket clients
            vitalsWebSocketBroadcaster.pushByPatientId(patientId);

            // 3b. Broadcast kafka.events summary to monitoring UI
            String eventText = patientId + " · " + type + "=" + (int) value + " · device=" + (deviceId != null ? deviceId : "—");
            kafkaEventBroadcaster.broadcast(KafkaTopics.VITALS_RAW, patientId, eventText);
            monitoringService.recordEvent(KafkaTopics.VITALS_RAW, patientId, eventText);

            // 4. Evaluate anomaly rules
            anomalyDetectionService.checkVitals(patientId, type, value, snapshotStr);

            // 5. Publish twin.update — the twin service handles its own state update
            twinService.findTwinByPatientId(patientId).ifPresent(twin -> {
                kafkaEventPublisher.publishTwinUpdate(TwinUpdateEvent.builder()
                        .twinId(twin.getId())
                        .patientId(patientId)
                        .stateVersion(twin.getStateVersion())
                        .completeness(twin.getCompleteness())
                        .timestamp(Instant.now())
                        .build());
            });

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing vitals.raw message at offset {}: {}",
                    record.offset(), e.getMessage(), e);
            // Acknowledge anyway to avoid infinite retry on bad messages.
            // A dead-letter queue strategy belongs in Phase 8 production hardening.
            ack.acknowledge();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static double getDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    private static String formatSnapshotValue(String type, double value, String unit) {
        return switch (type) {
            case "heartRate"              -> (int) value + " BPM";
            case "spo2"                   -> String.format("%.1f%%", value);
            case "bloodPressureSystolic"  -> (int) value + " mmHg (sys)";
            case "bloodPressureDiastolic" -> (int) value + " mmHg (dia)";
            case "temperature"            -> String.format("%.1f °C", value);
            case "respiratoryRate"        -> (int) value + " /min";
            default -> value + (unit != null ? " " + unit : "");
        };
    }
}

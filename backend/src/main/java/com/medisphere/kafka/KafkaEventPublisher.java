package com.medisphere.kafka;

import com.medisphere.kafka.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Typed Kafka event publisher.
 *
 * All services that need to publish Kafka events inject this class —
 * they never interact with KafkaTemplate directly. This keeps the
 * Kafka implementation detail out of business services.
 *
 * All publish methods are fire-and-forget with async completion logging.
 * In development, Kafka may not be running; failures are logged as WARN
 * rather than propagated — the system degrades gracefully to REST-only mode.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── vitals.raw ───────────────────────────────────────────────────────

    public void publishVitalsRaw(VitalsRawEvent event) {
        publish(KafkaTopics.VITALS_RAW, event.getPatientId(), event);
    }

    // ── vitals.anomaly ───────────────────────────────────────────────────

    public void publishVitalsAnomaly(VitalsAnomalyEvent event) {
        publish(KafkaTopics.VITALS_ANOMALY, event.getPatientId(), event);
    }

    // ── alert.created ────────────────────────────────────────────────────

    public void publishAlertCreated(AlertCreatedEvent event) {
        publish(KafkaTopics.ALERT_CREATED, event.getAlertId(), event);
    }

    // ── twin.update ──────────────────────────────────────────────────────

    public void publishTwinUpdate(TwinUpdateEvent event) {
        publish(KafkaTopics.TWIN_UPDATE, event.getTwinId(), event);
    }

    // ── careplan.approved ────────────────────────────────────────────────

    public void publishCarePlanApproved(CarePlanApprovedEvent event) {
        publish(KafkaTopics.CAREPLAN_APPROVED, event.getPlanId(), event);
    }

    // ── report.ready ─────────────────────────────────────────────────────

    public void publishReportReady(ReportReadyEvent event) {
        publish(KafkaTopics.REPORT_READY, event.getReportId(), event);
    }

    // ── internal ─────────────────────────────────────────────────────────

    private void publish(String topic, String key, Object payload) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka publish failed — topic={} key={} error={}", topic, key, ex.getMessage());
                } else {
                    log.debug("Kafka published — topic={} key={} offset={}",
                            topic, key,
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            // Kafka not available (e.g. running without broker in unit tests) — degrade gracefully
            log.warn("Kafka unavailable — publish skipped — topic={} key={} error={}", topic, key, ex.getMessage());
        }
    }
}

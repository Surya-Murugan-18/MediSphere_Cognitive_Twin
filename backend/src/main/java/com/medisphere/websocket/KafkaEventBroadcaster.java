package com.medisphere.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Forwards Kafka event summaries to WebSocket subscribers.
 *
 * Per design.md §8.1 destination:
 *   /topic/kafka.events — Kafka event log feed
 *
 * Called from KafkaEventPublisher after each successful publish, and also
 * directly from VitalsKafkaConsumer for vitals.raw events.
 *
 * The payload is a lightweight summary (topic, key, timestamp, text)
 * — not the full event body — to keep the WebSocket traffic minimal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast a Kafka event summary to connected monitoring UI clients.
     *
     * @param topic  Kafka topic name
     * @param key    Message key (e.g. patientId or alertId)
     * @param text   Human-readable summary line (shown in the Monitoring Kafka panel)
     */
    public void broadcast(String topic, String key, String text) {
        try {
            Map<String, Object> event = Map.of(
                    "topic",     topic,
                    "key",       key != null ? key : "",
                    "text",      text,
                    "timestamp", Instant.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/kafka.events", event);
            log.debug("WS kafka.events broadcast — topic={} key={}", topic, key);
        } catch (Exception e) {
            log.warn("KafkaEventBroadcaster failed: {}", e.getMessage());
        }
    }
}

package com.medisphere.websocket;

import com.medisphere.dto.response.VitalsSnapshotResponse;
import com.medisphere.repository.VitalsSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts current vital sign snapshots to WebSocket subscribers.
 *
 * Per design.md §8.1 destinations:
 *   /topic/vitals.{patientId}    — per-patient vital push
 *   /topic/monitoring.vitals     — bulk monitoring table update
 *
 * Called by VitalsKafkaConsumer after each vitals.raw message is processed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VitalsWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final VitalsSnapshotRepository snapshotRepository;

    /**
     * Fetch the current snapshot for the given patient and push it to
     * both the per-patient topic and the bulk monitoring topic.
     */
    public void pushByPatientId(String patientId) {
        snapshotRepository.findByPatientId(patientId).ifPresentOrElse(
                snapshot -> {
                    VitalsSnapshotResponse response = VitalsSnapshotResponse.from(snapshot);
                    push(patientId, response);
                },
                () -> log.debug("VitalsWebSocket: no snapshot found for patient {}", patientId)
        );
    }

    /**
     * Push an already-resolved snapshot response directly (avoids a second DB read).
     */
    public void push(String patientId, VitalsSnapshotResponse response) {
        try {
            // Per-patient topic: Vitals.tsx live tile updates
            messagingTemplate.convertAndSend("/topic/vitals." + patientId, response);

            // Bulk monitoring topic: Monitoring.tsx table live updates
            messagingTemplate.convertAndSend("/topic/monitoring.vitals", response);

            log.debug("WS vitals pushed — patient={} hr={}", patientId, response.heartRate());
        } catch (Exception e) {
            log.warn("VitalsWebSocket broadcast failed for patient {}: {}", patientId, e.getMessage());
        }
    }
}

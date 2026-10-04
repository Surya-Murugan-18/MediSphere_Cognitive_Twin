package com.medisphere.websocket;

import com.medisphere.domain.Alert;
import com.medisphere.dto.response.AlertResponse;
import com.medisphere.service.AlertService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts alert events to WebSocket subscribers.
 *
 * Per design.md §8.1 destinations:
 *   /topic/alerts.new              — new alert broadcast (all subscribers)
 *   /topic/alerts.{alertId}.status — per-alert status change
 *
 * Registers its broadcast callbacks with AlertService via @PostConstruct
 * to avoid a circular bean dependency (AlertService → broadcaster → AlertService).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertService alertService;

    /**
     * Register broadcast callbacks with AlertService after construction.
     * This avoids the AlertService needing to directly depend on the broadcaster.
     */
    @PostConstruct
    public void registerCallbacks() {
        alertService.setNewAlertBroadcast(this::broadcastNewAlert);
        alertService.setStatusUpdateBroadcast(this::broadcastStatusUpdate);
        log.debug("AlertWebSocketBroadcaster: callbacks registered with AlertService");
    }

    /**
     * Broadcast a newly created alert to all monitoring subscribers.
     * Frontend useAlertStream hook listens on /topic/alerts.new.
     */
    public void broadcastNewAlert(Alert alert) {
        try {
            AlertResponse response = alertService.toResponse(alert);
            messagingTemplate.convertAndSend("/topic/alerts.new", response);
            log.debug("WS new alert broadcast — alertId={} severity={}", alert.getId(), alert.getSeverity());
        } catch (Exception e) {
            log.warn("Alert WS new broadcast failed for {}: {}", alert.getId(), e.getMessage());
        }
    }

    /**
     * Broadcast an alert status update to per-alert subscribers.
     * Frontend AlertDetails page uses this to update status without a full reload.
     */
    public void broadcastStatusUpdate(Alert alert) {
        try {
            AlertResponse response = alertService.toResponse(alert);
            messagingTemplate.convertAndSend("/topic/alerts." + alert.getId() + ".status", response);
            // Also push to the general new/update channel so the Alerts list refreshes
            messagingTemplate.convertAndSend("/topic/alerts.new", response);
            log.debug("WS alert status broadcast — alertId={} status={}", alert.getId(), alert.getStatus());
        } catch (Exception e) {
            log.warn("Alert WS status broadcast failed for {}: {}", alert.getId(), e.getMessage());
        }
    }
}

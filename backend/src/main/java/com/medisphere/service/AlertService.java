package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.audit.Auditable;
import com.medisphere.domain.Alert;
import com.medisphere.domain.AlertAuditEntry;
import com.medisphere.dto.request.AcknowledgeAlertRequest;
import com.medisphere.dto.request.EscalateAlertRequest;
import com.medisphere.dto.request.ResolveAlertRequest;
import com.medisphere.dto.response.AlertAuditEntryResponse;
import com.medisphere.dto.response.AlertCountResponse;
import com.medisphere.dto.response.AlertResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.exception.ValidationException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.AlertCreatedEvent;
import com.medisphere.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for clinical alert lifecycle.
 *
 * Alert flow: create → acknowledge → escalate → resolve
 *
 * RBAC enforcement:
 *   Acknowledge: NURSE, CLINICIAN, ADMIN
 *   Escalate:    CLINICIAN, ADMIN only
 *   Resolve:     CLINICIAN, ADMIN only
 *
 * Enforcement is done in this service (not only the controller) because
 * the controller uses @PreAuthorize but service-level checks are
 * the authoritative security boundary per the architecture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private static final String MODULE = "Alerts";

    private final AlertRepository alertRepository;
    private final AuditService auditService;
    private final KafkaEventPublisher kafkaEventPublisher;

    // WebSocket broadcasters injected lazily to avoid circular dependency
    // at startup — set by AlertWebSocketBroadcaster via setter injection
    private Object alertWebSocketBroadcaster; // typed via interface in Phase 5

    // ── Command object for creating an alert ─────────────────────────────

    public record CreateAlertCommand(
            String severity,
            String patientId,
            String patientName,
            String event,
            String analysis,
            String type,
            String ruleCode,
            String assignedProvider,
            String currentValue,
            String previousValue,
            int confidence
    ) {}

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Creates, persists, and broadcasts a new alert.
     * Called by AnomalyDetectionService.
     */
    public Alert createAlert(CreateAlertCommand cmd) {
        String alertId = "A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        AlertAuditEntry detectionEntry = AlertAuditEntry.builder()
                .id("at-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(now)
                .actor("MediSphere Stream Processor")
                .actorId("system")
                .action("Anomaly detected on vitals.raw · rule " + cmd.ruleCode())
                .build();

        AlertAuditEntry creationEntry = AlertAuditEntry.builder()
                .id("at-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(now.plusMillis(1))
                .actor("Alert Service")
                .actorId("system")
                .action("Alert " + alertId + " created with severity " + cmd.severity())
                .build();

        Alert alert = Alert.builder()
                .id(alertId)
                .severity(cmd.severity())
                .patientId(cmd.patientId())
                .patientName(cmd.patientName())
                .event(cmd.event())
                .analysis(cmd.analysis())
                .type(cmd.type())
                .ruleCode(cmd.ruleCode())
                .detectedAt(now)
                .status("Unacknowledged")
                .assignedProvider(cmd.assignedProvider())
                .currentValue(cmd.currentValue())
                .previousValue(cmd.previousValue())
                .confidence(cmd.confidence())
                .auditTrail(java.util.Arrays.asList(detectionEntry, creationEntry))
                .build();

        Alert saved = alertRepository.save(alert);
        log.info("Alert created: {} severity={} patient={} rule={}", alertId, cmd.severity(), cmd.patientId(), cmd.ruleCode());

        // Publish to Kafka
        kafkaEventPublisher.publishAlertCreated(AlertCreatedEvent.builder()
                .alertId(saved.getId())
                .severity(saved.getSeverity())
                .patientId(saved.getPatientId())
                .patientName(saved.getPatientName())
                .event(saved.getEvent())
                .type(saved.getType())
                .status(saved.getStatus())
                .assignedProvider(saved.getAssignedProvider())
                .confidence(saved.getConfidence())
                .detectedAt(saved.getDetectedAt())
                .build());

        // Broadcast via WebSocket (broadcaster calls back into this via a callback — see AlertWebSocketBroadcaster)
        broadcastAlert(saved);

        // Global audit log
        auditService.log(AuditContext.builder()
                .userId("system")
                .userName("Alert Service")
                .userRole("SYSTEM")
                .action("Alert created: " + saved.getEvent())
                .module(MODULE)
                .patientId(saved.getPatientId())
                .patientName(saved.getPatientName())
                .status("Success")
                .build());

        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────

    public PageResponse<AlertResponse> getAlerts(
            String severity, String status, String patientId, String type,
            Instant from, Instant to, int page, int size) {

        // Null means "no filter" — the repository query handles nulls via $or
        Instant effectiveFrom = (from != null) ? from : Instant.EPOCH;
        Instant effectiveTo   = (to   != null) ? to   : Instant.now().plus(365 * 10, ChronoUnit.DAYS);

        Page<Alert> alertPage = alertRepository.findFiltered(
                severity, status, patientId, type,
                effectiveFrom, effectiveTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAt")));

        return PageResponse.from(alertPage, this::toResponse);
    }

    public AlertResponse getAlert(String alertId) {
        return toResponse(findById(alertId));
    }

    public AlertCountResponse getAlertCount(String status) {
        long count = (status != null && !status.isBlank())
                ? alertRepository.countByStatus(status)
                : alertRepository.count();
        return new AlertCountResponse(count);
    }

    public List<AlertResponse> getPatientAlerts(String patientId) {
        return alertRepository.findByPatientIdOrderByDetectedAtDesc(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Acknowledge ───────────────────────────────────────────────────────

    @Auditable(action = "Acknowledged Alert", module = MODULE)
    public AlertResponse acknowledgeAlert(String alertId, AcknowledgeAlertRequest req, Authentication auth) {
        Alert alert = findById(alertId);

        if (!"Unacknowledged".equals(alert.getStatus())) {
            throw new ValidationException("Alert " + alertId + " is not in Unacknowledged status (current: " + alert.getStatus() + ")");
        }

        String providerId   = auth.getName();
        String providerName = auth.getName(); // display name from principal
        Instant now = Instant.now();

        alert.setStatus("Acknowledged");
        alert.setAcknowledgedBy(providerId);
        alert.setAcknowledgedAt(now);
        alert.setAcknowledgeNotes(req != null ? req.getNotes() : null);

        alert.getAuditTrail().add(AlertAuditEntry.builder()
                .id("at-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(now)
                .actor(providerName)
                .actorId(providerId)
                .action("Alert acknowledged" + (req != null && req.getNotes() != null ? " — " + req.getNotes() : ""))
                .build());

        Alert saved = alertRepository.save(alert);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .userRole(primaryRole(auth))
                .action("Acknowledged Alert: " + alert.getEvent())
                .module(MODULE)
                .patientId(alert.getPatientId())
                .patientName(alert.getPatientName())
                .status("Success")
                .build());

        broadcastStatusUpdate(saved);
        return toResponse(saved);
    }

    // ── Escalate ──────────────────────────────────────────────────────────

    @Auditable(action = "Escalated Alert", module = MODULE)
    public AlertResponse escalateAlert(String alertId, EscalateAlertRequest req, Authentication auth) {
        ensureClinician(auth);

        Alert alert = findById(alertId);

        if ("Resolved".equals(alert.getStatus())) {
            throw new ValidationException("Cannot escalate a resolved alert.");
        }

        String providerId   = auth.getName();
        Instant now = Instant.now();

        alert.setStatus("Escalated");
        alert.setEscalatedBy(providerId);
        alert.setEscalatedAt(now);
        alert.setEscalationReason(req.getReason());
        alert.setEscalatedTo(req.getEscalateTo());

        alert.getAuditTrail().add(AlertAuditEntry.builder()
                .id("at-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(now)
                .actor(providerId)
                .actorId(providerId)
                .action("Alert escalated to " + req.getEscalateTo() + " — " + req.getReason())
                .build());

        Alert saved = alertRepository.save(alert);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerId)
                .userRole(primaryRole(auth))
                .action("Escalated Alert: " + alert.getEvent())
                .module(MODULE)
                .patientId(alert.getPatientId())
                .patientName(alert.getPatientName())
                .status("Success")
                .build());

        broadcastStatusUpdate(saved);
        return toResponse(saved);
    }

    // ── Resolve ───────────────────────────────────────────────────────────

    @Auditable(action = "Resolved Alert", module = MODULE)
    public AlertResponse resolveAlert(String alertId, ResolveAlertRequest req, Authentication auth) {
        ensureClinician(auth);

        Alert alert = findById(alertId);

        if ("Resolved".equals(alert.getStatus())) {
            throw new ValidationException("Alert " + alertId + " is already resolved.");
        }

        String providerId = auth.getName();
        Instant now = Instant.now();

        alert.setStatus("Resolved");
        alert.setResolvedBy(providerId);
        alert.setResolvedAt(now);
        alert.setResolution(req.getResolution());

        alert.getAuditTrail().add(AlertAuditEntry.builder()
                .id("at-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(now)
                .actor(providerId)
                .actorId(providerId)
                .action("Alert resolved — " + req.getResolution())
                .build());

        Alert saved = alertRepository.save(alert);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerId)
                .userRole(primaryRole(auth))
                .action("Resolved Alert: " + alert.getEvent())
                .module(MODULE)
                .patientId(alert.getPatientId())
                .patientName(alert.getPatientName())
                .status("Success")
                .build());

        broadcastStatusUpdate(saved);
        return toResponse(saved);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private Alert findById(String alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
    }

    /**
     * Enforce CLINICIAN or ADMIN role.
     * This is the server-side authority check — the controller @PreAuthorize
     * is an additional layer, not the sole guard.
     */
    private void ensureClinician(Authentication auth) {
        boolean allowed = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_CLINICIAN") || a.equals("ROLE_ADMIN"));
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only CLINICIAN or ADMIN can perform this action on alerts.");
        }
    }

    private String primaryRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("UNKNOWN")
                .replace("ROLE_", "");
    }

    // ── WebSocket broadcast hooks ─────────────────────────────────────────
    // The actual broadcaster is injected at runtime from Phase 5 WebSocket layer.
    // If the broadcaster is not yet registered (e.g. in unit tests), broadcast is a no-op.

    private java.util.function.Consumer<Alert> newAlertBroadcast    = a -> {};
    private java.util.function.Consumer<Alert> statusUpdateBroadcast = a -> {};

    /** Called by AlertWebSocketBroadcaster to register the new-alert broadcast callback. */
    public void setNewAlertBroadcast(java.util.function.Consumer<Alert> fn) {
        this.newAlertBroadcast = fn;
    }

    /** Called by AlertWebSocketBroadcaster to register the status-update broadcast callback. */
    public void setStatusUpdateBroadcast(java.util.function.Consumer<Alert> fn) {
        this.statusUpdateBroadcast = fn;
    }

    private void broadcastAlert(Alert alert) {
        try { newAlertBroadcast.accept(alert); }
        catch (Exception e) { log.warn("Alert WS broadcast failed: {}", e.getMessage()); }
    }

    private void broadcastStatusUpdate(Alert alert) {
        try { statusUpdateBroadcast.accept(alert); }
        catch (Exception e) { log.warn("Alert status WS broadcast failed: {}", e.getMessage()); }
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    public AlertResponse toResponse(Alert a) {
        List<AlertAuditEntryResponse> trail = a.getAuditTrail() == null ? List.of()
                : a.getAuditTrail().stream()
                    .map(e -> new AlertAuditEntryResponse(e.getId(), e.getTimestamp(),
                                                          e.getActor(), e.getActorId(), e.getAction()))
                    .toList();

        return new AlertResponse(
                a.getId(), a.getSeverity(), a.getPatientId(), a.getPatientName(),
                a.getEvent(), a.getAnalysis(), a.getType(), a.getRuleCode(),
                a.getDetectedAt(), a.getStatus(), a.getAssignedProvider(),
                a.getCurrentValue(), a.getPreviousValue(), a.getConfidence(),
                a.getAcknowledgedAt(), a.getAcknowledgedBy(), a.getAcknowledgeNotes(),
                a.getEscalatedAt(), a.getEscalatedBy(), a.getEscalationReason(), a.getEscalatedTo(),
                a.getResolvedAt(), a.getResolvedBy(), a.getResolution(),
                trail, a.getCreatedAt());
    }
}

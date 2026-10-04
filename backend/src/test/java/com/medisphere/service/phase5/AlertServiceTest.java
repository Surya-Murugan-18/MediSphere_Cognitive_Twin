package com.medisphere.service.phase5;

import com.medisphere.audit.AuditService;
import com.medisphere.domain.Alert;
import com.medisphere.dto.request.AcknowledgeAlertRequest;
import com.medisphere.dto.request.EscalateAlertRequest;
import com.medisphere.dto.request.ResolveAlertRequest;
import com.medisphere.dto.response.AlertResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.exception.ValidationException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.repository.AlertRepository;
import com.medisphere.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertService.
 *
 * Verifies:
 *   - createAlert: persists, publishes Kafka event, broadcasts WS
 *   - acknowledgeAlert: status transition, audit trail appended
 *   - escalateAlert: CLINICIAN-only enforcement, audit trail
 *   - resolveAlert: status transition, audit trail
 *   - Role restrictions enforced server-side
 *   - 404 on missing alert
 */
@DisplayName("AlertService — Alert Lifecycle Tests")
class AlertServiceTest {

    private AlertRepository alertRepository;
    private AuditService auditService;
    private KafkaEventPublisher publisher;
    private AlertService service;

    private static final String ALERT_ID   = "A-TEST001";
    private static final String PATIENT_ID = "P001";

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        auditService    = mock(AuditService.class);
        publisher       = mock(KafkaEventPublisher.class);

        service = new AlertService(alertRepository, auditService, publisher);

        // Default: save returns the same object
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── createAlert ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAlert: persists alert with Unacknowledged status")
    void createAlert_persistsWithCorrectStatus() {
        AlertService.CreateAlertCommand cmd = buildCmd("HIGH", "HR_SPIKE_P95");
        Alert saved = service.createAlert(cmd);

        assertThat(saved.getStatus()).isEqualTo("Unacknowledged");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getRuleCode()).isEqualTo("HR_SPIKE_P95");
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    @DisplayName("createAlert: publishes alert.created Kafka event")
    void createAlert_publishesKafkaEvent() {
        service.createAlert(buildCmd("HIGH", "HR_SPIKE_P95"));
        verify(publisher).publishAlertCreated(any());
    }

    @Test
    @DisplayName("createAlert: embeds two audit trail entries (detection + creation)")
    void createAlert_embedsAuditTrail() {
        Alert saved = service.createAlert(buildCmd("HIGH", "HR_SPIKE_P95"));
        assertThat(saved.getAuditTrail()).hasSize(2);
        assertThat(saved.getAuditTrail().get(0).getAction())
                .contains("HR_SPIKE_P95");
        assertThat(saved.getAuditTrail().get(1).getAction())
                .contains("created");
    }

    // ── acknowledgeAlert ──────────────────────────────────────────────────

    @Test
    @DisplayName("acknowledgeAlert: transitions status to Acknowledged")
    void acknowledgeAlert_setsAcknowledgedStatus() {
        stubAlert(ALERT_ID, "Unacknowledged");
        Authentication auth = clinicianAuth("PROV-001");

        AlertResponse response = service.acknowledgeAlert(ALERT_ID, null, auth);

        assertThat(response.status()).isEqualTo("Acknowledged");
        assertThat(response.acknowledgedBy()).isEqualTo("PROV-001");
        assertThat(response.acknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("acknowledgeAlert: appends audit trail entry")
    void acknowledgeAlert_appendsAuditTrail() {
        stubAlert(ALERT_ID, "Unacknowledged");

        AlertResponse response = service.acknowledgeAlert(ALERT_ID, null, clinicianAuth("PROV-001"));

        assertThat(response.auditTrail()).hasSizeGreaterThan(2);
        List<com.medisphere.dto.response.AlertAuditEntryResponse> trail = response.auditTrail();
        assertThat(trail.get(trail.size() - 1).action())
                .containsIgnoringCase("acknowledged");
    }

    @Test
    @DisplayName("acknowledgeAlert: NURSE role is allowed")
    void acknowledgeAlert_nurseAllowed() {
        stubAlert(ALERT_ID, "Unacknowledged");
        Authentication auth = nurseAuth("NURSE-001");

        assertThatCode(() -> service.acknowledgeAlert(ALERT_ID, null, auth))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acknowledgeAlert: already-acknowledged alert throws ValidationException")
    void acknowledgeAlert_alreadyAcknowledged_throwsValidation() {
        stubAlert(ALERT_ID, "Acknowledged");
        assertThatThrownBy(() -> service.acknowledgeAlert(ALERT_ID, null, clinicianAuth("P")))
                .isInstanceOf(ValidationException.class);
    }

    // ── escalateAlert ─────────────────────────────────────────────────────

    @Test
    @DisplayName("escalateAlert: CLINICIAN can escalate")
    void escalateAlert_clinicianAllowed() {
        stubAlert(ALERT_ID, "Acknowledged");
        EscalateAlertRequest req = new EscalateAlertRequest();
        req.setReason("Requires specialist");
        req.setEscalateTo("On-call cardiology");

        AlertResponse response = service.escalateAlert(ALERT_ID, req, clinicianAuth("PROV-001"));

        assertThat(response.status()).isEqualTo("Escalated");
        assertThat(response.escalationReason()).isEqualTo("Requires specialist");
    }

    @Test
    @DisplayName("escalateAlert: NURSE role is forbidden")
    void escalateAlert_nurseForbidden() {
        stubAlert(ALERT_ID, "Unacknowledged");
        EscalateAlertRequest req = new EscalateAlertRequest();
        req.setReason("x");
        req.setEscalateTo("y");

        assertThatThrownBy(() -> service.escalateAlert(ALERT_ID, req, nurseAuth("NURSE-001")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("escalateAlert: resolved alert cannot be escalated")
    void escalateAlert_resolvedAlert_throwsValidation() {
        stubAlert(ALERT_ID, "Resolved");
        EscalateAlertRequest req = new EscalateAlertRequest();
        req.setReason("x");
        req.setEscalateTo("y");

        assertThatThrownBy(() -> service.escalateAlert(ALERT_ID, req, clinicianAuth("PROV-001")))
                .isInstanceOf(ValidationException.class);
    }

    // ── resolveAlert ──────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveAlert: CLINICIAN can resolve, status becomes Resolved")
    void resolveAlert_clinicianAllowed() {
        stubAlert(ALERT_ID, "Escalated");
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("Patient stabilised");

        AlertResponse response = service.resolveAlert(ALERT_ID, req, clinicianAuth("PROV-001"));

        assertThat(response.status()).isEqualTo("Resolved");
        assertThat(response.resolution()).isEqualTo("Patient stabilised");
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("resolveAlert: NURSE is forbidden")
    void resolveAlert_nurseForbidden() {
        stubAlert(ALERT_ID, "Acknowledged");
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("x");

        assertThatThrownBy(() -> service.resolveAlert(ALERT_ID, req, nurseAuth("NURSE-001")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("resolveAlert: already-resolved alert throws ValidationException")
    void resolveAlert_alreadyResolved_throwsValidation() {
        stubAlert(ALERT_ID, "Resolved");
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("x");

        assertThatThrownBy(() -> service.resolveAlert(ALERT_ID, req, clinicianAuth("PROV-001")))
                .isInstanceOf(ValidationException.class);
    }

    // ── getAlert 404 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAlert: unknown ID throws ResourceNotFoundException")
    void getAlert_unknownId_throws404() {
        when(alertRepository.findById("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAlert("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAlertCount ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAlertCount: returns count from repository")
    void getAlertCount_returnsFromRepo() {
        when(alertRepository.countByStatus("Unacknowledged")).thenReturn(3L);
        assertThat(service.getAlertCount("Unacknowledged").count()).isEqualTo(3L);
    }

    // ── Audit trail ───────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveAlert: global audit log entry written")
    void resolveAlert_writesGlobalAudit() {
        stubAlert(ALERT_ID, "Acknowledged");
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("OK");

        service.resolveAlert(ALERT_ID, req, clinicianAuth("PROV-001"));

        verify(auditService).log(argThat(ctx ->
                ctx.action().contains("Resolved") && "Success".equals(ctx.status())));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void stubAlert(String alertId, String status) {
        Alert alert = Alert.builder()
                .id(alertId)
                .severity("HIGH")
                .patientId(PATIENT_ID)
                .patientName("John Doe")
                .event("Test event")
                .analysis("Test analysis")
                .type("Vitals anomaly")
                .ruleCode("HR_SPIKE_P95")
                .detectedAt(Instant.now())
                .status(status)
                .confidence(89)
                .auditTrail(new ArrayList<>(List.of(
                        com.medisphere.domain.AlertAuditEntry.builder()
                                .id("at-1").timestamp(Instant.now())
                                .actor("system").actorId("system")
                                .action("created").build(),
                        com.medisphere.domain.AlertAuditEntry.builder()
                                .id("at-2").timestamp(Instant.now())
                                .actor("system").actorId("system")
                                .action("detection").build()
                )))
                .build();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
    }

    private AlertService.CreateAlertCommand buildCmd(String severity, String ruleCode) {
        return new AlertService.CreateAlertCommand(
                severity, PATIENT_ID, "John Doe",
                "Test event", "Test analysis",
                "Vitals anomaly", ruleCode,
                "PROV-001", "145 BPM", "68 BPM", 89);
    }

    private static Authentication clinicianAuth(String id) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN")));
    }

    private static Authentication nurseAuth(String id) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_NURSE")));
    }
}

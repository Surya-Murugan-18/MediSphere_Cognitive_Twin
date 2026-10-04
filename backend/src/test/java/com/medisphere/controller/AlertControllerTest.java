package com.medisphere.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.domain.Alert;
import com.medisphere.domain.AlertAuditEntry;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import com.medisphere.dto.request.AcknowledgeAlertRequest;
import com.medisphere.dto.request.EscalateAlertRequest;
import com.medisphere.dto.request.LoginRequest;
import com.medisphere.dto.request.ResolveAlertRequest;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.ProviderRepository;
import com.medisphere.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AlertController.
 *
 * Uses embedded MongoDB and real Spring Security.
 * Exercises all endpoints with correct and incorrect roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.kafka.test.context.EmbeddedKafka(
        partitions = 1,
        topics = {"vitals.raw", "vitals.anomaly", "alert.created", "twin.update",
                  "federated.round", "careplan.approved", "fhir.ingested", "report.ready"})
@DisplayName("AlertController Integration Tests")
class AlertControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String CLINICIAN_EMAIL = "clinician@alert.test";
    private static final String NURSE_EMAIL     = "nurse@alert.test";
    private static final String PASSWORD        = "TestPass@123";
    private static final String ALERT_ID        = "A-INTTEST01";

    private String clinicianToken;
    private String nurseToken;

    @BeforeEach
    void setUp() throws Exception {
        providerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        alertRepository.deleteAll();

        // Seed CLINICIAN
        providerRepository.save(Provider.builder()
                .id("PROV-CLINICIAN").name("Dr Test").email(CLINICIAN_EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(ProviderRole.CLINICIAN).specialty("General").facility("Test")
                .notificationPrefs(NotificationPrefs.defaults()).active(true).build());

        // Seed NURSE
        providerRepository.save(Provider.builder()
                .id("PROV-NURSE").name("Nurse Test").email(NURSE_EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(ProviderRole.NURSE).specialty("").facility("Test")
                .notificationPrefs(NotificationPrefs.defaults()).active(true).build());

        // Seed a test alert
        Alert alert = Alert.builder()
                .id(ALERT_ID).severity("HIGH").patientId("P001").patientName("John Doe")
                .event("Test HR Spike").analysis("Test analysis")
                .type("Vitals anomaly").ruleCode("HR_SPIKE_P95")
                .detectedAt(Instant.now()).status("Unacknowledged")
                .confidence(89)
                .auditTrail(new ArrayList<>(List.of(
                        AlertAuditEntry.builder().id("at-1").timestamp(Instant.now())
                                .actor("system").actorId("system").action("detected").build()
                )))
                .build();
        alertRepository.save(alert);

        clinicianToken = obtainToken(CLINICIAN_EMAIL);
        nurseToken     = obtainToken(NURSE_EMAIL);
    }

    // ── GET /api/alerts ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/alerts returns 200 for authenticated user")
    void getAlerts_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/alerts")
                        .header("Authorization", "Bearer " + clinicianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/alerts returns 401 without token")
    void getAlerts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/alerts/count ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/alerts/count?status=Unacknowledged returns correct count")
    void getAlertCount_returnsCount() throws Exception {
        mockMvc.perform(get("/api/alerts/count?status=Unacknowledged")
                        .header("Authorization", "Bearer " + clinicianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // ── GET /api/alerts/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/alerts/{id} returns alert by ID")
    void getAlert_byId_returns200() throws Exception {
        mockMvc.perform(get("/api/alerts/" + ALERT_ID)
                        .header("Authorization", "Bearer " + clinicianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ALERT_ID))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("Unacknowledged"));
    }

    @Test
    @DisplayName("GET /api/alerts/{id} returns 404 for unknown alert")
    void getAlert_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/alerts/MISSING-999")
                        .header("Authorization", "Bearer " + clinicianToken))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/alerts/{id}/acknowledge ────────────────────────────────

    @Test
    @DisplayName("PATCH acknowledge: CLINICIAN can acknowledge")
    void acknowledge_clinician_returns200() throws Exception {
        mockMvc.perform(patch("/api/alerts/" + ALERT_ID + "/acknowledge")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Acknowledged"));
    }

    @Test
    @DisplayName("PATCH acknowledge: NURSE can acknowledge")
    void acknowledge_nurse_returns200() throws Exception {
        mockMvc.perform(patch("/api/alerts/" + ALERT_ID + "/acknowledge")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Acknowledged"));
    }

    @Test
    @DisplayName("PATCH acknowledge: unauthenticated returns 401")
    void acknowledge_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/alerts/" + ALERT_ID + "/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/alerts/{id}/escalate ────────────────────────────────────

    @Test
    @DisplayName("POST escalate: CLINICIAN can escalate")
    void escalate_clinician_returns200() throws Exception {
        // First acknowledge so we can escalate
        acknowledgeViaApi(ALERT_ID, clinicianToken);

        EscalateAlertRequest req = new EscalateAlertRequest();
        req.setReason("Urgent cardiology review needed");
        req.setEscalateTo("On-call cardiologist");

        mockMvc.perform(post("/api/alerts/" + ALERT_ID + "/escalate")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Escalated"));
    }

    @Test
    @DisplayName("POST escalate: NURSE returns 403")
    void escalate_nurse_returns403() throws Exception {
        EscalateAlertRequest req = new EscalateAlertRequest();
        req.setReason("x");
        req.setEscalateTo("y");

        mockMvc.perform(post("/api/alerts/" + ALERT_ID + "/escalate")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/alerts/{id}/resolve ─────────────────────────────────────

    @Test
    @DisplayName("POST resolve: CLINICIAN can resolve")
    void resolve_clinician_returns200() throws Exception {
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("Patient stabilised, normal sinus rhythm");

        mockMvc.perform(post("/api/alerts/" + ALERT_ID + "/resolve")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Resolved"))
                .andExpect(jsonPath("$.resolution").value("Patient stabilised, normal sinus rhythm"));
    }

    @Test
    @DisplayName("POST resolve: NURSE returns 403")
    void resolve_nurse_returns403() throws Exception {
        ResolveAlertRequest req = new ResolveAlertRequest();
        req.setResolution("x");

        mockMvc.perform(post("/api/alerts/" + ALERT_ID + "/resolve")
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String obtainToken(String email) throws Exception {
        LoginRequest req = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode root =
                objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("accessToken").asText();
    }

    private void acknowledgeViaApi(String alertId, String token) throws Exception {
        mockMvc.perform(patch("/api/alerts/" + alertId + "/acknowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}

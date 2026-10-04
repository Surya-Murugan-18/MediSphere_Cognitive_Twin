package com.medisphere.integration;

import com.medisphere.domain.Alert;
import com.medisphere.domain.AlertAuditEntry;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.repository.AlertRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.HealthTwinRepository;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.WearableDeviceRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 8 — B8.5 AlertIntegrationTest
 *
 * Verifies the full alert pipeline against real Testcontainers infrastructure:
 *   1. Publish vitals.raw Kafka message with anomalous SpO2
 *   2. VitalsKafkaConsumer processes → AnomalyDetectionService fires SPO2_LOW rule
 *   3. AlertService creates Alert document in MongoDB
 *   4. Alert is queryable via REST API
 *   5. Acknowledge alert → status persisted in MongoDB
 *
 * Alert determinism:
 *   The SPO2_LOW rule triggers when spo2 < 94.0 (configurable, default 94.0).
 *   This rule does NOT require a rolling baseline window (unlike HR_SPIKE_P95),
 *   making it the most reliable trigger for integration testing.
 *   Sending spo2 = 90.0 will always fire SPO2_LOW deterministically.
 *
 * Async synchronisation:
 *   Kafka consumption and alert creation are async operations.
 *   Awaitility polls MongoDB until the alert appears — max 20 seconds.
 *   No arbitrary Thread.sleep() calls.
 *
 * WebSocket push:
 *   VitalsWebSocketBroadcaster and KafkaEventBroadcaster push to connected
 *   STOMP sessions. There are no STOMP clients in this integration test — those
 *   pushes complete without error (no subscribers = no-op). Full WebSocket
 *   subscription testing belongs in E2E (Playwright tests).
 */
@DisplayName("Alert Integration Tests — Testcontainers")
class AlertIntegrationTest extends IntegrationTestBase {

    @Autowired private AlertRepository       alertRepository;
    @Autowired private PatientRepository     patientRepository;
    @Autowired private HealthTwinRepository  twinRepository;
    @Autowired private ConsentRepository     consentRepository;
    @Autowired private WearableDeviceRepository deviceRepository;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String VITALS_RAW_TOPIC = "vitals.raw";
    private String testPatientId;

    @BeforeEach
    void clearAlertState() throws Exception {
        alertRepository.deleteAll();
        patientRepository.deleteAll();
        twinRepository.deleteAll();
        consentRepository.deleteAll();
        deviceRepository.deleteAll();

        // Create a patient so AnomalyDetectionService can resolve it from MongoDB
        testPatientId = createAlertTestPatient();
    }

    // ── Test 1: Kafka → anomaly → alert created ───────────────────────────────

    @Test
    @DisplayName("Publishing low SpO2 vitals.raw message creates SPO2_LOW alert in MongoDB")
    void publishLowSpo2_createsAlertInMongoDB() {
        // Publish a vitals.raw message with SpO2 = 90.0 (well below 94.0 threshold)
        Map<String, Object> vitalsPayload = Map.of(
                "patientId", testPatientId,
                "deviceId",  "SW-INTTEST-01",
                "type",      "spo2",
                "value",     90.0,
                "unit",      "%",
                "timestamp", Instant.now().toString()
        );

        kafkaTemplate.send(VITALS_RAW_TOPIC, testPatientId, vitalsPayload);

        // Poll MongoDB until the SPO2_LOW alert appears — max 20 seconds
        Awaitility.await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<Alert> alerts = alertRepository.findAll().stream()
                            .filter(a -> testPatientId.equals(a.getPatientId()))
                            .filter(a -> "SPO2_LOW".equals(a.getRuleCode()))
                            .toList();
                    assertThat(alerts)
                            .as("SPO2_LOW alert should be created after vitals.raw message")
                            .isNotEmpty();
                });

        // Verify alert details in MongoDB
        Alert alert = alertRepository.findAll().stream()
                .filter(a -> testPatientId.equals(a.getPatientId()))
                .filter(a -> "SPO2_LOW".equals(a.getRuleCode()))
                .findFirst()
                .orElseThrow();

        assertThat(alert.getSeverity()).isIn("HIGH", "MEDIUM");
        assertThat(alert.getStatus()).isEqualTo("Unacknowledged");
        assertThat(alert.getPatientId()).isEqualTo(testPatientId);
        assertThat(alert.getCurrentValue()).contains("90");
        assertThat(alert.getAuditTrail()).isNotEmpty();
    }

    // ── Test 2: Alert queryable via REST API ──────────────────────────────────

    @Test
    @DisplayName("Alert created from Kafka message is queryable via REST API")
    void alertFromKafka_isQueryableViaAPI() throws Exception {
        // Publish anomalous vitals
        kafkaTemplate.send(VITALS_RAW_TOPIC, testPatientId, Map.of(
                "patientId", testPatientId,
                "deviceId",  "SW-INTTEST-01",
                "type",      "spo2",
                "value",     88.0,
                "unit",      "%",
                "timestamp", Instant.now().toString()
        ));

        // Wait for alert to be created
        Awaitility.await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(alertRepository.findAll().stream()
                                .anyMatch(a -> testPatientId.equals(a.getPatientId())))
                                .isTrue()
                );

        String alertId = alertRepository.findAll().stream()
                .filter(a -> testPatientId.equals(a.getPatientId()))
                .findFirst()
                .orElseThrow()
                .getId();

        String token = clinicianToken();

        // Fetch via REST API — must return the alert
        mockMvc.perform(get("/api/alerts/" + alertId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertId))
                .andExpect(jsonPath("$.patientId").value(testPatientId))
                .andExpect(jsonPath("$.ruleCode").value("SPO2_LOW"))
                .andExpect(jsonPath("$.status").value("Unacknowledged"));
    }

    // ── Test 3: Acknowledge alert → status persisted in MongoDB ──────────────

    @Test
    @DisplayName("PATCH acknowledge alert → Acknowledged status persisted in real MongoDB")
    void acknowledgeAlert_statusPersistedInMongoDB() throws Exception {
        // Create a direct alert in MongoDB for this test (no need to go through Kafka)
        Alert alert = Alert.builder()
                .id("A-INTTEST-ACK-01")
                .severity("HIGH")
                .patientId(testPatientId)
                .patientName("Alert Integration Patient")
                .event("SpO2 critically low: 88 %")
                .analysis("SpO2 below clinical threshold. Immediate review required.")
                .type("Vitals anomaly")
                .ruleCode("SPO2_LOW")
                .detectedAt(Instant.now())
                .status("Unacknowledged")
                .currentValue("88 %")
                .previousValue("97 %")
                .confidence(95)
                .auditTrail(new ArrayList<>(List.of(
                        AlertAuditEntry.builder()
                                .id("AT-INTTEST-01")
                                .timestamp(Instant.now())
                                .actor("system")
                                .actorId("system")
                                .action("SPO2_LOW rule triggered")
                                .build()
                )))
                .build();
        alertRepository.save(alert);

        String token = clinicianToken();

        // Acknowledge via API
        mockMvc.perform(patch("/api/alerts/A-INTTEST-ACK-01/acknowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Acknowledged"))
                .andExpect(jsonPath("$.acknowledgedBy").isNotEmpty());

        // Verify the Acknowledged status was actually persisted to the real MongoDB container
        Alert persisted = alertRepository.findById("A-INTTEST-ACK-01").orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("Acknowledged");
        assertThat(persisted.getAcknowledgedBy()).isNotEmpty();
        assertThat(persisted.getAcknowledgedAt()).isNotNull();

        // Audit trail should have a new entry
        assertThat(persisted.getAuditTrail().size()).isGreaterThan(1);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String createAlertTestPatient() throws Exception {
        String token = clinicianToken();

        CreatePatientRequest req = new CreatePatientRequest(
                "Alert Integration Patient",
                "1972-05-10",
                "Male",
                null, null,
                "fhir:Patient/alert-inttest-001",
                "Epic",
                List.of("COPD", "Hypertension"),
                new CreatePatientRequest.ConsentFlags(true, true, true));

        MvcResult result = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}

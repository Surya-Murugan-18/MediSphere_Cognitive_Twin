package com.medisphere.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.repository.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 8 — B8.5 PatientIntegrationTest
 *
 * Verifies the full patient lifecycle against real containerized infrastructure:
 *   1. Create patient → digital twin auto-created → consent auto-created
 *   2. Prediction execution triggered (async — awaited via Awaitility)
 *   3. FHIR labs ingested into real MongoDB
 *
 * Uses Testcontainers MongoDB 7.0 + Kafka from IntegrationTestBase.
 *
 * Awaitility replaces Thread.sleep() for async operation synchronisation:
 *   - prediction run is @Async → poll MongoDB until record appears
 *   - maximum wait: 15 seconds (StubAIPredictionService is fast)
 */
@DisplayName("Patient Integration Tests — Testcontainers")
class PatientIntegrationTest extends IntegrationTestBase {

    @Autowired private PatientRepository    patientRepository;
    @Autowired private HealthTwinRepository twinRepository;
    @Autowired private ConsentRepository    consentRepository;
    @Autowired private PredictionRepository predictionRepository;
    @Autowired private LabResultRepository  labResultRepository;

    @BeforeEach
    void clearPatientState() {
        patientRepository.deleteAll();
        twinRepository.deleteAll();
        consentRepository.deleteAll();
        predictionRepository.deleteAll();
        labResultRepository.deleteAll();
    }

    // ── Test 1: Create patient → twin auto-created ────────────────────────────

    @Test
    @DisplayName("POST /api/patients creates patient with twin and consent in real MongoDB")
    void createPatient_twinAndConsentAutoCreated() throws Exception {
        String token = clinicianToken();

        CreatePatientRequest request = new CreatePatientRequest(
                "Integration Test Patient",
                "1975-03-22",
                "Male",
                "+1 (415) 555-0200",
                null,
                "fhir:Patient/inttest-001",
                "Epic Integration",
                List.of("Hypertension", "Type 2 Diabetes"),
                new CreatePatientRequest.ConsentFlags(true, true, true));

        MvcResult result = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Integration Test Patient"))
                .andExpect(jsonPath("$.fhirId").value("fhir:Patient/inttest-001"))
                .andExpect(jsonPath("$.twinId").isNotEmpty())
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        String patientId = responseBody.get("id").asText();
        String twinId    = responseBody.get("twinId").asText();

        // Verify patient is in the real MongoDB container
        assertThat(patientRepository.findById(patientId)).isPresent();

        // Verify digital twin was created in real MongoDB container
        assertThat(twinRepository.findById(twinId)).isPresent();
        assertThat(twinRepository.findByPatientId(patientId)).isPresent();

        // Verify consent was created in real MongoDB container
        assertThat(consentRepository.findByPatientId(patientId)).isPresent();

        // Verify consent flags match the request
        var consent = consentRepository.findByPatientId(patientId).orElseThrow();
        assertThat(consent.isEhr()).isTrue();
        assertThat(consent.isWearable()).isTrue();
        assertThat(consent.isAi()).isTrue();
    }

    // ── Test 2: Prediction execution after patient creation ───────────────────

    @Test
    @DisplayName("Creating patient triggers async AI prediction execution")
    void createPatient_predictionsExecutedAsynchronously() throws Exception {
        String token = clinicianToken();

        CreatePatientRequest request = new CreatePatientRequest(
                "Prediction Test Patient",
                "1960-08-14",
                "Female",
                null, null,
                "fhir:Patient/inttest-002",
                "Epic",
                List.of("Diabetes", "Hypertension"),
                new CreatePatientRequest.ConsentFlags(true, false, true));

        MvcResult result = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String patientId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        // PatientService.createPatient triggers async runPredictions.
        // StubAIPredictionService is deterministic and fast but still async.
        // Poll MongoDB until at least one prediction document appears — max 15s.
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(predictionRepository.existsByPatientIdAndModel(patientId, "CVD-Risk-v3.2"))
                                .as("CVD-Risk prediction should be created by StubAIPredictionService")
                                .isTrue()
                );

        // Verify prediction content from real MongoDB
        var predictions = predictionRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        assertThat(predictions).isNotEmpty();
        assertThat(predictions.get(0).getPatientId()).isEqualTo(patientId);
        assertThat(predictions.get(0).getValue()).isGreaterThan(0.0);
    }

    // ── Test 3: Lab ingestion via TwinService sync ────────────────────────────

    @Test
    @DisplayName("POST /api/twins/{twinId}/sync ingests FHIR labs into real MongoDB")
    void syncTwin_ingestsFHIRLabsIntoMongoDB() throws Exception {
        String token = clinicianToken();

        // Create a patient with FHIR ID so the mock FHIR client can respond
        CreatePatientRequest request = new CreatePatientRequest(
                "Lab Ingestion Patient",
                "1980-01-01",
                "Male",
                null, null,
                "fhir:Patient/inttest-003",
                "Epic",
                List.of("Diabetes"),
                new CreatePatientRequest.ConsentFlags(true, true, true));

        MvcResult createResult = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode patientBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String patientId = patientBody.get("id").asText();
        String twinId    = patientBody.get("twinId").asText();

        // Trigger a full twin sync which will call MockFHIRClient.fetchObservations
        // and persist LabResults via FHIRResourceMapper
        mockMvc.perform(post("/api/twins/" + twinId + "/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(twinId));

        // MockFHIRClient returns deterministic observations.
        // Wait for async lab ingestion to complete and appear in MongoDB.
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var labs = labResultRepository.findAll().stream()
                            .filter(l -> patientId.equals(l.getPatientId()))
                            .toList();
                    assertThat(labs)
                            .as("Lab results should be ingested from MockFHIRClient")
                            .isNotEmpty();
                });
    }

    // ── Test 4: Duplicate FHIR ID returns 409 ─────────────────────────────────

    @Test
    @DisplayName("POST /api/patients with duplicate fhirId returns 409 Conflict")
    void createPatient_duplicateFhirId_returns409() throws Exception {
        String token = clinicianToken();
        String fhirId = "fhir:Patient/inttest-dup-001";

        CreatePatientRequest req = new CreatePatientRequest(
                "First Patient", "1985-01-01", "Male",
                null, null, fhirId, "Epic", null, null);

        // Create once — succeeds
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Create again with same fhirId — should conflict
        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }
}

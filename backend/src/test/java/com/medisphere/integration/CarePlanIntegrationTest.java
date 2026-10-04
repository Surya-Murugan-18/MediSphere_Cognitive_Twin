package com.medisphere.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.medisphere.domain.CarePlanStatus;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.dto.request.GenerateCarePlanRequest;
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
 * Phase 8 — B8.5 CarePlanIntegrationTest
 *
 * Verifies the complete care plan lifecycle against real Testcontainers infrastructure:
 *   1. Generate care plan → Draft plan persisted in real MongoDB
 *   2. Review care plan → plan content retrievable via REST
 *   3. Approve care plan → status ACTIVE in real MongoDB
 *   4. Record adherence → adherence data queryable via REST
 *
 * Uses:
 *   - StubAICarePlanService (AI_CAREPLAN_MODE=stub) — deterministic, no external AI
 *   - Real MongoDB Testcontainer for persistence
 *   - Real Kafka Testcontainer for careplan.approved event publishing
 *   - Awaitility for async operation synchronisation
 *
 * Pre-conditions per test:
 *   - A patient is created (POST /api/patients)
 *   - Predictions are awaited before care plan generation (AI stub requires patient context)
 */
@DisplayName("Care Plan Integration Tests — Testcontainers")
class CarePlanIntegrationTest extends IntegrationTestBase {

    @Autowired private PatientRepository     patientRepository;
    @Autowired private HealthTwinRepository  twinRepository;
    @Autowired private ConsentRepository     consentRepository;
    @Autowired private CarePlanRepository    carePlanRepository;
    @Autowired private AdherenceRepository   adherenceRepository;
    @Autowired private PredictionRepository  predictionRepository;

    private String testPatientId;

    @BeforeEach
    void clearCarePlanState() throws Exception {
        patientRepository.deleteAll();
        twinRepository.deleteAll();
        consentRepository.deleteAll();
        carePlanRepository.deleteAll();
        adherenceRepository.deleteAll();
        predictionRepository.deleteAll();

        // Create a patient and wait for predictions (StubAI generates them on patient create)
        testPatientId = createCarePlanTestPatient();

        // Wait for async prediction to complete before care plan generation
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(predictionRepository.existsByPatientIdAndModel(testPatientId, "CVD-Risk-v3.2"))
                                .as("Predictions must exist before care plan generation")
                                .isTrue()
                );
    }

    // ── Test 1: Generate care plan → Draft in MongoDB ─────────────────────────

    @Test
    @DisplayName("POST /api/care-plans/generate creates Draft care plan in real MongoDB")
    void generateCarePlan_createsDraftPlan() throws Exception {
        String token = clinicianToken();

        GenerateCarePlanRequest req = new GenerateCarePlanRequest(testPatientId);

        MvcResult result = mockMvc.perform(post("/api/care-plans/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.patientId").value(testPatientId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String planId = body.get("id").asText();

        // Verify the plan was actually persisted to the real MongoDB container
        assertThat(carePlanRepository.findById(planId)).isPresent();

        var plan = carePlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(plan.getPatientId()).isEqualTo(testPatientId);
        assertThat(plan.getRecommendations()).isNotEmpty();
        assertThat(plan.getGoal()).isNotBlank();
    }

    // ── Test 2: Review (GET) care plan content ────────────────────────────────

    @Test
    @DisplayName("GET /api/care-plans/{id} retrieves Draft plan with full recommendation detail")
    void getCarePlan_returnsDraftWithRecommendations() throws Exception {
        String token = clinicianToken();

        // Generate plan
        String planId = generatePlan(token);

        // Retrieve via GET
        mockMvc.perform(get("/api/care-plans/" + planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.patientId").value(testPatientId))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.recommendations[0].action").isNotEmpty())
                .andExpect(jsonPath("$.riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.goal").isNotEmpty());
    }

    // ── Test 3: Approve → status ACTIVE in MongoDB ───────────────────────────

    @Test
    @DisplayName("POST /api/care-plans/{id}/approve sets status ACTIVE in real MongoDB")
    void approveCarePlan_statusActiveInMongoDB() throws Exception {
        String token = clinicianToken();
        String planId = generatePlan(token);

        // Approve the plan
        mockMvc.perform(post("/api/care-plans/" + planId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.approvedBy").isNotEmpty())
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        // Verify ACTIVE status actually persisted to real MongoDB
        var plan = carePlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(plan.getApprovedBy()).isNotBlank();
        assertThat(plan.getApprovedAt()).isNotNull();
    }

    // ── Test 4: Adherence queryable after recording ───────────────────────────

    @Test
    @DisplayName("POST /api/care-plans/{id}/adherence records data queryable via GET adherence")
    void recordAdherence_adherenceIsQueryable() throws Exception {
        String token = clinicianToken();
        String planId = generatePlan(token);

        // First approve the plan (adherence recording requires ACTIVE plan)
        mockMvc.perform(post("/api/care-plans/" + planId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Record week 1 adherence
        String adherenceBody = """
                {
                    "medicationAdherence": 85,
                    "monitoringAdherence": 90,
                    "lifestyleAdherence": 75,
                    "notes": "Good week — diet improved"
                }
                """;

        mockMvc.perform(post("/api/care-plans/" + planId + "/adherence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adherenceBody))
                .andExpect(status().isNoContent());

        // Verify adherence record in MongoDB
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(adherenceRepository.findByPlanIdOrderByWeekNumberAsc(planId))
                                .as("Adherence record should be persisted to real MongoDB")
                                .isNotEmpty()
                );

        // Query adherence via REST API
        mockMvc.perform(get("/api/care-plans/" + planId + "/adherence")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").isNumber())
                .andExpect(jsonPath("$.weeklyTrend").isArray());

        // Query adherence trend
        mockMvc.perform(get("/api/care-plans/" + planId + "/adherence/trend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── Test 5: Reject care plan → status REJECTED ────────────────────────────

    @Test
    @DisplayName("POST /api/care-plans/{id}/reject sets status REJECTED in real MongoDB")
    void rejectCarePlan_statusRejectedInMongoDB() throws Exception {
        String token = clinicianToken();
        String planId = generatePlan(token);

        String rejectBody = "{\"reason\": \"Recommendations do not align with current protocol\"}";

        mockMvc.perform(post("/api/care-plans/" + planId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify REJECTED status in MongoDB
        var plan = carePlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getStatus()).isEqualTo(CarePlanStatus.REJECTED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generate a care plan for the test patient and return its ID.
     */
    private String generatePlan(String token) throws Exception {
        GenerateCarePlanRequest req = new GenerateCarePlanRequest(testPatientId);

        MvcResult result = mockMvc.perform(post("/api/care-plans/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    /**
     * Create a patient suitable for care plan generation and return its ID.
     */
    private String createCarePlanTestPatient() throws Exception {
        String token = clinicianToken();

        CreatePatientRequest req = new CreatePatientRequest(
                "CarePlan Integration Patient",
                "1965-11-20",
                "Female",
                null, null,
                "fhir:Patient/careplan-inttest-001",
                "Epic",
                List.of("Type 2 Diabetes", "Hypertension", "Hyperlipidemia"),
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

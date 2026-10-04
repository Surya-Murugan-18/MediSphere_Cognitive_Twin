package com.medisphere.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.dto.request.UpdatePatientRequest;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.HealthTwinRepository;
import com.medisphere.repository.PatientRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.kafka.test.context.EmbeddedKafka(
        partitions = 1,
        topics = {"vitals.raw", "vitals.anomaly", "alert.created", "twin.update",
                  "federated.round", "careplan.approved", "fhir.ingested", "report.ready"})
@DisplayName("PatientController Integration Tests")
class PatientControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PatientRepository patientRepository;
    @Autowired private HealthTwinRepository twinRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String CLINICIAN_EMAIL = "clinician@patient-test.dev";
    private static final String CLINICIAN_PASS  = "Clinician@123";
    private static final String NURSE_EMAIL     = "nurse@patient-test.dev";
    private static final String NURSE_PASS      = "Nurse@123";

    @BeforeEach
    void setUp() {
        patientRepository.deleteAll();
        twinRepository.deleteAll();
        consentRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        providerRepository.deleteAll();

        providerRepository.save(Provider.builder()
                .id("PROV-CL").name("Dr. Clinician").email(CLINICIAN_EMAIL)
                .passwordHash(passwordEncoder.encode(CLINICIAN_PASS))
                .role(ProviderRole.CLINICIAN).specialty("Cardiology")
                .facility("Test Hospital").notificationPrefs(NotificationPrefs.defaults())
                .active(true).build());

        providerRepository.save(Provider.builder()
                .id("PROV-NU").name("Nurse Test").email(NURSE_EMAIL)
                .passwordHash(passwordEncoder.encode(NURSE_PASS))
                .role(ProviderRole.NURSE).specialty("General")
                .facility("Test Hospital").notificationPrefs(NotificationPrefs.defaults())
                .active(true).build());
    }

    // ── GET /api/patients ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/patients returns 200 with paginated list for authenticated user")
    void getPatients_authenticated_returns200() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        mockMvc.perform(get("/api/patients")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/patients returns 401 when not authenticated")
    void getPatients_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/patients supports server-side filtering by status")
    void getPatients_withStatusFilter_returnsFiltered() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        // Create one patient via API
        createTestPatient(token, "Filter Patient", "fhir:Patient/fp-001");

        mockMvc.perform(get("/api/patients?status=Active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/patients supports pagination parameters")
    void getPatients_pagination_respectedInResponse() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        mockMvc.perform(get("/api/patients?page=0&size=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.page").value(0));
    }

    // ── POST /api/patients ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/patients creates patient + twin + consent, returns 201")
    void createPatient_clinician_returns201WithTwin() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        CreatePatientRequest req = new CreatePatientRequest(
                "Alice Testpatient", "1990-06-15", "Female",
                "+1 (415) 555-0100", null,
                "fhir:Patient/test-alice-001", "Epic",
                List.of("Diabetes"),
                new CreatePatientRequest.ConsentFlags(true, false, true));

        MvcResult result = mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Alice Testpatient"))
                .andExpect(jsonPath("$.fhirId").value("fhir:Patient/test-alice-001"))
                .andExpect(jsonPath("$.twinId").isNotEmpty())
                .andReturn();

        // Verify digital twin was created
        String body = result.getResponse().getContentAsString();
        String patientId = objectMapper.readTree(body).get("id").asText();
        assertThat(twinRepository.findByPatientId(patientId)).isPresent();

        // Verify consent was created
        assertThat(consentRepository.findByPatientId(patientId)).isPresent();
    }

    @Test
    @DisplayName("POST /api/patients returns 403 when NURSE tries to create")
    void createPatient_nurseRole_returns403() throws Exception {
        String token = loginAndGetToken(NURSE_EMAIL, NURSE_PASS);

        CreatePatientRequest req = new CreatePatientRequest(
                "Bob Forbidden", "1985-01-01", "Male",
                null, null, "fhir:Patient/bob-forbidden", "Epic",
                null, null);

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/patients returns 400 when required fields missing")
    void createPatient_missingName_returns400() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        String body = "{\"name\":\"\",\"dob\":\"1990-01-01\",\"gender\":\"Male\",\"fhirId\":\"fhir:Patient/x\"}";

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("POST /api/patients returns 409 when fhirId already exists")
    void createPatient_duplicateFhirId_returns409() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);
        String fhirId = "fhir:Patient/dup-test-001";

        // Create first patient
        createTestPatient(token, "First Patient", fhirId);

        // Attempt duplicate
        CreatePatientRequest req = new CreatePatientRequest(
                "Second Patient", "1980-01-01", "Male",
                null, null, fhirId, "Epic", null, null);

        mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ── GET /api/patients/next-id ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/patients/next-id returns formatted patient ID")
    void getNextId_authenticated_returnsFormattedId() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        mockMvc.perform(get("/api/patients/next-id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextId").isNotEmpty());
    }

    // ── GET /api/patients/filter-options ─────────────────────────────────

    @Test
    @DisplayName("GET /api/patients/filter-options returns available filter values")
    void getFilterOptions_returns200WithOptions() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        mockMvc.perform(get("/api/patients/filter-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses").isArray())
                .andExpect(jsonPath("$.riskLevels").isArray());
    }

    // ── GET /api/patients/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/patients/{id} returns full patient with twin status")
    void getPatient_existingId_returnsFullResponse() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        MvcResult created = createTestPatient(token, "Get Test Patient", "fhir:Patient/get-test-001");
        String patientId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/patients/" + patientId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patientId))
                .andExpect(jsonPath("$.twinId").isNotEmpty())
                .andExpect(jsonPath("$.twinStatus").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/patients/{id} returns 404 for unknown id")
    void getPatient_unknownId_returns404() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        mockMvc.perform(get("/api/patients/P_NOT_EXIST")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/patients/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/patients/{id} updates patient fields")
    void updatePatient_validRequest_returnsUpdated() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        MvcResult created = createTestPatient(token, "Update Me", "fhir:Patient/update-me-001");
        String patientId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        UpdatePatientRequest update = new UpdatePatientRequest(
                "Updated Name", null, null, null, null,
                null, null, List.of("Hypertension"), "Active", "High",
                "Stable", 85);

        mockMvc.perform(put("/api/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.riskLevel").value("High"))
                .andExpect(jsonPath("$.adherence").value(85));
    }

    @Test
    @DisplayName("PUT /api/patients/{id} returns 403 for NURSE role")
    void updatePatient_nurseRole_returns403() throws Exception {
        String clinicianToken = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);
        String nurseToken = loginAndGetToken(NURSE_EMAIL, NURSE_PASS);

        MvcResult created = createTestPatient(clinicianToken, "Nurse Cannot Edit", "fhir:Patient/nurse-edit-001");
        String patientId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        UpdatePatientRequest update = new UpdatePatientRequest(
                "Hacked", null, null, null, null,
                null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/patients/" + patientId)
                        .header("Authorization", "Bearer " + nurseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/patients/{id}/timeline ──────────────────────────────────

    @Test
    @DisplayName("GET /api/patients/{id}/timeline returns timeline events")
    void getTimeline_existingPatient_returnsEvents() throws Exception {
        String token = loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);

        MvcResult created = createTestPatient(token, "Timeline Test", "fhir:Patient/timeline-001");
        String patientId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/patients/" + patientId + "/timeline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Twin created"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private MvcResult createTestPatient(String token, String name, String fhirId) throws Exception {
        CreatePatientRequest req = new CreatePatientRequest(
                name, "1985-06-15", "Male",
                null, null, fhirId, "Epic",
                null, new CreatePatientRequest.ConsentFlags(true, false, true));
        return mockMvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
    }
}

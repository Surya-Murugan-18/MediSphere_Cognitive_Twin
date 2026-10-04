package com.medisphere.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import com.medisphere.dto.request.LoginRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AuthController.
 * Uses embedded MongoDB (de.flapdoodle) and embedded Kafka broker (@EmbeddedKafka).
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.kafka.test.context.EmbeddedKafka(
        partitions = 1,
        topics = {"vitals.raw", "vitals.anomaly", "alert.created", "twin.update",
                  "federated.round", "careplan.approved", "fhir.ingested", "report.ready"})
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String TEST_EMAIL    = "test.provider@medisphere.test";
    private static final String TEST_PASSWORD = "TestPass@123";
    private static final String TEST_ID       = "PROV-TEST";

    @BeforeEach
    void setUp() {
        // Clean state before each test
        providerRepository.deleteAll();
        refreshTokenRepository.deleteAll();

        // Create test provider
        Provider provider = Provider.builder()
                .id(TEST_ID)
                .name("Test Provider")
                .email(TEST_EMAIL)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(ProviderRole.CLINICIAN)
                .specialty("Cardiology")
                .facility("Test Hospital")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build();
        providerRepository.save(provider);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 and accessToken")
    void login_validCredentials_returns200() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.provider.id").value(TEST_ID))
                .andExpect(jsonPath("$.provider.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.provider.role").value("CLINICIAN"))
                // passwordHash must NEVER appear in response
                .andExpect(jsonPath("$.provider.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login sets httpOnly refresh_token cookie")
    void login_validCredentials_setsRefreshCookie() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Cookie should be set (exact format varies by servlet container)
        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refresh_token");
        assertThat(setCookieHeader).contains("HttpOnly");
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, "WrongPassword@999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login with unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        LoginRequest request = new LoginRequest("nobody@medisphere.test", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login with missing email returns 400")
    void login_missingEmail_returns400() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"" + TEST_PASSWORD + "\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login with missing password returns 400")
    void login_missingPassword_returns400() throws Exception {
        String body = "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login with invalid email format returns 400")
    void login_invalidEmailFormat_returns400() throws Exception {
        LoginRequest request = new LoginRequest("not-an-email", TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /me ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me with valid JWT returns 200 and provider profile")
    void getMe_validToken_returns200() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_ID))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.role").value("CLINICIAN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me without token returns 401")
    void getMe_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me with invalid token returns 401")
    void getMe_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout with valid token returns 204")
    void logout_validToken_returns204() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout without token still returns 204 (idempotent)")
    void logout_noToken_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh without cookie returns 401")
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String loginAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}

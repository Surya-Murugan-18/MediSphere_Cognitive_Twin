package com.medisphere.integration;

import com.medisphere.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 8 — B8.5 AuthIntegrationTest
 *
 * Full login → refresh → logout cycle against a real MongoDB Testcontainer
 * and real Kafka Testcontainer.
 *
 * Extends IntegrationTestBase which:
 *   - starts MongoDB 7.0 + Kafka containers (static, shared within class)
 *   - injects dynamic connection properties into Spring context
 *   - seeds CLINICIAN + ADMIN providers before each test
 *   - provides loginAndGetToken() / clinicianToken() helpers
 *
 * These tests prove the full security filter chain works with real storage:
 *   - BCrypt password verification against MongoDB-persisted hash
 *   - JWT generation and validation
 *   - Refresh token persistence and retrieval from MongoDB
 *   - Refresh token revocation on logout
 */
@DisplayName("Auth Integration Tests — Testcontainers")
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login with valid credentials persists refresh token and returns 200")
    void login_validCredentials_returns200AndPersistsRefreshToken() throws Exception {
        long tokensBefore = refreshTokenRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CLINICIAN_EMAIL + "\",\"password\":\"" + CLINICIAN_PASS + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.provider.email").value(CLINICIAN_EMAIL))
                .andExpect(jsonPath("$.provider.role").value("CLINICIAN"))
                // Sensitive field must never appear in response
                .andExpect(jsonPath("$.provider.passwordHash").doesNotExist());

        // Verify refresh token was actually persisted to the real MongoDB container
        assertThat(refreshTokenRepository.count()).isEqualTo(tokensBefore + 1);
    }

    @Test
    @DisplayName("POST /api/auth/login sets httpOnly refresh_token cookie")
    void login_validCredentials_setsHttpOnlyCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CLINICIAN_EMAIL + "\",\"password\":\"" + CLINICIAN_PASS + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refresh_token");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password returns 401 and does not persist token")
    void login_wrongPassword_returns401() throws Exception {
        long tokensBefore = refreshTokenRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CLINICIAN_EMAIL + "\",\"password\":\"WrongPass999!\"}"))
                .andExpect(status().isUnauthorized());

        // No token should have been persisted
        assertThat(refreshTokenRepository.count()).isEqualTo(tokensBefore);
    }

    @Test
    @DisplayName("POST /api/auth/login with unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@integration.test\",\"password\":\"SomePass@1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /me ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me with valid JWT returns 200 and correct profile from real DB")
    void getMe_validToken_returnsProviderProfile() throws Exception {
        String token = clinicianToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CLINICIAN_EMAIL))
                .andExpect(jsonPath("$.role").value("CLINICIAN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me without token returns 401")
    void getMe_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh with valid cookie returns new access token")
    void refresh_validCookie_returnsNewToken() throws Exception {
        // Login to get the refresh_token cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CLINICIAN_EMAIL + "\",\"password\":\"" + CLINICIAN_PASS + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the Set-Cookie header value
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();

        // Extract just the cookie name=value portion (before the first semicolon)
        String cookieValue = setCookie.split(";")[0].trim();

        // Use the refresh cookie to get a new access token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("Cookie", cookieValue))
                .andExpect(status().isOk())
                .andReturn();

        String newToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(newToken).isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/auth/refresh without cookie returns 401")
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout revokes all refresh tokens in real MongoDB")
    void logout_revokesRefreshTokensInDatabase() throws Exception {
        // Login — this persists a refresh token to MongoDB
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CLINICIAN_EMAIL + "\",\"password\":\"" + CLINICIAN_PASS + "\"}"))
                .andExpect(status().isOk());

        long tokensAfterLogin = refreshTokenRepository.count();
        assertThat(tokensAfterLogin).isGreaterThan(0);

        String accessToken = clinicianToken();

        // Logout — should delete refresh tokens from MongoDB
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // All refresh tokens for this provider should be gone from MongoDB
        assertThat(refreshTokenRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /api/auth/logout without token returns 204 (idempotent)")
    void logout_noToken_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}

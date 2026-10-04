package com.medisphere.security;

import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "test-only-jwt-secret-key-for-unit-tests-minimum-32-chars";

    private JwtTokenProvider tokenProvider;
    private Provider testProvider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(TEST_SECRET, 900L, 86400L, "medisphere");
        tokenProvider = new JwtTokenProvider(props);

        testProvider = Provider.builder()
                .id("PROV-001")
                .name("Dr. A. Mehta")
                .email("a.mehta@medisphere.dev")
                .passwordHash("$2a$12$hashed")
                .role(ProviderRole.CLINICIAN)
                .specialty("Cardiology")
                .facility("Hospital A")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build();
    }

    @Test
    @DisplayName("generateAccessToken returns non-blank JWT string")
    void generateAccessToken_returnsToken() {
        String token = tokenProvider.generateAccessToken(testProvider);
        assertThat(token).isNotBlank();
        // JWT format: three dot-separated base64 segments
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("validateAccessToken returns true for a freshly generated token")
    void validateAccessToken_validToken_returnsTrue() {
        String token = tokenProvider.generateAccessToken(testProvider);
        assertThat(tokenProvider.validateAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateAccessToken returns false for a tampered token")
    void validateAccessToken_tamperedToken_returnsFalse() {
        String token = tokenProvider.generateAccessToken(testProvider);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(tokenProvider.validateAccessToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateAccessToken returns false for blank string")
    void validateAccessToken_blank_returnsFalse() {
        assertThat(tokenProvider.validateAccessToken("")).isFalse();
        assertThat(tokenProvider.validateAccessToken("   ")).isFalse();
    }

    @Test
    @DisplayName("validateAccessToken returns false for completely invalid string")
    void validateAccessToken_garbage_returnsFalse() {
        assertThat(tokenProvider.validateAccessToken("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("extractProviderId returns correct subject claim")
    void extractProviderId_returnsCorrectId() {
        String token = tokenProvider.generateAccessToken(testProvider);
        assertThat(tokenProvider.extractProviderId(token)).isEqualTo("PROV-001");
    }

    @Test
    @DisplayName("extractRole returns correct role claim")
    void extractRole_returnsCorrectRole() {
        String token = tokenProvider.generateAccessToken(testProvider);
        assertThat(tokenProvider.extractRole(token)).isEqualTo(ProviderRole.CLINICIAN);
    }

    @Test
    @DisplayName("extractClaims returns name and specialty claims")
    void extractClaims_returnsExpectedClaims() {
        String token = tokenProvider.generateAccessToken(testProvider);
        Claims claims = tokenProvider.extractClaims(token);

        assertThat(claims.get(JwtTokenProvider.CLAIM_NAME, String.class)).isEqualTo("Dr. A. Mehta");
        assertThat(claims.get(JwtTokenProvider.CLAIM_SPECIALTY, String.class)).isEqualTo("Cardiology");
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("CLINICIAN");
        assertThat(claims.getIssuer()).isEqualTo("medisphere");
    }

    @Test
    @DisplayName("token generated for different provider has different subject")
    void generateAccessToken_differentProviders_differentSubjects() {
        Provider admin = Provider.builder()
                .id("PROV-ADM")
                .name("Admin User")
                .email("admin@medisphere.dev")
                .passwordHash("$2a$12$hashed2")
                .role(ProviderRole.ADMIN)
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build();

        String token1 = tokenProvider.generateAccessToken(testProvider);
        String token2 = tokenProvider.generateAccessToken(admin);

        assertThat(tokenProvider.extractProviderId(token1)).isEqualTo("PROV-001");
        assertThat(tokenProvider.extractProviderId(token2)).isEqualTo("PROV-ADM");
        assertThat(tokenProvider.extractRole(token2)).isEqualTo(ProviderRole.ADMIN);
    }

    @Test
    @DisplayName("token signed with wrong secret fails validation")
    void validateAccessToken_wrongSecret_returnsFalse() {
        JwtProperties wrongProps = new JwtProperties(
                "different-secret-key-that-is-also-long-enough-for-hmac",
                900L, 86400L, "medisphere");
        JwtTokenProvider otherProvider = new JwtTokenProvider(wrongProps);

        String tokenFromOther = otherProvider.generateAccessToken(testProvider);
        // Our provider should reject token signed by the other provider's secret
        assertThat(tokenProvider.validateAccessToken(tokenFromOther)).isFalse();
    }
}

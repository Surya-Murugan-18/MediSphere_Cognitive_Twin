package com.medisphere.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.domain.ProviderRole;
import com.medisphere.dto.request.LoginRequest;
import com.medisphere.repository.ProviderRepository;
import com.medisphere.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for Phase 8 Testcontainers integration tests.
 *
 * Starts real containerized MongoDB 7.0 and Kafka 7.6 (Confluent, maps to Apache 3.6)
 * once per test class lifecycle using {@code @Container} static fields.
 *
 * Spring dynamic properties are injected via {@code @DynamicPropertySource} so
 * the application context receives the actual container-assigned ports rather
 * than any localhost:port defaults. This guarantees tests never conflict with
 * a locally running MongoDB or Kafka.
 *
 * Container reuse strategy:
 * - Containers are static per class → started once, shared across all tests in the class.
 * - Each test subclass gets its own container pair → isolation between test classes.
 * - @BeforeEach clears state to keep tests independent.
 *
 * Embedded MongoDB (Flapdoodle) and @EmbeddedKafka are NOT active in this profile:
 * The application-integrationtest.yml disables auto-configuration that would
 * conflict with the Testcontainers-provided instances.
 *
 * Kafka image note:
 * Testcontainers KafkaContainer uses confluentinc/cp-kafka which provides
 * Apache Kafka 3.x compatibility under a Confluent Platform version number.
 * cp-kafka:7.6.1 ships Apache Kafka 3.6.1 — compatible with Spring Boot 3.3.4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestBase {

    // ── Container declarations ─────────────────────────────────────────────────
    // Static so each concrete subclass shares one container pair for all its tests.

    @Container
    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
                    .withReuse(false);

    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withReuse(false);

    // ── Dynamic property injection ─────────────────────────────────────────────
    // Override Spring properties with the actual container-allocated host/port.
    // This runs before the Spring ApplicationContext is created.

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Disable Flapdoodle embedded MongoDB — we use the real container
        registry.add("spring.autoconfigure.exclude",
                () -> "de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration");

        // Point MongoDB URI to the Testcontainers instance
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "medisphere-integration-test");

        // Point Kafka bootstrap to the Testcontainers instance
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Disable DataSeeder in integration tests — tests set up their own state
        registry.add("spring.profiles.active", () -> "test");

        // JWT secret for tests
        registry.add("medisphere.jwt.secret",
                () -> "integration-test-jwt-secret-key-minimum-32-chars-ok");
    }

    // ── Injected Spring beans ──────────────────────────────────────────────────

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProviderRepository providerRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    // ── Shared test provider constants ────────────────────────────────────────

    protected static final String CLINICIAN_EMAIL = "clinician@integration.test";
    protected static final String CLINICIAN_PASS  = "Clinician@123";
    protected static final String ADMIN_EMAIL     = "admin@integration.test";
    protected static final String ADMIN_PASS      = "Admin@123";

    // ── Common setup ──────────────────────────────────────────────────────────

    @BeforeEach
    void baseSetUp() {
        // Clear authentication state before each test
        refreshTokenRepository.deleteAll();
        providerRepository.deleteAll();

        // Seed a CLINICIAN and ADMIN for each test — idempotent through deleteAll above
        providerRepository.save(Provider.builder()
                .id("INTTEST-PROV-CL")
                .name("Dr. Integration Clinician")
                .email(CLINICIAN_EMAIL)
                .passwordHash(passwordEncoder.encode(CLINICIAN_PASS))
                .role(ProviderRole.CLINICIAN)
                .specialty("Cardiology")
                .facility("Integration Test Hospital")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build());

        providerRepository.save(Provider.builder()
                .id("INTTEST-PROV-ADM")
                .name("Admin Integration")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASS))
                .role(ProviderRole.ADMIN)
                .specialty("Administration")
                .facility("Integration Test Hospital")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build());
    }

    // ── Shared helper methods ─────────────────────────────────────────────────

    /**
     * Perform a real login via the API and return the access token.
     * Uses the shared MockMvc so it exercises the full filter chain.
     */
    protected String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /**
     * Login as the shared integration-test CLINICIAN.
     */
    protected String clinicianToken() throws Exception {
        return loginAndGetToken(CLINICIAN_EMAIL, CLINICIAN_PASS);
    }

    /**
     * Login as the shared integration-test ADMIN.
     */
    protected String adminToken() throws Exception {
        return loginAndGetToken(ADMIN_EMAIL, ADMIN_PASS);
    }
}

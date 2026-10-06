package com.medisphere.ai.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medisphere.ai.ml.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MLServiceClient.
 *
 * Verifies:
 *   A. Correct endpoint URL for each model
 *   B. POST method used
 *   C. X-Internal-Token header is sent
 *   D. Content-Type is application/json
 *   E. Response is correctly deserialized and returned
 *   F. null returned on HTTP client error
 *   G. null returned on HTTP server error
 *   H. null returned on connection failure (ResourceAccessException)
 *   I. Token value is never asserted in a way that reveals it (test uses placeholder)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MLServiceClient — Unit Tests")
class MLServiceClientTest {

    private static final String BASE_URL    = "http://ml-service:8000";
    private static final String TEST_TOKEN  = "test-internal-token";

    @Mock
    private RestTemplate restTemplate;

    private MLServiceClient client;

    @BeforeEach
    void setUp() {
        client = new MLServiceClient(BASE_URL, TEST_TOKEN, restTemplate);
    }

    // ── A+B+C+D: CVD request wiring ───────────────────────────────────────

    @Test
    @DisplayName("predictCvd: POSTs to /predict/cvd-risk with correct headers")
    void predictCvd_correctEndpointAndHeaders() {
        CvdRiskResponse mockResponse = buildCvdResponse();
        ResponseEntity<CvdRiskResponse> entity = ResponseEntity.ok(mockResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/predict/cvd-risk"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CvdRiskResponse.class)))
                .thenReturn(entity);

        CvdRiskRequest request = new CvdRiskRequest("P001", 1, 55, 135.0, 85.0, 72, 210.0, 95.0, 1, 0, 0, 0);
        CvdRiskResponse result = client.predictCvd(request);

        assertThat(result).isNotNull();
        assertThat(result.probabilityScore()).isEqualTo(0.24);
        assertThat(result.riskCategory()).isEqualTo("High");

        // Verify the exact URL was called with POST
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST),
                entityCaptor.capture(), eq(CvdRiskResponse.class));

        assertThat(urlCaptor.getValue()).isEqualTo(BASE_URL + "/predict/cvd-risk");

        // Verify X-Internal-Token header is present (without asserting the value)
        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.containsKey("X-Internal-Token")).isTrue();
        assertThat(headers.getFirst("X-Internal-Token")).isNotBlank();

        // Verify Content-Type is JSON
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("predictDiabetes: POSTs to /predict/diabetes-risk")
    void predictDiabetes_correctEndpoint() {
        DiabetesRiskResponse mockResponse = buildDiabetesResponse();
        ResponseEntity<DiabetesRiskResponse> entity = ResponseEntity.ok(mockResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/predict/diabetes-risk"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(DiabetesRiskResponse.class)))
                .thenReturn(entity);

        DiabetesRiskRequest request = new DiabetesRiskRequest("P001", 55, 1, 1, 1, 1, 0, 0, null);
        DiabetesRiskResponse result = client.predictDiabetes(request);

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo("diabetes-risk");

        verify(restTemplate).exchange(
                eq(BASE_URL + "/predict/diabetes-risk"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(DiabetesRiskResponse.class));
    }

    @Test
    @DisplayName("predictReadmission: POSTs to /predict/readmission-30d")
    void predictReadmission_correctEndpoint() {
        ReadmissionResponse mockResponse = buildReadmissionResponse();
        ResponseEntity<ReadmissionResponse> entity = ResponseEntity.ok(mockResponse);

        when(restTemplate.exchange(
                eq(BASE_URL + "/predict/readmission-30d"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(ReadmissionResponse.class)))
                .thenReturn(entity);

        ReadmissionRequest request = new ReadmissionRequest(
                "P001", 1, 62, 3, "Circulatory", "Metabolic/Endocrine", "Unknown", 2, 1, 0);
        ReadmissionResponse result = client.predictReadmission(request);

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo("readmission-30d");

        verify(restTemplate).exchange(
                eq(BASE_URL + "/predict/readmission-30d"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(ReadmissionResponse.class));
    }

    // ── E: Response deserialization ───────────────────────────────────────

    @Test
    @DisplayName("predictCvd: maps SHAP factors from response")
    void predictCvd_mapsShapFactors() {
        CvdRiskResponse mockResponse = new CvdRiskResponse(
                "P001", "CVD-10Y", "1.0.0", 0.18, "Low", 0, 0.2157,
                List.of(new MlShapFactor("age", "55", 0.05, "increases"),
                        new MlShapFactor("sysBP", "135.0", 0.03, "increases")),
                List.of("glucose"),
                "2026-10-06T10:00:00Z");

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(CvdRiskResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        CvdRiskResponse result = client.predictCvd(
                new CvdRiskRequest("P001", 1, 55, 135.0, 85.0, 72, 210.0, null, 1, 0, 0, 0));

        assertThat(result.shapFactors()).hasSize(2);
        assertThat(result.shapFactors().get(0).feature()).isEqualTo("age");
        assertThat(result.shapFactors().get(0).contribution()).isEqualTo(0.05);
        assertThat(result.imputedFields()).containsExactly("glucose");
    }

    // ── F: 4xx error handling ─────────────────────────────────────────────

    @Test
    @DisplayName("predictCvd: returns null on 401 Unauthorized (bad token)")
    void predictCvd_returns_null_on_401() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(CvdRiskResponse.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        HttpStatus.UNAUTHORIZED));

        CvdRiskRequest request = new CvdRiskRequest("P001", 1, 55, null, null, null, null, null, null, null, null, null);
        CvdRiskResponse result = client.predictCvd(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("predictDiabetes: returns null on 422 Unprocessable Entity")
    void predictDiabetes_returns_null_on_422() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(DiabetesRiskResponse.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        HttpStatus.UNPROCESSABLE_ENTITY));

        DiabetesRiskResponse result = client.predictDiabetes(
                new DiabetesRiskRequest("P001", null, null, null, null, null, null, null, null));

        assertThat(result).isNull();
    }

    // ── G: 5xx error handling ─────────────────────────────────────────────

    @Test
    @DisplayName("predictReadmission: returns null on 500 Internal Server Error")
    void predictReadmission_returns_null_on_500() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(ReadmissionResponse.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR));

        ReadmissionResponse result = client.predictReadmission(
                new ReadmissionRequest("P001", null, null, null, null, null, null, null, null, null));

        assertThat(result).isNull();
    }

    // ── H: Connection failure ─────────────────────────────────────────────

    @Test
    @DisplayName("predictCvd: returns null when ML service is unreachable")
    void predictCvd_returns_null_when_unreachable() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(CvdRiskResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        CvdRiskResponse result = client.predictCvd(
                new CvdRiskRequest("P001", 1, 55, null, null, null, null, null, null, null, null, null));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("predictDiabetes: returns null when ML service is unreachable")
    void predictDiabetes_returns_null_when_unreachable() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(DiabetesRiskResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        DiabetesRiskResponse result = client.predictDiabetes(
                new DiabetesRiskRequest("P001", null, null, null, null, null, null, null, null));

        assertThat(result).isNull();
    }

    // ── Base URL trailing slash normalization ─────────────────────────────

    @Test
    @DisplayName("constructor: strips trailing slash from base URL")
    void constructor_stripsTrailingSlash() {
        MLServiceClient trailingSlash = new MLServiceClient(
                "http://ml-service:8000/", TEST_TOKEN, restTemplate);

        when(restTemplate.exchange(
                eq("http://ml-service:8000/predict/cvd-risk"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CvdRiskResponse.class)))
                .thenReturn(ResponseEntity.ok(buildCvdResponse()));

        trailingSlash.predictCvd(
                new CvdRiskRequest("P001", null, null, null, null, null, null, null, null, null, null, null));

        verify(restTemplate).exchange(
                eq("http://ml-service:8000/predict/cvd-risk"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(CvdRiskResponse.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CvdRiskResponse buildCvdResponse() {
        return new CvdRiskResponse(
                "P001", "CVD-10Y", "1.0.0", 0.24, "High", 1, 0.2157,
                List.of(new MlShapFactor("age", "55", 0.08, "increases")),
                List.of(),
                "2026-10-06T10:00:00Z");
    }

    private DiabetesRiskResponse buildDiabetesResponse() {
        return new DiabetesRiskResponse(
                "P001", "diabetes-risk", "1.0.0", 0.31, "High", 1, 0.40,
                List.of(new MlShapFactor("HighBP", "1.0", 0.06, "increases")),
                List.of(),
                "2026-10-06T10:00:00Z");
    }

    private ReadmissionResponse buildReadmissionResponse() {
        return new ReadmissionResponse(
                "P001", "readmission-30d", "1.0.0", 0.22, "Medium", 1, 0.30,
                List.of(new MlShapFactor("age_midpoint", "65.0", 0.04, "increases")),
                List.of(),
                "early_hospitalization",
                "2026-10-06T10:00:00Z");
    }
}

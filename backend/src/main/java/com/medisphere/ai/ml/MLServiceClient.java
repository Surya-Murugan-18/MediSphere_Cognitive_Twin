package com.medisphere.ai.ml;

import com.medisphere.ai.ml.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for the FastAPI ML inference service.
 *
 * Security contract:
 *   - Sends X-Internal-Token on every request.
 *   - Token is never logged, never included in error messages returned to callers.
 *   - Token is never exposed to frontend clients.
 *
 * Error contract:
 *   - Returns null if the ML service is unavailable or returns an error.
 *   - Callers (TFFAIPredictionService) are responsible for handling null by
 *     throwing a safe, informative exception — never fabricating a result.
 *
 * Thread safety:
 *   - RestTemplate is thread-safe when constructed once with shared configuration.
 *   - This class is designed to be a Spring singleton bean.
 */
public class MLServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MLServiceClient.class);

    private static final String HEADER_TOKEN = "X-Internal-Token";
    private static final String PATH_CVD         = "/predict/cvd-risk";
    private static final String PATH_DIABETES     = "/predict/diabetes-risk";
    private static final String PATH_READMISSION  = "/predict/readmission-30d";

    private final String baseUrl;
    private final String internalToken;
    private final RestTemplate restTemplate;

    public MLServiceClient(String baseUrl, String internalToken, RestTemplate restTemplate) {
        this.baseUrl       = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken;
        this.restTemplate  = restTemplate;
    }

    // ── CVD Risk ──────────────────────────────────────────────────────────

    /**
     * POST /predict/cvd-risk — returns the ML response or null on failure.
     *
     * @param request the assembled CVD feature request
     * @return ML response, or null if the service is unavailable or errors
     */
    public CvdRiskResponse predictCvd(CvdRiskRequest request) {
        log.info("Calling ML service for model cvd-risk (patient={})", request.patientId());
        return post(PATH_CVD, request, CvdRiskResponse.class, "cvd-risk");
    }

    // ── Diabetes Risk ─────────────────────────────────────────────────────

    /**
     * POST /predict/diabetes-risk — returns the ML response or null on failure.
     */
    public DiabetesRiskResponse predictDiabetes(DiabetesRiskRequest request) {
        log.info("Calling ML service for model diabetes-risk (patient={})", request.patientId());
        return post(PATH_DIABETES, request, DiabetesRiskResponse.class, "diabetes-risk");
    }

    // ── Readmission Risk ──────────────────────────────────────────────────

    /**
     * POST /predict/readmission-30d — returns the ML response or null on failure.
     */
    public ReadmissionResponse predictReadmission(ReadmissionRequest request) {
        log.info("Calling ML service for model readmission-30d (patient={})", request.patientId());
        return post(PATH_READMISSION, request, ReadmissionResponse.class, "readmission-30d");
    }

    // ── Generic POST helper ───────────────────────────────────────────────

    private <REQ, RESP> RESP post(String path, REQ request, Class<RESP> responseType, String modelId) {
        String url = baseUrl + path;
        HttpEntity<REQ> entity = new HttpEntity<>(request, buildHeaders());
        try {
            ResponseEntity<RESP> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, responseType);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("ML prediction completed for model {}", modelId);
                return response.getBody();
            }
            log.error("ML service returned unexpected status {} for model {}",
                    response.getStatusCode(), modelId);
            return null;

        } catch (HttpClientErrorException e) {
            // 4xx — misconfigured request or unauthorized
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.error("ML service rejected request for model {} — check ML_INTERNAL_TOKEN configuration", modelId);
            } else {
                log.error("ML service client error for model {}: HTTP {} — {}",
                        modelId, e.getStatusCode(), e.getStatusText());
            }
            return null;

        } catch (HttpServerErrorException e) {
            // 5xx — ML service internal error
            log.error("ML service internal error for model {}: HTTP {} — {}",
                    modelId, e.getStatusCode(), e.getStatusText());
            return null;

        } catch (ResourceAccessException e) {
            // Connection refused, timeout, DNS failure
            log.error("ML service unavailable for model {} — cannot connect to {}: {}",
                    modelId, baseUrl, e.getMessage());
            return null;

        } catch (Exception e) {
            log.error("Unexpected error calling ML service for model {}: {}",
                    modelId, e.getMessage(), e);
            return null;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_TOKEN, internalToken);
        // Never log the token value
        return headers;
    }
}

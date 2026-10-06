package com.medisphere.ai.tff;

/**
 * Thrown by TFFAIPredictionService when the FastAPI ML service is unavailable
 * or returns an error for a prediction request.
 *
 * The GlobalExceptionHandler maps this as a 503 Service Unavailable response,
 * keeping the error message safe for API consumers without exposing internal
 * stack traces or infrastructure details.
 *
 * Per task requirement: never fabricate a prediction when the ML service fails.
 */
public class MLServiceUnavailableException extends RuntimeException {

    public MLServiceUnavailableException(String message) {
        super(message);
    }

    public MLServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

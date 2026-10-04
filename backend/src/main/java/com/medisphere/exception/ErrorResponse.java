package com.medisphere.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String requestId,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String message, String requestId, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, requestId, path, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message,
                                                 String requestId, String path,
                                                 Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, requestId, path, fieldErrors);
    }
}

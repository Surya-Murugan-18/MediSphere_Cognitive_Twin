package com.medisphere.dto.response;

/**
 * Response DTO for a single integration service health entry.
 * Used by GET /api/system/services
 */
public record SystemServiceResponse(
        String name,
        String state,
        String uptime,
        String detail,
        /** healthy | warning | critical | neutral */
        String tone
) {}

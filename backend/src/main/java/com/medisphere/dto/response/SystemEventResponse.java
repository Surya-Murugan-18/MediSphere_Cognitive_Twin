package com.medisphere.dto.response;

import com.medisphere.domain.SystemEvent;

import java.time.Instant;

/**
 * Response DTO for system events.
 * Used by GET /api/system/events
 */
public record SystemEventResponse(
        String id,
        Instant timestamp,
        String text,
        String tone,
        String category
) {
    public static SystemEventResponse from(SystemEvent event) {
        return new SystemEventResponse(
                event.getId(),
                event.getTimestamp(),
                event.getText(),
                event.getTone(),
                event.getCategory()
        );
    }
}

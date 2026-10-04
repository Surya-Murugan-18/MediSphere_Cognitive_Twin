package com.medisphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic timeline event response DTO.
 * Used by care plan timeline, patient timeline, and twin timeline endpoints.
 *
 * Tone values: healthy | info | warning | critical
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventResponse {

    private String id;
    private String timestamp;
    private String title;
    private String detail;
    private String tone;
}

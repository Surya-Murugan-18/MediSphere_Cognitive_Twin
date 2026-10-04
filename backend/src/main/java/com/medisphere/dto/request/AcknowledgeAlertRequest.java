package com.medisphere.dto.request;

import lombok.Data;

/** Request body for PATCH /api/alerts/{id}/acknowledge */
@Data
public class AcknowledgeAlertRequest {
    /** Optional clinician notes attached to the acknowledgement. */
    private String notes;
}

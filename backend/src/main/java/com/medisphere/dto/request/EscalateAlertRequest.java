package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for POST /api/alerts/{id}/escalate */
@Data
public class EscalateAlertRequest {
    @NotBlank(message = "Escalation reason is required")
    private String reason;

    @NotBlank(message = "Escalation target is required")
    private String escalateTo;
}

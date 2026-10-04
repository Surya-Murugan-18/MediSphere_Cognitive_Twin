package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/care-plans/{id}/reject
 * Reason is mandatory per requirements.md FR-CP-09.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectCarePlanRequest {

    @NotBlank(message = "reason is required when rejecting a care plan")
    private String reason;
}

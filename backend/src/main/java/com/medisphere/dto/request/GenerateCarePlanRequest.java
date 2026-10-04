package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/care-plans/generate
 * Per design.md §10 and tasks.md B6.3.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateCarePlanRequest {

    @NotBlank(message = "patientId is required")
    private String patientId;
}

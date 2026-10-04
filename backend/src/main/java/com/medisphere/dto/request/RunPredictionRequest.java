package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/predictions/run.
 * Triggers a new prediction run for a specific patient and model.
 *
 * @param patientId  the patient ID (e.g. "P001")
 * @param model      one of: "CVD-Risk-v3.2", "DM-Complication-v2.4", "Readmit-30d-v1.8",
 *                   or "ALL" to run all three models
 */
public record RunPredictionRequest(
        @NotBlank(message = "patientId is required")
        String patientId,

        @NotBlank(message = "model is required")
        @Pattern(
            regexp = "CVD-Risk-v3\\.2|DM-Complication-v2\\.4|Readmit-30d-v1\\.8|ALL",
            message = "model must be one of: CVD-Risk-v3.2, DM-Complication-v2.4, Readmit-30d-v1.8, ALL"
        )
        String model
) {}

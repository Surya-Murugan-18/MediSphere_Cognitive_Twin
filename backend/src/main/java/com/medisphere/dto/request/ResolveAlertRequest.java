package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for POST /api/alerts/{id}/resolve */
@Data
public class ResolveAlertRequest {
    @NotBlank(message = "Resolution description is required")
    private String resolution;
}

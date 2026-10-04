package com.medisphere.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for PUT /api/patients/{id}
 * All fields optional — only provided fields are updated.
 */
public record UpdatePatientRequest(

        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,

        String dob,

        String gender,

        String phone,

        String email,

        String fhirId,

        String ehrSystem,

        List<String> conditions,

        String status,   // Active | Inactive | Pending Consent

        String riskLevel,

        String healthStatus,

        Integer adherence
) {
}

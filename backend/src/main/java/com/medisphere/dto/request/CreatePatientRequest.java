package com.medisphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST /api/patients
 */
public record CreatePatientRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,

        @NotBlank(message = "Date of birth is required")
        String dob,

        @NotBlank(message = "Gender is required")
        String gender,

        String phone,

        String email,

        @NotBlank(message = "FHIR Patient ID is required to establish the digital twin")
        String fhirId,

        String ehrSystem,

        List<String> conditions,

        ConsentFlags consents
) {
    public record ConsentFlags(boolean ehr, boolean wearable, boolean ai) {}
}

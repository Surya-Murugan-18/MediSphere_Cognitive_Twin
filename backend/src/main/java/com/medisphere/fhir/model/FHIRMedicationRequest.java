package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

/**
 * Simplified FHIR R4 MedicationRequest resource representation.
 */
@Data
@Builder
public class FHIRMedicationRequest {
    private String id;
    private String patientFhirId;
    private String medicationName;
    private String dose;
    private String frequency;
    private String startDate;       // ISO-8601 date
    private String status;          // active | stopped | completed
}

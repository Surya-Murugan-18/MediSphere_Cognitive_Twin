package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

/**
 * Simplified FHIR R4 Condition resource representation.
 */
@Data
@Builder
public class FHIRCondition {
    private String id;
    private String patientFhirId;
    private String code;            // SNOMED / ICD code
    private String displayName;     // Human-readable condition name
    private String clinicalStatus;  // active | resolved | inactive
    private String onsetDate;       // ISO-8601 date
}

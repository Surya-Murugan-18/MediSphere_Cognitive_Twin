package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

/**
 * Simplified FHIR R4 Encounter resource representation.
 */
@Data
@Builder
public class FHIREncounter {
    private String id;
    private String patientFhirId;
    private String encounterClass;    // IMP (inpatient) | AMB (ambulatory) | EMER (emergency)
    private String type;              // e.g. "Annual physical exam"
    private String status;            // finished | in-progress | planned
    private String startDate;         // ISO-8601 date-time
    private String endDate;           // ISO-8601 date-time
    private String reasonCode;        // Chief complaint code
    private String reasonDisplay;     // e.g. "Diabetes follow-up"
}

package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Simplified FHIR R4 DiagnosticReport resource representation.
 */
@Data
@Builder
public class FHIRDiagnosticReport {
    private String id;
    private String patientFhirId;
    private String title;           // e.g. "Comprehensive Metabolic Panel"
    private String status;          // final | preliminary | amended
    private String effectiveDate;   // ISO-8601 date
    private String category;        // LAB | RAD
    private List<String> observationIds;  // references to FHIRObservation ids in this report
}

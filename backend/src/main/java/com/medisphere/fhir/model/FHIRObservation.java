package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

/**
 * Simplified FHIR R4 Observation resource representation.
 * Used for both vitals and lab result observations.
 */
@Data
@Builder
public class FHIRObservation {
    private String id;               // FHIR resource id (deduplication key)
    private String patientFhirId;
    private String loincCode;        // e.g. "4548-4"
    private String displayName;      // e.g. "HbA1c"
    private String valueString;      // display string e.g. "8.2%"
    private Double valueQuantity;    // numeric value
    private String unit;             // e.g. "%", "mg/dL"
    private String status;           // final | preliminary | amended
    private String effectiveDate;    // ISO-8601 date
    private String referenceRangeLow;
    private String referenceRangeHigh;
    private String referenceRangeText;
    private String category;         // laboratory | vital-signs
    private String interpretation;   // H | L | N (high/low/normal)
}

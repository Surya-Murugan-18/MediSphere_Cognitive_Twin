package com.medisphere.fhir.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Simplified FHIR R4 Patient resource representation.
 * Only the fields needed by MediSphere ingestion are modelled.
 */
@Data
@Builder
public class FHIRPatient {
    private String id;             // FHIR resource id
    private String familyName;
    private String givenName;
    private String birthDate;      // ISO-8601 date
    private String gender;
    private List<String> conditions;
}

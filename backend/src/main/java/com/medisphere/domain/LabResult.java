package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Laboratory result document — one per FHIR Observation ingestion.
 * Collection: lab_results
 *
 * The fhirObservationId field is the deduplication key — the same FHIR observation
 * will never produce two LabResult documents.
 *
 * Per design.md §4.6
 */
@Document(collection = "lab_results")
@CompoundIndexes({
    @CompoundIndex(name = "patient_date_idx", def = "{'patientId':1,'date':-1}"),
    @CompoundIndex(name = "patient_cat_idx",  def = "{'patientId':1,'category':1,'date':-1}"),
    @CompoundIndex(name = "patient_status_idx",def = "{'patientId':1,'status':1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResult {

    @Id
    private String id;                   // "LAB-" + fhirObservationId

    @Indexed
    private String patientId;

    /** FHIR Observation resource ID — used for deduplication */
    @Indexed(unique = true)
    private String fhirObservationId;

    /** LOINC code e.g. "4548-4" */
    private String loinc;

    /** Human-readable test name e.g. "HbA1c" */
    private String test;

    /** Display result string e.g. "8.2 %" */
    private String result;

    /** Numeric value for trend calculation */
    private double numeric;

    /** Unit string e.g. "%" */
    private String unit;

    /** Reference range display e.g. "< 5.7%" */
    private String referenceRange;

    /** High | Low | Normal | Pending */
    private String status;

    /** Metabolic | Lipids | Hematology | Cardiac */
    private String category;

    /** ISO-8601 date string e.g. "2026-09-06" */
    private String date;

    /** up | down | flat — computed by LabService */
    @Builder.Default
    private String trend = "flat";

    /** Previous result string — populated by LabService */
    @Builder.Default
    private String previous = "";

    /** Clinical significance text */
    private String significance;
}

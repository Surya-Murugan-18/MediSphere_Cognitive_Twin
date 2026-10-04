package com.medisphere.fhir;

import com.medisphere.fhir.model.FHIRCondition;
import com.medisphere.fhir.model.FHIRDiagnosticReport;
import com.medisphere.fhir.model.FHIREncounter;
import com.medisphere.fhir.model.FHIRMedicationRequest;
import com.medisphere.fhir.model.FHIRObservation;
import com.medisphere.fhir.model.FHIRPatient;

import java.time.LocalDate;
import java.util.List;

/**
 * FHIR integration abstraction — the ONLY way to access FHIR data in MediSphere.
 *
 * All six resource types from design.md §7.1 are represented here.
 *
 * Implementations:
 *   MockFHIRClient  — active when FHIR_MODE=mock (default in development)
 *   LiveFHIRClient  — active when FHIR_MODE=live (production; requires credentials)
 *
 * No frontend code and no non-FHIR service should call a FHIR server directly.
 * Always inject this interface, never a concrete implementation.
 */
public interface FHIRClient {

    /**
     * Fetch the FHIR Patient resource for the given FHIR patient ID.
     *
     * @param fhirId  the FHIR resource identifier (e.g. "fhir:Patient/8a21-4c77")
     * @return the patient resource
     */
    FHIRPatient fetchPatient(String fhirId);

    /**
     * Fetch Observation resources for the given patient.
     * Optionally filter by LOINC code and date range.
     *
     * @param fhirPatientId  FHIR patient identifier
     * @param loincCode      filter by LOINC code, or null for all observations
     * @param from           start date (inclusive), or null for no lower bound
     * @param to             end date (inclusive), or null for no upper bound
     * @return list of matching observations
     */
    List<FHIRObservation> fetchObservations(String fhirPatientId,
                                             String loincCode,
                                             LocalDate from,
                                             LocalDate to);

    /**
     * Fetch all DiagnosticReport resources for the given patient.
     *
     * @param fhirPatientId  FHIR patient identifier
     * @return list of diagnostic reports
     */
    List<FHIRDiagnosticReport> fetchDiagnosticReports(String fhirPatientId);

    /**
     * Fetch Condition resources for the given patient.
     *
     * @param fhirPatientId  FHIR patient identifier
     * @return list of active and historical conditions
     */
    List<FHIRCondition> fetchConditions(String fhirPatientId);

    /**
     * Fetch MedicationRequest resources for the given patient.
     *
     * @param fhirPatientId  FHIR patient identifier
     * @return list of medication requests
     */
    List<FHIRMedicationRequest> fetchMedications(String fhirPatientId);

    /**
     * Fetch Encounter resources for the given patient.
     *
     * @param fhirPatientId  FHIR patient identifier
     * @return list of encounters
     */
    List<FHIREncounter> fetchEncounters(String fhirPatientId);

    /**
     * Validate that the given FHIR ID exists in the connected FHIR system.
     * Throws an exception if the ID cannot be resolved.
     *
     * @param fhirId  the FHIR resource identifier to validate
     */
    void validatePatientId(String fhirId);
}

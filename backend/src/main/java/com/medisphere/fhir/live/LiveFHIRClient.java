package com.medisphere.fhir.live;

import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.model.FHIRCondition;
import com.medisphere.fhir.model.FHIRDiagnosticReport;
import com.medisphere.fhir.model.FHIREncounter;
import com.medisphere.fhir.model.FHIRMedicationRequest;
import com.medisphere.fhir.model.FHIRObservation;
import com.medisphere.fhir.model.FHIRPatient;

import java.time.LocalDate;
import java.util.List;

/**
 * Production FHIR client placeholder — active when FHIR_MODE=live.
 *
 * This implementation is a placeholder for Phase 3. Connecting to a real
 * FHIR R4 server using SMART on FHIR OAuth2 is out of scope for Phase 3.
 *
 * To enable: set FHIR_MODE=live and provide the required credentials:
 *   FHIR_BASE_URL  — the FHIR server base URL
 *   FHIR_CLIENT_ID — SMART on FHIR OAuth2 client ID
 *   FHIR_CLIENT_SECRET — SMART on FHIR OAuth2 client secret
 *
 * Implement using a FHIR client library (e.g. HAPI FHIR) in a future phase.
 */
public class LiveFHIRClient implements FHIRClient {

    private static final String NOT_CONFIGURED =
            "Configure FHIR_MODE=live with real FHIR credentials";

    @Override
    public FHIRPatient fetchPatient(String fhirId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public List<FHIRObservation> fetchObservations(String fhirPatientId,
                                                    String loincCode,
                                                    LocalDate from,
                                                    LocalDate to) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public List<FHIRDiagnosticReport> fetchDiagnosticReports(String fhirPatientId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public List<FHIRCondition> fetchConditions(String fhirPatientId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public List<FHIRMedicationRequest> fetchMedications(String fhirPatientId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public List<FHIREncounter> fetchEncounters(String fhirPatientId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }

    @Override
    public void validatePatientId(String fhirId) {
        throw new UnsupportedOperationException(NOT_CONFIGURED);
    }
}

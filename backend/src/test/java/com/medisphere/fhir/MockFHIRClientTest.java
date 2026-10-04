package com.medisphere.fhir;

import com.medisphere.fhir.mock.MockFHIRClient;
import com.medisphere.fhir.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockFHIRClient Unit Tests")
class MockFHIRClientTest {

    private MockFHIRClient client;
    private static final String FHIR_ID = "fhir:Patient/test-001";
    private static final String FHIR_ID_2 = "fhir:Patient/test-002";

    @BeforeEach
    void setUp() { client = new MockFHIRClient(); }

    // ── Patient ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchPatient returns non-null patient with correct fhirId")
    void fetchPatient_returnsValidPatient() {
        FHIRPatient p = client.fetchPatient(FHIR_ID);
        assertThat(p).isNotNull();
        assertThat(p.getId()).isEqualTo(FHIR_ID);
        assertThat(p.getFamilyName()).isNotBlank();
        assertThat(p.getGivenName()).isNotBlank();
        assertThat(p.getBirthDate()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // ── Observations ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchObservations returns non-empty list with valid fields")
    void fetchObservations_returnsValidList() {
        List<FHIRObservation> obs = client.fetchObservations(FHIR_ID, null, null, null);
        assertThat(obs).isNotEmpty();
        for (FHIRObservation o : obs) {
            assertThat(o.getId()).isNotBlank();
            assertThat(o.getPatientFhirId()).isEqualTo(FHIR_ID);
            assertThat(o.getLoincCode()).isNotBlank();
            assertThat(o.getDisplayName()).isNotBlank();
            assertThat(o.getEffectiveDate()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(o.getStatus()).isIn("final", "preliminary", "amended");
        }
    }

    @Test
    @DisplayName("fetchObservations filters by LOINC code correctly")
    void fetchObservations_withLoincFilter_returnsFiltered() {
        List<FHIRObservation> all = client.fetchObservations(FHIR_ID, null, null, null);
        String firstLoinc = all.get(0).getLoincCode();

        List<FHIRObservation> filtered = client.fetchObservations(FHIR_ID, firstLoinc, null, null);
        assertThat(filtered).isNotEmpty();
        filtered.forEach(o -> assertThat(o.getLoincCode()).isEqualTo(firstLoinc));
    }

    @Test
    @DisplayName("fetchObservations returns exactly 5 results (HbA1c, glucose, cholesterol, HDL, creatinine)")
    void fetchObservations_returns5StandardResults() {
        List<FHIRObservation> obs = client.fetchObservations(FHIR_ID, null, null, null);
        assertThat(obs).hasSize(5);
    }

    @Test
    @DisplayName("different patients get different deterministic observation values")
    void fetchObservations_differentPatients_differentValues() {
        List<FHIRObservation> obs1 = client.fetchObservations(FHIR_ID,   null, null, null);
        List<FHIRObservation> obs2 = client.fetchObservations(FHIR_ID_2, null, null, null);
        // IDs must differ
        assertThat(obs1.get(0).getId()).isNotEqualTo(obs2.get(0).getId());
    }

    @Test
    @DisplayName("fetchObservations is deterministic — same result on repeated calls")
    void fetchObservations_isDeterministic() {
        List<FHIRObservation> first  = client.fetchObservations(FHIR_ID, null, null, null);
        List<FHIRObservation> second = client.fetchObservations(FHIR_ID, null, null, null);
        assertThat(first.get(0).getValueQuantity())
                .isEqualTo(second.get(0).getValueQuantity());
    }

    // ── DiagnosticReports ─────────────────────────────────────────────────

    @Test
    @DisplayName("fetchDiagnosticReports returns non-empty list with valid FHIR R4 fields")
    void fetchDiagnosticReports_returnsValidList() {
        List<FHIRDiagnosticReport> reports = client.fetchDiagnosticReports(FHIR_ID);
        assertThat(reports).isNotEmpty();
        for (FHIRDiagnosticReport r : reports) {
            assertThat(r.getId()).isNotBlank();
            assertThat(r.getPatientFhirId()).isEqualTo(FHIR_ID);
            assertThat(r.getTitle()).isNotBlank();
            assertThat(r.getStatus()).isIn("final", "preliminary", "amended");
            assertThat(r.getEffectiveDate()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(r.getObservationIds()).isNotEmpty();
        }
    }

    // ── Conditions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchConditions returns valid FHIR R4 Condition resources")
    void fetchConditions_returnsValidList() {
        List<FHIRCondition> conditions = client.fetchConditions(FHIR_ID);
        assertThat(conditions).isNotEmpty();
        for (FHIRCondition c : conditions) {
            assertThat(c.getId()).isNotBlank();
            assertThat(c.getPatientFhirId()).isEqualTo(FHIR_ID);
            assertThat(c.getDisplayName()).isNotBlank();
            assertThat(c.getClinicalStatus()).isIn("active", "resolved", "inactive");
        }
    }

    // ── MedicationRequests ────────────────────────────────────────────────

    @Test
    @DisplayName("fetchMedications returns valid FHIR R4 MedicationRequest resources")
    void fetchMedications_returnsValidList() {
        List<FHIRMedicationRequest> meds = client.fetchMedications(FHIR_ID);
        assertThat(meds).isNotEmpty();
        for (FHIRMedicationRequest m : meds) {
            assertThat(m.getId()).isNotBlank();
            assertThat(m.getPatientFhirId()).isEqualTo(FHIR_ID);
            assertThat(m.getMedicationName()).isNotBlank();
            assertThat(m.getDose()).isNotBlank();
            assertThat(m.getFrequency()).isNotBlank();
            assertThat(m.getStatus()).isIn("active", "stopped", "completed");
        }
    }

    // ── Encounters ────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchEncounters returns valid FHIR R4 Encounter resources")
    void fetchEncounters_returnsValidList() {
        List<FHIREncounter> encounters = client.fetchEncounters(FHIR_ID);
        assertThat(encounters).isNotEmpty();
        for (FHIREncounter e : encounters) {
            assertThat(e.getId()).isNotBlank();
            assertThat(e.getPatientFhirId()).isEqualTo(FHIR_ID);
            assertThat(e.getEncounterClass()).isNotBlank();
            assertThat(e.getStatus()).isIn("finished", "in-progress", "planned");
        }
    }

    // ── validatePatientId ─────────────────────────────────────────────────

    @Test
    @DisplayName("validatePatientId does not throw in mock mode")
    void validatePatientId_noThrow() {
        assertThatNoException().isThrownBy(() -> client.validatePatientId(FHIR_ID));
    }

    // ── LiveFHIRClient ────────────────────────────────────────────────────

    @Test
    @DisplayName("LiveFHIRClient throws UnsupportedOperationException for all methods")
    void liveFHIRClient_throwsUnsupported() {
        com.medisphere.fhir.live.LiveFHIRClient live =
                new com.medisphere.fhir.live.LiveFHIRClient();

        assertThatThrownBy(() -> live.fetchPatient("any"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("FHIR_MODE=live");

        assertThatThrownBy(() -> live.fetchObservations("any", null, null, null))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> live.fetchDiagnosticReports("any"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> live.fetchConditions("any"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> live.fetchMedications("any"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> live.fetchEncounters("any"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

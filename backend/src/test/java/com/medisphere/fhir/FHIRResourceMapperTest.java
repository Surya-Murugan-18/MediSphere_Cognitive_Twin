package com.medisphere.fhir;

import com.medisphere.domain.LabResult;
import com.medisphere.fhir.model.FHIRObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FHIRResourceMapper Unit Tests")
class FHIRResourceMapperTest {

    private FHIRResourceMapper mapper;

    @BeforeEach
    void setUp() { mapper = new FHIRResourceMapper(); }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("maps valid FHIRObservation to LabResult with correct fields")
    void mapObservation_validInput_mapsCorrectly() {
        FHIRObservation obs = FHIRObservation.builder()
                .id("OBS-001")
                .patientFhirId("fhir:Patient/abc")
                .loincCode("4548-4")
                .displayName("HbA1c")
                .valueQuantity(8.2)
                .unit("%")
                .valueString("8.2 %")
                .status("final")
                .effectiveDate("2026-09-15")
                .referenceRangeText("< 5.7%")
                .category("laboratory")
                .interpretation("H")
                .build();

        LabResult result = mapper.mapObservationToLabResult(obs, "P001");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("LAB-OBS-001");
        assertThat(result.getFhirObservationId()).isEqualTo("OBS-001");
        assertThat(result.getPatientId()).isEqualTo("P001");
        assertThat(result.getLoinc()).isEqualTo("4548-4");
        assertThat(result.getTest()).isEqualTo("HbA1c");
        assertThat(result.getNumeric()).isEqualTo(8.2);
        assertThat(result.getUnit()).isEqualTo("%");
        assertThat(result.getDate()).isEqualTo("2026-09-15");
        assertThat(result.getStatus()).isEqualTo("High");
        assertThat(result.getCategory()).isEqualTo("Metabolic"); // 4548-4 → Metabolic
        assertThat(result.getReferenceRange()).isEqualTo("< 5.7%");
        assertThat(result.getSignificance()).contains("above the reference range");
    }

    // ── Status mapping ────────────────────────────────────────────────────

    @Test
    @DisplayName("maps interpretation H to status High")
    void mapStatus_H_returnsHigh() {
        LabResult r = mapper.mapObservationToLabResult(buildObs("H", "final"), "P001");
        assertThat(r.getStatus()).isEqualTo("High");
    }

    @Test
    @DisplayName("maps interpretation L to status Low")
    void mapStatus_L_returnsLow() {
        LabResult r = mapper.mapObservationToLabResult(buildObs("L", "final"), "P001");
        assertThat(r.getStatus()).isEqualTo("Low");
    }

    @Test
    @DisplayName("maps interpretation N to status Normal")
    void mapStatus_N_returnsNormal() {
        LabResult r = mapper.mapObservationToLabResult(buildObs("N", "final"), "P001");
        assertThat(r.getStatus()).isEqualTo("Normal");
    }

    @Test
    @DisplayName("maps fhirStatus preliminary to status Pending")
    void mapStatus_preliminary_returnsPending() {
        LabResult r = mapper.mapObservationToLabResult(buildObs(null, "preliminary"), "P001");
        assertThat(r.getStatus()).isEqualTo("Pending");
    }

    // ── Category mapping ──────────────────────────────────────────────────

    @Test
    @DisplayName("LOINC 4548-4 (HbA1c) maps to Metabolic category")
    void mapCategory_hba1cLoinc_mapsToMetabolic() {
        LabResult r = mapper.mapObservationToLabResult(buildObsWithLoinc("4548-4"), "P001");
        assertThat(r.getCategory()).isEqualTo("Metabolic");
    }

    @Test
    @DisplayName("LOINC 2085-9 (HDL) maps to Lipids category")
    void mapCategory_hdlLoinc_mapsToLipids() {
        LabResult r = mapper.mapObservationToLabResult(buildObsWithLoinc("2085-9"), "P001");
        assertThat(r.getCategory()).isEqualTo("Lipids");
    }

    @Test
    @DisplayName("LOINC 10839-9 (Troponin) maps to Cardiac category")
    void mapCategory_troponinLoinc_mapsToCardiac() {
        LabResult r = mapper.mapObservationToLabResult(buildObsWithLoinc("10839-9"), "P001");
        assertThat(r.getCategory()).isEqualTo("Cardiac");
    }

    // ── Reference range ───────────────────────────────────────────────────

    @Test
    @DisplayName("builds reference range from low and high when text is absent")
    void referenceRange_fromLowHigh() {
        FHIRObservation obs = FHIRObservation.builder()
                .id("OBS-002").patientFhirId("fhir:Patient/p").loincCode("2345-7")
                .displayName("Glucose").valueQuantity(90.0).unit("mg/dL")
                .status("final").effectiveDate("2026-09-15")
                .referenceRangeLow("70").referenceRangeHigh("100")
                .category("laboratory").interpretation("N").build();

        LabResult r = mapper.mapObservationToLabResult(obs, "P001");
        assertThat(r.getReferenceRange()).isEqualTo("70 – 100");
    }

    // ── Null / invalid input ──────────────────────────────────────────────

    @Test
    @DisplayName("returns null when observation is null")
    void nullObservation_returnsNull() {
        assertThat(mapper.mapObservationToLabResult(null, "P001")).isNull();
    }

    @Test
    @DisplayName("returns null when patientFhirId is missing")
    void missingPatientFhirId_returnsNull() {
        FHIRObservation obs = FHIRObservation.builder()
                .id("OBS-BAD").loincCode("4548-4").displayName("HbA1c")
                .valueQuantity(8.0).status("final").effectiveDate("2026-09-15")
                .build();
        assertThat(mapper.mapObservationToLabResult(obs, "P001")).isNull();
    }

    @Test
    @DisplayName("returns null when effectiveDate is missing")
    void missingEffectiveDate_returnsNull() {
        FHIRObservation obs = FHIRObservation.builder()
                .id("OBS-BAD2").patientFhirId("fhir:Patient/x")
                .loincCode("4548-4").displayName("HbA1c").valueQuantity(8.0)
                .status("final").build();
        assertThat(mapper.mapObservationToLabResult(obs, "P001")).isNull();
    }

    @Test
    @DisplayName("uses fallback test name when displayName is blank")
    void blankDisplayName_usesFallback() {
        FHIRObservation obs = FHIRObservation.builder()
                .id("OBS-003").patientFhirId("fhir:Patient/x").loincCode("4548-4")
                .displayName("").valueQuantity(8.0).unit("%")
                .status("final").effectiveDate("2026-09-15")
                .category("laboratory").interpretation("N").build();
        LabResult r = mapper.mapObservationToLabResult(obs, "P001");
        assertThat(r).isNotNull();
        assertThat(r.getTest()).isEqualTo("Unknown Test");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private FHIRObservation buildObs(String interpretation, String status) {
        return FHIRObservation.builder()
                .id("OBS-T").patientFhirId("fhir:Patient/p").loincCode("4548-4")
                .displayName("HbA1c").valueQuantity(8.0).unit("%")
                .status(status).effectiveDate("2026-09-15")
                .category("laboratory").interpretation(interpretation).build();
    }

    private FHIRObservation buildObsWithLoinc(String loinc) {
        return FHIRObservation.builder()
                .id("OBS-" + loinc).patientFhirId("fhir:Patient/p").loincCode(loinc)
                .displayName("Test").valueQuantity(1.0).unit("units")
                .status("final").effectiveDate("2026-09-15")
                .category("laboratory").interpretation("N").build();
    }
}

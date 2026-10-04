package com.medisphere.fhir.mock;

import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.model.FHIRCondition;
import com.medisphere.fhir.model.FHIRDiagnosticReport;
import com.medisphere.fhir.model.FHIREncounter;
import com.medisphere.fhir.model.FHIRMedicationRequest;
import com.medisphere.fhir.model.FHIRObservation;
import com.medisphere.fhir.model.FHIRPatient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Development mock FHIR client — active when FHIR_MODE=mock (default).
 *
 * Properties:
 *  - No external HTTP calls
 *  - Fully deterministic — same inputs always produce same outputs
 *  - No Math.random() or random UUIDs in clinical values
 *  - Data keyed by patient FHIR ID
 *  - Different patients receive different realistic mock data
 *  - Valid FHIR R4-shaped resource structure
 *  - All six resource types supported
 *
 * Development data is clearly marked with the "DEV-" prefix on IDs.
 * This implementation must be replaced by LiveFHIRClient in production.
 */
public class MockFHIRClient implements FHIRClient {

    private static final Logger log =
            LoggerFactory.getLogger(MockFHIRClient.class);

    @Override
    public FHIRPatient fetchPatient(String fhirId) {

        log.debug("[MOCK FHIR] fetchPatient: {}", fhirId);

        List<String> conditions =
                conditionNamesFor(fhirId);

        return FHIRPatient.builder()
                .id(fhirId)
                .familyName("MockPatient")
                .givenName("Development")
                .birthDate("1968-04-12")
                .gender("male")
                .conditions(conditions)
                .build();
    }

    @Override
    public List<FHIRObservation> fetchObservations(
            String fhirPatientId,
            String loincCode,
            LocalDate from,
            LocalDate to) {

        log.debug(
                "[MOCK FHIR] fetchObservations: patient={} loinc={}",
                fhirPatientId,
                loincCode
        );

        List<FHIRObservation> all =
                labObservationsFor(fhirPatientId);

        if (loincCode != null) {
            return all.stream()
                    .filter(o ->
                            loincCode.equals(o.getLoincCode()))
                    .toList();
        }

        return all;
    }

    @Override
    public List<FHIRDiagnosticReport> fetchDiagnosticReports(
            String fhirPatientId) {

        log.debug(
                "[MOCK FHIR] fetchDiagnosticReports: patient={}",
                fhirPatientId
        );

        List<FHIRObservation> obs =
                labObservationsFor(fhirPatientId);

        List<String> obsIds =
                obs.stream()
                        .map(FHIRObservation::getId)
                        .toList();

        return List.of(
                FHIRDiagnosticReport.builder()
                        .id(
                                "DEV-DR-"
                                        + patientSuffix(fhirPatientId)
                                        + "-001"
                        )
                        .patientFhirId(fhirPatientId)
                        .title("Comprehensive Metabolic Panel")
                        .status("final")
                        .effectiveDate(
                                devDate(fhirPatientId, 15)
                        )
                        .category("LAB")
                        .observationIds(obsIds)
                        .build()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Conditions
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<FHIRCondition> fetchConditions(
            String fhirPatientId) {

        log.debug(
                "[MOCK FHIR] fetchConditions: patient={}",
                fhirPatientId
        );

        String sfx =
                patientSuffix(fhirPatientId);

        List<String> conditionNames =
                conditionNamesFor(fhirPatientId);

        return conditionNames.stream()
                .map(conditionName ->
                        buildCondition(
                                fhirPatientId,
                                sfx,
                                conditionName
                        )
                )
                .toList();
    }

    /**
     * Returns deterministic patient-specific conditions.
     *
     * Known development patients use explicit clinical profiles.
     * Unknown/new FHIR IDs use a deterministic fallback based on
     * the FHIR ID so the same patient always receives the same data.
     */
    private List<String> conditionNamesFor(
            String fhirPatientId) {

        String patientId =
                normalizePatientId(fhirPatientId);

        return switch (patientId) {

            case "P001" ->
                    List.of(
                            "Essential hypertension"
                    );

            case "P002" ->
                    List.of(
                            "Diabetes mellitus type 2"
                    );

            case "P003" ->
                    List.of(
                            "Asthma"
                    );

            case "P004" ->
                    List.of(
                            "Essential hypertension",
                            "Hyperlipidemia"
                    );

            case "P005" ->
                    List.of(
                            "Diabetes mellitus type 2",
                            "Essential hypertension"
                    );

            case "P006" ->
                    List.of(
                            "Asthma",
                            "Allergic rhinitis"
                    );

            default ->
                    fallbackConditions(fhirPatientId);
        };
    }

    /**
     * Builds a FHIR condition resource from a condition display name.
     */
    private FHIRCondition buildCondition(
            String fhirPatientId,
            String suffix,
            String conditionName) {

        String code;
        String onsetDate;

        switch (conditionName) {

            case "Diabetes mellitus type 2" -> {
                code = "44054006";
                onsetDate = "2020-03-01";
            }

            case "Essential hypertension" -> {
                code = "38341003";
                onsetDate = "2019-06-15";
            }

            case "Asthma" -> {
                code = "195967001";
                onsetDate = "2021-02-10";
            }

            case "Hyperlipidemia" -> {
                code = "55822004";
                onsetDate = "2022-08-20";
            }

            case "Allergic rhinitis" -> {
                code = "61582004";
                onsetDate = "2023-04-12";
            }

            default -> {
                code = "UNKNOWN";
                onsetDate = "2024-01-01";
            }
        }

        return FHIRCondition.builder()
                .id(
                        "DEV-COND-"
                                + suffix
                                + "-"
                                + conditionCodeSuffix(conditionName)
                )
                .patientFhirId(fhirPatientId)
                .code(code)
                .displayName(conditionName)
                .clinicalStatus("active")
                .onsetDate(onsetDate)
                .build();
    }

    private String conditionCodeSuffix(
            String conditionName) {

        return conditionName
                .toLowerCase()
                .replace(" ", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    /**
     * Fallback profile for new/unknown FHIR IDs.
     *
     * This remains deterministic:
     * the same FHIR ID always receives the same profile.
     */
    private List<String> fallbackConditions(
            String fhirPatientId) {

        int seed =
                deterministicSeed(fhirPatientId);

        return switch (Math.floorMod(seed, 5)) {

            case 0 ->
                    List.of(
                            "Essential hypertension"
                    );

            case 1 ->
                    List.of(
                            "Diabetes mellitus type 2"
                    );

            case 2 ->
                    List.of(
                            "Asthma"
                    );

            case 3 ->
                    List.of(
                            "Essential hypertension",
                            "Hyperlipidemia"
                    );

            default ->
                    List.of(
                            "Asthma",
                            "Allergic rhinitis"
                    );
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Medications
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<FHIRMedicationRequest> fetchMedications(
            String fhirPatientId) {

        log.debug(
                "[MOCK FHIR] fetchMedications: patient={}",
                fhirPatientId
        );

        String sfx =
                patientSuffix(fhirPatientId);

        return List.of(
                FHIRMedicationRequest.builder()
                        .id(
                                "DEV-MED-"
                                        + sfx
                                        + "-001"
                        )
                        .patientFhirId(fhirPatientId)
                        .medicationName("Metformin")
                        .dose("500 mg")
                        .frequency("Twice daily")
                        .startDate("2024-03-02")
                        .status("active")
                        .build(),

                FHIRMedicationRequest.builder()
                        .id(
                                "DEV-MED-"
                                        + sfx
                                        + "-002"
                        )
                        .patientFhirId(fhirPatientId)
                        .medicationName("Lisinopril")
                        .dose("10 mg")
                        .frequency("Once daily")
                        .startDate("2024-06-18")
                        .status("active")
                        .build()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Encounters
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public List<FHIREncounter> fetchEncounters(
            String fhirPatientId) {

        log.debug(
                "[MOCK FHIR] fetchEncounters: patient={}",
                fhirPatientId
        );

        String sfx =
                patientSuffix(fhirPatientId);

        return List.of(
                FHIREncounter.builder()
                        .id(
                                "DEV-ENC-"
                                        + sfx
                                        + "-001"
                        )
                        .patientFhirId(fhirPatientId)
                        .encounterClass("AMB")
                        .type("Ambulatory encounter")
                        .status("finished")
                        .startDate(
                                devDate(
                                        fhirPatientId,
                                        45
                                )
                                        + "T09:00:00Z"
                        )
                        .endDate(
                                devDate(
                                        fhirPatientId,
                                        45
                                )
                                        + "T09:30:00Z"
                        )
                        .reasonCode("73211009")
                        .reasonDisplay(
                                "Diabetes mellitus follow-up"
                        )
                        .build(),

                FHIREncounter.builder()
                        .id(
                                "DEV-ENC-"
                                        + sfx
                                        + "-002"
                        )
                        .patientFhirId(fhirPatientId)
                        .encounterClass("AMB")
                        .type("Ambulatory encounter")
                        .status("finished")
                        .startDate(
                                devDate(
                                        fhirPatientId,
                                        15
                                )
                                        + "T10:00:00Z"
                        )
                        .endDate(
                                devDate(
                                        fhirPatientId,
                                        15
                                )
                                        + "T10:45:00Z"
                        )
                        .reasonCode("44054006")
                        .reasonDisplay(
                                "Annual diabetes review with lab work"
                        )
                        .build()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void validatePatientId(String fhirId) {

        log.debug(
                "[MOCK FHIR] validatePatientId: {} — always valid in mock mode",
                fhirId
        );

        // Mock always accepts any FHIR ID.
    }

    // ─────────────────────────────────────────────────────────────────────
    // Laboratory data
    // ─────────────────────────────────────────────────────────────────────

    private List<FHIRObservation> labObservationsFor(
            String fhirPatientId) {

        String sfx =
                patientSuffix(fhirPatientId);

        int seed =
                deterministicSeed(fhirPatientId);

        // HbA1c — varies between 6.5 and 9.0
        double hba1c =
                6.5 + (seed % 6) * 0.5;

        String hba1cStatus =
                hba1c > 7.0 ? "H" : "N";

        // Fasting glucose
        double glucose =
                90 + (seed % 8) * 12;

        String glucoseStatus =
                glucose > 126 ? "H" : "N";

        // Total cholesterol
        double cholesterol =
                180 + (seed % 7) * 10;

        String cholStatus =
                cholesterol > 200 ? "H" : "N";

        // HDL
        double hdl =
                40 + (seed % 5) * 5;

        String hdlStatus =
                hdl < 40 ? "L" : "N";

        // Creatinine
        double creatinine =
                0.7 + (seed % 4) * 0.1;

        String creatStatus =
                creatinine > 1.2 ? "H" : "N";

        String dateStr =
                devDate(fhirPatientId, 15);

        return List.of(

                FHIRObservation.builder()
                        .id(
                                "DEV-OBS-"
                                        + sfx
                                        + "-hba1c"
                        )
                        .patientFhirId(fhirPatientId)
                        .loincCode("4548-4")
                        .displayName("HbA1c")
                        .valueQuantity(hba1c)
                        .unit("%")
                        .valueString(hba1c + " %")
                        .status("final")
                        .effectiveDate(dateStr)
                        .referenceRangeHigh("5.7 %")
                        .referenceRangeText("< 5.7%")
                        .category("laboratory")
                        .interpretation(hba1cStatus)
                        .build(),

                FHIRObservation.builder()
                        .id(
                                "DEV-OBS-"
                                        + sfx
                                        + "-glucose"
                        )
                        .patientFhirId(fhirPatientId)
                        .loincCode("2345-7")
                        .displayName("Fasting Glucose")
                        .valueQuantity(glucose)
                        .unit("mg/dL")
                        .valueString(glucose + " mg/dL")
                        .status("final")
                        .effectiveDate(dateStr)
                        .referenceRangeLow("70")
                        .referenceRangeHigh("100")
                        .referenceRangeText(
                                "70 – 100 mg/dL"
                        )
                        .category("laboratory")
                        .interpretation(glucoseStatus)
                        .build(),

                FHIRObservation.builder()
                        .id(
                                "DEV-OBS-"
                                        + sfx
                                        + "-cholesterol"
                        )
                        .patientFhirId(fhirPatientId)
                        .loincCode("2093-3")
                        .displayName("Total Cholesterol")
                        .valueQuantity(cholesterol)
                        .unit("mg/dL")
                        .valueString(
                                cholesterol + " mg/dL"
                        )
                        .status("final")
                        .effectiveDate(dateStr)
                        .referenceRangeText("< 200 mg/dL")
                        .category("laboratory")
                        .interpretation(cholStatus)
                        .build(),

                FHIRObservation.builder()
                        .id(
                                "DEV-OBS-"
                                        + sfx
                                        + "-hdl"
                        )
                        .patientFhirId(fhirPatientId)
                        .loincCode("2085-9")
                        .displayName("HDL Cholesterol")
                        .valueQuantity(hdl)
                        .unit("mg/dL")
                        .valueString(
                                hdl + " mg/dL"
                        )
                        .status("final")
                        .effectiveDate(dateStr)
                        .referenceRangeText("> 40 mg/dL")
                        .category("laboratory")
                        .interpretation(hdlStatus)
                        .build(),

                FHIRObservation.builder()
                        .id(
                                "DEV-OBS-"
                                        + sfx
                                        + "-creatinine"
                        )
                        .patientFhirId(fhirPatientId)
                        .loincCode("2160-0")
                        .displayName("Creatinine")
                        .valueQuantity(creatinine)
                        .unit("mg/dL")
                        .valueString(
                                creatinine + " mg/dL"
                        )
                        .status("final")
                        .effectiveDate(dateStr)
                        .referenceRangeLow("0.6")
                        .referenceRangeHigh("1.2")
                        .referenceRangeText(
                                "0.6 – 1.2 mg/dL"
                        )
                        .category("laboratory")
                        .interpretation(creatStatus)
                        .build()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String devDate(
            String fhirPatientId,
            int daysBack) {

        return LocalDate.now()
                .minusDays(daysBack)
                .toString();
    }

    private String patientSuffix(
            String fhirPatientId) {

        if (fhirPatientId == null) {
            return "UNKNOWN";
        }

        String clean =
                fhirPatientId
                        .replace(
                                "fhir:Patient/",
                                ""
                        )
                        .replace(
                                "Patient/",
                                ""
                        )
                        .replace(
                                ":", 
                                "-"
                        )
                        .replace(
                                "/",
                                "-"
                        );

        return clean.length() > 8
                ? clean.substring(0, 8)
                : clean;
    }

    /**
     * Converts common FHIR identifiers such as:
     *
     * fhir:Patient/P001
     * Patient/P001
     * P001
     *
     * into:
     *
     * P001
     */
    private String normalizePatientId(
            String fhirPatientId) {

        if (fhirPatientId == null
                || fhirPatientId.isBlank()) {

            return "";
        }

        String clean =
                fhirPatientId.trim()
                        .replace(
                                "fhir:Patient/",
                                ""
                        )
                        .replace(
                                "Patient/",
                                ""
                        );

        /*
         * If a longer identifier contains P001/P002/etc,
         * extract the patient ID.
         */
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(
                                "(P\\d+)$",
                                java.util.regex.Pattern.CASE_INSENSITIVE
                        )
                        .matcher(clean);

        if (matcher.find()) {
            return matcher.group(1)
                    .toUpperCase();
        }

        return clean.toUpperCase();
    }

    private int deterministicSeed(
            String fhirPatientId) {

        if (fhirPatientId == null) {
            return 0;
        }

        int sum = 0;

        for (char c :
                fhirPatientId.toCharArray()) {

            sum += c;
        }

        return Math.abs(sum);
    }
}
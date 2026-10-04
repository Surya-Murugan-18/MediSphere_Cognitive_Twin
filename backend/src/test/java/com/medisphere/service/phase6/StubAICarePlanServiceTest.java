package com.medisphere.service.phase6;

import com.medisphere.ai.stub.StubAICarePlanService;
import com.medisphere.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StubAICarePlanService.
 *
 * Verifies:
 *  - deterministic recommendations for the same patient state
 *  - correct recommendations for condition profiles
 *  - safety checks always return 4 entries
 *  - hypoglycaemia check tone is "warning" for diabetic patient
 *
 * Per tasks.md B6.4.
 */
class StubAICarePlanServiceTest {

    private StubAICarePlanService service;

    @BeforeEach
    void setUp() {
        service = new StubAICarePlanService();
    }

    // ── generateRecommendations ───────────────────────────────────────────

    @Test
    void generateRecommendations_diabeticPatient_includesMedicationManagement() {
        Patient patient = buildPatient(List.of("Diabetes", "Hypertension"));
        List<LabResult> labs = List.of(buildLabResult("HbA1c", 8.5));

        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, labs);

        assertThat(recs).isNotEmpty();
        assertThat(recs).anyMatch(r -> r.getTitle().contains("Medication Management"));
    }

    @Test
    void generateRecommendations_hypertensivePatient_includesBpControl() {
        Patient patient = buildPatient(List.of("Hypertension"));
        List<LabResult> labs = List.of();

        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, labs);

        assertThat(recs).anyMatch(r -> r.getTitle().contains("Blood Pressure Control"));
    }

    @Test
    void generateRecommendations_highHba1c_includesTitrationLanguage() {
        Patient patient = buildPatient(List.of("Diabetes"));
        List<LabResult> labs = List.of(buildLabResult("HbA1c", 8.5));

        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, labs);

        CarePlanRecommendation medRec = recs.stream()
                .filter(r -> r.getTitle().contains("Medication Management"))
                .findFirst().orElseThrow();

        assertThat(medRec.getIntervention()).containsIgnoringCase("titrat");
    }

    @Test
    void generateRecommendations_lowHba1c_noTitrationLanguage() {
        Patient patient = buildPatient(List.of("Diabetes"));
        List<LabResult> labs = List.of(buildLabResult("HbA1c", 6.5));

        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, labs);

        CarePlanRecommendation medRec = recs.stream()
                .filter(r -> r.getTitle().contains("Medication Management"))
                .findFirst().orElseThrow();

        assertThat(medRec.getIntervention()).doesNotContainIgnoringCase("titrat");
    }

    @Test
    void generateRecommendations_alwaysIncludesLifestyleAndMonitoring() {
        Patient patient = buildPatient(List.of());
        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, List.of());

        assertThat(recs).anyMatch(r -> r.getTitle().contains("Lifestyle"));
        assertThat(recs).anyMatch(r -> r.getTitle().contains("Monitoring"));
    }

    @Test
    void generateRecommendations_deterministic_sameInputProducesSameOutput() {
        Patient patient = buildPatient(List.of("Diabetes", "Hypertension"));
        List<LabResult> labs = List.of(buildLabResult("HbA1c", 8.2));

        List<CarePlanRecommendation> first  = service.generateRecommendations(patient, null, labs);
        List<CarePlanRecommendation> second = service.generateRecommendations(patient, null, labs);

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i).getTitle()).isEqualTo(second.get(i).getTitle());
            assertThat(first.get(i).getGoal()).isEqualTo(second.get(i).getGoal());
            assertThat(first.get(i).getIntervention()).isEqualTo(second.get(i).getIntervention());
        }
    }

    @Test
    void generateRecommendations_nullPrediction_doesNotThrow() {
        Patient patient = buildPatient(List.of("Diabetes"));
        List<CarePlanRecommendation> recs = service.generateRecommendations(patient, null, List.of());
        assertThat(recs).isNotEmpty();
    }

    // ── runSafetyChecks ───────────────────────────────────────────────────

    @Test
    void runSafetyChecks_alwaysReturnsFourChecks() {
        Patient patient = buildPatient(List.of());
        List<SafetyCheck> checks = service.runSafetyChecks(patient, List.of());
        assertThat(checks).hasSize(4);
    }

    @Test
    void runSafetyChecks_diabeticPatient_hypoglycaemiaIsWarning() {
        Patient patient = buildPatient(List.of("Diabetes"));
        List<SafetyCheck> checks = service.runSafetyChecks(patient, List.of());

        SafetyCheck hypo = checks.stream()
                .filter(c -> c.getLabel().toLowerCase().contains("hypoglyc"))
                .findFirst().orElseThrow();

        assertThat(hypo.getTone()).isEqualTo("warning");
    }

    @Test
    void runSafetyChecks_nonDiabeticPatient_hypoglycaemiaIsHealthy() {
        Patient patient = buildPatient(List.of("Hypertension"));
        List<SafetyCheck> checks = service.runSafetyChecks(patient, List.of());

        SafetyCheck hypo = checks.stream()
                .filter(c -> c.getLabel().toLowerCase().contains("hypoglyc"))
                .findFirst().orElseThrow();

        assertThat(hypo.getTone()).isEqualTo("healthy");
    }

    @Test
    void runSafetyChecks_allChecksHaveIds() {
        Patient patient = buildPatient(List.of());
        List<SafetyCheck> checks = service.runSafetyChecks(patient, List.of());
        assertThat(checks).allMatch(c -> c.getId() != null && !c.getId().isBlank());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(List<String> conditions) {
        Patient p = new Patient();
        p.setId("P-TEST");
        p.setName("Test Patient");
        p.setConditions(new ArrayList<>(conditions));
        return p;
    }

    private LabResult buildLabResult(String test, double value) {
        LabResult lab = new LabResult();
        lab.setTest(test);
        lab.setNumeric(value);
        lab.setResult(value + "%");
        return lab;
    }
}

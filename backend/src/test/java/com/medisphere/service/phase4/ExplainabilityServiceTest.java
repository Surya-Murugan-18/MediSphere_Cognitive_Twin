package com.medisphere.service.phase4;

import com.medisphere.audit.AuditService;
import com.medisphere.domain.Patient;
import com.medisphere.domain.Prediction;
import com.medisphere.dto.response.ShapExplanationResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.PredictionRepository;
import com.medisphere.service.ExplainabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExplainabilityService Unit Tests")
class ExplainabilityServiceTest {

    @Mock private PredictionRepository predictionRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AuditService auditService;

    private ExplainabilityService explainabilityService;

    @BeforeEach
    void setUp() {
        explainabilityService = new ExplainabilityService(
                predictionRepository, patientRepository, auditService);
    }

    // ── getExplanation — success path ─────────────────────────────────────

    @Test
    @DisplayName("getExplanation: returns full response with patient name, SHAP, evidence, summary")
    void getExplanation_found_returnsFullResponse() {
        Prediction prediction = buildPrediction("PR-9001", "P001");
        Patient patient = buildPatient("P001", "John Doe");

        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));

        ShapExplanationResponse result =
                explainabilityService.getExplanation("PR-9001", "PROV-001");

        assertThat(result).isNotNull();
        assertThat(result.predictionId()).isEqualTo("PR-9001");
        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.patientName()).isEqualTo("John Doe");
        assertThat(result.model()).isEqualTo("CVD-Risk-v3.2");
        assertThat(result.value()).isEqualTo(24.3);
        assertThat(result.category()).isEqualTo("High");
        assertThat(result.federatedRound()).isEqualTo(47);
        assertThat(result.shapFactors()).isNotEmpty();
        assertThat(result.clinicalEvidence()).isNotEmpty();
        assertThat(result.naturalLanguageSummary()).isNotBlank();
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("getExplanation: uses patientId as name fallback when patient not found")
    void getExplanation_patientNotFound_usesPatientIdAsFallback() {
        Prediction prediction = buildPrediction("PR-9001", "P999");

        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P999")).thenReturn(Optional.empty());

        ShapExplanationResponse result =
                explainabilityService.getExplanation("PR-9001", "PROV-001");

        assertThat(result.patientName()).isEqualTo("P999"); // falls back to patientId
    }

    @Test
    @DisplayName("getExplanation: throws 404 when prediction not found")
    void getExplanation_predictionNotFound_throws404() {
        when(predictionRepository.findById("PR-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                explainabilityService.getExplanation("PR-UNKNOWN", "PROV-001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PR-UNKNOWN");

        verify(auditService, never()).log(any());
    }

    // ── Natural-language summary ──────────────────────────────────────────

    @Test
    @DisplayName("getExplanation: natural-language summary mentions prediction value and label")
    void getExplanation_summary_mentionsValueAndLabel() {
        Prediction prediction = buildPrediction("PR-9001", "P001");
        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(buildPatient("P001", "John Doe")));

        ShapExplanationResponse result =
                explainabilityService.getExplanation("PR-9001", "PROV-001");

        assertThat(result.naturalLanguageSummary()).contains("24.3");
        assertThat(result.naturalLanguageSummary())
                .containsIgnoringCase("10-year cardiovascular risk");
    }

    @Test
    @DisplayName("getExplanation: summary contains privacy statement about federated learning")
    void getExplanation_summary_containsPrivacyStatement() {
        Prediction prediction = buildPrediction("PR-9001", "P001");
        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(buildPatient("P001", "Test")));

        ShapExplanationResponse result =
                explainabilityService.getExplanation("PR-9001", "PROV-001");

        assertThat(result.naturalLanguageSummary())
                .containsIgnoringCase("federated");
    }

    @Test
    @DisplayName("getExplanation: summary still generated when SHAP factors are empty")
    void getExplanation_noShapFactors_gracefulSummary() {
        Prediction prediction = Prediction.builder()
                .id("PR-EMPTY").patientId("P001")
                .model("CVD-Risk-v3.2").label("10-Year Cardiovascular Risk")
                .value(15.0).category("Low").federatedRound(47)
                .confidence(88).calibration("Calibrated")
                .shapFactors(List.of())          // empty SHAP
                .clinicalEvidence(List.of())
                .createdAt(Instant.now())
                .build();

        when(predictionRepository.findById("PR-EMPTY")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(buildPatient("P001", "Jane")));

        ShapExplanationResponse result =
                explainabilityService.getExplanation("PR-EMPTY", "PROV-001");

        assertThat(result.naturalLanguageSummary()).isNotBlank();
        assertThat(result.shapFactors()).isEmpty();
    }

    // ── Audit ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getExplanation: logs audit with correct action and patientId")
    void getExplanation_logsAuditEntry() {
        Prediction prediction = buildPrediction("PR-9001", "P001");
        when(predictionRepository.findById("PR-9001")).thenReturn(Optional.of(prediction));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(buildPatient("P001", "John Doe")));

        explainabilityService.getExplanation("PR-9001", "PROV-001");

        verify(auditService, times(1)).log(argThat(ctx ->
                "PROV-001".equals(ctx.userId()) &&
                "P001".equals(ctx.patientId()) &&
                ctx.action().contains("SHAP")
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Prediction buildPrediction(String id, String patientId) {
        return Prediction.builder()
                .id(id)
                .patientId(patientId)
                .model("CVD-Risk-v3.2")
                .label("10-Year Cardiovascular Risk")
                .value(24.3)
                .category("High")
                .federatedRound(47)
                .confidence(91)
                .calibration("Calibrated")
                .shapFactors(new ArrayList<>(List.of(
                        Prediction.ShapFactor.builder()
                                .feature("HbA1c").contribution(8.0)
                                .value("8.2%").direction("increases").build(),
                        Prediction.ShapFactor.builder()
                                .feature("Blood Pressure").contribution(6.0)
                                .value("142/91 mmHg").direction("increases").build(),
                        Prediction.ShapFactor.builder()
                                .feature("Medication Adherence").contribution(-2.1)
                                .value("78%").direction("decreases").build()
                )))
                .clinicalEvidence(new ArrayList<>(List.of(
                        new Prediction.ClinicalEvidence(
                                "ACC/AHA CVD Risk Calculator",
                                "Validated against Pooled Cohort Equations.")
                )))
                .createdAt(Instant.now())
                .build();
    }

    private Patient buildPatient(String id, String name) {
        return Patient.builder()
                .id(id).name(name).dob("1968-04-12")
                .conditions(new ArrayList<>())
                .riskLevel("High").status("Active")
                .providerId("PROV-001").fhirConnected(true)
                .adherence(78).wearableStatus("Online").build();
    }
}

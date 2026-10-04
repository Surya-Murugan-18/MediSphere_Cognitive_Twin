package com.medisphere.service.phase3;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.LabResult;
import com.medisphere.domain.Patient;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.FHIRResourceMapper;
import com.medisphere.fhir.mock.MockFHIRClient;
import com.medisphere.repository.LabResultRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.service.LabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LabService Unit Tests (Phase 3)")
class LabServiceTest {

    @Mock private LabResultRepository  labResultRepository;
    @Mock private PatientRepository    patientRepository;
    @Mock private AuditService         auditService;

    private FHIRClient        fhirClient;
    private FHIRResourceMapper fhirMapper;
    private LabService         labService;

    @BeforeEach
    void setUp() {
        fhirClient = new MockFHIRClient();
        fhirMapper  = new FHIRResourceMapper();
        // Pass the mocked AuditService — required by the Phase 3 FR-AUD-05 fix
        labService  = new LabService(labResultRepository, patientRepository,
                fhirClient, fhirMapper, auditService);
    }

    // ── Trend calculation ─────────────────────────────────────────────────

    @Test
    @DisplayName("computeTrend: no previous result → trend=flat, previous=empty")
    void computeTrend_noHistory_flat() {
        when(labResultRepository.findFirstByPatientIdAndTestOrderByDateDesc("P001", "HbA1c"))
                .thenReturn(Optional.empty());
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        LabResult result = LabResult.builder()
                .id("LAB-001").patientId("P001").test("HbA1c")
                .numeric(8.2).result("8.2 %").status("High")
                .category("Metabolic").date("2026-09-15")
                .fhirObservationId("OBS-001").build();

        LabResult saved = labService.saveLabResult(result);

        assertThat(saved.getTrend()).isEqualTo("flat");
        assertThat(saved.getPrevious()).isEmpty();
    }

    @Test
    @DisplayName("computeTrend: current > previous → trend=up")
    void computeTrend_currentHigher_up() {
        LabResult previous = LabResult.builder()
                .id("LAB-000").patientId("P001").test("HbA1c")
                .numeric(7.5).result("7.5 %").date("2026-08-01").build();

        when(labResultRepository.findFirstByPatientIdAndTestOrderByDateDesc("P001", "HbA1c"))
                .thenReturn(Optional.of(previous));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        LabResult current = LabResult.builder()
                .id("LAB-001").patientId("P001").test("HbA1c")
                .numeric(8.2).result("8.2 %").status("High")
                .category("Metabolic").date("2026-09-15")
                .fhirObservationId("OBS-001").build();

        LabResult saved = labService.saveLabResult(current);

        assertThat(saved.getTrend()).isEqualTo("up");
        assertThat(saved.getPrevious()).isEqualTo("7.5 %");
    }

    @Test
    @DisplayName("computeTrend: current < previous → trend=down")
    void computeTrend_currentLower_down() {
        LabResult previous = LabResult.builder()
                .id("LAB-000").patientId("P001").test("HbA1c")
                .numeric(9.1).result("9.1 %").date("2026-08-01").build();

        when(labResultRepository.findFirstByPatientIdAndTestOrderByDateDesc("P001", "HbA1c"))
                .thenReturn(Optional.of(previous));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        LabResult current = LabResult.builder()
                .id("LAB-001").patientId("P001").test("HbA1c")
                .numeric(8.2).result("8.2 %").status("High")
                .category("Metabolic").date("2026-09-15")
                .fhirObservationId("OBS-001").build();

        LabResult saved = labService.saveLabResult(current);

        assertThat(saved.getTrend()).isEqualTo("down");
    }

    @Test
    @DisplayName("computeTrend: delta < 0.01 → trend=flat")
    void computeTrend_tinyDelta_flat() {
        LabResult previous = LabResult.builder()
                .id("LAB-000").patientId("P001").test("HbA1c")
                .numeric(8.2).result("8.2 %").date("2026-08-01").build();

        when(labResultRepository.findFirstByPatientIdAndTestOrderByDateDesc("P001", "HbA1c"))
                .thenReturn(Optional.of(previous));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        LabResult current = LabResult.builder()
                .id("LAB-001").patientId("P001").test("HbA1c")
                .numeric(8.201).result("8.201 %").status("High")
                .category("Metabolic").date("2026-09-15")
                .fhirObservationId("OBS-002").build();

        LabResult saved = labService.saveLabResult(current);

        assertThat(saved.getTrend()).isEqualTo("flat");
    }

    // ── FR-AUD-05 audit event ─────────────────────────────────────────────

    @Test
    @DisplayName("getLabResults: emits exactly one audit entry with correct fields")
    void getLabResults_emitsExactlyOneAuditEntry() {
        Patient patient = Patient.builder()
                .id("P001").name("Eva Testpatient")
                .fhirId("fhir:Patient/e2e-001").build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.findByPatientId(eq("P001"), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        labService.getLabResults("P001", null, null, null, null, 0, 20, "PROV-001");

        // Exactly one audit log call
        ArgumentCaptor<AuditContext> captor = ArgumentCaptor.forClass(AuditContext.class);
        verify(auditService, times(1)).log(captor.capture());

        AuditContext ctx = captor.getValue();
        assertThat(ctx.action()).isEqualTo("Viewed Lab Results");
        assertThat(ctx.module()).isEqualTo("Labs");
        assertThat(ctx.userId()).isEqualTo("PROV-001");
        assertThat(ctx.patientId()).isEqualTo("P001");
        assertThat(ctx.patientName()).isEqualTo("Eva Testpatient");
        assertThat(ctx.status()).isEqualTo("Success");
    }

    @Test
    @DisplayName("getLabResults: filters do not produce additional audit entries")
    void getLabResults_withFilters_stillOneAuditEntry() {
        Patient patient = Patient.builder()
                .id("P001").name("Eva Testpatient")
                .fhirId("fhir:Patient/e2e-001").build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.findByPatientIdAndCategory(eq("P001"), eq("Metabolic"), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // Call with a category filter
        labService.getLabResults("P001", "Metabolic", null, null, null, 0, 20, "PROV-001");

        // Still exactly one audit entry despite the filter
        verify(auditService, times(1)).log(any(AuditContext.class));
    }

    @Test
    @DisplayName("getRecentLabResults: does NOT emit an audit entry")
    void getRecentLabResults_doesNotAudit() {
        when(patientRepository.existsById("P001")).thenReturn(true);
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001"))
                .thenReturn(java.util.List.of());

        labService.getRecentLabResults("P001", 3);

        // getRecentLabResults must not produce any audit entries
        verify(auditService, never()).log(any(AuditContext.class));
    }

    // ── FHIR ingestion ────────────────────────────────────────────────────

    @Test
    @DisplayName("ingestFromFHIR persists 5 new lab results from MockFHIRClient")
    void ingestFromFHIR_newResults_persistsAll() {
        Patient patient = Patient.builder()
                .id("P001").name("Test").fhirId("fhir:Patient/test-001")
                .conditions(new ArrayList<>()).build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(labResultRepository.existsByFhirObservationId(anyString())).thenReturn(false);
        when(labResultRepository.findFirstByPatientIdAndTestOrderByDateDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(i -> i.getArgument(0));

        int count = labService.ingestFromFHIR("P001");

        assertThat(count).isEqualTo(5); // MockFHIRClient returns 5 observations
        verify(labResultRepository, times(5)).save(any(LabResult.class));
    }

    @Test
    @DisplayName("ingestFromFHIR skips existing observations — duplicate prevention")
    void ingestFromFHIR_allAlreadyExists_skipsAll() {
        Patient patient = Patient.builder()
                .id("P001").name("Test").fhirId("fhir:Patient/test-001")
                .conditions(new ArrayList<>()).build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        // All observations already exist
        when(labResultRepository.existsByFhirObservationId(anyString())).thenReturn(true);

        int count = labService.ingestFromFHIR("P001");

        assertThat(count).isEqualTo(0);
        verify(labResultRepository, never()).save(any(LabResult.class));
    }

    @Test
    @DisplayName("ingestFromFHIR returns 0 and does not throw when patient has no fhirId")
    void ingestFromFHIR_noFhirId_returnsZero() {
        Patient patient = Patient.builder()
                .id("P002").name("NoFHIR").fhirId("")
                .conditions(new ArrayList<>()).build();

        when(patientRepository.findById("P002")).thenReturn(Optional.of(patient));

        int count = labService.ingestFromFHIR("P002");

        assertThat(count).isEqualTo(0);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingestFromFHIR is idempotent — second call ingests 0 when all already persisted")
    void ingestFromFHIR_idempotent() {
        Patient patient = Patient.builder()
                .id("P001").name("Test").fhirId("fhir:Patient/test-001")
                .conditions(new ArrayList<>()).build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        // Simulate all observations already exist in DB
        when(labResultRepository.existsByFhirObservationId(anyString())).thenReturn(true);

        int count = labService.ingestFromFHIR("P001");

        assertThat(count).isEqualTo(0);
        verify(labResultRepository, never()).save(any(LabResult.class));
    }
}

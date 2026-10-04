package com.medisphere.service.phase6;

import com.medisphere.ai.AICarePlanService;
import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.*;
import com.medisphere.dto.request.ApproveCarePlanRequest;
import com.medisphere.dto.request.GenerateCarePlanRequest;
import com.medisphere.dto.request.RejectCarePlanRequest;
import com.medisphere.dto.response.CarePlanResponse;
import com.medisphere.dto.response.CarePlanStatsResponse;
import com.medisphere.exception.ConflictException;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.CarePlanApprovedEvent;
import com.medisphere.repository.*;
import com.medisphere.service.CarePlanService;
import com.medisphere.service.TwinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CarePlanService.
 *
 * Covers:
 *  - generate: happy path, patient not found
 *  - approve: happy path, wrong status, audit, Kafka event, twin update
 *  - reject: happy path, wrong status, missing reason, audit
 *  - getActiveCarePlan: found, not found
 *  - getCarePlanStats
 *
 * Per tasks.md B6.4.
 */
@ExtendWith(MockitoExtension.class)
class CarePlanServiceTest {

    @Mock CarePlanRepository carePlanRepository;
    @Mock PatientRepository patientRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock LabResultRepository labResultRepository;
    @Mock HealthTwinRepository twinRepository;
    @Mock AICarePlanService aiCarePlanService;
    @Mock KafkaEventPublisher kafkaEventPublisher;
    @Mock TwinService twinService;
    @Mock AuditService auditService;

    CarePlanService service;

    @BeforeEach
    void setUp() {
        service = new CarePlanService(
                carePlanRepository, patientRepository, predictionRepository,
                labResultRepository, twinRepository, aiCarePlanService,
                kafkaEventPublisher, twinService, auditService);
    }

    // ── generateCarePlan ──────────────────────────────────────────────────

    @Test
    void generateCarePlan_success_returnsDraftPlan() {
        Patient patient = buildPatient("P001", "Alice");
        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(predictionRepository.findFirstByPatientIdAndModelOrderByCreatedAtDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(predictionRepository.findByPatientIdOrderByCreatedAtDesc("P001"))
                .thenReturn(List.of());
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001")).thenReturn(List.of());
        when(aiCarePlanService.generateRecommendations(any(), any(), any()))
                .thenReturn(List.of(buildRec("Medication Management")));
        when(aiCarePlanService.runSafetyChecks(any(), any()))
                .thenReturn(List.of(buildCheck("Drug interaction")));
        when(carePlanRepository.count()).thenReturn(0L);
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarePlanResponse response = service.generateCarePlan(
                new GenerateCarePlanRequest("P001"), "PROV-001", "Dr. Test");

        assertThat(response.getStatus()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(response.getPatientId()).isEqualTo("P001");
        assertThat(response.getGeneratedBy()).isEqualTo("AI");
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getSafetyChecks()).hasSize(1);
        verify(auditService).log(any(AuditContext.class));
    }

    @Test
    void generateCarePlan_patientNotFound_throws404() {
        when(patientRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.generateCarePlan(new GenerateCarePlanRequest("UNKNOWN"), "PROV-001", "Dr. Test"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateCarePlan_noPredictions_stillGenerates() {
        Patient patient = buildPatient("P001", "Alice");
        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(predictionRepository.findFirstByPatientIdAndModelOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(predictionRepository.findByPatientIdOrderByCreatedAtDesc("P001")).thenReturn(List.of());
        when(labResultRepository.findByPatientIdOrderByDateDesc("P001")).thenReturn(List.of());
        when(aiCarePlanService.generateRecommendations(any(), any(), any()))
                .thenReturn(List.of(buildRec("Lifestyle Intervention")));
        when(aiCarePlanService.runSafetyChecks(any(), any())).thenReturn(List.of());
        when(carePlanRepository.count()).thenReturn(0L);
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarePlanResponse response = service.generateCarePlan(
                new GenerateCarePlanRequest("P001"), "PROV-001", "Dr. Test");

        assertThat(response.getStatus()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(response.getRecommendations()).isNotEmpty();
    }

    // ── approveCarePlan ───────────────────────────────────────────────────

    @Test
    void approveCarePlan_success_setsActiveAndPublishesEvent() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.DRAFT);
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthTwin twin = new HealthTwin();
        twin.setId("HT-001");
        twin.setPatientId("P001");
        twin.setTimeline(new ArrayList<>());
        when(twinService.getTwinByPatientId("P001")).thenReturn(twin);
        when(twinService.addTimelineEvent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(twin);

        CarePlanResponse response = service.approveCarePlan(
                "CP-001", new ApproveCarePlanRequest("Looks good"), "PROV-001", "Dr. Test");

        assertThat(response.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(response.getApprovedBy()).isEqualTo("PROV-001");
        assertThat(response.getApprovedByName()).isEqualTo("Dr. Test");
        assertThat(response.getApprovalNotes()).isEqualTo("Looks good");

        // Kafka event published
        ArgumentCaptor<CarePlanApprovedEvent> kafkaCaptor =
                ArgumentCaptor.forClass(CarePlanApprovedEvent.class);
        verify(kafkaEventPublisher).publishCarePlanApproved(kafkaCaptor.capture());
        assertThat(kafkaCaptor.getValue().getPlanId()).isEqualTo("CP-001");
        assertThat(kafkaCaptor.getValue().getPatientId()).isEqualTo("P001");

        // Audit logged
        verify(auditService).log(any(AuditContext.class));

        // Twin updated
        verify(twinService).addTimelineEvent(eq("HT-001"), anyString(), anyString(), eq("healthy"));
    }

    @Test
    void approveCarePlan_alreadyActive_throwsConflict() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.ACTIVE);
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() ->
                service.approveCarePlan("CP-001", null, "PROV-001", "Dr. Test"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void approveCarePlan_rejectedPlan_throwsConflict() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.REJECTED);
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() ->
                service.approveCarePlan("CP-001", null, "PROV-001", "Dr. Test"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approveCarePlan_planNotFound_throws404() {
        when(carePlanRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.approveCarePlan("UNKNOWN", null, "PROV-001", "Dr. Test"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── rejectCarePlan ────────────────────────────────────────────────────

    @Test
    void rejectCarePlan_success_setsRejectedAndAudits() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.DRAFT);
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarePlanResponse response = service.rejectCarePlan(
                "CP-001", new RejectCarePlanRequest("Needs revision"), "PROV-001", "Dr. Test");

        assertThat(response.getStatus()).isEqualTo(CarePlanStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Needs revision");
        verify(auditService).log(any(AuditContext.class));
        // careplan.approved must NOT be published on rejection
        verify(kafkaEventPublisher, never()).publishCarePlanApproved(any());
    }

    @Test
    void rejectCarePlan_alreadyActive_throwsConflict() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.ACTIVE);
        when(carePlanRepository.findById("CP-001")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() ->
                service.rejectCarePlan("CP-001", new RejectCarePlanRequest("reason"), "PROV-001", "Dr. T"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectCarePlan_planNotFound_throws404() {
        when(carePlanRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.rejectCarePlan("UNKNOWN", new RejectCarePlanRequest("reason"), "PROV-001", "Dr. T"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getActiveCarePlan ─────────────────────────────────────────────────

    @Test
    void getActiveCarePlan_found_returnsPlan() {
        CarePlan plan = buildPlan("CP-001", "P001", CarePlanStatus.ACTIVE);
        when(carePlanRepository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc("P001", CarePlanStatus.ACTIVE))
                .thenReturn(Optional.of(plan));

        CarePlanResponse response = service.getActiveCarePlan("P001");

        assertThat(response.getId()).isEqualTo("CP-001");
        assertThat(response.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
    }

    @Test
    void getActiveCarePlan_notFound_throws404() {
        when(carePlanRepository.findFirstByPatientIdAndStatusOrderByCreatedAtDesc("P001", CarePlanStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveCarePlan("P001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getCarePlanStats ──────────────────────────────────────────────────

    @Test
    void getCarePlanStats_returnsCounts() {
        when(carePlanRepository.countByStatus(CarePlanStatus.ACTIVE)).thenReturn(10L);
        when(carePlanRepository.countByStatus(CarePlanStatus.DRAFT)).thenReturn(3L);
        when(carePlanRepository.countByStatus(CarePlanStatus.REJECTED)).thenReturn(2L);
        when(carePlanRepository.findByStatusOrderByCreatedAtDesc(eq(CarePlanStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of()));

        CarePlanStatsResponse stats = service.getCarePlanStats();

        assertThat(stats.getActiveCount()).isEqualTo(10);
        assertThat(stats.getDraftCount()).isEqualTo(3);
        assertThat(stats.getRejectedCount()).isEqualTo(2);
        assertThat(stats.getAvgAdherence()).isEqualTo(0.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(String id, String name) {
        Patient p = new Patient();
        p.setId(id);
        p.setName(name);
        p.setConditions(List.of("Diabetes"));
        p.setRiskLevel("High");
        return p;
    }

    private CarePlan buildPlan(String id, String patientId, CarePlanStatus status) {
        return CarePlan.builder()
                .id(id).patientId(patientId).patientName("Test Patient")
                .goal("HbA1c < 7.0%").riskLevel("High")
                .status(status).providerId("PROV-001").generatedBy("AI")
                .recommendations(new ArrayList<>()).safetyChecks(new ArrayList<>())
                .build();
    }

    private CarePlanRecommendation buildRec(String title) {
        return CarePlanRecommendation.builder().id("rec-1").title(title)
                .goal("goal").intervention("intervention").monitoring("monitoring")
                .outcome("outcome").evidence("evidence").build();
    }

    private SafetyCheck buildCheck(String label) {
        return SafetyCheck.builder().id("sc-1").label(label).detail("detail").tone("healthy").build();
    }
}

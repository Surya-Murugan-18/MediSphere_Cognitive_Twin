package com.medisphere.service;

import com.medisphere.ai.AICarePlanService;
import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.*;
import com.medisphere.dto.request.ApproveCarePlanRequest;
import com.medisphere.dto.request.GenerateCarePlanRequest;
import com.medisphere.dto.request.RejectCarePlanRequest;
import com.medisphere.dto.request.UpdateCarePlanRequest;
import com.medisphere.dto.response.CarePlanResponse;
import com.medisphere.dto.response.CarePlanStatsResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.TimelineEventResponse;
import com.medisphere.exception.ConflictException;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.CarePlanApprovedEvent;
import com.medisphere.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core care plan service.
 *
 * Responsibilities:
 *  - AI-assisted care plan generation (delegates to AICarePlanService)
 *  - Provider approval and rejection workflows
 *  - Care plan CRUD
 *  - Stats and timeline
 *  - Audit logging for all clinically significant actions
 *
 * Per tasks.md B6.3.
 */
@Service
@Slf4j
public class CarePlanService {

    private static final String MODULE = "Care Plans";

    private final CarePlanRepository carePlanRepository;
    private final PatientRepository patientRepository;
    private final PredictionRepository predictionRepository;
    private final LabResultRepository labResultRepository;
    private final HealthTwinRepository twinRepository;
    private final AICarePlanService aiCarePlanService;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final TwinService twinService;
    private final AuditService auditService;

    public CarePlanService(CarePlanRepository carePlanRepository,
                           PatientRepository patientRepository,
                           PredictionRepository predictionRepository,
                           LabResultRepository labResultRepository,
                           HealthTwinRepository twinRepository,
                           AICarePlanService aiCarePlanService,
                           KafkaEventPublisher kafkaEventPublisher,
                           TwinService twinService,
                           AuditService auditService) {
        this.carePlanRepository = carePlanRepository;
        this.patientRepository = patientRepository;
        this.predictionRepository = predictionRepository;
        this.labResultRepository = labResultRepository;
        this.twinRepository = twinRepository;
        this.aiCarePlanService = aiCarePlanService;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.twinService = twinService;
        this.auditService = auditService;
    }

    // ── Generation ────────────────────────────────────────────────────────

    /**
     * Generate a new AI-assisted care plan for a patient.
     *
     * Flow:
     *   1. Load patient (404 if not found)
     *   2. Load latest prediction (null-safe)
     *   3. Load lab results (empty-list-safe)
     *   4. Call AICarePlanService.generateRecommendations()
     *   5. Call AICarePlanService.runSafetyChecks()
     *   6. Construct CarePlan with status=DRAFT
     *   7. Persist and log audit
     */
    public CarePlanResponse generateCarePlan(GenerateCarePlanRequest request, String providerId, String providerName) {
        String patientId = request.getPatientId();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Load the latest prediction (CVD model preferred; falls back to any)
        Prediction latestPrediction = predictionRepository
                .findFirstByPatientIdAndModelOrderByCreatedAtDesc(patientId, "CVD-Risk-v3.2")
                .orElseGet(() -> predictionRepository
                        .findByPatientIdOrderByCreatedAtDesc(patientId)
                        .stream().findFirst().orElse(null));

        // Load all lab results (stub filters internally by test name)
        List<LabResult> labs = labResultRepository.findByPatientIdOrderByDateDesc(patientId);

        // Generate recommendations via AI abstraction layer
        List<CarePlanRecommendation> recommendations =
                aiCarePlanService.generateRecommendations(patient, latestPrediction, labs);

        // Run safety checks via AI abstraction layer
        List<SafetyCheck> safetyChecks =
                aiCarePlanService.runSafetyChecks(patient, recommendations);

        // Build predicted outcome from the prediction value
        CarePlan.PredictedOutcome predictedOutcome = buildPredictedOutcome(latestPrediction);

        // Derive risk level from prediction or patient
        String riskLevel = deriveRiskLevel(latestPrediction, patient);

        // Build goal from prediction context
        String goal = deriveGoal(patient, latestPrediction);

        CarePlan plan = CarePlan.builder()
                .id(generatePlanId())
                .patientId(patientId)
                .patientName(patient.getName())
                .goal(goal)
                .riskLevel(riskLevel)
                .adherence(0)
                .status(CarePlanStatus.DRAFT)
                .providerId(providerId)
                .generatedBy("AI")
                .recommendations(new ArrayList<>(recommendations))
                .safetyChecks(new ArrayList<>(safetyChecks))
                .predictedOutcome(predictedOutcome)
                .build();

        CarePlan saved = carePlanRepository.save(plan);
        log.info("Care plan {} generated for patient {} by provider {}", saved.getId(), patientId, providerId);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Generated Care Plan")
                .module(MODULE)
                .patientId(patientId)
                .patientName(patient.getName())
                .status("Success")
                .build());

        return CarePlanResponse.from(saved);
    }

    // ── Read operations ───────────────────────────────────────────────────

    public PageResponse<CarePlanResponse> getCarePlans(String patientId,
                                                        CarePlanStatus status,
                                                        String riskLevel,
                                                        Pageable pageable) {
        Page<CarePlan> page = carePlanRepository.findFiltered(patientId, status, riskLevel, pageable);
        return PageResponse.from(page, CarePlanResponse::from);
    }

    public CarePlanResponse getCarePlan(String planId) {
        CarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("CarePlan", planId));
        return CarePlanResponse.from(plan);
    }

    public CarePlanResponse getActiveCarePlan(String patientId) {
        return carePlanRepository
                .findFirstByPatientIdAndStatusOrderByCreatedAtDesc(patientId, CarePlanStatus.ACTIVE)
                .map(CarePlanResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active care plan found for patient " + patientId));
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Update an existing care plan (edit mode).
     * Only updates goal and riskLevel; immutable fields (patient, provider, status lifecycle) are preserved.
     */
    public CarePlanResponse updateCarePlan(String planId, UpdateCarePlanRequest request,
                                            String providerId, String providerName) {
        CarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("CarePlan", planId));

        if (StringUtils.hasText(request.getGoal())) {
            plan.setGoal(request.getGoal());
        }
        if (StringUtils.hasText(request.getRiskLevel())) {
            plan.setRiskLevel(request.getRiskLevel());
        }

        CarePlan saved = carePlanRepository.save(plan);
        log.info("Care plan {} updated by provider {}", planId, providerId);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Updated Care Plan")
                .module(MODULE)
                .patientId(plan.getPatientId())
                .patientName(plan.getPatientName())
                .status("Success")
                .build());

        return CarePlanResponse.from(saved);
    }

    // ── Approval ──────────────────────────────────────────────────────────

    /**
     * Approve a DRAFT care plan.
     *
     * Flow:
     *   1. Load plan (404 if not found)
     *   2. Validate status is DRAFT (409 if not)
     *   3. Set status = ACTIVE, record approval metadata
     *   4. Save
     *   5. Publish careplan.approved Kafka event
     *   6. Update twin timeline
     *   7. Log audit
     */
    public CarePlanResponse approveCarePlan(String planId, ApproveCarePlanRequest request,
                                             String providerId, String providerName) {
        CarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("CarePlan", planId));

        if (plan.getStatus() != CarePlanStatus.DRAFT) {
            throw new ConflictException(
                    "Care plan " + planId + " cannot be approved — current status is " + plan.getStatus());
        }

        plan.setStatus(CarePlanStatus.ACTIVE);
        plan.setApprovedBy(providerId);
        plan.setApprovedByName(providerName);
        plan.setApprovedAt(Instant.now());
        plan.setApprovalNotes(request != null ? request.getNotes() : null);

        CarePlan saved = carePlanRepository.save(plan);
        log.info("Care plan {} approved by {} ({})", planId, providerName, providerId);

        // Publish careplan.approved Kafka event (fire-and-forget; degrades gracefully)
        kafkaEventPublisher.publishCarePlanApproved(CarePlanApprovedEvent.builder()
                .planId(planId)
                .patientId(plan.getPatientId())
                .providerId(providerId)
                .timestamp(Instant.now())
                .build());

        // Update the patient's health twin timeline
        try {
            HealthTwin twin = twinService.getTwinByPatientId(plan.getPatientId());
            twinService.addTimelineEvent(
                    twin.getId(),
                    "Care plan approved",
                    "Plan " + planId + " approved by " + providerName + ". Goal: " + plan.getGoal(),
                    "healthy"
            );
        } catch (Exception e) {
            // Twin update failure must not roll back the approval
            log.warn("Could not update twin timeline after care plan approval: {}", e.getMessage());
        }

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Approved Care Plan")
                .module(MODULE)
                .patientId(plan.getPatientId())
                .patientName(plan.getPatientName())
                .status("Success")
                .build());

        return CarePlanResponse.from(saved);
    }

    // ── Rejection ─────────────────────────────────────────────────────────

    /**
     * Reject a DRAFT care plan.
     * Reason is mandatory per FR-CP-09.
     */
    public CarePlanResponse rejectCarePlan(String planId, RejectCarePlanRequest request,
                                            String providerId, String providerName) {
        CarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("CarePlan", planId));

        if (plan.getStatus() != CarePlanStatus.DRAFT) {
            throw new ConflictException(
                    "Care plan " + planId + " cannot be rejected — current status is " + plan.getStatus());
        }

        plan.setStatus(CarePlanStatus.REJECTED);
        plan.setRejectionReason(request.getReason());

        CarePlan saved = carePlanRepository.save(plan);
        log.info("Care plan {} rejected by {} — reason: {}", planId, providerName, request.getReason());

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Rejected Care Plan")
                .module(MODULE)
                .patientId(plan.getPatientId())
                .patientName(plan.getPatientName())
                .status("Success")
                .build());

        return CarePlanResponse.from(saved);
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public CarePlanStatsResponse getCarePlanStats() {
        long activeCount   = carePlanRepository.countByStatus(CarePlanStatus.ACTIVE);
        long draftCount    = carePlanRepository.countByStatus(CarePlanStatus.DRAFT);
        long rejectedCount = carePlanRepository.countByStatus(CarePlanStatus.REJECTED);

        // Average adherence across all active plans
        List<CarePlan> activePlans =
                carePlanRepository.findByStatusOrderByCreatedAtDesc(CarePlanStatus.ACTIVE,
                        Pageable.unpaged()).getContent();
        double avgAdherence = activePlans.isEmpty() ? 0.0
                : activePlans.stream().mapToInt(CarePlan::getAdherence).average().orElse(0.0);

        return CarePlanStatsResponse.builder()
                .activeCount(activeCount)
                .draftCount(draftCount)
                .rejectedCount(rejectedCount)
                .avgAdherence(Math.round(avgAdherence * 10.0) / 10.0)
                .hospitalizationReduction(23.0)   // Placeholder — real computation in Phase 7 analytics
                .build();
    }

    // ── Timeline ──────────────────────────────────────────────────────────

    /**
     * Return a care-plan-level timeline derived from the plan's own state history.
     */
    public List<TimelineEventResponse> getCarePlanTimeline(String planId) {
        CarePlan plan = carePlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("CarePlan", planId));

        List<TimelineEventResponse> timeline = new ArrayList<>();

        // Generation event
        timeline.add(TimelineEventResponse.builder()
                .id("cpt-gen-" + planId)
                .timestamp(plan.getCreatedAt() != null ? plan.getCreatedAt().toString() : "")
                .title("Care plan generated")
                .detail("AI-generated care plan " + planId + " for " + plan.getPatientName()
                        + ". Goal: " + plan.getGoal())
                .tone("info")
                .build());

        // Approval event
        if (plan.getApprovedAt() != null) {
            timeline.add(TimelineEventResponse.builder()
                    .id("cpt-apr-" + planId)
                    .timestamp(plan.getApprovedAt().toString())
                    .title("Care plan approved")
                    .detail("Approved by " + plan.getApprovedByName()
                            + (StringUtils.hasText(plan.getApprovalNotes())
                                    ? ". Notes: " + plan.getApprovalNotes() : ""))
                    .tone("healthy")
                    .build());
        }

        // Rejection event
        if (plan.getStatus() == CarePlanStatus.REJECTED) {
            timeline.add(TimelineEventResponse.builder()
                    .id("cpt-rej-" + planId)
                    .timestamp(plan.getUpdatedAt() != null ? plan.getUpdatedAt().toString() : "")
                    .title("Care plan rejected")
                    .detail("Reason: " + plan.getRejectionReason())
                    .tone("critical")
                    .build());
        }

        return timeline;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String generatePlanId() {
        // Simple sequential approach: find current max and increment
        long count = carePlanRepository.count();
        return String.format("CP-%03d", count + 1);
    }

    private CarePlan.PredictedOutcome buildPredictedOutcome(Prediction prediction) {
        if (prediction == null) return null;
        // Compute a projected improvement based on risk reduction heuristic
        double before = prediction.getValue();
        double after = Math.max(before * 0.65, before - 8.0);  // ~35% reduction or 8pp absolute, whichever is larger
        after = Math.round(after * 10.0) / 10.0;
        return CarePlan.PredictedOutcome.builder()
                .metric(prediction.getLabel() != null ? prediction.getLabel() : "CVD risk")
                .before(before)
                .after(after)
                .unit("%")
                .build();
    }

    private String deriveRiskLevel(Prediction prediction, Patient patient) {
        if (prediction != null && StringUtils.hasText(prediction.getCategory())) {
            return prediction.getCategory(); // High | Medium | Low
        }
        return patient.getRiskLevel() != null ? patient.getRiskLevel() : "Medium";
    }

    private String deriveGoal(Patient patient, Prediction prediction) {
        if (patient.getConditions() != null) {
            for (String cond : patient.getConditions()) {
                if (cond != null && cond.toLowerCase().contains("diabetes")) {
                    return "HbA1c < 7.0%";
                }
                if (cond != null && cond.toLowerCase().contains("hypertension")) {
                    return "BP < 130/80 mmHg";
                }
            }
        }
        if (prediction != null) {
            return "Reduce " + (prediction.getLabel() != null ? prediction.getLabel() : "risk");
        }
        return "Prevent 30-day readmission";
    }
}

package com.medisphere.dto.response;

import com.medisphere.domain.CarePlan;
import com.medisphere.domain.CarePlanRecommendation;
import com.medisphere.domain.CarePlanStatus;
import com.medisphere.domain.SafetyCheck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for care plan operations.
 * Maps CarePlan document → API response.
 * Per design.md §10 and tasks.md B6.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlanResponse {

    private String id;
    private String patientId;
    private String patientName;
    private String goal;
    private String riskLevel;
    private int adherence;

    /** Enum value: DRAFT | ACTIVE | REJECTED */
    private CarePlanStatus status;

    private String providerId;
    private String generatedBy;

    private List<CarePlanRecommendation> recommendations;
    private List<SafetyCheck> safetyChecks;
    private PredictedOutcomeDto predictedOutcome;

    // Approval info
    private String approvedBy;
    private String approvedByName;
    private Instant approvedAt;
    private String approvalNotes;

    // Rejection info
    private String rejectionReason;

    private Instant createdAt;
    private Instant updatedAt;

    // ── Static factory ────────────────────────────────────────────────────

    public static CarePlanResponse from(CarePlan plan) {
        return CarePlanResponse.builder()
                .id(plan.getId())
                .patientId(plan.getPatientId())
                .patientName(plan.getPatientName())
                .goal(plan.getGoal())
                .riskLevel(plan.getRiskLevel())
                .adherence(plan.getAdherence())
                .status(plan.getStatus())
                .providerId(plan.getProviderId())
                .generatedBy(plan.getGeneratedBy())
                .recommendations(plan.getRecommendations())
                .safetyChecks(plan.getSafetyChecks())
                .predictedOutcome(plan.getPredictedOutcome() != null
                        ? PredictedOutcomeDto.from(plan.getPredictedOutcome())
                        : null)
                .approvedBy(plan.getApprovedBy())
                .approvedByName(plan.getApprovedByName())
                .approvedAt(plan.getApprovedAt())
                .approvalNotes(plan.getApprovalNotes())
                .rejectionReason(plan.getRejectionReason())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    // ── Nested DTO ────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictedOutcomeDto {
        private String metric;
        private double before;
        private double after;
        private String unit;

        public static PredictedOutcomeDto from(CarePlan.PredictedOutcome po) {
            return PredictedOutcomeDto.builder()
                    .metric(po.getMetric())
                    .before(po.getBefore())
                    .after(po.getAfter())
                    .unit(po.getUnit())
                    .build();
        }
    }
}

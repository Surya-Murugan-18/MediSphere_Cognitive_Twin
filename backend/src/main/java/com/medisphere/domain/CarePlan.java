package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Care Plan document.
 * Collection: care_plans
 *
 * Per design.md §4.9.
 *
 * Status lifecycle:  DRAFT → ACTIVE | REJECTED
 * Recommendations and safety checks are embedded (not separate collections).
 *
 * Phase 6 implementation.
 */
@Document(collection = "care_plans")
@CompoundIndexes({
    @CompoundIndex(name = "cp_patient_status_idx", def = "{'patientId':1,'status':1}"),
    @CompoundIndex(name = "cp_provider_idx",       def = "{'providerId':1}"),
    @CompoundIndex(name = "cp_created_idx",        def = "{'createdAt':-1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarePlan {

    /** Format: CP-xxx */
    @Id
    private String id;

    private String patientId;
    @TextIndexed
    private String patientName;

    /** Primary care goal, e.g. "HbA1c < 7.0%" */
    @TextIndexed
    private String goal;

    /** High | Medium | Low — derived from AI prediction at generation time. */
    private String riskLevel;

    /**
     * Adherence percentage (0–100).
     * Starts at 0 on generation; updated by AdherenceService.
     */
    @Builder.Default
    private int adherence = 0;

    /** DRAFT | ACTIVE | REJECTED */
    @Builder.Default
    private CarePlanStatus status = CarePlanStatus.DRAFT;

    /** Provider who triggered generation. */
    private String providerId;

    /** Always "AI" for plans generated via AICarePlanService. */
    @Builder.Default
    private String generatedBy = "AI";

    // ── Embedded recommendations ──────────────────────────────────────────

    @Builder.Default
    private List<CarePlanRecommendation> recommendations = new ArrayList<>();

    // ── Embedded safety checks ────────────────────────────────────────────

    @Builder.Default
    private List<SafetyCheck> safetyChecks = new ArrayList<>();

    // ── Predicted outcome (single primary metric) ─────────────────────────

    private PredictedOutcome predictedOutcome;

    // ── Approval fields (null until approved) ────────────────────────────

    /** Provider ID who approved the plan. */
    private String approvedBy;

    /** Display name of approving provider. */
    private String approvedByName;

    private Instant approvedAt;

    /** Optional approval notes. */
    private String approvalNotes;

    // ── Rejection fields (null unless rejected) ───────────────────────────

    /** Mandatory reason when status = REJECTED. */
    private String rejectionReason;

    // ── Timestamps ────────────────────────────────────────────────────────

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ── Embedded sub-type ─────────────────────────────────────────────────

    /**
     * Predicted primary clinical outcome attached to this plan.
     * Derived from the AI prediction at generation time.
     * e.g. { metric="CVD risk", before=24.3, after=16.2, unit="%" }
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictedOutcome {
        private String metric;  // e.g. "CVD risk"
        private double before;  // e.g. 24.3
        private double after;   // e.g. 16.2
        private String unit;    // e.g. "%"
    }
}

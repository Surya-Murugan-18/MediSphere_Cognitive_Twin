package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Clinical Alert document.
 * Collection: alerts
 *
 * Per design.md §4.8. The auditTrail list is embedded (not a separate collection)
 * so all alert state transitions are atomically co-located with the alert document.
 *
 * Status lifecycle: Unacknowledged → Acknowledged → Escalated → Resolved
 * Severity:         HIGH | MEDIUM | LOW
 */
@Document(collection = "alerts")
@CompoundIndexes({
    @CompoundIndex(name = "alert_patient_idx",    def = "{'patientId':1}"),
    @CompoundIndex(name = "alert_status_idx",     def = "{'status':1}"),
    @CompoundIndex(name = "alert_severity_idx",   def = "{'severity':1}"),
    @CompoundIndex(name = "alert_detectedAt_idx", def = "{'detectedAt':-1}"),
    @CompoundIndex(name = "alert_patient_status_idx", def = "{'patientId':1,'status':1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    /** Format: A-xxxx */
    @Id
    private String id;

    /** HIGH | MEDIUM | LOW */
    private String severity;

    private String patientId;
    @TextIndexed
    private String patientName;

    /** Short human-readable event description, e.g. "Heart Rate Spike: 145 BPM" */
    @TextIndexed
    private String event;

    /** AI analysis narrative. */
    private String analysis;

    /** Alert type, e.g. "Vitals anomaly", "Device" */
    private String type;

    /** Rule code that triggered the alert: HR_SPIKE_P95 | SPO2_LOW | BP_ELEVATED | DEVICE_OFFLINE */
    private String ruleCode;

    /** When the anomaly was detected (set at creation, immutable). */
    @Indexed
    private Instant detectedAt;

    /** Unacknowledged | Acknowledged | Escalated | Resolved */
    private String status;

    /** Provider ID assigned to this alert. */
    private String assignedProvider;

    /** Current value at time of detection, e.g. "145 BPM" */
    private String currentValue;

    /** Previous normal value, e.g. "68 BPM" */
    private String previousValue;

    /** AI confidence score 0–100 */
    private int confidence;

    // ── Acknowledge fields ────────────────────────────────────────────────
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private String acknowledgeNotes;

    // ── Escalate fields ───────────────────────────────────────────────────
    private String escalatedBy;
    private Instant escalatedAt;
    private String escalationReason;
    private String escalatedTo;

    // ── Resolve fields ────────────────────────────────────────────────────
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolution;

    /**
     * Embedded per-alert audit trail. Every state transition appends an entry here.
     * Initialised as an empty list so null checks are never needed.
     */
    @Builder.Default
    private List<AlertAuditEntry> auditTrail = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;
}

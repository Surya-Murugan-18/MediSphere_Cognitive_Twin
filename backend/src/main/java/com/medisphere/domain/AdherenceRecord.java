package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Weekly adherence record per care plan.
 * Collection: adherence_records
 *
 * Tracks three dimensions per week:
 *   - medication adherence
 *   - monitoring adherence
 *   - follow-up adherence
 *
 * Overall adherence = average of the three dimensions (equal weighting).
 *
 * Per tasks.md B6.2 and requirements.md FR-ADH-01/FR-ADH-02.
 */
@Document(collection = "adherence_records")
@CompoundIndexes({
    @CompoundIndex(name = "adh_plan_week_idx", def = "{'planId':1,'weekNumber':1}"),
    @CompoundIndex(name = "adh_patient_idx",   def = "{'patientId':1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdherenceRecord {

    @Id
    private String id;

    private String planId;
    private String patientId;

    /** 1-based week number since plan was approved. */
    private int weekNumber;

    /** Medication adherence percentage (0–100). */
    private int medicationAdherence;

    /** Monitoring adherence percentage (0–100). */
    private int monitoringAdherence;

    /** Follow-up adherence percentage (0–100). */
    private int followUpAdherence;

    /**
     * Overall adherence — equal-weighted average of the three dimensions.
     * Formula: (medication + monitoring + followUp) / 3
     * Per FR-ADH-02: "weighted composite of the three dimensions."
     */
    private int overallAdherence;

    /** Start date of this adherence week. */
    private Instant weekStartDate;

    @CreatedDate
    private Instant recordedAt;
}

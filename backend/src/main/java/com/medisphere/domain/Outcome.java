package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Clinical outcome measurement per care plan.
 * Collection: outcomes
 *
 * Stores the primary clinical metric's baseline, current value, and goal.
 * e.g. HbA1c: baseline=8.2, current=7.6, goal=7.0 (unit=%)
 *
 * Per tasks.md B6.2 and requirements.md FR-ADH-04.
 */
@Document(collection = "outcomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Outcome {

    @Id
    private String id;

    @Indexed
    private String planId;

    private String patientId;

    /** Metric name, e.g. "HbA1c", "CVD risk" */
    private String metric;

    /** Value at plan creation. */
    private double baseline;

    /** Most recent measured value. */
    private double current;

    /** Target value. */
    private double goal;

    /** Unit string, e.g. "%" */
    private String unit;

    /**
     * Trend direction: improving | stable | worsening.
     * Computed server-side based on direction toward goal.
     */
    private String trend;

    private Instant updatedAt;
    private Instant createdAt;
}

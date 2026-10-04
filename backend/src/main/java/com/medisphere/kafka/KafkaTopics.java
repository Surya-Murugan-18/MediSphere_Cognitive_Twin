package com.medisphere.kafka;

/**
 * Central registry of all Kafka topic names used in MediSphere.
 *
 * Phase 5 activates: vitals.raw, vitals.anomaly, alert.created, twin.update
 * Phase 6 activates: careplan.approved
 * Phase 7 activates: report.ready, fhir.ingested
 * Phase 4 activates: federated.round
 *
 * Constants are defined here so no service hard-codes a topic name string.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // ── Phase 5 topics ───────────────────────────────────────────────────
    /** Raw vital sign readings from wearable devices / simulator. */
    public static final String VITALS_RAW      = "vitals.raw";

    /** Anomaly events produced when a clinical rule fires. */
    public static final String VITALS_ANOMALY  = "vitals.anomaly";

    /** Alert lifecycle events (created, acknowledged, etc.). */
    public static final String ALERT_CREATED   = "alert.created";

    /** Digital twin state changes. */
    public static final String TWIN_UPDATE     = "twin.update";

    // ── Phase 6 ──────────────────────────────────────────────────────────
    /** Care plan approval notifications. */
    public static final String CAREPLAN_APPROVED = "careplan.approved";

    // ── Phase 7 ──────────────────────────────────────────────────────────
    /** FHIR resource ingestion events. */
    public static final String FHIR_INGESTED   = "fhir.ingested";

    /** Async report generation completion. */
    public static final String REPORT_READY    = "report.ready";

    // ── Phase 4 ──────────────────────────────────────────────────────────
    /** Federated learning round progress. */
    public static final String FEDERATED_ROUND = "federated.round";
}

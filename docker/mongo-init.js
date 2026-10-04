// =============================================================================
// MediSphere — MongoDB Initialisation Script
// Phase 8 — B8.1
//
// Runs inside the mongo container on first startup (MONGO_INITDB_DATABASE).
// Creates all required indexes for the medisphere database.
//
// Idempotent: createIndex() is a no-op if the index already exists.
// =============================================================================

const db = db.getSiblingDB('medisphere');

// ── providers ─────────────────────────────────────────────────────────────────
db.providers.createIndex({ email: 1 }, { unique: true, name: "idx_providers_email_unique" });
db.providers.createIndex({ active: 1 }, { name: "idx_providers_active" });

// ── refresh_tokens ─────────────────────────────────────────────────────────────
db.refresh_tokens.createIndex({ tokenHash: 1 }, { unique: true, name: "idx_refresh_tokens_hash_unique" });
db.refresh_tokens.createIndex({ providerId: 1 }, { name: "idx_refresh_tokens_provider" });
// TTL index: MongoDB auto-deletes expired refresh tokens
db.refresh_tokens.createIndex(
  { expiresAt: 1 },
  { expireAfterSeconds: 0, name: "idx_refresh_tokens_ttl" }
);

// ── patients ──────────────────────────────────────────────────────────────────
db.patients.createIndex({ name: "text" }, { name: "idx_patients_name_text" });
db.patients.createIndex({ risk: 1 }, { name: "idx_patients_risk" });
db.patients.createIndex({ status: 1 }, { name: "idx_patients_status" });
db.patients.createIndex({ "provider": 1 }, { name: "idx_patients_provider" });
db.patients.createIndex({ fhirId: 1 }, { sparse: true, name: "idx_patients_fhir_id" });

// ── health_twins ──────────────────────────────────────────────────────────────
db.health_twins.createIndex({ patientId: 1 }, { unique: true, name: "idx_twins_patient_unique" });
db.health_twins.createIndex({ status: 1 }, { name: "idx_twins_status" });

// ── vitals_timeseries (time-series vitals) ────────────────────────────────────
// Collection name from VitalsTimeSeries.java @Document("vitals_timeseries")
db.vitals_timeseries.createIndex({ patientId: 1, timestamp: -1 }, { name: "idx_vitals_patient_time" });
db.vitals_timeseries.createIndex({ patientId: 1, type: 1, timestamp: -1 }, { name: "idx_vitals_patient_type_time" });

// ── vitals_snapshots (current vitals snapshot per patient) ────────────────────
// Collection name from VitalsSnapshot.java @Document("vitals_snapshots")
db.vitals_snapshots.createIndex({ patientId: 1 }, { unique: true, name: "idx_vitals_snapshots_patient_unique" });
db.vitals_snapshots.createIndex({ updatedAt: -1 }, { name: "idx_vitals_snapshots_time" });

// ── lab_results ───────────────────────────────────────────────────────────────
db.lab_results.createIndex({ patientId: 1, reportedAt: -1 }, { name: "idx_labs_patient_time" });
db.lab_results.createIndex({ fhirObservationId: 1 }, { sparse: true, name: "idx_labs_fhir_obs" });

// ── predictions ───────────────────────────────────────────────────────────────
db.predictions.createIndex({ patientId: 1, createdAt: -1 }, { name: "idx_predictions_patient_time" });
db.predictions.createIndex({ patientId: 1, model: 1 }, { name: "idx_predictions_patient_model" });

// ── alerts ────────────────────────────────────────────────────────────────────
db.alerts.createIndex({ patientId: 1, createdAt: -1 }, { name: "idx_alerts_patient_time" });
db.alerts.createIndex({ status: 1 }, { name: "idx_alerts_status" });
db.alerts.createIndex({ severity: 1 }, { name: "idx_alerts_severity" });
db.alerts.createIndex({ createdAt: -1 }, { name: "idx_alerts_time" });

// ── care_plans ────────────────────────────────────────────────────────────────
db.care_plans.createIndex({ patientId: 1, createdAt: -1 }, { name: "idx_care_plans_patient_time" });
db.care_plans.createIndex({ status: 1 }, { name: "idx_care_plans_status" });

// ── consents ──────────────────────────────────────────────────────────────────
db.consents.createIndex({ patientId: 1 }, { unique: true, name: "idx_consents_patient_unique" });

// ── audit_logs ────────────────────────────────────────────────────────────────
db.audit_logs.createIndex({ timestamp: -1 }, { name: "idx_audit_time" });
db.audit_logs.createIndex({ userId: 1, timestamp: -1 }, { name: "idx_audit_user_time" });
db.audit_logs.createIndex({ patientId: 1, timestamp: -1 }, { sparse: true, name: "idx_audit_patient_time" });
db.audit_logs.createIndex({ module: 1, timestamp: -1 }, { name: "idx_audit_module_time" });

// ── adherence_records ─────────────────────────────────────────────────────────
db.adherence_records.createIndex({ planId: 1, weekStart: -1 }, { name: "idx_adherence_plan_week" });

// ── report_jobs ───────────────────────────────────────────────────────────────
db.report_jobs.createIndex({ reportId: 1, createdAt: -1 }, { name: "idx_report_jobs_report_time" });

// ── fhir_config (FHIR server configuration) ───────────────────────────────────
// Collection name from FhirConfiguration.java @Document("fhir_config") — singular
db.fhir_config.createIndex({ updatedAt: -1 }, { name: "idx_fhir_config_time" });

// ── system_events (platform-level events) ────────────────────────────────────
// Collection name from SystemEvent.java @Document("system_events")
db.system_events.createIndex({ timestamp: -1 }, { name: "idx_system_events_time" });

// ── outcomes (care plan outcome records) ─────────────────────────────────────
// Collection name from Outcome.java @Document("outcomes")
db.outcomes.createIndex({ planId: 1, recordedAt: -1 }, { name: "idx_outcomes_plan_time" });

// ── wearable_devices ──────────────────────────────────────────────────────────
db.wearable_devices.createIndex({ patientId: 1 }, { unique: true, name: "idx_devices_patient_unique" });

print("MediSphere MongoDB indexes created successfully.");

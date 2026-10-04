package com.medisphere.service;

import com.medisphere.domain.*;
import org.springframework.data.domain.Sort;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.model.FHIRCondition;
import com.medisphere.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Development-only data seeder.
 *
 * DEVELOPMENT USE ONLY — NOT FOR PRODUCTION.
 * Runs only when spring.profiles.active=dev  (@Profile("dev") guard).
 *
 * Responsibilities (all idempotent — safe to run on every dev startup):
 *  1. Seed development provider accounts (CLINICIAN + ADMIN).
 *  2. Seed one deterministic E2E test patient with full clinical context:
 *       - Patient document (P-E2E-01)
 *       - Digital health twin (HT-E2E-01)
 *       - Consent record (CST-E2E-01)
 *       - Wearable device (DEV-E2E-01)
 *       - Vitals snapshot (current readings)
 *       - Vitals time-series (24-hour HR history)
 *       - Lab results (HbA1c, Creatinine, LDL)
 *       - One pre-seeded Unacknowledged alert (A-E2E-01)
 *  3. Backfill missing FHIR conditions for existing patients.
 *  4. Trigger predictions for existing patients without them.
 *
 * Patient identifier conventions:
 *   Patient ID:  P-E2E-01   (recognisably test data, not P001-style prod IDs)
 *   Twin ID:     HT-E2E-01
 *   Consent ID:  CST-E2E-01
 *   Device ID:   DEV-E2E-01
 *   Alert ID:    A-E2E-01
 *   Lab IDs:     LAB-E2E-01 … LAB-E2E-03
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // ── Provider seed credentials ──────────────────────────────────────────────
    private static final String CLINICIAN_EMAIL    = "a.mehta@medisphere.dev";
    private static final String CLINICIAN_PASSWORD = "Medisphere@123";
    private static final String ADMIN_EMAIL        = "admin@medisphere.dev";
    private static final String ADMIN_PASSWORD     = "Admin@123";

    // ── E2E test patient constants ─────────────────────────────────────────────
    static final String E2E_PATIENT_ID   = "P-E2E-01";
    static final String E2E_PATIENT_NAME = "Eva Testpatient";
    static final String E2E_TWIN_ID      = "HT-E2E-01";
    static final String E2E_CONSENT_ID   = "CST-E2E-01";
    static final String E2E_DEVICE_ID    = "DEV-E2E-01";
    static final String E2E_ALERT_ID     = "A-E2E-01";
    static final String E2E_FHIR_ID      = "fhir:Patient/e2e-test-0001";

    // ── Repositories ──────────────────────────────────────────────────────────
    private final ProviderRepository        providerRepository;
    private final PasswordEncoder           passwordEncoder;
    private final PatientRepository         patientRepository;
    private final HealthTwinRepository      twinRepository;
    private final ConsentRepository         consentRepository;
    private final WearableDeviceRepository  deviceRepository;
    private final VitalsSnapshotRepository  snapshotRepository;
    private final VitalsTimeSeriesRepository timeSeriesRepository;
    private final LabResultRepository       labResultRepository;
    private final AlertRepository           alertRepository;
    private final PredictionRepository      predictionRepository;
    private final PredictionService         predictionService;
    private final FHIRClient                fhirClient;

    public DataSeeder(
            ProviderRepository providerRepository,
            PasswordEncoder passwordEncoder,
            PatientRepository patientRepository,
            HealthTwinRepository twinRepository,
            ConsentRepository consentRepository,
            WearableDeviceRepository deviceRepository,
            VitalsSnapshotRepository snapshotRepository,
            VitalsTimeSeriesRepository timeSeriesRepository,
            LabResultRepository labResultRepository,
            AlertRepository alertRepository,
            PredictionRepository predictionRepository,
            PredictionService predictionService,
            FHIRClient fhirClient) {

        this.providerRepository   = providerRepository;
        this.passwordEncoder      = passwordEncoder;
        this.patientRepository    = patientRepository;
        this.twinRepository       = twinRepository;
        this.consentRepository    = consentRepository;
        this.deviceRepository     = deviceRepository;
        this.snapshotRepository   = snapshotRepository;
        this.timeSeriesRepository = timeSeriesRepository;
        this.labResultRepository  = labResultRepository;
        this.alertRepository      = alertRepository;
        this.predictionRepository = predictionRepository;
        this.predictionService    = predictionService;
        this.fhirClient           = fhirClient;
    }

    @Override
    public void run(String... args) {
        seedClinician();
        seedAdmin();
        seedE2ETestPatient();
        seedMissingConditions();
        seedMissingPredictions();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Provider seeding
    // ─────────────────────────────────────────────────────────────────────────

    private void seedClinician() {
        if (providerRepository.existsByEmail(CLINICIAN_EMAIL)) {
            log.debug("[DEV SEED] Clinician account already exists — skipping");
            return;
        }
        Provider clinician = Provider.builder()
                .id("PROV-001")
                .name("Dr. A. Mehta")
                .email(CLINICIAN_EMAIL)
                .passwordHash(passwordEncoder.encode(CLINICIAN_PASSWORD))
                .role(ProviderRole.CLINICIAN)
                .specialty("Cardiology")
                .facility("Hospital A — Northside General")
                .npi("1234567890")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build();
        providerRepository.save(clinician);
        log.info("[DEV SEED] Created clinician account: {}", CLINICIAN_EMAIL);
    }

    private void seedAdmin() {
        if (providerRepository.existsByEmail(ADMIN_EMAIL)) {
            log.debug("[DEV SEED] Admin account already exists — skipping");
            return;
        }
        Provider admin = Provider.builder()
                .id("PROV-ADM")
                .name("Admin User")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(ProviderRole.ADMIN)
                .specialty("Administration")
                .facility("MediSphere Platform")
                .notificationPrefs(NotificationPrefs.defaults())
                .active(true)
                .build();
        providerRepository.save(admin);
        log.info("[DEV SEED] Created admin account: {}", ADMIN_EMAIL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // E2E test patient — full clinical context
    // ─────────────────────────────────────────────────────────────────────────

    private void seedE2ETestPatient() {
        if (patientRepository.existsById(E2E_PATIENT_ID)) {
            log.debug("[DEV SEED] E2E test patient {} already exists — skipping", E2E_PATIENT_ID);
            return;
        }

        Instant now = Instant.now();

        // 1. Patient document ───────────────────────────────────────────────
        Patient patient = Patient.builder()
                .id(E2E_PATIENT_ID)
                .fhirId(E2E_FHIR_ID)
                .name(E2E_PATIENT_NAME)
                .dob("1968-04-12")
                .gender("Female")
                .contact(Patient.ContactInfo.builder()
                        .phone("+1 (415) 555-0199")
                        .email("eva.testpatient@dev.invalid")
                        .build())
                .conditions(List.of("Type 2 Diabetes", "Hypertension"))
                .riskLevel("High")
                .status("Active")
                .providerId("PROV-001")
                .providerName("Dr. A. Mehta")
                .twinId(E2E_TWIN_ID)
                .consentId(E2E_CONSENT_ID)
                .ehrSystem("Mock EHR")
                .fhirConnected(true)
                .wearableStatus("Online")
                .consentComplete(true)
                .healthStatus("Stable")
                .adherence(78)
                .build();
        patientRepository.save(patient);
        log.info("[DEV SEED] Created E2E test patient: {} ({})", E2E_PATIENT_NAME, E2E_PATIENT_ID);

        // 2. Digital health twin ────────────────────────────────────────────
        if (!twinRepository.existsById(E2E_TWIN_ID)) {
            HealthTwin twin = HealthTwin.builder()
                    .id(E2E_TWIN_ID)
                    .patientId(E2E_PATIENT_ID)
                    .modelVersion("v2.1")
                    .completeness(82)
                    .status("Synchronized")
                    .lastUpdated(now.minus(5, ChronoUnit.MINUTES))
                    .stateVersion(3L)
                    .dataSources(HealthTwin.DataSources.builder()
                            .ehr(new HealthTwin.DataSourceEntry(true, now.minus(1, ChronoUnit.HOURS)))
                            .lab(new HealthTwin.DataSourceEntry(true, now.minus(2, ChronoUnit.HOURS)))
                            .wearable(new HealthTwin.DataSourceEntry(true, now.minus(5, ChronoUnit.MINUTES)))
                            .kafka(new HealthTwin.DataSourceEntry(true, now.minus(5, ChronoUnit.MINUTES)))
                            .build())
                    .bodyRegions(List.of(
                            new HealthTwin.BodyRegion("cardiac",     "Cardiac",               "Elevated CVD risk — 10-year score 24.3%",     "high"),
                            new HealthTwin.BodyRegion("vascular",    "Vascular / Blood Pressure", "BP 142/91 — above threshold",             "medium"),
                            new HealthTwin.BodyRegion("metabolic",   "Metabolic",             "HbA1c 8.2% — above target",                   "high"),
                            new HealthTwin.BodyRegion("renal",       "Renal",                 "Creatinine 0.9 mg/dL — within range",         "low"),
                            new HealthTwin.BodyRegion("respiratory", "Respiratory",           "SpO2 97% — normal",                           "low")
                    ))
                    .timeline(List.of(
                            HealthTwin.TimelineEvent.builder()
                                    .id("TW-E2E-01")
                                    .timestamp(now.minus(2, ChronoUnit.HOURS))
                                    .title("Digital Twin created")
                                    .detail("Initial twin created with EHR + lab data.")
                                    .tone("info")
                                    .build(),
                            HealthTwin.TimelineEvent.builder()
                                    .id("TW-E2E-02")
                                    .timestamp(now.minus(30, ChronoUnit.MINUTES))
                                    .title("AI risk prediction updated")
                                    .detail("10-year CVD risk assessed at 24.3%.")
                                    .tone("warning")
                                    .build()
                    ))
                    .build();
            twinRepository.save(twin);
            log.info("[DEV SEED] Created digital twin {} for patient {}", E2E_TWIN_ID, E2E_PATIENT_ID);
        }

        // 3. Consent record ─────────────────────────────────────────────────
        if (!consentRepository.existsById(E2E_CONSENT_ID)) {
            Consent consent = Consent.builder()
                    .id(E2E_CONSENT_ID)
                    .patientId(E2E_PATIENT_ID)
                    .ehr(true)
                    .wearable(true)
                    .ai(true)
                    .updatedAt(now.minus(2, ChronoUnit.HOURS))
                    .updatedBy("PROV-001")
                    .history(List.of(
                            Consent.ConsentHistoryEntry.builder()
                                    .id("CH-E2E-01")
                                    .date(now.minus(2, ChronoUnit.HOURS))
                                    .type("EHR Data Access")
                                    .status("Granted")
                                    .updatedBy("Patient portal")
                                    .build(),
                            Consent.ConsentHistoryEntry.builder()
                                    .id("CH-E2E-02")
                                    .date(now.minus(2, ChronoUnit.HOURS))
                                    .type("Wearable Data Access")
                                    .status("Granted")
                                    .updatedBy("Patient portal")
                                    .build(),
                            Consent.ConsentHistoryEntry.builder()
                                    .id("CH-E2E-03")
                                    .date(now.minus(2, ChronoUnit.HOURS))
                                    .type("AI Risk Analysis")
                                    .status("Granted")
                                    .updatedBy("Patient portal")
                                    .build()
                    ))
                    .build();
            consentRepository.save(consent);
            log.info("[DEV SEED] Created consent record {} for patient {}", E2E_CONSENT_ID, E2E_PATIENT_ID);
        }

        // 4. Wearable device ────────────────────────────────────────────────
        if (!deviceRepository.existsById(E2E_DEVICE_ID)) {
            WearableDevice device = WearableDevice.builder()
                    .id(E2E_DEVICE_ID)
                    .patientId(E2E_PATIENT_ID)
                    .displayName("Smart Watch · SW-E2E01")
                    .deviceType("Smartwatch")
                    .manufacturer("MediSphere Dev")
                    .kafkaTopic("vitals.raw")
                    .deviceKey("SW-E2E01")
                    .status("Online")
                    .lastSeen(now.minus(1, ChronoUnit.MINUTES))
                    .registeredAt(now.minus(2, ChronoUnit.HOURS))
                    .build();
            deviceRepository.save(device);
            log.info("[DEV SEED] Created wearable device {} for patient {}", E2E_DEVICE_ID, E2E_PATIENT_ID);
        }

        // 5. Vitals snapshot (current readings) ────────────────────────────
        if (snapshotRepository.findByPatientId(E2E_PATIENT_ID).isEmpty()) {
            VitalsSnapshot snapshot = VitalsSnapshot.builder()
                    .patientId(E2E_PATIENT_ID)
                    .heartRate(85)
                    .bloodPressure("142/91")
                    .spo2(97.0)
                    .temperature(36.8)
                    .respiratoryRate(16)
                    .source("seed")
                    .deviceId("SW-E2E01")
                    .updatedAt(now.minus(1, ChronoUnit.MINUTES))
                    .build();
            snapshotRepository.save(snapshot);
            log.info("[DEV SEED] Created vitals snapshot for patient {}", E2E_PATIENT_ID);
        }

        // 6. Vitals time-series (24-hour HR history, hourly) ───────────────
        boolean hasVitalsHistory = !timeSeriesRepository
        .findByPatientIdAndTypeAndTimestampBetween(
                E2E_PATIENT_ID,
                "heartRate",
                now.minus(25, ChronoUnit.HOURS),
                now,
                Sort.by(Sort.Direction.ASC, "timestamp"))
        .isEmpty();

        if (!hasVitalsHistory) {
            // 24 hourly HR readings — clinically plausible for a cardiac patient
            int[] hrValues = {72, 68, 65, 70, 74, 78, 80, 82, 85, 88, 84, 81,
                              79, 76, 80, 83, 87, 85, 82, 80, 78, 75, 73, 85};
            for (int i = 0; i < hrValues.length; i++) {
                timeSeriesRepository.save(VitalsTimeSeries.builder()
                        .id("VTS-E2E-HR-" + String.format("%02d", i))
                        .patientId(E2E_PATIENT_ID)
                        .type("heartRate")
                        .value(hrValues[i])
                        .unit("BPM")
                        .timestamp(now.minus(24 - i, ChronoUnit.HOURS))
                        .source("seed")
                        .deviceId("SW-E2E01")
                        .build());
            }
            // Also seed a few SpO2 readings
            int[] spo2Values = {97, 96, 97, 98, 97, 96, 97, 98};
            for (int i = 0; i < spo2Values.length; i++) {
                timeSeriesRepository.save(VitalsTimeSeries.builder()
                        .id("VTS-E2E-SPO2-" + String.format("%02d", i))
                        .patientId(E2E_PATIENT_ID)
                        .type("spo2")
                        .value(spo2Values[i])
                        .unit("%")
                        .timestamp(now.minus(24 - (i * 3), ChronoUnit.HOURS))
                        .source("seed")
                        .deviceId("SW-E2E01")
                        .build());
            }
            log.info("[DEV SEED] Created vitals time-series history for patient {}", E2E_PATIENT_ID);
        }

        // 7. Lab results ────────────────────────────────────────────────────
        if (!labResultRepository.existsById("LAB-E2E-01")) {
            labResultRepository.save(LabResult.builder()
                    .id("LAB-E2E-01")
                    .patientId(E2E_PATIENT_ID)
                    .fhirObservationId("obs-e2e-hba1c")
                    .loinc("4548-4")
                    .test("HbA1c")
                    .result("8.2 %")
                    .numeric(8.2)
                    .unit("%")
                    .referenceRange("< 5.7%")
                    .status("High")
                    .category("Metabolic")
                    .date("2026-09-15")
                    .trend("up")
                    .previous("7.8 %")
                    .significance("Above target — review medication adherence")
                    .build());

            labResultRepository.save(LabResult.builder()
                    .id("LAB-E2E-02")
                    .patientId(E2E_PATIENT_ID)
                    .fhirObservationId("obs-e2e-creatinine")
                    .loinc("38483-4")
                    .test("Creatinine")
                    .result("0.9 mg/dL")
                    .numeric(0.9)
                    .unit("mg/dL")
                    .referenceRange("0.6 – 1.2 mg/dL")
                    .status("Normal")
                    .category("Metabolic")
                    .date("2026-09-15")
                    .trend("flat")
                    .previous("0.9 mg/dL")
                    .significance("Within normal range")
                    .build());

            labResultRepository.save(LabResult.builder()
                    .id("LAB-E2E-03")
                    .patientId(E2E_PATIENT_ID)
                    .fhirObservationId("obs-e2e-ldl")
                    .loinc("18262-6")
                    .test("LDL Cholesterol")
                    .result("142 mg/dL")
                    .numeric(142.0)
                    .unit("mg/dL")
                    .referenceRange("< 100 mg/dL")
                    .status("High")
                    .category("Lipids")
                    .date("2026-09-15")
                    .trend("down")
                    .previous("158 mg/dL")
                    .significance("Above optimal — statin therapy in progress")
                    .build());

            log.info("[DEV SEED] Created 3 lab results for patient {}", E2E_PATIENT_ID);
        }

        // 8. Pre-seeded alert (Unacknowledged — deterministic for E2E tests) ─
        if (!alertRepository.existsById(E2E_ALERT_ID)) {
            Alert alert = Alert.builder()
                    .id(E2E_ALERT_ID)
                    .severity("HIGH")
                    .patientId(E2E_PATIENT_ID)
                    .patientName(E2E_PATIENT_NAME)
                    .event("Heart Rate Spike: 145 BPM")
                    .analysis("Heart rate exceeded 95th-percentile baseline (68 BPM). " +
                              "Combined with elevated BP may indicate autonomic dysregulation. " +
                              "Review medication adherence and recent activity.")
                    .type("Vitals anomaly")
                    .ruleCode("HR_SPIKE_P95")
                    .detectedAt(now.minus(30, ChronoUnit.MINUTES))
                    .status("Unacknowledged")
                    .assignedProvider("PROV-001")
                    .currentValue("145 BPM")
                    .previousValue("68 BPM")
                    .confidence(89)
                    .auditTrail(List.of(
                            AlertAuditEntry.builder()
                                    .id("AU-E2E-01")
                                    .timestamp(now.minus(30, ChronoUnit.MINUTES))
                                    .actor("MediSphere Stream Processor")
                                    .action("Anomaly detected on vitals.raw — rule HR_SPIKE_P95")
                                    .build(),
                            AlertAuditEntry.builder()
                                    .id("AU-E2E-02")
                                    .timestamp(now.minus(30, ChronoUnit.MINUTES))
                                    .actor("Alert Service")
                                    .action("Alert A-E2E-01 created with severity HIGH (seeded)")
                                    .build()
                    ))
                    .build();
            alertRepository.save(alert);
            log.info("[DEV SEED] Created pre-seeded alert {} for patient {}", E2E_ALERT_ID, E2E_PATIENT_ID);
        }

        // 9. Trigger AI predictions if none exist ──────────────────────────
        boolean hasPredictions = predictionRepository.existsByPatientIdAndModel(
                E2E_PATIENT_ID, "CVD-Risk-v3.2");
        if (!hasPredictions) {
            log.info("[DEV SEED] Triggering predictions for E2E patient {}", E2E_PATIENT_ID);
            predictionService.runPredictions(E2E_PATIENT_ID);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conditions backfill (existing patients from pre-Phase-8 data)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedMissingConditions() {
        try {
            patientRepository.findAll().forEach(patient -> {
                if (patient.getFhirId() == null || patient.getFhirId().isBlank()) {
                    log.debug("[DEV SEED] Patient {} has no FHIR ID — skipping condition sync",
                            patient.getId());
                    return;
                }
                try {
                    List<FHIRCondition> fhirConditions = fhirClient.fetchConditions(patient.getFhirId());
                    List<String> conditions = fhirConditions == null ? List.of()
                            : fhirConditions.stream()
                                    .filter(c -> c != null && c.getDisplayName() != null && !c.getDisplayName().isBlank())
                                    .filter(c -> !"resolved".equalsIgnoreCase(c.getClinicalStatus()))
                                    .map(FHIRCondition::getDisplayName)
                                    .distinct()
                                    .toList();

                    if (conditions.isEmpty()) return;

                    List<String> existing = patient.getConditions() == null ? List.of() : patient.getConditions();
                    if (existing.equals(conditions)) return;

                    patient.setConditions(conditions);
                    patientRepository.save(patient);
                    log.info("[DEV SEED] Patient {} conditions synchronized: {}", patient.getId(), conditions);

                } catch (Exception e) {
                    log.warn("[DEV SEED] Failed to sync conditions for patient {}: {}",
                            patient.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[DEV SEED] Failed to sync patient conditions: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prediction backfill (existing patients without predictions)
    // ─────────────────────────────────────────────────────────────────────────

    private void seedMissingPredictions() {
        try {
            patientRepository.findAll().forEach(patient -> {
                boolean hasPredictions = predictionRepository.existsByPatientIdAndModel(
                        patient.getId(), "CVD-Risk-v3.2");
                if (!hasPredictions) {
                    log.info("[DEV SEED] Patient {} has no predictions — triggering async run",
                            patient.getId());
                    predictionService.runPredictions(patient.getId());
                }
            });
        } catch (Exception e) {
            log.warn("[DEV SEED] Failed to seed predictions: {}", e.getMessage());
        }
    }
}

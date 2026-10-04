package com.medisphere.service;

import com.medisphere.config.AnomalyProperties;
import com.medisphere.config.WearableProperties;
import com.medisphere.domain.Patient;
import com.medisphere.domain.VitalsTimeSeries;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.VitalsAnomalyEvent;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.VitalsTimeSeriesRepository;
import com.medisphere.repository.WearableDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Anomaly detection service — evaluates incoming vital readings against
 * configurable clinical rules and fires alerts when thresholds are breached.
 *
 * Rules (per tasks.md B5.3):
 *   HR_SPIKE_P95  — HR > patient's rolling 95th-percentile baseline
 *   SPO2_LOW      — SpO₂ < 94% (configurable)
 *   BP_ELEVATED   — systolic > 140 OR diastolic > 90 (configurable)
 *   DEVICE_OFFLINE — no reading for wearable.offline-threshold-minutes
 *
 * All thresholds are read from {@link AnomalyProperties} (externalised to
 * application.yml / env vars). No threshold is hardcoded here.
 *
 * The service is deterministic: for the same patient history + reading,
 * it always produces the same result. No randomness.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionService {

    private final AnomalyProperties props;
    private final WearableProperties wearableProps;
    private final VitalsTimeSeriesRepository timeSeriesRepository;
    private final PatientRepository patientRepository;
    private final WearableDeviceRepository wearableDeviceRepository;
    private final AlertService alertService;
    private final KafkaEventPublisher kafkaEventPublisher;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Evaluates a single incoming vital reading against all applicable rules.
     * Each rule that fires will create one alert (via AlertService).
     *
     * @param patientId the patient the reading belongs to
     * @param type      vital type: heartRate | spo2 | bloodPressureSystolic | bloodPressureDiastolic
     * @param value     the numeric value
     * @param snapshot  current snapshot string (e.g. "145 BPM")
     */
    public void checkVitals(String patientId, String type, double value, String snapshotValue) {
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            log.warn("AnomalyDetection: patient {} not found, skipping", patientId);
            return;
        }
        Patient patient = patientOpt.get();

        switch (type) {
            case "heartRate"             -> checkHrSpike(patient, value, snapshotValue);
            case "spo2"                  -> checkSpo2(patient, value, snapshotValue);
            case "bloodPressureSystolic" -> checkBpSystolic(patient, value, snapshotValue);
            case "bloodPressureDiastolic"-> checkBpDiastolic(patient, value, snapshotValue);
            default                      -> log.debug("No anomaly rule for vital type: {}", type);
        }
    }

    /**
     * DEVICE_OFFLINE rule — called on a scheduled basis by the wearable checker.
     * Checks all Online wearables that have not reported within the threshold window.
     */
    public void checkDeviceOffline() {
        Instant threshold = Instant.now().minus(props.getHrHistoryWindow(), ChronoUnit.MINUTES);

        wearableDeviceRepository.findAll().forEach(device -> {
            if (!"Online".equals(device.getStatus())) return;

            Instant offlineThreshold = Instant.now()
                    .minus(getOfflineThresholdMinutes(), ChronoUnit.MINUTES);

            if (device.getLastSeen() == null || device.getLastSeen().isBefore(offlineThreshold)) {
                String patientId = device.getPatientId();
                Optional<Patient> patientOpt = patientRepository.findById(patientId);
                String patientName = patientOpt.map(Patient::getName).orElse("Unknown");

                // Mark device offline in the DB
                device.setStatus("Offline");
                wearableDeviceRepository.save(device);
                log.info("Device {} marked offline for patient {}", device.getId(), patientId);

                // Create alert
                String eventDesc = "Wearable device offline — no signal for >"
                        + getOfflineThresholdMinutes() + " minutes";
                createAlert(patientId, patientName, "DEVICE_OFFLINE", "LOW",
                        "Device", eventDesc,
                        "Device has not streamed to Kafka topic vitals.raw for >"
                                + getOfflineThresholdMinutes() + " minutes.",
                        "No signal", "Streaming", 0.99,
                        device.getId() != null ? device.getId() : patientId);
            }
        });
    }

    // ── Private rule implementations ──────────────────────────────────────

    private void checkHrSpike(Patient patient, double hr, String snapshotValue) {
        List<Double> history = getHrHistory(patient.getId());

        if (history.size() < props.getHrMinSamples()) {
            log.debug("HR_SPIKE_P95: insufficient samples ({}) for patient {}",
                    history.size(), patient.getId());
            return;
        }

        double p95 = percentile(history, props.getHrSpikePercentile());
        if (hr > p95) {
            log.info("HR_SPIKE_P95 fired: patient={} hr={} p95={}", patient.getId(), hr, p95);

            // Get previous "normal" HR as the P50 (median) of the baseline
            double prevNormal = percentile(history, 50);

            createAlert(patient.getId(), patient.getName(),
                    "HR_SPIKE_P95", "HIGH",
                    "Vitals anomaly",
                    "Heart Rate Spike: " + (int) hr + " BPM",
                    "Sustained tachycardia inconsistent with patient baseline (P95=" + String.format("%.0f", p95) + " BPM).",
                    snapshotValue != null ? snapshotValue : (int) hr + " BPM",
                    String.format("%.0f BPM", prevNormal),
                    computeHrConfidence(hr, p95),
                    patient.getId());
        }
    }

    private void checkSpo2(Patient patient, double spo2, String snapshotValue) {
        if (spo2 < props.getSpo2LowThreshold()) {
            log.info("SPO2_LOW fired: patient={} spo2={}", patient.getId(), spo2);

            String prevValue = getPreviousSpo2(patient.getId(), spo2);

            createAlert(patient.getId(), patient.getName(),
                    "SPO2_LOW", "HIGH",
                    "Vitals anomaly",
                    "Low SpO₂: " + String.format("%.1f", spo2) + "%",
                    "SpO₂ below safe threshold of " + props.getSpo2LowThreshold() + "%. "
                            + "Risk of hypoxia — immediate assessment required.",
                    snapshotValue != null ? snapshotValue : String.format("%.1f%%", spo2),
                    prevValue,
                    computeSpo2Confidence(spo2, props.getSpo2LowThreshold()),
                    patient.getId());
        }
    }

    private void checkBpSystolic(Patient patient, double systolic, String snapshotValue) {
        if (systolic > props.getBpSystolicThreshold()) {
            log.info("BP_ELEVATED (systolic) fired: patient={} sys={}", patient.getId(), systolic);

            createAlert(patient.getId(), patient.getName(),
                    "BP_ELEVATED", "MEDIUM",
                    "Vitals anomaly",
                    "Elevated Blood Pressure: " + (int) systolic + " mmHg (systolic)",
                    "Systolic blood pressure above " + props.getBpSystolicThreshold()
                            + " mmHg threshold — hypertensive reading.",
                    snapshotValue != null ? snapshotValue : (int) systolic + " mmHg",
                    "—",
                    0.80,
                    patient.getId());
        }
    }

    private void checkBpDiastolic(Patient patient, double diastolic, String snapshotValue) {
        if (diastolic > props.getBpDiastolicThreshold()) {
            log.info("BP_ELEVATED (diastolic) fired: patient={} dia={}", patient.getId(), diastolic);

            createAlert(patient.getId(), patient.getName(),
                    "BP_ELEVATED", "MEDIUM",
                    "Vitals anomaly",
                    "Elevated Blood Pressure: " + (int) diastolic + " mmHg (diastolic)",
                    "Diastolic blood pressure above " + props.getBpDiastolicThreshold()
                            + " mmHg threshold.",
                    snapshotValue != null ? snapshotValue : (int) diastolic + " mmHg",
                    "—",
                    0.75,
                    patient.getId());
        }
    }

    // ── Alert creation helper ─────────────────────────────────────────────

    private void createAlert(String patientId, String patientName,
                              String ruleCode, String severity, String type,
                              String event, String analysis,
                              String currentValue, String previousValue,
                              double confidenceDecimal,
                              String key) {

        AlertService.CreateAlertCommand cmd = new AlertService.CreateAlertCommand(
                severity, patientId, patientName, event, analysis, type, ruleCode,
                "PROV-001",           // default assignment — Phase 7 routing will improve this
                currentValue, previousValue,
                (int) Math.round(confidenceDecimal * 100));

        try {
            com.medisphere.domain.Alert alert = alertService.createAlert(cmd);

            // Publish vitals.anomaly event
            kafkaEventPublisher.publishVitalsAnomaly(VitalsAnomalyEvent.builder()
                    .patientId(patientId)
                    .alertId(alert.getId())
                    .rule(ruleCode)
                    .score(confidenceDecimal)
                    .currentValue(currentValue)
                    .previousValue(previousValue)
                    .timestamp(Instant.now())
                    .build());

        } catch (Exception e) {
            log.error("Failed to create alert for rule {} patient {}: {}",
                    ruleCode, patientId, e.getMessage(), e);
        }
    }

    // ── Statistics helpers ────────────────────────────────────────────────

    /**
     * Retrieve the last {@code hrHistoryWindow} heart rate readings for a patient.
     * Returns newest-first order; values only (doubles).
     */
    private List<Double> getHrHistory(String patientId) {
        Instant windowStart = Instant.now().minus(7, ChronoUnit.DAYS); // 7-day window cap
        List<VitalsTimeSeries> readings = timeSeriesRepository
                .findByPatientIdAndTypeAndTimestampBetween(
                        patientId, "heartRate", windowStart, Instant.now(),
                        Sort.by(Sort.Direction.DESC, "timestamp"));

        int limit = Math.min(readings.size(), props.getHrHistoryWindow());
        List<Double> values = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            values.add(readings.get(i).getValue());
        }
        return values;
    }

    private String getPreviousSpo2(String patientId, double currentSpo2) {
        Instant windowStart = Instant.now().minus(1, ChronoUnit.HOURS);
        List<VitalsTimeSeries> readings = timeSeriesRepository
                .findByPatientIdAndTypeAndTimestampBetween(
                        patientId, "spo2", windowStart, Instant.now(),
                        Sort.by(Sort.Direction.DESC, "timestamp"));
        // Return the second-most-recent reading (the "previous" value)
        if (readings.size() >= 2) {
            return String.format("%.1f%%", readings.get(1).getValue());
        }
        return "—";
    }

    /**
     * Compute the Nth percentile of a list of values.
     * Uses the nearest-rank method for simplicity and determinism.
     * Public for testability.
     */
    public static double percentile(List<Double> values, int percentile) {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /** Higher confidence when HR further exceeds the P95 baseline. */
    private static double computeHrConfidence(double hr, double p95) {
        double excess = (hr - p95) / p95; // fractional excess above baseline
        return Math.min(0.99, 0.70 + excess * 0.5);
    }

    private static double computeSpo2Confidence(double spo2, double threshold) {
        double deficit = threshold - spo2;
        return Math.min(0.99, 0.80 + deficit * 0.04);
    }

    private long getOfflineThresholdMinutes() {
        // Reads from medisphere.wearable.offline-threshold-minutes (application.yml).
        // Default: 30. Override via env var WEARABLE_OFFLINE_THRESHOLD.
        return wearableProps.getOfflineThresholdMinutes();
    }
}

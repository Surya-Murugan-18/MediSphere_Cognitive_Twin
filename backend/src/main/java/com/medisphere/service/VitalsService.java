package com.medisphere.service;

import com.medisphere.domain.VitalsSnapshot;
import com.medisphere.domain.VitalsTimeSeries;
import com.medisphere.domain.WearableDevice;
import com.medisphere.dto.response.VitalsHistoryResponse;
import com.medisphere.dto.response.VitalsSnapshotResponse;
import com.medisphere.dto.response.WearableDeviceResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.VitalsSnapshotRepository;
import com.medisphere.repository.VitalsTimeSeriesRepository;
import com.medisphere.repository.WearableDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vitals service — manages current snapshot and historical time-series data.
 *
 * Phase 3 note: Real-time Kafka streaming is added in Phase 5.
 * For Phase 3, vitals are seeded deterministically from patient attributes
 * via seedVitalsForPatient() called on patient creation.
 */
@Service
public class VitalsService {

    private static final Logger log = LoggerFactory.getLogger(VitalsService.class);

    private final VitalsSnapshotRepository snapshotRepository;
    private final VitalsTimeSeriesRepository timeSeriesRepository;
    private final WearableDeviceRepository wearableRepository;
    private final PatientRepository patientRepository;

    public VitalsService(VitalsSnapshotRepository snapshotRepository,
                         VitalsTimeSeriesRepository timeSeriesRepository,
                         WearableDeviceRepository wearableRepository,
                         PatientRepository patientRepository) {
        this.snapshotRepository = snapshotRepository;
        this.timeSeriesRepository = timeSeriesRepository;
        this.wearableRepository = wearableRepository;
        this.patientRepository = patientRepository;
    }

    // ── Current snapshot ──────────────────────────────────────────────────

    /**
     * Get the current vital sign snapshot for a patient.
     * If no snapshot exists, seeds default vitals (development convenience).
     */
    public VitalsSnapshotResponse getCurrentVitals(String patientId) {
        validatePatientExists(patientId);
        VitalsSnapshot snapshot = snapshotRepository.findByPatientId(patientId)
                .orElseGet(() -> {
                    log.debug("No vitals snapshot for {}; seeding default values", patientId);
                    return seedSnapshotForPatient(patientId);
                });
        return VitalsSnapshotResponse.from(snapshot);
    }

    // ── Vitals history ────────────────────────────────────────────────────

    /**
     * Get historical time-series data for a specific vital type and period.
     *
     * Supported periods (matching the existing Vitals UI):
     *   24h  — last 24 hours (heart rate, SpO2)
     *   7d   — last 7 days   (blood pressure)
     *   30d  — last 30 days
     *
     * If no historical data exists, seeds development data automatically.
     */
    public VitalsHistoryResponse getVitalsHistory(String patientId, String type, String period) {
        validatePatientExists(patientId);

        Instant now = Instant.now();
        Instant from = switch (period != null ? period.toLowerCase() : "24h") {
            case "7d"  -> now.minus(7,  ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            default    -> now.minus(24, ChronoUnit.HOURS); // "24h" default
        };

        Sort sort = Sort.by(Sort.Direction.ASC, "timestamp");
        List<VitalsTimeSeries> raw = timeSeriesRepository
                .findByPatientIdAndTypeAndTimestampBetween(patientId, type, from, now, sort);

        // Seed development data if nothing stored yet
        if (raw.isEmpty()) {
            log.debug("No history for {}/{} — seeding development data", patientId, type);
            seedHistoryForPatient(patientId, type, from, now);
            raw = timeSeriesRepository
                    .findByPatientIdAndTypeAndTimestampBetween(patientId, type, from, now, sort);
        }

        List<VitalsHistoryResponse.DataPoint> points = buildDataPoints(raw, period);

        return new VitalsHistoryResponse(patientId, type, period, points);
    }

    // ── Save a vital reading ──────────────────────────────────────────────

    /**
     * Persist a new vital reading to time-series and upsert the current snapshot.
     * Called by Phase 5 Kafka consumer — stub interface already in place.
     */
    public void saveVitalReading(String patientId, String type, double value,
                                  String unit, String source, String deviceId) {
        // Persist to time-series
        VitalsTimeSeries entry = VitalsTimeSeries.builder()
                .id(UUID.randomUUID().toString())
                .patientId(patientId)
                .type(type)
                .value(value)
                .unit(unit)
                .timestamp(Instant.now())
                .source(source)
                .deviceId(deviceId)
                .build();
        timeSeriesRepository.save(entry);

        // Upsert the current snapshot
        VitalsSnapshot snapshot = snapshotRepository.findByPatientId(patientId)
                .orElse(VitalsSnapshot.builder().patientId(patientId).build());

        applyToSnapshot(snapshot, type, value, source, deviceId);
        snapshotRepository.save(snapshot);

        // Keep the registered wearable's presence/freshness in sync with
        // incoming readings. This is used by the Digital Twin source status.
        wearableRepository.findByPatientId(patientId).ifPresent(device -> {
            device.setStatus("Online");
            device.setLastSeen(Instant.now());
            wearableRepository.save(device);
        });
    }

    // ── Wearable device ───────────────────────────────────────────────────

    /**
     * Get the wearable device registered for a patient.
     * Returns a default "not registered" device if none exists.
     */
    public WearableDeviceResponse getWearableDevice(String patientId) {
        validatePatientExists(patientId);
        WearableDevice device = wearableRepository.findByPatientId(patientId)
                .orElseGet(() -> buildDefaultDevice(patientId));
        return WearableDeviceResponse.from(device);
    }

    // ── Seed helpers (Phase 3 development convenience) ────────────────────

    /**
     * Seed a default vitals snapshot and wearable device when a patient is created.
     * Values are derived deterministically from the patient ID — no randomness.
     */
    public void seedVitalsForPatient(String patientId) {
        // Avoid overwriting existing data
        if (snapshotRepository.findByPatientId(patientId).isPresent()) return;

        VitalsSnapshot snap = seedSnapshotForPatient(patientId);

        // Seed a wearable device
        if (wearableRepository.findByPatientId(patientId).isEmpty()) {
            int seed = deterministicSeed(patientId);
            String devKey = "SW-" + String.format("%04d", seed % 9000 + 1000);
            WearableDevice device = WearableDevice.builder()
                    .id("DEV-" + patientId)
                    .patientId(patientId)
                    .displayName("Smart Watch · " + devKey)
                    .deviceType("Smartwatch")
                    .manufacturer("BioSense")
                    .kafkaTopic("vitals.raw")
                    .deviceKey(devKey)
                    .status("Online")
                    .lastSeen(Instant.now())
                    .registeredAt(Instant.now())
                    .build();
            wearableRepository.save(device);
            log.debug("Seeded wearable device {} for patient {}", devKey, patientId);
        }

        // Seed 24h heart rate and SpO2 history + 7d blood pressure history
        Instant now = Instant.now();
        seedHistoryForPatient(patientId, "heartRate",             now.minus(24, ChronoUnit.HOURS), now);
        seedHistoryForPatient(patientId, "spo2",                  now.minus(24, ChronoUnit.HOURS), now);
        seedHistoryForPatient(patientId, "bloodPressureSystolic", now.minus(7,  ChronoUnit.DAYS),  now);
        seedHistoryForPatient(patientId, "bloodPressureDiastolic",now.minus(7,  ChronoUnit.DAYS),  now);
    }

    // ── Private internals ─────────────────────────────────────────────────

    private VitalsSnapshot seedSnapshotForPatient(String patientId) {
        int seed = deterministicSeed(patientId);
        VitalsSnapshot snap = VitalsSnapshot.builder()
                .patientId(patientId)
                .heartRate(60 + (seed % 30))
                .bloodPressure((110 + (seed % 25)) + "/" + (70 + (seed % 15)))
                .spo2(96.0 + (seed % 3))
                .temperature(36.5 + ((seed % 5) * 0.1))
                .respiratoryRate(14 + (seed % 7))
                .source("seed")
                .deviceId("DEV-" + patientId)
                .build();
        return snapshotRepository.save(snap);
    }

    private void seedHistoryForPatient(String patientId, String type,
                                        Instant from, Instant until) {
        // Skip if data already exists
        if (timeSeriesRepository.countByPatientIdAndType(patientId, type) > 0) return;

        int seed = deterministicSeed(patientId);
        List<VitalsTimeSeries> entries = new ArrayList<>();

        // Determine base value and step count from period
        long totalMinutes = ChronoUnit.MINUTES.between(from, until);
        int steps = (int) Math.min(totalMinutes / 30, 48); // up to 48 data points
        if (steps < 2) steps = 9; // ensure at least a few points

        long stepMinutes = totalMinutes / steps;

        double baseValue = baseValueFor(type, seed);
        String unit = unitFor(type);

        for (int i = 0; i < steps; i++) {
            // Vary value deterministically based on step and seed — no randomness
            double variation = ((i * 7 + seed) % 11) - 5;
            double value = Math.max(0, baseValue + variation * variationScaleFor(type));

            Instant ts = from.plus(stepMinutes * i, ChronoUnit.MINUTES);

            entries.add(VitalsTimeSeries.builder()
                    .id(patientId + "-" + type + "-" + i)
                    .patientId(patientId)
                    .type(type)
                    .value(Math.round(value * 10.0) / 10.0)
                    .unit(unit)
                    .timestamp(ts)
                    .source("seed")
                    .deviceId("DEV-" + patientId)
                    .build());
        }
        timeSeriesRepository.saveAll(entries);
        log.debug("Seeded {} {} history points for patient {}", entries.size(), type, patientId);
    }

    private double baseValueFor(String type, int seed) {
        return switch (type) {
            case "heartRate"              -> 68 + (seed % 20);
            case "bloodPressureSystolic"  -> 120 + (seed % 20);
            case "bloodPressureDiastolic" -> 78 + (seed % 12);
            case "spo2"                   -> 97 + (seed % 2);
            case "temperature"            -> 36.6;
            case "respiratoryRate"        -> 15 + (seed % 5);
            default                       -> 0.0;
        };
    }

    private double variationScaleFor(String type) {
        return switch (type) {
            case "heartRate"              -> 0.8;
            case "bloodPressureSystolic"  -> 1.0;
            case "bloodPressureDiastolic" -> 0.6;
            case "spo2"                   -> 0.1;
            case "temperature"            -> 0.05;
            case "respiratoryRate"        -> 0.3;
            default                       -> 0.5;
        };
    }

    private String unitFor(String type) {
        return switch (type) {
            case "heartRate"                               -> "BPM";
            case "bloodPressureSystolic",
                 "bloodPressureDiastolic"                  -> "mmHg";
            case "spo2"                                    -> "%";
            case "temperature"                             -> "°C";
            case "respiratoryRate"                         -> "/min";
            default                                        -> "";
        };
    }

    private List<VitalsHistoryResponse.DataPoint> buildDataPoints(
            List<VitalsTimeSeries> raw, String period) {

        boolean is7d = "7d".equalsIgnoreCase(period);

        return raw.stream().map(ts -> {
            String label = is7d
                    ? formatDayLabel(ts.getTimestamp())
                    : formatTimeLabel(ts.getTimestamp());
            return VitalsHistoryResponse.DataPoint.from(ts, label);
        }).toList();
    }

    private String formatTimeLabel(Instant ts) {
        // "HH:mm" in UTC
        java.time.LocalTime t = java.time.LocalTime.ofInstant(ts, java.time.ZoneOffset.UTC);
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private String formatDayLabel(Instant ts) {
        // Day abbreviation: "Mon", "Tue", etc.
        java.time.LocalDate d = ts.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT,
                java.util.Locale.ENGLISH);
    }

    private void applyToSnapshot(VitalsSnapshot snap, String type, double value,
                                  String source, String deviceId) {
        snap.setSource(source);
        snap.setDeviceId(deviceId);
        switch (type) {
            case "heartRate" -> snap.setHeartRate((int) Math.round(value));
            case "bloodPressureSystolic" -> {
                int systolic = (int) Math.round(value);
                String current = snap.getBloodPressure();
                String diastolic = extractDiastolic(current);
                snap.setBloodPressure(systolic + "/" + diastolic);
            }
            case "bloodPressureDiastolic" -> {
                int diastolic = (int) Math.round(value);
                String current = snap.getBloodPressure();
                String systolic = extractSystolic(current);
                snap.setBloodPressure(systolic + "/" + diastolic);
            }
            case "spo2" -> snap.setSpo2(value);
            case "temperature" -> snap.setTemperature(value);
            case "respiratoryRate" -> snap.setRespiratoryRate((int) Math.round(value));
            default -> log.debug("Ignoring unsupported vital type '{}' for snapshot patient={}", type, snap.getPatientId());
        }
    }

    private String extractSystolic(String bloodPressure) {
        if (bloodPressure == null || !bloodPressure.contains("/")) return "—";
        String value = bloodPressure.substring(0, bloodPressure.indexOf('/')).trim();
        return value.isBlank() ? "—" : value;
    }

    private String extractDiastolic(String bloodPressure) {
        if (bloodPressure == null || !bloodPressure.contains("/")) return "—";
        String value = bloodPressure.substring(bloodPressure.indexOf('/') + 1).trim();
        return value.isBlank() ? "—" : value;
    }

    private WearableDevice buildDefaultDevice(String patientId) {
        return WearableDevice.builder()
                .id("DEV-" + patientId)
                .patientId(patientId)
                .displayName("No device registered")
                .deviceType("Unknown")
                .kafkaTopic("vitals.raw")
                .deviceKey("NONE")
                .status("Offline")
                .build();
    }

    private void validatePatientExists(String patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
    }

    private int deterministicSeed(String id) {
        if (id == null) return 0;
        int sum = 0;
        for (char c : id.toCharArray()) sum += c;
        return Math.abs(sum);
    }
}

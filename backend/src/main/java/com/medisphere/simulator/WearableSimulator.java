package com.medisphere.simulator;

import com.medisphere.config.SimulatorProperties;
import com.medisphere.domain.Patient;
import com.medisphere.domain.WearableDevice;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.VitalsRawEvent;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.WearableDeviceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Development-only wearable device simulator.
 *
 * Per tasks.md B5.7:
 *   - Active only in the 'dev' Spring profile (@Profile("dev"))
 *   - Publishes synthetic vitals.raw events to Kafka every 5 seconds
 *   - Only for patients with status=Active and wearable=Online
 *   - Values derived DETERMINISTICALLY from patient attributes + sinusoidal variation
 *   - No randomness — produces reproducible test scenarios
 *   - Controlled by medisphere.simulator.enabled=true (default)
 *
 * Determinism design:
 *   Each vital type has a patient-specific baseline derived from the patient ID hash.
 *   A sinusoidal offset is applied using the current epoch second to simulate
 *   natural variation. This makes the output predictable and testable.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class WearableSimulator {

    private final SimulatorProperties props;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final PatientRepository patientRepository;
    private final WearableDeviceRepository wearableDeviceRepository;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @PostConstruct
    public void start() {
        if (!props.isEnabled()) {
            log.info("WearableSimulator disabled (medisphere.simulator.enabled=false)");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wearable-simulator");
            t.setDaemon(true);
            return t;
        });

        int interval = Math.max(1, props.getIntervalSeconds());
        task = scheduler.scheduleAtFixedRate(
                this::publishCycle,
                interval,       // initial delay — wait one cycle before first publish
                interval,
                TimeUnit.SECONDS
        );

        log.info("WearableSimulator started — interval={}s (dev profile only)", interval);
    }

    @PreDestroy
    public void stop() {
        if (task != null) task.cancel(false);
        if (scheduler != null) {
            scheduler.shutdown();
            try { scheduler.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        log.info("WearableSimulator stopped");
    }

    // ── Simulation cycle ─────────────────────────────────────────────────

    /**
     * One simulation cycle: publishes one vital reading per active/online patient.
     * Cycles through vital types to spread load: each cycle publishes a different type.
     */
    private void publishCycle() {
        try {
            List<Patient> active = patientRepository.findByStatus("Active");
            if (active.isEmpty()) {
                log.debug("WearableSimulator: no active patients — skipping cycle");
                return;
            }

            long epochSecond = Instant.now().getEpochSecond();
            // Rotate through 6 vital types one per cycle
            String[] types = {"heartRate", "spo2", "bloodPressureSystolic",
                               "bloodPressureDiastolic", "temperature", "respiratoryRate"};
            String vitalType = types[(int) (epochSecond / props.getIntervalSeconds() % types.length)];

            for (Patient patient : active) {
                publishVitalForPatient(patient, vitalType, epochSecond);
            }
        } catch (Exception e) {
            log.warn("WearableSimulator cycle error: {}", e.getMessage(), e);
        }
    }

    private void publishVitalForPatient(Patient patient, String type, long epochSecond) {
        // Only simulate if the patient has an online wearable
        WearableDevice device = wearableDeviceRepository.findByPatientId(patient.getId())
                .orElse(null);
        if (device == null || !"Online".equals(device.getStatus())) return;

        double value = simulatedValue(patient.getId(), type, epochSecond);
        String unit  = unitFor(type);

        // Update the device last-seen timestamp
        device.setLastSeen(Instant.now());
        wearableDeviceRepository.save(device);

        VitalsRawEvent event = VitalsRawEvent.builder()
                .patientId(patient.getId())
                .deviceId(device.getId())
                .timestamp(Instant.now())
                .type(type)
                .value(value)
                .unit(unit)
                .build();

        kafkaEventPublisher.publishVitalsRaw(event);
        log.debug("Simulated vital — patient={} type={} value={}", patient.getId(), type, value);
    }

    // ── Deterministic value generation ───────────────────────────────────

    /**
     * Generates a deterministic vital value.
     *
     * Formula: baseline + amplitude * sin(2π * epochSecond / period)
     *
     * The baseline is derived from a hash of the patient ID so each patient
     * has a slightly different "normal" range. The sinusoidal offset ensures
     * values vary realistically without randomness.
     */
    static double simulatedValue(String patientId, String type, long epochSecond) {
        // Patient-specific hash offset in range [0, 1]
        int hash = Math.abs(patientId.hashCode()) % 1000;
        double patientFactor = hash / 1000.0;  // 0.0 – 0.999

        return switch (type) {
            case "heartRate" -> {
                // Baseline: 65–85 BPM; amplitude ±8 with a 60-second sinusoidal cycle
                double base = 65 + patientFactor * 20;
                double offset = 8 * Math.sin(2 * Math.PI * epochSecond / 60.0);
                yield Math.round((base + offset) * 10.0) / 10.0;
            }
            case "spo2" -> {
                // Baseline: 96–99%; amplitude ±1 with a 120-second cycle
                double base = 96 + patientFactor * 3;
                double offset = Math.sin(2 * Math.PI * epochSecond / 120.0);
                yield Math.round((base + offset) * 10.0) / 10.0;
            }
            case "bloodPressureSystolic" -> {
                // Baseline: 110–135 mmHg; amplitude ±5 with a 90-second cycle
                double base = 110 + patientFactor * 25;
                double offset = 5 * Math.sin(2 * Math.PI * epochSecond / 90.0);
                yield Math.round(base + offset);
            }
            case "bloodPressureDiastolic" -> {
                // Baseline: 70–85 mmHg; amplitude ±4
                double base = 70 + patientFactor * 15;
                double offset = 4 * Math.sin(2 * Math.PI * epochSecond / 90.0);
                yield Math.round(base + offset);
            }
            case "temperature" -> {
                // Baseline: 36.3–37.1 °C; amplitude ±0.2 with a 180-second cycle
                double base = 36.3 + patientFactor * 0.8;
                double offset = 0.2 * Math.sin(2 * Math.PI * epochSecond / 180.0);
                yield Math.round((base + offset) * 10.0) / 10.0;
            }
            case "respiratoryRate" -> {
                // Baseline: 14–18 /min; amplitude ±1
                double base = 14 + patientFactor * 4;
                double offset = Math.sin(2 * Math.PI * epochSecond / 60.0);
                yield Math.round(base + offset);
            }
            default -> 0.0;
        };
    }

    private static String unitFor(String type) {
        return switch (type) {
            case "heartRate"                          -> "bpm";
            case "spo2"                               -> "%";
            case "bloodPressureSystolic",
                 "bloodPressureDiastolic"             -> "mmHg";
            case "temperature"                        -> "°C";
            case "respiratoryRate"                    -> "/min";
            default                                   -> "";
        };
    }
}

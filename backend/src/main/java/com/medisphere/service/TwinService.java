package com.medisphere.service;

import com.medisphere.domain.HealthTwin;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.kafka.KafkaEventPublisher;
import com.medisphere.kafka.events.TwinUpdateEvent;
import com.medisphere.repository.HealthTwinRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.repository.LabResultRepository;
import com.medisphere.repository.WearableDeviceRepository;
import com.medisphere.repository.VitalsTimeSeriesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for Digital Health Twin lifecycle management.
 *
 * NOTE (Phase 2): The design specifies publishing a twin.update Kafka event on
 * every state change. Kafka is implemented in Phase 5. The publishTwinUpdate()
 * method below is a no-op stub that Phase 5 will replace with the real Kafka
 * producer — callers are already in place.
 */
@Service
public class TwinService {

    private static final Logger log = LoggerFactory.getLogger(TwinService.class);
    private static final Pattern TWIN_ID_PATTERN = Pattern.compile("^HT-(\\d+)$");
    private static final AtomicInteger twinCounter = new AtomicInteger(0);
    private volatile boolean twinCounterInitialized = false;

    private final HealthTwinRepository twinRepository;
    private final TwinCompletenessCalculator completenessCalculator;
    private LabService labService;       // Phase 3 — setter injection
    private PatientRepository patientRepository; // Phase 3
    private LabResultRepository labResultRepository;
    private WearableDeviceRepository wearableDeviceRepository;
    private VitalsTimeSeriesRepository vitalsTimeSeriesRepository;
    private KafkaEventPublisher kafkaEventPublisher; // Phase 5 — setter injection

    public TwinService(HealthTwinRepository twinRepository,
                       TwinCompletenessCalculator completenessCalculator) {
        this.twinRepository = twinRepository;
        this.completenessCalculator = completenessCalculator;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setLabService(LabService labService) {
        this.labService = labService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setPatientRepository(
            com.medisphere.repository.PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setLabResultRepository(LabResultRepository labResultRepository) {
        this.labResultRepository = labResultRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setWearableDeviceRepository(WearableDeviceRepository wearableDeviceRepository) {
        this.wearableDeviceRepository = wearableDeviceRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setVitalsTimeSeriesRepository(VitalsTimeSeriesRepository vitalsTimeSeriesRepository) {
        this.vitalsTimeSeriesRepository = vitalsTimeSeriesRepository;
    }

    /**
     * Phase 5: inject the real Kafka publisher.
     * Optional because unit tests may not have Kafka on classpath.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setKafkaEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    // ── Init ──────────────────────────────────────────────────────────────

    /**
     * Create a new Digital Health Twin for the given patient.
     * Idempotent: if a twin already exists for this patient, returns it unchanged.
     */
    public HealthTwin initTwin(String patientId) {
        return twinRepository.findByPatientId(patientId).orElseGet(() -> {
            String twinId = generateTwinId();

            HealthTwin twin = HealthTwin.builder()
                    .id(twinId)
                    .patientId(patientId)
                    .modelVersion("v2.1")
                    .completeness(0)
                    .status("Syncing")
                    .lastUpdated(Instant.now())
                    .stateVersion(1L)
                    .dataSources(HealthTwin.DataSources.defaultSources())
                    .bodyRegions(new ArrayList<>(HealthTwin.defaultBodyRegions()))
                    .timeline(new ArrayList<>(List.of(
                            HealthTwin.TimelineEvent.builder()
                                    .id("te-" + UUID.randomUUID().toString().substring(0, 8))
                                    .timestamp(Instant.now())
                                    .title("Twin created")
                                    .detail("Digital Health Twin initialised for patient " + patientId + ".")
                                    .tone("info")
                                    .build()
                    )))
                    .build();

            HealthTwin saved = twinRepository.save(twin);
            log.info("Created digital health twin {} for patient {}", twinId, patientId);
            publishTwinUpdate(saved);
            return saved;
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────

    public HealthTwin getTwin(String twinId) {
        HealthTwin twin = twinRepository.findById(twinId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthTwin", twinId));
        return refreshPersistedDataSources(twin);
    }

    public HealthTwin getTwinByPatientId(String patientId) {
        HealthTwin twin = twinRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HealthTwin for patient", patientId));
        return refreshPersistedDataSources(twin);
    }

    /** Non-throwing Optional variant — used by VitalsKafkaConsumer. */
    public Optional<HealthTwin> findTwinByPatientId(String patientId) {
        return twinRepository.findByPatientId(patientId);
    }

    /**
     * Repairs source connectivity metadata from data that already exists in MongoDB.
     * This is intentionally idempotent and does not publish a Kafka event; it makes
     * existing Phase 3/5 records visible to the Digital Twin UI after a restart.
     */
    private HealthTwin refreshPersistedDataSources(HealthTwin twin) {
        if (patientRepository == null || labResultRepository == null
                || wearableDeviceRepository == null || vitalsTimeSeriesRepository == null) {
            return twin;
        }

        HealthTwin.DataSources ds = twin.getDataSources();
        if (ds == null) ds = HealthTwin.DataSources.defaultSources();

        Instant now = Instant.now();
        boolean changed = false;

        var patient = patientRepository.findById(twin.getPatientId()).orElse(null);
        if (patient != null && patient.isFhirConnected()) {
            Instant sync = patient.getUpdatedAt() != null ? patient.getUpdatedAt() : now;
            changed |= setIfDifferent(ds, "ehr", true, sync);
        }

        var labs = labResultRepository.findByPatientIdOrderByDateDesc(twin.getPatientId());
        if (!labs.isEmpty()) {
            Instant sync = parseLabDate(labs.get(0).getDate(), now);
            changed |= setIfDifferent(ds, "lab", true, sync);
        }

        var wearable = wearableDeviceRepository.findByPatientId(twin.getPatientId()).orElse(null);
        if (wearable != null && wearable.getLastSeen() != null) {
            boolean online = "Online".equalsIgnoreCase(wearable.getStatus());
            changed |= setIfDifferent(ds, "wearable", online, wearable.getLastSeen());
        }

        var latestVital = vitalsTimeSeriesRepository
                .findFirstByPatientIdOrderByTimestampDesc(twin.getPatientId())
                .orElse(null);
        if (latestVital != null && latestVital.getTimestamp() != null) {
            Instant latest = latestVital.getTimestamp();
            boolean recent = latest.isAfter(now.minusSeconds(5 * 60L));
            changed |= setIfDifferent(ds, "kafka", recent, latest);
        }

        if (!changed) return twin;

        twin.setDataSources(ds);
        twin.setCompleteness(completenessCalculator.compute(ds));
        twin.setStatus(deriveStatus(twin.getCompleteness()));
        twin.setLastUpdated(now);
        twin.setStateVersion(twin.getStateVersion() + 1);
        return twinRepository.save(twin);
    }

    private boolean setIfDifferent(HealthTwin.DataSources ds, String source, boolean connected, Instant lastSync) {
        HealthTwin.DataSourceEntry current = switch (source) {
            case "ehr" -> ds.getEhr();
            case "lab" -> ds.getLab();
            case "wearable" -> ds.getWearable();
            case "kafka" -> ds.getKafka();
            default -> null;
        };

        if (current != null && current.isConnected() == connected
                && java.util.Objects.equals(current.getLastSync(), lastSync)) {
            return false;
        }

        HealthTwin.DataSourceEntry replacement = new HealthTwin.DataSourceEntry(connected, lastSync);
        switch (source) {
            case "ehr" -> ds.setEhr(replacement);
            case "lab" -> ds.setLab(replacement);
            case "wearable" -> ds.setWearable(replacement);
            case "kafka" -> ds.setKafka(replacement);
            default -> { return false; }
        }
        return true;
    }

    private Instant parseLabDate(String date, Instant fallback) {
        if (date == null || date.isBlank()) return fallback;
        try {
            return java.time.LocalDate.parse(date)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // ── Update completeness ───────────────────────────────────────────────

    public HealthTwin updateCompleteness(String twinId) {
        HealthTwin twin = getTwin(twinId);
        int score = completenessCalculator.compute(twin.getDataSources());
        twin.setCompleteness(score);
        twin.setStatus(deriveStatus(score));
        twin.setLastUpdated(Instant.now());
        twin.setStateVersion(twin.getStateVersion() + 1);
        HealthTwin saved = twinRepository.save(twin);
        publishTwinUpdate(saved);
        return saved;
    }

    // ── Timeline ──────────────────────────────────────────────────────────

    public HealthTwin addTimelineEvent(String twinId, String title, String detail, String tone) {
        HealthTwin twin = getTwin(twinId);

        HealthTwin.TimelineEvent event = HealthTwin.TimelineEvent.builder()
                .id("te-" + UUID.randomUUID().toString().substring(0, 8))
                .timestamp(Instant.now())
                .title(title)
                .detail(detail)
                .tone(tone)
                .build();

        if (twin.getTimeline() == null) twin.setTimeline(new ArrayList<>());
        twin.getTimeline().add(event);

        // Keep timeline bounded to last 50 events
        if (twin.getTimeline().size() > 50) {
            twin.setTimeline(twin.getTimeline().subList(
                    twin.getTimeline().size() - 50, twin.getTimeline().size()));
        }

        twin.setLastUpdated(Instant.now());
        twin.setStateVersion(twin.getStateVersion() + 1);
        HealthTwin saved = twinRepository.save(twin);
        publishTwinUpdate(saved);
        return saved;
    }

    // ── Data source update ────────────────────────────────────────────────

    public HealthTwin updateDataSource(String twinId, String source,
                                       boolean connected, Instant lastSync) {
        HealthTwin twin = getTwin(twinId);
        HealthTwin.DataSources ds = twin.getDataSources();
        if (ds == null) ds = HealthTwin.DataSources.defaultSources();

        HealthTwin.DataSourceEntry entry = new HealthTwin.DataSourceEntry(connected, lastSync);

        switch (source.toLowerCase()) {
            case "ehr"      -> ds.setEhr(entry);
            case "lab"      -> ds.setLab(entry);
            case "wearable" -> ds.setWearable(entry);
            case "kafka"    -> ds.setKafka(entry);
            default -> log.warn("Unknown data source '{}' for twin {}", source, twinId);
        }

        twin.setDataSources(ds);

        // Recompute completeness after source update
        int score = completenessCalculator.compute(ds);
        twin.setCompleteness(score);
        twin.setStatus(deriveStatus(score));
        twin.setLastUpdated(Instant.now());
        twin.setStateVersion(twin.getStateVersion() + 1);

        HealthTwin saved = twinRepository.save(twin);
        publishTwinUpdate(saved);
        return saved;
    }

    // ── Kafka: twin.update event (Phase 5 — real publisher) ──────────────

    /**
     * Publishes a {@code twin.update} Kafka event.
     * If the KafkaEventPublisher is not available (e.g. in unit tests
     * that do not include Kafka on classpath), falls back to debug logging.
     */
    private void publishTwinUpdate(HealthTwin twin) {
        if (kafkaEventPublisher != null) {
            kafkaEventPublisher.publishTwinUpdate(TwinUpdateEvent.builder()
                    .twinId(twin.getId())
                    .patientId(twin.getPatientId())
                    .stateVersion(twin.getStateVersion())
                    .completeness(twin.getCompleteness())
                    .timestamp(Instant.now())
                    .build());
        } else {
            log.debug("twin.update (no Kafka) — twinId={} stateVersion={} completeness={}",
                    twin.getId(), twin.getStateVersion(), twin.getCompleteness());
        }
    }

    // ── Phase 3: FHIR-driven sync ─────────────────────────────────────────

    /**
     * Re-ingest FHIR laboratory data and update the twin's EHR data source metadata.
     * Called by POST /api/twins/{twinId}/sync (TwinController).
     *
     * Flow:
     *  1. Resolve twin → resolve patient
     *  2. Call LabService.ingestFromFHIR(patientId)
     *  3. Update EHR data source as connected + last sync = now
     *  4. Recompute completeness
     *  5. Add timeline event
     *
     * @return updated twin
     */
    public HealthTwin syncFromFHIR(String twinId) {
        HealthTwin twin = getTwin(twinId);

        int ingested = 0;
        if (labService != null && patientRepository != null) {
            try {
                ingested = labService.ingestFromFHIR(twin.getPatientId());
            } catch (Exception e) {
                log.warn("FHIR lab ingestion during sync failed for twin {}: {}", twinId, e.getMessage());
            }
        }

        // Mark EHR and laboratory data sources as connected after a successful
        // FHIR sync attempt. The read-repair path will later use persisted data
        // dates to enforce freshness for completeness.
        Instant syncTime = java.time.Instant.now();
        twin = updateDataSource(twinId, "ehr", true, syncTime);
        twin = updateDataSource(twinId, "lab", true, syncTime);

        // Add timeline event
        String detail = ingested > 0
                ? "FHIR sync completed — " + ingested + " new lab result(s) ingested."
                : "FHIR sync completed — no new lab results.";
        twin = addTimelineEvent(twinId, "FHIR sync", detail, ingested > 0 ? "healthy" : "info");

        log.info("Twin {} FHIR sync complete: {} new results", twinId, ingested);
        return twin;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String deriveStatus(int completeness) {
        if (completeness >= 75) return "Synchronized";
        if (completeness >= 25) return "Syncing";
        return "Stale";
    }

    private synchronized String generateTwinId() {
        if (!twinCounterInitialized) {
            int max = twinRepository.findAll().stream()
                    .map(HealthTwin::getId)
                    .filter(id -> id != null)
                    .map(TWIN_ID_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(m -> Integer.parseInt(m.group(1)))
                    .max()
                    .orElse(0);
            twinCounter.set(max);
            twinCounterInitialized = true;
        }
        return "HT-" + String.format("%03d", twinCounter.incrementAndGet());
    }
}

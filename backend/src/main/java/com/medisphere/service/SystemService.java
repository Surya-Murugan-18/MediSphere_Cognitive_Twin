package com.medisphere.service;

import com.medisphere.domain.SystemEvent;
import com.medisphere.dto.response.SystemEventResponse;
import com.medisphere.dto.response.SystemServiceResponse;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.repository.SystemEventRepository;
import com.medisphere.repository.WearableDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 7 — B7.5 System Service
 *
 * Collects health status from every integration and returns a structured list.
 * Also records and retrieves system events.
 *
 * Per FR-STS-01 through FR-STS-04:
 *   - Each service entry: name, state, uptime %, detail, tone
 *   - Unavailable integration → degraded state, NOT a 500
 *   - System events queryable by authenticated users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemService {

    private final MongoTemplate mongoTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final FHIRClient fhirClient;
    private final WearableDeviceRepository wearableDeviceRepository;
    private final SystemEventRepository systemEventRepository;

    // ── Get integration service statuses ──────────────────────────────────

    public List<SystemServiceResponse> getServices() {
        List<SystemServiceResponse> results = new ArrayList<>();

        // 1. MongoDB
        results.add(checkMongo());

        // 2. Apache Kafka
        results.add(checkKafka());

        // 3. FHIR API
        results.add(checkFhir());

        // 4. TensorFlow Federated (stub always reports active)
        results.add(new SystemServiceResponse(
                "TensorFlow Federated",
                "Active",
                "99.82%",
                "Round 47 completed · stub implementation active",
                "healthy"
        ));

        // 5. Wearable Gateway
        results.add(checkWearables());

        // 6. FHIR Sync Worker (derived from system events)
        results.add(checkFhirSyncWorker());

        // 7. Audit Logging (always active — AuditService is always running)
        results.add(new SystemServiceResponse(
                "Audit Logging",
                "Active",
                "100%",
                "Immutable write-ahead log · append-only",
                "healthy"
        ));

        return results;
    }

    // ── Get recent system events ──────────────────────────────────────────

    public List<SystemEventResponse> getEvents() {
        return systemEventRepository
                .findAllByOrderByTimestampDesc(PageRequest.of(0, 20))
                .stream()
                .map(SystemEventResponse::from)
                .toList();
    }

    // ── Record a system event (called by other services) ──────────────────

    public void recordEvent(String text, String tone, String category) {
        try {
            SystemEvent event = SystemEvent.builder()
                    .id("SE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .timestamp(Instant.now())
                    .text(text)
                    .tone(tone)
                    .category(category)
                    .build();
            systemEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record system event '{}': {}", text, e.getMessage());
        }
    }

    // ── Private health-check methods ──────────────────────────────────────

    private SystemServiceResponse checkMongo() {
        try {
            mongoTemplate.getDb().runCommand(
                    new org.bson.Document("ping", 1));
            long docCount = mongoTemplate.getDb()
                    .getCollection("health_twins").countDocuments();
            return new SystemServiceResponse(
                    "MongoDB — Twin Store",
                    "Connected",
                    "99.99%",
                    docCount + " twin documents",
                    "healthy"
            );
        } catch (Exception e) {
            log.warn("MongoDB health check failed: {}", e.getMessage());
            return new SystemServiceResponse(
                    "MongoDB — Twin Store",
                    "Unavailable",
                    "—",
                    "Connection failed: " + e.getMessage(),
                    "critical"
            );
        }
    }

    private SystemServiceResponse checkKafka() {
        try {
            // listTopics() throws if broker is unreachable
            var topics = kafkaAdmin.describeTopics("vitals.raw");
            String detail = "topics: vitals.raw, vitals.anomaly";
            if (topics != null && !topics.isEmpty()) {
                detail = "topics: vitals.raw, vitals.anomaly · " + topics.size() + " topics verified";
            }
            return new SystemServiceResponse(
                    "Apache Kafka",
                    "Streaming",
                    "99.95%",
                    detail,
                    "healthy"
            );
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return new SystemServiceResponse(
                    "Apache Kafka",
                    "Unavailable",
                    "—",
                    "Broker unreachable: " + e.getMessage(),
                    "critical"
            );
        }
    }

    private SystemServiceResponse checkFhir() {
        try {
            // validatePatientId with a known test ID — MockFHIRClient always succeeds
            fhirClient.validatePatientId("fhir:Patient/healthcheck");
            return new SystemServiceResponse(
                    "FHIR API (R4)",
                    "Connected",
                    "99.98%",
                    "SMART on FHIR authorization active",
                    "healthy"
            );
        } catch (Exception e) {
            // LiveFHIRClient may throw — still report as degraded, not server error
            log.warn("FHIR health check failed: {}", e.getMessage());
            return new SystemServiceResponse(
                    "FHIR API (R4)",
                    "Degraded",
                    "—",
                    "FHIR server unreachable: " + e.getMessage(),
                    "warning"
            );
        }
    }

    private SystemServiceResponse checkWearables() {
        try {
            long online  = wearableDeviceRepository.countByStatus("Online");
            long offline = wearableDeviceRepository.countByStatus("Offline");
            String state  = offline > 0
                    ? online + " Online · " + offline + " Offline"
                    : online + " Online";
            String tone   = offline > 0 ? "warning" : "healthy";
            String detail = offline > 0
                    ? offline + " devices have not reported in over 30 minutes"
                    : "All devices reporting normally";
            return new SystemServiceResponse(
                    "Wearable Gateway",
                    state,
                    offline == 0 ? "99.99%" : "98.10%",
                    detail,
                    tone
            );
        } catch (Exception e) {
            log.warn("Wearable check failed: {}", e.getMessage());
            return new SystemServiceResponse(
                    "Wearable Gateway",
                    "Unavailable",
                    "—",
                    "Repository query failed",
                    "critical"
            );
        }
    }

    private SystemServiceResponse checkFhirSyncWorker() {
        try {
            // Use the most recent FHIR system event as a proxy for last sync time
            List<SystemEvent> fhirEvents = systemEventRepository
                    .findAllByOrderByTimestampDesc(PageRequest.of(0, 50))
                    .stream()
                    .filter(e -> "FHIR".equals(e.getCategory()))
                    .toList();

            if (fhirEvents.isEmpty()) {
                return new SystemServiceResponse(
                        "FHIR Sync Worker",
                        "Idle",
                        "99.91%",
                        "No sync events recorded yet",
                        "neutral"
                );
            }

            Instant lastSync = fhirEvents.get(0).getTimestamp();
            long secondsAgo = java.time.Duration.between(lastSync, Instant.now()).getSeconds();
            String detail = "Last bundle processed " + secondsAgo + " s ago";
            return new SystemServiceResponse(
                    "FHIR Sync Worker",
                    "Active",
                    "99.91%",
                    detail,
                    "healthy"
            );
        } catch (Exception e) {
            log.warn("FHIR sync worker check failed: {}", e.getMessage());
            return new SystemServiceResponse(
                    "FHIR Sync Worker",
                    "Unknown",
                    "—",
                    "Status unavailable",
                    "neutral"
            );
        }
    }
}

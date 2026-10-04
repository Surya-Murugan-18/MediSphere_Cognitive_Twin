package com.medisphere.controller;

import com.medisphere.dto.response.KafkaStatsResponse;
import com.medisphere.dto.response.KafkaStatsResponse.KafkaEventEntry;
import com.medisphere.dto.response.MonitoringStatsResponse;
import com.medisphere.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for monitoring statistics.
 *
 * Per design.md §10 and tasks.md B5.6:
 *   GET /api/monitoring/stats
 *   GET /api/monitoring/kafka-stats
 *   GET /api/monitoring/kafka-events?limit=
 */
@RestController
@RequestMapping("/api/monitoring")
@Tag(name = "Monitoring", description = "Real-time monitoring statistics and Kafka event log")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Monitoring KPIs", description = "alertsToday, wearablesOnline, avgResponseMin, streamStatus")
    public ResponseEntity<MonitoringStatsResponse> getStats() {
        return ResponseEntity.ok(monitoringService.getStats());
    }

    @GetMapping("/kafka-stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kafka stream statistics")
    public ResponseEntity<KafkaStatsResponse> getKafkaStats() {
        return ResponseEntity.ok(monitoringService.getKafkaStats());
    }

    @GetMapping("/kafka-events")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Latest Kafka events", description = "Rolling tail of vitals.raw and vitals.anomaly")
    public ResponseEntity<List<KafkaEventEntry>> getKafkaEvents(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(monitoringService.getKafkaEvents(limit));
    }
}

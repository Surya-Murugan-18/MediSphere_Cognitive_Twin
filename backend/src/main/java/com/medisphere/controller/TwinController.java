package com.medisphere.controller;

import com.medisphere.audit.Auditable;
import com.medisphere.domain.HealthTwin;
import com.medisphere.dto.response.TwinResponse;
import com.medisphere.service.TwinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/twins")
@Tag(name = "Digital Health Twins", description = "Digital Health Twin access and synchronisation")
public class TwinController {

    private final TwinService twinService;

    public TwinController(TwinService twinService) {
        this.twinService = twinService;
    }

    // ── GET /api/twins/{twinId} ───────────────────────────────────────────

    @GetMapping("/{twinId}")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "Viewed Digital Twin", module = "Health Twins", patientParam = "#twinId")
    @Operation(summary = "Get digital health twin", description = "Full twin document including body regions and timeline")
    public ResponseEntity<TwinResponse> getTwin(@PathVariable String twinId) {
        HealthTwin twin = twinService.getTwin(twinId);
        return ResponseEntity.ok(TwinResponse.from(twin));
    }

    // ── GET /api/twins/{twinId}/body-regions ─────────────────────────────

    @GetMapping("/{twinId}/body-regions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get twin body regions", description = "Returns the 5 organ-system risk regions for the body heatmap")
    public ResponseEntity<List<TwinResponse.BodyRegionResponse>> getBodyRegions(
            @PathVariable String twinId) {
        HealthTwin twin = twinService.getTwin(twinId);
        List<TwinResponse.BodyRegionResponse> regions = twin.getBodyRegions() == null
                ? List.of()
                : twin.getBodyRegions().stream()
                        .map(TwinResponse.BodyRegionResponse::from)
                        .toList();
        return ResponseEntity.ok(regions);
    }

    // ── GET /api/twins/{twinId}/data-sources ─────────────────────────────

    @GetMapping("/{twinId}/data-sources")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get twin data sources", description = "Returns connectivity and freshness for EHR, lab, wearable and Kafka")
    public ResponseEntity<TwinResponse.DataSourcesResponse> getDataSources(
            @PathVariable String twinId) {
        HealthTwin twin = twinService.getTwin(twinId);
        return ResponseEntity.ok(TwinResponse.DataSourcesResponse.from(twin.getDataSources()));
    }

    // ── GET /api/twins/{twinId}/timeline ─────────────────────────────────

    @GetMapping("/{twinId}/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get twin timeline", description = "Returns chronological state-change events")
    public ResponseEntity<List<TwinResponse.TimelineEventResponse>> getTimeline(
            @PathVariable String twinId) {
        HealthTwin twin = twinService.getTwin(twinId);
        List<TwinResponse.TimelineEventResponse> timeline = twin.getTimeline() == null
                ? List.of()
                : twin.getTimeline().stream()
                        .map(TwinResponse.TimelineEventResponse::from)
                        .toList();
        return ResponseEntity.ok(timeline);
    }

    // ── POST /api/twins/{twinId}/sync ─────────────────────────────────────

    @PostMapping("/{twinId}/sync")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Auditable(action = "Triggered Digital Twin Sync", module = "Health Twins", patientParam = "#twinId")
    @Operation(summary = "Trigger twin sync", description = "Re-ingests FHIR lab data and updates twin completeness + timeline")
    public ResponseEntity<TwinResponse> syncTwin(
            @PathVariable String twinId,
            Authentication auth) {
        // Phase 3: full FHIR re-ingestion + completeness + timeline event
        HealthTwin twin = twinService.syncFromFHIR(twinId);
        return ResponseEntity.ok(TwinResponse.from(twin));
    }
}

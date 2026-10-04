package com.medisphere.controller;

import com.medisphere.dto.response.SystemEventResponse;
import com.medisphere.dto.response.SystemServiceResponse;
import com.medisphere.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 7 — B7.5 System Controller
 *
 * Per design.md §10 and tasks.md B7.5:
 *
 *   GET /api/system/services   — any authenticated user (FR-STS-04)
 *   GET /api/system/events     — any authenticated user
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System Status", description = "Integration health and platform event log")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/services")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get integration service statuses",
               description = "Returns health of MongoDB, Kafka, FHIR, AI, Wearables, Sync Worker, and Audit Logging")
    public ResponseEntity<List<SystemServiceResponse>> getServices() {
        return ResponseEntity.ok(systemService.getServices());
    }

    @GetMapping("/events")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get recent system events",
               description = "Returns the 20 most recent platform-level system events")
    public ResponseEntity<List<SystemEventResponse>> getEvents() {
        return ResponseEntity.ok(systemService.getEvents());
    }
}

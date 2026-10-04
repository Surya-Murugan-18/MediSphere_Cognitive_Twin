package com.medisphere.controller;

import com.medisphere.dto.response.VitalsHistoryResponse;
import com.medisphere.dto.response.VitalsSnapshotResponse;
import com.medisphere.dto.response.WearableDeviceResponse;
import com.medisphere.service.VitalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Vitals", description = "Patient vital signs — current snapshot and history")
public class VitalsController {

    private final VitalsService vitalsService;

    public VitalsController(VitalsService vitalsService) {
        this.vitalsService = vitalsService;
    }

    /**
     * GET /api/patients/{id}/vitals/current
     * Returns the current vital sign snapshot for a patient.
     */
    @GetMapping("/api/patients/{id}/vitals/current")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Current vitals", description = "Latest vital sign readings for a patient")
    public ResponseEntity<VitalsSnapshotResponse> getCurrentVitals(@PathVariable String id) {
        return ResponseEntity.ok(vitalsService.getCurrentVitals(id));
    }

    /**
     * GET /api/patients/{id}/vitals/history?type=heartRate&period=24h
     * Supported periods: 24h (default) | 7d | 30d
     * Supported types: heartRate | bloodPressureSystolic | bloodPressureDiastolic | spo2 | temperature | respiratoryRate
     */
    @GetMapping("/api/patients/{id}/vitals/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Vitals history", description = "Time-series vital history for a given type and period")
    public ResponseEntity<VitalsHistoryResponse> getVitalsHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "heartRate") String type,
            @RequestParam(defaultValue = "24h")       String period) {
        return ResponseEntity.ok(vitalsService.getVitalsHistory(id, type, period));
    }

    /**
     * GET /api/devices/by-patient/{patientId}
     * Returns the wearable device registered for a patient.
     */
    @GetMapping("/api/devices/by-patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Wearable device", description = "Wearable device information for a patient")
    public ResponseEntity<WearableDeviceResponse> getWearableDevice(
            @PathVariable String patientId) {
        return ResponseEntity.ok(vitalsService.getWearableDevice(patientId));
    }
}

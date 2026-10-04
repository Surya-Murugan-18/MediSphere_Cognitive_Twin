package com.medisphere.controller;

import com.medisphere.dto.request.FhirConfigRequest;
import com.medisphere.dto.request.UpdateNotificationPrefsRequest;
import com.medisphere.dto.request.UpdateProviderRequest;
import com.medisphere.dto.response.FhirConfigResponse;
import com.medisphere.dto.response.NotificationPrefsResponse;
import com.medisphere.dto.response.ProviderResponse;
import com.medisphere.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 7 — B7.6 Settings Controller
 *
 * Per design.md §10 and tasks.md B7.6:
 *
 *   GET  /api/providers/me                  — own profile (any authenticated)
 *   PUT  /api/providers/me                  — update own profile (any authenticated)
 *   GET  /api/providers/me/notifications    — own notification prefs
 *   PUT  /api/providers/me/notifications    — update own notification prefs
 *   GET  /api/settings/fhir                 — FHIR config (ADMIN only)
 *   PUT  /api/settings/fhir                 — update FHIR config (ADMIN only)
 *
 * SECURITY:
 *   - Provider ID always derived from JWT principal (authenticated caller)
 *   - ADMIN may not update other providers via this controller (self-only pattern)
 *   - FHIR config responses never include credentials
 */
@RestController
@Tag(name = "Settings", description = "Provider profile, notification preferences and FHIR configuration")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // ── Provider profile ──────────────────────────────────────────────────

    @GetMapping("/api/providers/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get own provider profile")
    public ResponseEntity<ProviderResponse> getProviderProfile(Authentication auth) {
        return ResponseEntity.ok(settingsService.getProviderProfile(providerId(auth)));
    }

    @PutMapping("/api/providers/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update own provider profile",
               description = "Editable fields: name, specialty, facility. Provider ID is read-only (FR-SET-02).")
    public ResponseEntity<ProviderResponse> updateProviderProfile(
            @Valid @RequestBody UpdateProviderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                settingsService.updateProviderProfile(providerId(auth), request));
    }

    // ── Notification preferences ──────────────────────────────────────────

    @GetMapping("/api/providers/me/notifications")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get notification preferences")
    public ResponseEntity<NotificationPrefsResponse> getNotificationPrefs(Authentication auth) {
        return ResponseEntity.ok(settingsService.getNotificationPrefs(providerId(auth)));
    }

    @PutMapping("/api/providers/me/notifications")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update notification preferences",
               description = "Persists immediately — no separate save button needed. Audited.")
    public ResponseEntity<NotificationPrefsResponse> updateNotificationPrefs(
            @RequestBody UpdateNotificationPrefsRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                settingsService.updateNotificationPrefs(providerId(auth), request));
    }

    // ── FHIR configuration (ADMIN only) ───────────────────────────────────

    @GetMapping("/api/settings/fhir")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get FHIR configuration (ADMIN)",
               description = "Returns current FHIR config. Credentials are intentionally omitted.")
    public ResponseEntity<FhirConfigResponse> getFhirConfig() {
        return ResponseEntity.ok(settingsService.getFhirConfig());
    }

    @PutMapping("/api/settings/fhir")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update FHIR configuration (ADMIN)",
               description = "Persists FHIR config to MongoDB. Credentials cannot be updated via this endpoint.")
    public ResponseEntity<FhirConfigResponse> updateFhirConfig(
            @RequestBody FhirConfigRequest request,
            Authentication auth) {
        String adminId   = providerId(auth);
        String adminName = auth.getDetails() instanceof String s ? s : adminId;
        return ResponseEntity.ok(settingsService.updateFhirConfig(request, adminId, adminName));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String providerId(Authentication auth) {
        return (String) auth.getPrincipal();
    }
}

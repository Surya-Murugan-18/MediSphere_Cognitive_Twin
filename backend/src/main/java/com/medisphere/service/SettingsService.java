package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.FhirConfiguration;
import com.medisphere.domain.NotificationPrefs;
import com.medisphere.domain.Provider;
import com.medisphere.dto.request.FhirConfigRequest;
import com.medisphere.dto.request.UpdateNotificationPrefsRequest;
import com.medisphere.dto.request.UpdateProviderRequest;
import com.medisphere.dto.response.FhirConfigResponse;
import com.medisphere.dto.response.NotificationPrefsResponse;
import com.medisphere.dto.response.ProviderResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.fhir.FHIRProperties;
import com.medisphere.repository.FhirConfigurationRepository;
import com.medisphere.repository.ProviderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Phase 7 — B7.6 Settings Service
 *
 * Manages provider profile, notification preferences, and FHIR configuration.
 *
 * Per FR-SET-01 through FR-SET-05:
 *   - Provider ID is read-only (never updated)
 *   - Notification prefs persist per provider
 *   - FHIR config is persisted to MongoDB (singleton document id="default")
 *   - FHIR config seeded from FHIRProperties on first run
 *   - All settings changes are audited
 *
 * SECURITY: FHIR credentials (smartClientId, smartClientSecret) are NEVER exposed.
 * They remain environment-variable-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final ProviderRepository providerRepository;
    private final FhirConfigurationRepository fhirConfigRepository;
    private final AuditService auditService;
    private final FHIRProperties fhirProperties;

    private static final String MODULE = "Settings";
    private static final String FHIR_CONFIG_ID = "default";

    // ── Seed FHIR config on first startup ────────────────────────────────

    @PostConstruct
    public void seedFhirConfig() {
        if (!fhirConfigRepository.existsById(FHIR_CONFIG_ID)) {
            FhirConfiguration config = FhirConfiguration.builder()
                    .id(FHIR_CONFIG_ID)
                    .mode(fhirProperties.mode() != null ? fhirProperties.mode() : "mock")
                    .baseUrl(fhirProperties.baseUrl() != null ? fhirProperties.baseUrl() : "")
                    .version("R4")
                    .authType("SMART on FHIR")
                    .syncIntervalMinutes(5)
                    .build();
            fhirConfigRepository.save(config);
            log.info("SettingsService: FHIR config seeded from environment (mode={})",
                    config.getMode());
        }
    }

    // ── Provider profile ──────────────────────────────────────────────────

    public ProviderResponse getProviderProfile(String providerId) {
        Provider provider = findProviderOrThrow(providerId);
        return ProviderResponse.from(provider);
    }

    public ProviderResponse updateProviderProfile(String providerId,
                                                   UpdateProviderRequest request) {
        Provider provider = findProviderOrThrow(providerId);

        if (StringUtils.hasText(request.name()))     provider.setName(request.name());
        if (StringUtils.hasText(request.specialty())) provider.setSpecialty(request.specialty());
        if (StringUtils.hasText(request.facility()))  provider.setFacility(request.facility());

        Provider saved = providerRepository.save(provider);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(saved.getName())
                .action("Updated Provider Profile")
                .module(MODULE)
                .status("Success")
                .build());

        return ProviderResponse.from(saved);
    }

    // ── Notification preferences ──────────────────────────────────────────

    public NotificationPrefsResponse getNotificationPrefs(String providerId) {
        Provider provider = findProviderOrThrow(providerId);
        return NotificationPrefsResponse.from(provider.getNotificationPrefs());
    }

    public NotificationPrefsResponse updateNotificationPrefs(String providerId,
                                                              UpdateNotificationPrefsRequest request) {
        Provider provider = findProviderOrThrow(providerId);
        NotificationPrefs prefs = provider.getNotificationPrefs();
        if (prefs == null) prefs = NotificationPrefs.defaults();

        if (request.critical() != null) prefs.setCritical(request.critical());
        if (request.risk()     != null) prefs.setRisk(request.risk());
        if (request.approvals()!= null) prefs.setApprovals(request.approvals());
        if (request.system()   != null) prefs.setSystem(request.system());

        provider.setNotificationPrefs(prefs);
        providerRepository.save(provider);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(provider.getName())
                .action("Updated Notification Preferences")
                .module(MODULE)
                .status("Success")
                .build());

        return NotificationPrefsResponse.from(prefs);
    }

    // ── FHIR configuration (ADMIN only — enforced by controller) ─────────

    public FhirConfigResponse getFhirConfig() {
        FhirConfiguration config = fhirConfigRepository.findById(FHIR_CONFIG_ID)
                .orElseGet(this::createDefaultFhirConfig);
        return FhirConfigResponse.from(config);
    }

    public FhirConfigResponse updateFhirConfig(FhirConfigRequest request,
                                                String adminProviderId,
                                                String adminProviderName) {
        FhirConfiguration config = fhirConfigRepository.findById(FHIR_CONFIG_ID)
                .orElseGet(this::createDefaultFhirConfig);

        if (StringUtils.hasText(request.mode()))    config.setMode(request.mode());
        if (StringUtils.hasText(request.baseUrl())) config.setBaseUrl(request.baseUrl());
        if (StringUtils.hasText(request.version())) config.setVersion(request.version());
        if (StringUtils.hasText(request.authType())) config.setAuthType(request.authType());
        if (request.syncIntervalMinutes() > 0) {
            config.setSyncIntervalMinutes(request.syncIntervalMinutes());
        }
        config.setUpdatedBy(adminProviderName);
        config.setUpdatedAt(Instant.now());

        FhirConfiguration saved = fhirConfigRepository.save(config);

        auditService.log(AuditContext.builder()
                .userId(adminProviderId)
                .userName(adminProviderName)
                .action("Updated FHIR Configuration")
                .module(MODULE)
                .status("Success")
                .build());

        log.info("FHIR configuration updated by {}: mode={}", adminProviderId, saved.getMode());
        return FhirConfigResponse.from(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Provider findProviderOrThrow(String providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider", providerId));
    }

    private FhirConfiguration createDefaultFhirConfig() {
        FhirConfiguration config = FhirConfiguration.builder()
                .id(FHIR_CONFIG_ID)
                .mode(fhirProperties.mode() != null ? fhirProperties.mode() : "mock")
                .baseUrl(fhirProperties.baseUrl() != null ? fhirProperties.baseUrl() : "")
                .version("R4")
                .authType("SMART on FHIR")
                .syncIntervalMinutes(5)
                .build();
        return fhirConfigRepository.save(config);
    }
}

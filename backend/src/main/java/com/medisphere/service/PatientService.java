package com.medisphere.service;

import com.medisphere.audit.AuditContext;
import com.medisphere.audit.AuditService;
import com.medisphere.domain.Consent;
import com.medisphere.domain.HealthTwin;
import com.medisphere.domain.Patient;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.dto.request.UpdatePatientRequest;
import com.medisphere.dto.response.FilterOptionsResponse;
import com.medisphere.dto.response.PatientResponse;
import com.medisphere.dto.response.PatientSummaryResponse;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.TwinResponse;
import com.medisphere.exception.ConflictException;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.fhir.FHIRClient;
import com.medisphere.fhir.model.FHIRCondition;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.HealthTwinRepository;
import com.medisphere.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private static final String MODULE = "Patients";

    private final PatientRepository patientRepository;
    private final HealthTwinRepository twinRepository;
    private final ConsentRepository consentRepository;
    private final TwinService twinService;
    private final PatientIdGenerator idGenerator;
    private final AuditService auditService;
    private FHIRClient fhirClient;
    private LabService labService;           // Phase 3 — setter injection to avoid circular dep
    private VitalsService vitalsService;     // Phase 3
    private PredictionService predictionService; // Phase 4 — setter injection to avoid circular dep

    public PatientService(PatientRepository patientRepository,
                          HealthTwinRepository twinRepository,
                          ConsentRepository consentRepository,
                          TwinService twinService,
                          PatientIdGenerator idGenerator,
                          AuditService auditService) {
        this.patientRepository = patientRepository;
        this.twinRepository = twinRepository;
        this.consentRepository = consentRepository;
        this.twinService = twinService;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    /** Setter injection used to break the LabService ↔ PatientService circular dependency */
    @org.springframework.beans.factory.annotation.Autowired
    public void setLabService(LabService labService) {
        this.labService = labService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setVitalsService(VitalsService vitalsService) {
        this.vitalsService = vitalsService;
    }

    /** Setter injection to break the PredictionService ↔ PatientService circular dependency */
    @org.springframework.beans.factory.annotation.Autowired
    public void setPredictionService(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setFhirClient(FHIRClient fhirClient) {
    this.fhirClient = fhirClient;
}
    // ── Create ────────────────────────────────────────────────────────────

    public PatientResponse createPatient(CreatePatientRequest request, String providerId, String providerName) {
        // Guard: fhirId must be unique
        if (StringUtils.hasText(request.fhirId()) && patientRepository.existsByFhirId(request.fhirId())) {
            throw new ConflictException("A patient with FHIR ID '" + request.fhirId() + "' already exists");
        }

        String patientId = idGenerator.next();

        Patient.ContactInfo contact = null;
        if (StringUtils.hasText(request.phone()) || StringUtils.hasText(request.email())) {
            contact = Patient.ContactInfo.builder()
                    .phone(request.phone())
                    .email(request.email())
                    .build();
        }

        // Determine initial consent state from request
        boolean consentComplete = request.consents() != null
                && request.consents().ehr()
                && request.consents().ai();

        Patient patient = Patient.builder()
                .id(patientId)
                .fhirId(request.fhirId())
                .name(request.name())
                .dob(request.dob())
                .gender(request.gender())
                .contact(contact)
              .conditions(resolveInitialConditions(request))
                .riskLevel("Low")
                .status("Active")
                .providerId(providerId)
                .providerName(providerName)
                .ehrSystem(request.ehrSystem())
                .fhirConnected(StringUtils.hasText(request.fhirId()))
                .wearableStatus("Offline")
                .consentComplete(consentComplete)
                .healthStatus("Pending review")
                .adherence(0)
                .build();

        Patient saved = patientRepository.save(patient);

        // 1. Initialize digital health twin (idempotent)
        HealthTwin twin = twinService.initTwin(patientId);

        // 2. Link twinId back to patient
        saved.setTwinId(twin.getId());
        patientRepository.save(saved);

        // 3. Initialize consent record (Phase 2 minimal — full consent in Phase 7)
        initConsent(patientId, providerId, request);

        // 4. Phase 3 — Seed vitals (deterministic development data)
        if (vitalsService != null) {
            try {
                vitalsService.seedVitalsForPatient(patientId);
            } catch (Exception e) {
                log.warn("Vitals seeding failed for patient {} — continuing: {}", patientId, e.getMessage());
            }
        }

        // 5. Phase 3 — Ingest lab results from FHIR (MockFHIRClient in dev)
        if (StringUtils.hasText(request.fhirId())) {
            // FHIR connectivity is known from the patient record even if the
            // mock FHIR source currently has no new observations.
            twin = twinService.updateDataSource(twin.getId(), "ehr", true, Instant.now());

            if (labService != null) {
                try {
                    labService.ingestFromFHIR(patientId);
                } catch (Exception e) {
                    log.warn("FHIR lab ingestion failed for patient {} — continuing: {}", patientId, e.getMessage());
                }
            }

            // Lab source freshness is represented by the ingestion attempt.
            twin = twinService.updateDataSource(twin.getId(), "lab", true, Instant.now());
        }

        // 6. Audit
        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Created patient")
                .module(MODULE)
                .patientId(patientId)
                .patientName(request.name())
                .status("Success")
                .build());

        log.info("Created patient {} '{}' with twin {}", patientId, request.name(), twin.getId());

        // 7. Phase 4 — Run AI predictions asynchronously AFTER patient is fully persisted.
        // Failures are caught inside PredictionService.runPredictions — they must never
        // prevent the patient create response from being returned.
        if (predictionService != null) {
            predictionService.runPredictions(patientId);
        }

        return PatientResponse.from(saved, twin);
    }

    // ── Read — paginated list ─────────────────────────────────────────────

    public PageResponse<PatientSummaryResponse> getPatients(
        String search,
        String riskLevel,
        String condition,
        String status,
        String providerName,
        int page,
        int size) {

    Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "updatedAt")
    );

    Page<Patient> patientPage = patientRepository.searchPatients(
            search,
            riskLevel,
            condition,
            status,
            providerName,
            pageable
    );

    return PageResponse.from(patientPage, patient -> {

        Optional<HealthTwin> twin =
                patient.getTwinId() != null
                        ? twinRepository.findById(patient.getTwinId())
                        : twinRepository.findByPatientId(patient.getId());

        return PatientSummaryResponse.from(
                patient,
                twin.orElse(null)
        );
    });
}

    // ── Read — single ─────────────────────────────────────────────────────

    public PatientResponse getPatient(String patientId, String requestingProviderId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        Optional<HealthTwin> twin = patient.getTwinId() != null
                ? twinRepository.findById(patient.getTwinId())
                : twinRepository.findByPatientId(patientId);

        auditService.log(AuditContext.builder()
                .userId(requestingProviderId)
                .action("Viewed Patient Record")
                .module(MODULE)
                .patientId(patientId)
                .patientName(patient.getName())
                .status("Success")
                .build());

        return PatientResponse.from(patient, twin.orElse(null));
    }

    // ── Update ────────────────────────────────────────────────────────────

    public PatientResponse updatePatient(String patientId, UpdatePatientRequest request,
                                          String providerId, String providerName) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Apply only non-null fields from the request
        if (StringUtils.hasText(request.name()))        patient.setName(request.name());
        if (StringUtils.hasText(request.dob()))         patient.setDob(request.dob());
        if (StringUtils.hasText(request.gender()))      patient.setGender(request.gender());
        if (StringUtils.hasText(request.ehrSystem()))   patient.setEhrSystem(request.ehrSystem());
        if (StringUtils.hasText(request.status()))      patient.setStatus(request.status());
        if (StringUtils.hasText(request.riskLevel()))   patient.setRiskLevel(request.riskLevel());
        if (StringUtils.hasText(request.healthStatus())) patient.setHealthStatus(request.healthStatus());
        if (request.adherence() != null)                patient.setAdherence(request.adherence());
        if (request.conditions() != null)               patient.setConditions(request.conditions());

        if (StringUtils.hasText(request.phone()) || StringUtils.hasText(request.email())) {
            Patient.ContactInfo contact = patient.getContact() != null
                    ? patient.getContact() : new Patient.ContactInfo();
            if (StringUtils.hasText(request.phone())) contact.setPhone(request.phone());
            if (StringUtils.hasText(request.email())) contact.setEmail(request.email());
            patient.setContact(contact);
        }

        Patient saved = patientRepository.save(patient);

        auditService.log(AuditContext.builder()
                .userId(providerId)
                .userName(providerName)
                .action("Updated patient")
                .module(MODULE)
                .patientId(patientId)
                .patientName(saved.getName())
                .status("Success")
                .build());

        Optional<HealthTwin> twin = saved.getTwinId() != null
                ? twinRepository.findById(saved.getTwinId())
                : twinRepository.findByPatientId(patientId);

        return PatientResponse.from(saved, twin.orElse(null));
    }

    // ── Next ID ───────────────────────────────────────────────────────────

    public String getNextId() {
        return idGenerator.preview();
    }

    // ── Filter options ────────────────────────────────────────────────────

    public FilterOptionsResponse getFilterOptions() {
        List<String> conditions = patientRepository.findAllConditions()
                .stream()
                .flatMap(p -> p.getConditions() != null ? p.getConditions().stream() : Stream.empty())
                .distinct()
                .sorted()
                .toList();

        List<String> providers = patientRepository.findAllProviders()
                .stream()
                .map(Patient::getProviderName)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();

        return FilterOptionsResponse.of(conditions, providers);
    }

    // ── Timeline ──────────────────────────────────────────────────────────

    public List<TwinResponse.TimelineEventResponse> getTimeline(String patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        String rawTwinId = patient.getTwinId();
        if (rawTwinId == null) {
            Optional<HealthTwin> found = twinRepository.findByPatientId(patientId);
            if (found.isEmpty()) return List.of();
            rawTwinId = found.get().getId();
        }
        // Effectively final copy for use in lambda
        final String twinId = rawTwinId;

        HealthTwin twin = twinRepository.findById(twinId)
                .orElseThrow(() -> new ResourceNotFoundException("HealthTwin", twinId));

        return twin.getTimeline() == null ? List.of() :
                twin.getTimeline().stream()
                        .map(TwinResponse.TimelineEventResponse::from)
                        .toList();
    }



private List<String> resolveInitialConditions(
        CreatePatientRequest request) {

    /*
     * If the frontend explicitly supplied conditions,
     * preserve them.
     */
    if (request.conditions() != null
            && !request.conditions().isEmpty()) {

        return new ArrayList<>(request.conditions());
    }

    /*
     * No manually supplied conditions.
     * Try to retrieve them from FHIR.
     */
    if (!StringUtils.hasText(request.fhirId())
            || fhirClient == null) {

        return new ArrayList<>();
    }

    try {

        List<FHIRCondition> fhirConditions =
                fhirClient.fetchConditions(request.fhirId());

        if (fhirConditions == null
                || fhirConditions.isEmpty()) {

            return new ArrayList<>();
        }

        return fhirConditions.stream()
                .filter(condition ->
                        condition != null
                                && StringUtils.hasText(
                                        condition.getDisplayName()
                                )
                )
                .filter(condition ->
                        !"resolved".equalsIgnoreCase(
                                condition.getClinicalStatus()
                        )
                )
                .map(FHIRCondition::getDisplayName)
                .distinct()
                .toList();

    } catch (Exception e) {

        log.warn(
                "FHIR condition retrieval failed for patient {} — continuing with empty conditions: {}",
                request.fhirId(),
                e.getMessage()
        );

        return new ArrayList<>();
    }
}







    // ── Private helpers ───────────────────────────────────────────────────

    private void initConsent(String patientId, String providerId, CreatePatientRequest request) {
        if (consentRepository.existsByPatientId(patientId)) return; // idempotent

        boolean ehrConsent      = request.consents() != null && request.consents().ehr();
        boolean wearableConsent = request.consents() != null && request.consents().wearable();
        boolean aiConsent       = request.consents() != null && request.consents().ai();

        List<Consent.ConsentHistoryEntry> history = new ArrayList<>();
        Instant now = Instant.now();

        addHistoryEntry(history, "EHR Data Access",     ehrConsent      ? "Granted" : "Declined", providerId, now);
        addHistoryEntry(history, "Wearable Data Access", wearableConsent ? "Granted" : "Declined", providerId, now);
        addHistoryEntry(history, "AI Risk Analysis",     aiConsent       ? "Granted" : "Declined", providerId, now);

        Consent consent = Consent.builder()
                .id("CON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .patientId(patientId)
                .ehr(ehrConsent)
                .wearable(wearableConsent)
                .ai(aiConsent)
                .updatedAt(now)
                .updatedBy(providerId)
                .history(history)
                .build();

        Consent savedConsent = consentRepository.save(consent);

        // Link consentId back to patient
        patientRepository.findById(patientId).ifPresent(p -> {
            p.setConsentId(savedConsent.getId());
            p.setConsentComplete(ehrConsent && aiConsent);
            patientRepository.save(p);
        });
    }

    private void addHistoryEntry(List<Consent.ConsentHistoryEntry> history,
                                  String type, String status, String updatedBy, Instant now) {
        history.add(Consent.ConsentHistoryEntry.builder()
                .id("ch-" + UUID.randomUUID().toString().substring(0, 8))
                .date(now)
                .type(type)
                .status(status)
                .updatedBy(updatedBy)
                .build());
    }
}

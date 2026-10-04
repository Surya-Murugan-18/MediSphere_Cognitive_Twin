package com.medisphere.service;

import com.medisphere.audit.AuditService;
import com.medisphere.domain.Consent;
import com.medisphere.domain.HealthTwin;
import com.medisphere.domain.Patient;
import com.medisphere.dto.request.CreatePatientRequest;
import com.medisphere.dto.request.UpdatePatientRequest;
import com.medisphere.dto.response.PatientResponse;
import com.medisphere.exception.ConflictException;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.HealthTwinRepository;
import com.medisphere.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService Unit Tests")
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private HealthTwinRepository twinRepository;
    @Mock private ConsentRepository consentRepository;
    @Mock private TwinService twinService;
    @Mock private PatientIdGenerator idGenerator;
    @Mock private AuditService auditService;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(
                patientRepository, twinRepository, consentRepository,
                twinService, idGenerator, auditService);
    }

    // ── createPatient ─────────────────────────────────────────────────────

    @Test
    @DisplayName("createPatient persists patient, initialises twin and consent, returns response")
    void createPatient_success() {
        CreatePatientRequest req = new CreatePatientRequest(
                "Jane Doe", "1985-03-15", "Female",
                "+1 (415) 555-0100", "jane@example.com",
                "fhir:Patient/abc-123", "Epic",
                List.of("Diabetes"),
                new CreatePatientRequest.ConsentFlags(true, false, true));

        when(patientRepository.existsByFhirId("fhir:Patient/abc-123")).thenReturn(false);
        when(idGenerator.next()).thenReturn("P001");

        HealthTwin mockTwin = HealthTwin.builder()
                .id("HT-001").patientId("P001").status("Syncing").completeness(0)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources()).build();
        when(twinService.initTwin("P001")).thenReturn(mockTwin);
        // updateDataSource is called when fhirId is present — must not return null
        when(twinService.updateDataSource(anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(mockTwin);

        // save() called twice: initial save + twinId linkback
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consentRepository.existsByPatientId("P001")).thenReturn(false);
        when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.findById("P001")).thenReturn(Optional.of(buildPatient("P001", "Jane Doe")));

        PatientResponse result = patientService.createPatient(req, "PROV-001", "Dr. Test");

        assertThat(result.id()).isEqualTo("P001");
        assertThat(result.name()).isEqualTo("Jane Doe");
        assertThat(result.fhirId()).isEqualTo("fhir:Patient/abc-123");
        verify(twinService, times(1)).initTwin("P001");
        verify(consentRepository, times(1)).save(any(Consent.class));
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("createPatient throws ConflictException when fhirId already exists")
    void createPatient_duplicateFhirId_throwsConflict() {
        CreatePatientRequest req = new CreatePatientRequest(
                "John Dup", "1970-01-01", "Male",
                null, null,
                "fhir:Patient/EXISTING", "Epic",
                null, null);

        when(patientRepository.existsByFhirId("fhir:Patient/EXISTING")).thenReturn(true);

        assertThatThrownBy(() -> patientService.createPatient(req, "PROV-001", "Dr. Test"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("fhir:Patient/EXISTING");

        verify(patientRepository, never()).save(any());
        verify(twinService, never()).initTwin(any());
    }

    @Test
    @DisplayName("createPatient does NOT create duplicate consent when consent already exists — idempotent")
    void createPatient_existingConsent_skipsConsentCreation() {
        CreatePatientRequest req = new CreatePatientRequest(
                "Idem Potent", "1990-06-01", "Female",
                null, null, "fhir:Patient/new-idem", "Epic",
                null, new CreatePatientRequest.ConsentFlags(true, false, false));

        when(patientRepository.existsByFhirId(anyString())).thenReturn(false);
        when(idGenerator.next()).thenReturn("P002");

        HealthTwin mockTwin = HealthTwin.builder().id("HT-002").patientId("P002")
                .status("Syncing").completeness(0)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources()).build();
        when(twinService.initTwin("P002")).thenReturn(mockTwin);
        when(twinService.updateDataSource(anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(mockTwin);
        when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(consentRepository.existsByPatientId("P002")).thenReturn(true); // already exists
        // No findById stub needed — initConsent returns early, and twinId linkback
        // is done via save() directly (not findById) in the main createPatient flow

        patientService.createPatient(req, "PROV-001", "Dr. Test");

        verify(consentRepository, never()).save(any()); // no duplicate consent
    }

    // ── getPatient ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPatient returns full response with twin data")
    void getPatient_found_returnsResponse() {
        Patient patient = buildPatient("P001", "John Doe");
        patient.setTwinId("HT-001");

        HealthTwin twin = HealthTwin.builder().id("HT-001").patientId("P001")
                .status("Synchronized").completeness(96)
                .timeline(new ArrayList<>()).bodyRegions(new ArrayList<>())
                .dataSources(HealthTwin.DataSources.defaultSources()).build();

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(twinRepository.findById("HT-001")).thenReturn(Optional.of(twin));

        PatientResponse result = patientService.getPatient("P001", "PROV-001");

        assertThat(result.id()).isEqualTo("P001");
        assertThat(result.twinStatus()).isEqualTo("Synchronized");
        assertThat(result.twinCompleteness()).isEqualTo(96);
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("getPatient throws 404 when patient not found")
    void getPatient_notFound_throws404() {
        when(patientRepository.findById("P999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient("P999", "PROV-001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("P999");
    }

    // ── updatePatient ─────────────────────────────────────────────────────

    @Test
    @DisplayName("updatePatient applies partial changes and returns updated response")
    void updatePatient_partialUpdate_appliesChanges() {
        Patient patient = buildPatient("P001", "Old Name");
        patient.setTwinId(null);

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // twinId is null → code calls findByPatientId (not findById)
        when(twinRepository.findByPatientId("P001")).thenReturn(Optional.empty());

        UpdatePatientRequest req = new UpdatePatientRequest(
                "New Name", null, null, null, null,
                null, null, null, "Inactive", null, null, null);

        PatientResponse result = patientService.updatePatient("P001", req, "PROV-001", "Dr. Test");

        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.status()).isEqualTo("Inactive");
        verify(auditService, times(1)).log(any());
    }

    @Test
    @DisplayName("updatePatient throws 404 when patient does not exist")
    void updatePatient_notFound_throws404() {
        when(patientRepository.findById("P999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.updatePatient(
                "P999",
                new UpdatePatientRequest(null,null,null,null,null,null,null,null,null,null,null,null),
                "PROV-001", "Dr. Test"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getNextId ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getNextId returns preview ID without consuming it")
    void getNextId_returnsPreview() {
        when(idGenerator.preview()).thenReturn("P007");
        assertThat(patientService.getNextId()).isEqualTo("P007");
        verify(idGenerator, times(1)).preview();
        verify(idGenerator, never()).next();
    }

    // ── getFilterOptions ──────────────────────────────────────────────────

    @Test
    @DisplayName("getFilterOptions returns distinct conditions and providers")
    void getFilterOptions_returnsDistinct() {
        Patient p1 = buildPatient("P001", "Alice");
        p1.setConditions(List.of("Diabetes", "Hypertension"));
        p1.setProviderName("Dr. A");
        Patient p2 = buildPatient("P002", "Bob");
        p2.setConditions(List.of("Diabetes"));
        p2.setProviderName("Dr. B");

        when(patientRepository.findAllConditions()).thenReturn(List.of(p1, p2));
        when(patientRepository.findAllProviders()).thenReturn(List.of(p1, p2));

        var options = patientService.getFilterOptions();

        assertThat(options.conditions()).containsExactlyInAnyOrder("Diabetes", "Hypertension");
        assertThat(options.providers()).containsExactlyInAnyOrder("Dr. A", "Dr. B");
        assertThat(options.statuses()).containsExactly("Active", "Inactive", "Pending Consent");
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Patient buildPatient(String id, String name) {
        return Patient.builder()
                .id(id).name(name).fhirId("fhir:Patient/" + id)
                .dob("1980-01-01").gender("Male")
                .riskLevel("Low").status("Active")
                .providerId("PROV-001").providerName("Dr. Test")
                .conditions(new ArrayList<>()).wearableStatus("Offline")
                .fhirConnected(true).consentComplete(true).adherence(0)
                .healthStatus("Stable").build();
    }
}

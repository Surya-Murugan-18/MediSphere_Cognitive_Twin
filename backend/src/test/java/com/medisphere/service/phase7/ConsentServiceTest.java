package com.medisphere.service.phase7;

import com.medisphere.audit.AuditService;
import com.medisphere.domain.Consent;
import com.medisphere.domain.Patient;
import com.medisphere.dto.request.UpdateConsentRequest;
import com.medisphere.dto.response.ConsentHistoryEntryResponse;
import com.medisphere.dto.response.ConsentResponse;
import com.medisphere.exception.ResourceNotFoundException;
import com.medisphere.repository.ConsentRepository;
import com.medisphere.repository.PatientRepository;
import com.medisphere.service.ConsentService;
import com.medisphere.service.TwinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 — B7.9 ConsentService unit tests.
 * Per tasks.md: update, history, audit log entry.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentService — Phase 7 Tests")
class ConsentServiceTest {

    @Mock private ConsentRepository consentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private AuditService auditService;
    @Mock private TwinService twinService;

    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = new ConsentService(
                consentRepository, patientRepository, auditService, twinService);
    }

    // ── getConsent ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getConsent returns ConsentResponse for existing consent")
    void getConsent_found_returnsResponse() {
        Consent consent = buildConsent("P001", true, true, true);
        when(consentRepository.findByPatientId("P001")).thenReturn(Optional.of(consent));

        ConsentResponse result = consentService.getConsent("P001");

        assertThat(result.patientId()).isEqualTo("P001");
        assertThat(result.ehr()).isTrue();
        assertThat(result.wearable()).isTrue();
        assertThat(result.ai()).isTrue();
    }

    @Test
    @DisplayName("getConsent throws 404 when no consent record exists")
    void getConsent_notFound_throws404() {
        when(consentRepository.findByPatientId("P999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.getConsent("P999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateConsent ─────────────────────────────────────────────────────

    @Test
    @DisplayName("updateConsent persists updated flags")
    void updateConsent_updatesFlags() {
        Patient patient = buildPatient("P001");
        Consent consent  = buildConsent("P001", true, true, true);

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(consentRepository.findByPatientId("P001")).thenReturn(Optional.of(consent));
        when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twinService.updateDataSource(any(), any(), anyBoolean(), any())).thenReturn(null);

        UpdateConsentRequest req = new UpdateConsentRequest(false, null, null);

        ConsentResponse result = consentService.updateConsent(
                "P001", req, "PROV-001", "Dr. Test");

        assertThat(result.ehr()).isFalse(); // changed
        assertThat(result.wearable()).isTrue(); // unchanged
        assertThat(result.ai()).isTrue(); // unchanged
    }

    @Test
    @DisplayName("updateConsent appends one history entry per changed field")
    void updateConsent_appendsHistoryEntry() {
        Patient patient = buildPatient("P001");
        Consent consent = buildConsent("P001", true, true, false);

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(consentRepository.findByPatientId("P001")).thenReturn(Optional.of(consent));
        when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(twinService.updateDataSource(any(), any(), anyBoolean(), any())).thenReturn(null);

        // Change both ehr (true→false) and ai (false→true) — expect 2 history entries
        UpdateConsentRequest req = new UpdateConsentRequest(false, null, true);

        ArgumentCaptor<Consent> savedConsent = ArgumentCaptor.forClass(Consent.class);
        consentService.updateConsent("P001", req, "PROV-001", "Dr. Test");

        verify(consentRepository).save(savedConsent.capture());
        Consent saved = savedConsent.getValue();

        assertThat(saved.getHistory()).hasSize(2);
        assertThat(saved.getHistory()).anyMatch(h -> "EHR Data Access".equals(h.getType())
                                                && "Declined".equals(h.getStatus()));
        assertThat(saved.getHistory()).anyMatch(h -> "AI Risk Analysis".equals(h.getType())
                                                && "Granted".equals(h.getStatus()));
    }

    @Test
    @DisplayName("updateConsent logs an audit entry")
    void updateConsent_logsAudit() {
        Patient patient = buildPatient("P001");
        Consent consent = buildConsent("P001", true, false, true);

        when(patientRepository.findById("P001")).thenReturn(Optional.of(patient));
        when(consentRepository.findByPatientId("P001")).thenReturn(Optional.of(consent));
        when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // No twinId on patient → twin branch skipped; no twinService stub needed

        consentService.updateConsent("P001",
                new UpdateConsentRequest(null, true, null),
                "PROV-001", "Dr. Test");

        verify(auditService, times(1)).log(any());
    }

    // ── getConsentHistory ─────────────────────────────────────────────────

    @Test
    @DisplayName("getConsentHistory returns entries sorted newest first")
    void getConsentHistory_returnsSortedNewestFirst() {
        Consent consent = buildConsent("P001", true, true, true);
        Instant earlier = Instant.now().minusSeconds(3600);
        Instant later   = Instant.now();

        consent.getHistory().add(Consent.ConsentHistoryEntry.builder()
                .id("ch-1").date(earlier).type("EHR Data Access")
                .status("Granted").updatedBy("Portal").build());
        consent.getHistory().add(Consent.ConsentHistoryEntry.builder()
                .id("ch-2").date(later).type("AI Risk Analysis")
                .status("Granted").updatedBy("Dr. Test").build());

        when(consentRepository.findByPatientId("P001")).thenReturn(Optional.of(consent));

        List<ConsentHistoryEntryResponse> history = consentService.getConsentHistory("P001");

        assertThat(history).hasSize(2);
        // newest first
        assertThat(history.get(0).date()).isEqualTo(later);
        assertThat(history.get(1).date()).isEqualTo(earlier);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Consent buildConsent(String patientId, boolean ehr, boolean wearable, boolean ai) {
        return Consent.builder()
                .id("CON-" + patientId)
                .patientId(patientId)
                .ehr(ehr).wearable(wearable).ai(ai)
                .updatedAt(Instant.now())
                .updatedBy("system")
                .history(new ArrayList<>())
                .build();
    }

    private Patient buildPatient(String id) {
        return Patient.builder()
                .id(id).name("Test Patient").fhirId("fhir:Patient/" + id)
                .dob("1980-01-01").gender("Male")
                .riskLevel("Low").status("Active")
                .providerId("PROV-001").providerName("Dr. Test")
                .conditions(new ArrayList<>()).wearableStatus("Offline")
                .twinId("HT-" + id).build();
    }
}

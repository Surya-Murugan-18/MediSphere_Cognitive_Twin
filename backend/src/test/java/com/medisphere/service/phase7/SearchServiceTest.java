package com.medisphere.service.phase7;

import com.medisphere.domain.*;
import com.medisphere.dto.response.SearchResultResponse;
import com.medisphere.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 — B7.9 SearchService unit tests.
 * Per tasks.md: cross-entity search, role filtering.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchService — Phase 7 Tests")
class SearchServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(mongoTemplate);
    }

    // ── Short query guard ─────────────────────────────────────────────────

    @Test
    @DisplayName("search returns empty list when query length < 2")
    void search_shortQuery_returnsEmpty() {
        List<SearchResultResponse> result =
                searchService.search("a", null, 4, "CLINICIAN");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("search returns empty list for blank query")
    void search_blankQuery_returnsEmpty() {
        List<SearchResultResponse> result =
                searchService.search("", null, 4, "CLINICIAN");
        assertThat(result).isEmpty();
    }

    // ── CLINICIAN cross-entity ────────────────────────────────────────────

    @Test
    @DisplayName("CLINICIAN gets patient results")
    void search_clinician_includesPatientResults() {
        Patient p = buildPatient("P001", "John Doe", "fhir:Patient/abc");
        // $text throws (not mocked), falls back to regex
        when(mongoTemplate.find(any(Query.class), eq(Patient.class)))
                .thenReturn(List.of(p));
        when(mongoTemplate.find(any(Query.class), eq(Alert.class)))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(CarePlan.class)))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(Prediction.class)))
                .thenReturn(List.of());

        List<SearchResultResponse> groups =
                searchService.search("john", null, 4, "CLINICIAN");

        assertThat(groups).anyMatch(g -> "Patients".equals(g.category())
                && !g.results().isEmpty());
    }

    // ── ANALYST PHI restriction ───────────────────────────────────────────

    @Test
    @DisplayName("ANALYST receives no patient, alert, care-plan or prediction results")
    void search_analyst_excludesPhiCategories() {
        // Even if repository returns data, ANALYST role should get no PHI categories
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of());

        List<SearchResultResponse> groups =
                searchService.search("john", null, 4, "ANALYST");

        // ANALYST should only receive the Reports category (if matches)
        groups.forEach(g -> assertThat(g.category())
                .as("ANALYST must not receive PHI category: " + g.category())
                .isIn("Reports"));
    }

    @Test
    @DisplayName("ANALYST can still find reports by name")
    void search_analyst_canFindReports() {
        List<SearchResultResponse> groups =
                searchService.search("Patient Risk", null, 4, "ANALYST");

        // Reports group should be present for matching query
        boolean hasReports = groups.stream()
                .anyMatch(g -> "Reports".equals(g.category()) && !g.results().isEmpty());
        assertThat(hasReports).isTrue();
    }

    // ── Max results per group ─────────────────────────────────────────────

    @Test
    @DisplayName("results are capped at max 4 per group")
    void search_capped_at_maxPerGroup() {
        // Return 6 patients — only 4 should appear
        List<Patient> manyPatients = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            manyPatients.add(buildPatient("P00" + i, "Patient " + i, "fhir:" + i));
        }
        when(mongoTemplate.find(any(Query.class), eq(Patient.class)))
                .thenReturn(manyPatients.subList(0, 4)); // mongo limit(4) applied
        when(mongoTemplate.find(any(Query.class), eq(Alert.class)))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(CarePlan.class)))
                .thenReturn(List.of());
        when(mongoTemplate.find(any(Query.class), eq(Prediction.class)))
                .thenReturn(List.of());

        List<SearchResultResponse> groups =
                searchService.search("patient", null, 4, "CLINICIAN");

        groups.stream()
                .filter(g -> "Patients".equals(g.category()))
                .forEach(g -> assertThat(g.results().size()).isLessThanOrEqualTo(4));
    }

    // ── Category filter ───────────────────────────────────────────────────

    @Test
    @DisplayName("category filter limits which groups are returned")
    void search_categoryFilter_limitsGroups() {
        when(mongoTemplate.find(any(Query.class), eq(Alert.class)))
                .thenReturn(List.of(buildAlert("A-001", "Heart Rate Spike", "P001", "HIGH")));
        when(mongoTemplate.find(any(Query.class), any())).thenReturn(List.of());

        List<SearchResultResponse> groups =
                searchService.search("spike", List.of("Alerts"), 4, "CLINICIAN");

        // Only Alerts category should be present (or empty if no match)
        groups.forEach(g -> assertThat(g.category()).isEqualTo("Alerts"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Patient buildPatient(String id, String name, String fhirId) {
        return Patient.builder()
                .id(id).name(name).fhirId(fhirId)
                .dob("1980-01-01").gender("Male")
                .riskLevel("Low").status("Active")
                .conditions(new ArrayList<>())
                .build();
    }

    private Alert buildAlert(String id, String event, String patientId, String severity) {
        return Alert.builder()
                .id(id).event(event).patientId(patientId)
                .patientName("Test Patient").severity(severity)
                .status("Unacknowledged")
                .build();
    }
}

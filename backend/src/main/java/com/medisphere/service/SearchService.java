package com.medisphere.service;

import com.medisphere.domain.*;
import com.medisphere.dto.response.SearchResultResponse;
import com.medisphere.dto.response.SearchResultResponse.SearchResultItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 7 — B7.7 Search Service
 *
 * Cross-collection text search using MongoDB $text indexes.
 * Per FR-SCH-01 through FR-SCH-04:
 *   - Server-side search only (FR-SCH-02)
 *   - Results grouped by type, max 4 per group (FR-SCH-03)
 *   - Role-filtered: ANALYST receives no patient-scoped PHI (FR-SCH-04, FR-RBAC-05)
 *
 * Text indexes required:
 *   patients:    { name: text, fhirId: text } — already exists (Phase 2)
 *   alerts:      { event: text, patientName: text } — added Phase 7
 *   care_plans:  { goal: text, patientName: text } — added Phase 7
 *   predictions: { model: text, label: text } — added Phase 7
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final MongoTemplate mongoTemplate;

    private static final int MAX_PER_GROUP = 4;
    /** Roles that cannot see individual patient PHI */
    private static final Set<String> PHI_RESTRICTED_ROLES = Set.of("ANALYST");

    // ── Main search entry point ───────────────────────────────────────────

    public List<SearchResultResponse> search(String query,
                                              List<String> categories,
                                              int limit,
                                              String callerRole) {
        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            return List.of();
        }

        boolean isAnalyst = PHI_RESTRICTED_ROLES.contains(callerRole);
        String q = query.trim();
        int maxResults = Math.min(limit > 0 ? limit : MAX_PER_GROUP, MAX_PER_GROUP);

        List<SearchResultResponse> groups = new ArrayList<>();

        // Analysts receive no patient-scoped results (FR-RBAC-05)
        if (!isAnalyst) {
            if (wantCategory(categories, "Patients")) {
                groups.add(searchPatients(q, maxResults));
            }
            if (wantCategory(categories, "Alerts")) {
                groups.add(searchAlerts(q, maxResults));
            }
            if (wantCategory(categories, "Care Plans")) {
                groups.add(searchCarePlans(q, maxResults));
            }
            if (wantCategory(categories, "Predictions")) {
                groups.add(searchPredictions(q, maxResults));
            }
        }

        // All roles may search reports (not PHI)
        if (wantCategory(categories, "Reports")) {
            groups.add(searchReports(q, maxResults));
        }

        // Filter out empty groups
        return groups.stream()
                .filter(g -> !g.results().isEmpty())
                .toList();
    }

    // ── Per-collection search methods ─────────────────────────────────────

    private SearchResultResponse searchPatients(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            // Use MongoDB $text search on the patients text index (name + fhirId)
            Query tq = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q))
                    .limit(max);
            List<Patient> results = mongoTemplate.find(tq, Patient.class);
            for (Patient p : results) {
                items.add(new SearchResultItem(
                        p.getId(),
                        p.getName(),
                        p.getId() + " · " + p.getFhirId(),
                        "/patients/" + p.getId()
                ));
            }
        } catch (Exception e) {
            // Text index may not exist yet — fall back to regex search
            items.addAll(patientRegexFallback(q, max));
        }
        return new SearchResultResponse("Patients", items);
    }

    private SearchResultResponse searchAlerts(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            Query tq = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q))
                    .limit(max);
            List<Alert> results = mongoTemplate.find(tq, Alert.class);
            for (Alert a : results) {
                items.add(new SearchResultItem(
                        a.getId(),
                        a.getEvent(),
                        a.getPatientName() + " · " + a.getSeverity(),
                        "/alerts/" + a.getId()
                ));
            }
        } catch (Exception e) {
            items.addAll(alertRegexFallback(q, max));
        }
        return new SearchResultResponse("Alerts", items);
    }

    private SearchResultResponse searchCarePlans(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            Query tq = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q))
                    .limit(max);
            List<CarePlan> results = mongoTemplate.find(tq, CarePlan.class);
            for (CarePlan c : results) {
                items.add(new SearchResultItem(
                        c.getId(),
                        c.getGoal(),
                        c.getPatientName() + " · " + c.getStatus(),
                        "/care-plans/" + c.getId() + "/review"
                ));
            }
        } catch (Exception e) {
            items.addAll(carePlanRegexFallback(q, max));
        }
        return new SearchResultResponse("Care Plans", items);
    }

    private SearchResultResponse searchPredictions(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        try {
            Query tq = TextQuery.queryText(TextCriteria.forDefaultLanguage().matching(q))
                    .limit(max);
            List<Prediction> results = mongoTemplate.find(tq, Prediction.class);
            for (Prediction p : results) {
                items.add(new SearchResultItem(
                        p.getId(),
                        p.getLabel(),
                        p.getPatientId() + " · " + p.getModel(),
                        "/predictions"
                ));
            }
        } catch (Exception e) {
            items.addAll(predictionRegexFallback(q, max));
        }
        return new SearchResultResponse("Predictions", items);
    }

    private SearchResultResponse searchReports(String q, int max) {
        // Reports are a fixed catalog — simple in-memory match on title
        List<String[]> catalog = List.of(
                new String[]{"r1", "Patient Risk Report", "Cohort risk stratification"},
                new String[]{"r2", "Digital Twin Report", "Twin completeness and sync status"},
                new String[]{"r3", "Alert Report", "Alert volume and severity mix"},
                new String[]{"r4", "Care Plan Report", "Plan status and adherence"},
                new String[]{"r5", "Population Health Report", "Condition prevalence by hospital"},
                new String[]{"r6", "AI Model Report", "Federated rounds and accuracy audit"}
        );
        String ql = q.toLowerCase();
        List<SearchResultItem> items = catalog.stream()
                .filter(r -> r[1].toLowerCase().contains(ql) || r[2].toLowerCase().contains(ql))
                .limit(max)
                .map(r -> new SearchResultItem(r[0], r[1], r[2], "/reports"))
                .toList();
        return new SearchResultResponse("Reports", items);
    }

    // ── Regex fallbacks (when text index not yet built) ───────────────────

    private List<SearchResultItem> patientRegexFallback(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        Query rq = new Query(new Criteria().orOperator(
                Criteria.where("name").regex(q, "i"),
                Criteria.where("_id").regex(q, "i"),
                Criteria.where("fhirId").regex(q, "i")
        )).limit(max);
        mongoTemplate.find(rq, Patient.class).forEach(p ->
                items.add(new SearchResultItem(p.getId(), p.getName(),
                        p.getId() + " · " + p.getFhirId(), "/patients/" + p.getId())));
        return items;
    }

    private List<SearchResultItem> alertRegexFallback(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        Query rq = new Query(new Criteria().orOperator(
                Criteria.where("event").regex(q, "i"),
                Criteria.where("patientName").regex(q, "i"),
                Criteria.where("_id").regex(q, "i")
        )).limit(max);
        mongoTemplate.find(rq, Alert.class).forEach(a ->
                items.add(new SearchResultItem(a.getId(), a.getEvent(),
                        a.getPatientName() + " · " + a.getSeverity(), "/alerts/" + a.getId())));
        return items;
    }

    private List<SearchResultItem> carePlanRegexFallback(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        Query rq = new Query(new Criteria().orOperator(
                Criteria.where("goal").regex(q, "i"),
                Criteria.where("patientName").regex(q, "i"),
                Criteria.where("_id").regex(q, "i")
        )).limit(max);
        mongoTemplate.find(rq, CarePlan.class).forEach(c ->
                items.add(new SearchResultItem(c.getId(), c.getGoal(),
                        c.getPatientName() + " · " + c.getStatus(),
                        "/care-plans/" + c.getId() + "/review")));
        return items;
    }

    private List<SearchResultItem> predictionRegexFallback(String q, int max) {
        List<SearchResultItem> items = new ArrayList<>();
        Query rq = new Query(new Criteria().orOperator(
                Criteria.where("label").regex(q, "i"),
                Criteria.where("model").regex(q, "i"),
                Criteria.where("_id").regex(q, "i")
        )).limit(max);
        mongoTemplate.find(rq, Prediction.class).forEach(p ->
                items.add(new SearchResultItem(p.getId(), p.getLabel(),
                        p.getPatientId() + " · " + p.getModel(), "/predictions")));
        return items;
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private boolean wantCategory(List<String> categories, String name) {
        return categories == null || categories.isEmpty() || categories.contains(name);
    }
}

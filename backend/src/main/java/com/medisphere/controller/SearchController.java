package com.medisphere.controller;

import com.medisphere.dto.response.SearchResultResponse;
import com.medisphere.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 7 — B7.7 Search Controller
 *
 * Per design.md §10 and tasks.md B7.7:
 *
 *   GET /api/search?q=&categories=&limit=
 *
 * FR-SCH-01: cross-entity search (patients, alerts, care plans, predictions)
 * FR-SCH-02: server-side (no in-memory static data)
 * FR-SCH-03: results grouped by type, max 4 per group
 * FR-SCH-04: role-filtered — ANALYST sees no patient PHI
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Global cross-entity clinical search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Global search",
               description = "Cross-entity search across patients, alerts, care plans, predictions, and reports. " +
                             "ANALYST role receives no patient-scoped PHI results. Min query length: 2.")
    public ResponseEntity<List<SearchResultResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(defaultValue = "4") int limit,
            Authentication auth) {

        // Extract caller role from Spring Security authorities
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("CLINICIAN");

        return ResponseEntity.ok(searchService.search(q, categories, limit, role));
    }
}

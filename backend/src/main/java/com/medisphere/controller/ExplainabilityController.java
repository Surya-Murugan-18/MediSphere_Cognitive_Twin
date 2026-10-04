package com.medisphere.controller;

import com.medisphere.dto.response.ShapExplanationResponse;
import com.medisphere.service.ExplainabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for SHAP prediction explainability.
 * Serves the Explainability page.
 */
@RestController
@RequestMapping("/api/explainability")
@Tag(name = "Explainability", description = "SHAP feature attribution for AI predictions")
public class ExplainabilityController {

    private final ExplainabilityService explainabilityService;

    public ExplainabilityController(ExplainabilityService explainabilityService) {
        this.explainabilityService = explainabilityService;
    }

    // ── GET /api/explainability/{predictionId} ────────────────────────────

    @GetMapping("/{predictionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Get full SHAP explanation",
        description = "Returns the prediction, SHAP factors, clinical evidence, " +
                      "patient summary, and natural-language explanation for a prediction ID"
    )
    public ResponseEntity<ShapExplanationResponse> getExplanation(
            @PathVariable String predictionId,
            Authentication auth) {
        return ResponseEntity.ok(
                explainabilityService.getExplanation(
                        predictionId, (String) auth.getPrincipal()));
    }
}

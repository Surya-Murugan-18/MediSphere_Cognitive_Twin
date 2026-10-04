package com.medisphere.controller;

import com.medisphere.dto.request.RunPredictionRequest;
import com.medisphere.dto.response.PageResponse;
import com.medisphere.dto.response.PredictionResponse;
import com.medisphere.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI risk predictions.
 * All endpoints require authentication; role restrictions applied per design.md.
 */
@RestController
@RequestMapping("/api/predictions")
@Tag(name = "Predictions", description = "AI risk prediction endpoints")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    // ── GET /api/predictions ──────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List predictions", description = "Paginated, filtered prediction list")
    public ResponseEntity<PageResponse<PredictionResponse>> getPredictions(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                predictionService.getPredictions(patientId, category, model, page, size));
    }

    // ── GET /api/predictions/stats ────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Prediction stats", description = "KPI statistics for the Predictions page")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(predictionService.getStats());
    }

    // ── GET /api/predictions/risk-distribution ────────────────────────────

    @GetMapping("/risk-distribution")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Risk distribution", description = "High/Medium/Low counts for the donut chart")
    public ResponseEntity<List<Map<String, Object>>> getRiskDistribution() {
        return ResponseEntity.ok(predictionService.getRiskDistribution());
    }

    // ── GET /api/predictions/{id} ─────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get prediction by ID")
    public ResponseEntity<PredictionResponse> getPrediction(
            @PathVariable String id,
            Authentication auth) {
        return ResponseEntity.ok(
                predictionService.getPrediction(id, (String) auth.getPrincipal()));
    }

    // ── GET /api/predictions/{id}/shap ────────────────────────────────────

    @GetMapping("/{id}/shap")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SHAP factors for a prediction")
    public ResponseEntity<List<PredictionResponse.ShapFactorResponse>> getShapFactors(
            @PathVariable String id,
            Authentication auth) {
        PredictionResponse prediction =
                predictionService.getPrediction(id, (String) auth.getPrincipal());
        return ResponseEntity.ok(prediction.shapFactors());
    }

    // ── POST /api/predictions/run ─────────────────────────────────────────

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(summary = "Trigger prediction run",
               description = "Run one or all AI models for a patient and persist results")
    public ResponseEntity<List<PredictionResponse>> runPredictions(
            @Valid @RequestBody RunPredictionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                predictionService.runPredictionsSync(
                        request.patientId(), request.model(),
                        (String) auth.getPrincipal()));
    }
}

package com.medisphere.ai.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps the ShapFactor JSON returned by the FastAPI ML service.
 *
 * FastAPI schema (cvd_schema.py):
 *   feature      : str
 *   value        : str   (display string e.g. "8.2%")
 *   contribution : float
 *   direction    : str   ("increases" | "decreases")
 */
public record MlShapFactor(
        @JsonProperty("feature")      String feature,
        @JsonProperty("value")        String value,
        @JsonProperty("contribution") double contribution,
        @JsonProperty("direction")    String direction
) {}

package com.medisphere.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/providers/me
 *
 * Only name, specialty, and facility are editable.
 * Provider ID is read-only (FR-SET-02).
 * All fields are optional — only non-null values are applied.
 */
public record UpdateProviderRequest(
        @Size(min = 2, max = 200) String name,
        @Size(max = 100) String specialty,
        @Size(max = 200) String facility
) {}

package com.medisphere.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PUT /api/care-plans/{id}
 *
 * All fields are optional — only supplied fields are updated.
 * Immutable fields (patientId, providerId, generatedBy, status) are ignored.
 * Per tasks.md B6.3 updateCarePlan.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCarePlanRequest {

    @Size(max = 500, message = "goal must not exceed 500 characters")
    private String goal;

    /** High | Medium | Low */
    private String riskLevel;

    /** Free-text notes about the edit — appended to plan history. */
    private String editNotes;
}

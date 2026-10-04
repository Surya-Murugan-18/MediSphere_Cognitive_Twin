package com.medisphere.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/care-plans/{id}/approve
 * Notes field is optional per design.md §10.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveCarePlanRequest {

    /** Optional approval notes recorded in the plan. */
    private String notes;
}

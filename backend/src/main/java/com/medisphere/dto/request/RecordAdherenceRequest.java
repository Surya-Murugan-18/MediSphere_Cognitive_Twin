package com.medisphere.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/care-plans/{id}/adherence
 * Records a weekly adherence entry per the three required dimensions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordAdherenceRequest {

    @NotNull
    @Min(1)
    private Integer weekNumber;

    @NotNull
    @Min(0) @Max(100)
    private Integer medicationAdherence;

    @NotNull
    @Min(0) @Max(100)
    private Integer monitoringAdherence;

    @NotNull
    @Min(0) @Max(100)
    private Integer followUpAdherence;
}

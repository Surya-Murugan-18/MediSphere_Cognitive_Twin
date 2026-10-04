package com.medisphere.dto.response;

import com.medisphere.domain.LabResult;

/**
 * Laboratory result response — used by GET /api/patients/{id}/labs
 */
public record LabResultResponse(
        String id,
        String patientId,
        String loinc,
        String test,
        String result,
        double numeric,
        String unit,
        String referenceRange,
        String status,
        String category,
        String date,
        String trend,
        String previous,
        String significance
) {
    public static LabResultResponse from(LabResult r) {
        return new LabResultResponse(
                r.getId(),
                r.getPatientId(),
                r.getLoinc(),
                r.getTest(),
                r.getResult(),
                r.getNumeric(),
                r.getUnit(),
                r.getReferenceRange(),
                r.getStatus(),
                r.getCategory(),
                r.getDate(),
                r.getTrend(),
                r.getPrevious(),
                r.getSignificance()
        );
    }
}

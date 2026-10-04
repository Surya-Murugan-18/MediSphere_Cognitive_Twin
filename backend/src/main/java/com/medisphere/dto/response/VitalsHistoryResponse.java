package com.medisphere.dto.response;

import com.medisphere.domain.VitalsTimeSeries;

import java.time.Instant;
import java.util.List;

/**
 * Vitals history response — used by GET /api/patients/{id}/vitals/history
 */
public record VitalsHistoryResponse(
        String patientId,
        String type,
        String period,
        List<DataPoint> data
) {
    public record DataPoint(String t, double value, Instant timestamp) {
        public static DataPoint from(VitalsTimeSeries ts, String label) {
            return new DataPoint(label, ts.getValue(), ts.getTimestamp());
        }
    }
}

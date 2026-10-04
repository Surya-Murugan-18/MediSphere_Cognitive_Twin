package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Historical vital sign time-series record.
 * Collection: vitals_timeseries
 *
 * Each reading produces one document. Queries use patientId + type + timestamp range.
 * Use separate from VitalsSnapshot — this is append-only historical data.
 *
 * Supported types: heartRate | bloodPressureSystolic | bloodPressureDiastolic | spo2 | temperature | respiratoryRate
 */
@Document(collection = "vitals_timeseries")
@CompoundIndexes({
    @CompoundIndex(name = "patient_type_time_idx", def = "{'patientId':1,'type':1,'timestamp':-1}"),
    @CompoundIndex(name = "patient_time_idx",       def = "{'patientId':1,'timestamp':-1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsTimeSeries {

    @Id
    private String id;

    private String patientId;

    /**
     * Vital type: heartRate | bloodPressureSystolic | bloodPressureDiastolic
     *            | spo2 | temperature | respiratoryRate
     */
    private String type;

    private double value;

    /** Unit: BPM | mmHg | % | °C | /min */
    private String unit;

    private Instant timestamp;

    /** wearable | manual | kafka | seed */
    @Builder.Default
    private String source = "wearable";

    private String deviceId;
}

package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Current vital sign snapshot — one document per patient, upserted in-place.
 * Collection: vitals_snapshots
 *
 * This is the "latest reading" document. Historical time-series data is stored
 * separately in VitalsTimeSeries.
 */
@Document(collection = "vitals_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalsSnapshot {

    /** Patient ID — used as the document _id so upsert is by-patient */
    @Id
    private String patientId;

    /** Heart rate in BPM */
    @Builder.Default
    private int heartRate = 0;

    /** Blood pressure as "systolic/diastolic" e.g. "120/80" */
    @Builder.Default
    private String bloodPressure = "—";

    /** Peripheral oxygen saturation in % */
    @Builder.Default
    private double spo2 = 0.0;

    /** Body temperature in °C */
    @Builder.Default
    private double temperature = 0.0;

    /** Breaths per minute */
    @Builder.Default
    private int respiratoryRate = 0;

    /** Source of the reading: wearable | manual | kafka */
    @Builder.Default
    private String source = "wearable";

    /** Device ID that produced this reading */
    private String deviceId;

    @LastModifiedDate
    private Instant updatedAt;
}

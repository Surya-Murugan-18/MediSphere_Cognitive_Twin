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
import java.util.ArrayList;
import java.util.List;

/**
 * Digital Health Twin document — one per patient.
 * Collection: health_twins
 *
 * Body regions represent current risk concentrations across 5 organ systems.
 * Timeline is an ordered list of state-change events (newest appended last).
 * Data source flags track which ingestion channels have contributed recently.
 */
@Document(collection = "health_twins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthTwin {

    /** Format: HT-001, HT-002 … */
    @Id
    private String id;

    @Indexed(unique = true)
    private String patientId;

    @Builder.Default
    private String modelVersion = "v2.1";

    /** 0–100 percentage of expected data sources that have contributed recently */
    @Builder.Default
    private int completeness = 0;

    /** Synchronized | Syncing | Stale | Not Created */
    @Builder.Default
    private String status = "Syncing";

    private Instant lastUpdated;

    @LastModifiedDate
    private Instant updatedAt;

    /** Monotonically increasing counter; increments on every twin state change */
    @Builder.Default
    private long stateVersion = 1L;

    /** Data source connectivity and freshness flags */
    @Builder.Default
    private DataSources dataSources = DataSources.defaultSources();

    /** Per-organ-system risk region entries for the BodyMap */
    @Builder.Default
    private List<BodyRegion> bodyRegions = defaultBodyRegions();

    /** Chronological list of state-change events (newest at end) */
    @Builder.Default
    private List<TimelineEvent> timeline = new ArrayList<>();

    // ── Default body regions (placeholder until Phase 4 populates from AI) ─

    public static List<BodyRegion> defaultBodyRegions() {
        return List.of(
            new BodyRegion("cardiac",      "Cardiac",                    "Awaiting risk prediction data",        "low"),
            new BodyRegion("vascular",     "Vascular / Blood Pressure",  "Awaiting vital sign data",             "low"),
            new BodyRegion("metabolic",    "Metabolic",                  "Awaiting lab result data",             "low"),
            new BodyRegion("renal",        "Renal",                      "Awaiting lab result data",             "low"),
            new BodyRegion("respiratory",  "Respiratory",                "Awaiting wearable/vital sign data",    "low")
        );
    }

    // ── Embedded types ─────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BodyRegion {
        private String region;   // cardiac | vascular | metabolic | renal | respiratory
        private String label;    // display name
        private String detail;   // clinical detail text
        private String riskLevel; // high | medium | low
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSources {
        private DataSourceEntry ehr;
        private DataSourceEntry lab;
        private DataSourceEntry wearable;
        private DataSourceEntry kafka;

        public static DataSources defaultSources() {
            return DataSources.builder()
                .ehr(new DataSourceEntry(false, null))
                .lab(new DataSourceEntry(false, null))
                .wearable(new DataSourceEntry(false, null))
                .kafka(new DataSourceEntry(false, null))
                .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceEntry {
        private boolean connected;
        private Instant lastSync;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private String id;
        private Instant timestamp;
        private String title;
        private String detail;
        private String tone;   // healthy | warning | critical | info | neutral
    }
}

package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Patient consent document.
 * Collection: consents
 *
 * Phase 2 creates a minimal consent record during patient creation.
 * Full consent management (history, updates, UI wiring) is implemented in Phase 7.
 */
@Document(collection = "consents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String patientId;

    @Builder.Default
    private boolean ehr = false;

    @Builder.Default
    private boolean wearable = false;

    @Builder.Default
    private boolean ai = false;

    private Instant updatedAt;

    private String updatedBy;

    @Builder.Default
    private List<ConsentHistoryEntry> history = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentHistoryEntry {
        private String id;
        private Instant date;
        private String type;        // EHR Data Access | Wearable Data Access | AI Risk Analysis
        private String status;      // Granted | Declined
        private String updatedBy;
    }
}

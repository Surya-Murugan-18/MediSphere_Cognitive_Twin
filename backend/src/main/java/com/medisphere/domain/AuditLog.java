package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Immutable clinical audit log entry.
 * Collection: audit_logs
 *
 * IMPORTANT: No delete or update operations are ever permitted on this collection.
 * Entries are written once and never modified.
 */
@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    @Indexed
    private String userId;

    private String userName;

    private String userRole;

    private String action;

    @Indexed
    private String patientId;

    private String patientName;

    @Indexed
    private String module;

    /** "Success" or "Denied" */
    private String status;

    private String ipAddress;

    private String sessionId;
}

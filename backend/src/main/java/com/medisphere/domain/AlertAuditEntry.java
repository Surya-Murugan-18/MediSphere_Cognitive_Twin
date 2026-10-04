package com.medisphere.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Embedded audit-trail entry inside an Alert document.
 *
 * Every status change (creation, acknowledge, escalate, resolve)
 * appends one entry here AND writes to the global audit_logs collection.
 *
 * Per design.md §4.8 auditTrail structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertAuditEntry {
    private String id;
    private Instant timestamp;
    private String actor;       // provider display name or "MediSphere Stream Processor"
    private String actorId;     // provider ID or "system"
    private String action;
}

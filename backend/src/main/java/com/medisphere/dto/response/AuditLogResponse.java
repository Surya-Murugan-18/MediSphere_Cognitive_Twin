package com.medisphere.dto.response;

import com.medisphere.domain.AuditLog;

import java.time.Instant;

/**
 * Response DTO for audit log entries.
 * Used by GET /api/audit and GET /api/audit/export
 */
public record AuditLogResponse(
        String id,
        Instant timestamp,
        String userId,
        String userName,
        String userRole,
        String action,
        String patientId,
        String patientName,
        String module,
        String status,
        String ipAddress
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getTimestamp(),
                log.getUserId(),
                log.getUserName(),
                log.getUserRole(),
                log.getAction(),
                log.getPatientId(),
                log.getPatientName(),
                log.getModule(),
                log.getStatus(),
                log.getIpAddress()
        );
    }
}

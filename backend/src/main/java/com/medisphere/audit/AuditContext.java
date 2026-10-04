package com.medisphere.audit;

import lombok.Builder;

/**
 * Value object carrying the context needed to write one audit log entry.
 * Build this in service methods or via AuditAspect and pass to AuditService.log().
 */
@Builder
public record AuditContext(
        String userId,
        String userName,
        String userRole,
        String action,
        String module,
        String patientId,
        String patientName,
        String status,        // "Success" | "Denied"
        String ipAddress,
        String sessionId
) {
    public static AuditContext success(String userId, String userName, String userRole,
                                       String action, String module) {
        return AuditContext.builder()
                .userId(userId)
                .userName(userName)
                .userRole(userRole)
                .action(action)
                .module(module)
                .status("Success")
                .build();
    }

    public static AuditContext denied(String userId, String userName, String userRole,
                                      String action, String module) {
        return AuditContext.builder()
                .userId(userId)
                .userName(userName)
                .userRole(userRole)
                .action(action)
                .module(module)
                .status("Denied")
                .build();
    }
}

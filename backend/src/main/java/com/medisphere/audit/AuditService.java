package com.medisphere.audit;

import com.medisphere.domain.AuditLog;
import com.medisphere.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes immutable audit log entries to MongoDB.
 * Runs asynchronously so audit logging never blocks the main request thread.
 *
 * Conditional on medisphere.audit.enabled=true (default true).
 */
@Service
@ConditionalOnProperty(name = "medisphere.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persist an audit log entry asynchronously.
     * The audit entry is write-only — never modified after creation.
     */
    @Async
    public void log(AuditContext ctx) {
        try {
            AuditLog entry = AuditLog.builder()
                    .id("AU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .timestamp(Instant.now())
                    .userId(ctx.userId())
                    .userName(ctx.userName())
                    .userRole(ctx.userRole())
                    .action(ctx.action())
                    .module(ctx.module())
                    .patientId(ctx.patientId())
                    .patientName(ctx.patientName())
                    .status(ctx.status())
                    .ipAddress(ctx.ipAddress())
                    .sessionId(ctx.sessionId())
                    .build();

            auditLogRepository.save(entry);

        } catch (Exception e) {
            // Audit failure must never crash the main application flow
            log.error("Failed to write audit log entry for action '{}' by '{}': {}",
                    ctx.action(), ctx.userId(), e.getMessage(), e);
        }
    }
}

package com.medisphere.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Alert REST API response DTO.
 * Maps directly to the frontend Alert type in src/types/clinical.ts.
 */
public record AlertResponse(
        String id,
        String severity,
        String patientId,
        String patientName,
        String event,
        String analysis,
        String type,
        String ruleCode,
        Instant detectedAt,
        String status,
        String assignedProvider,
        String currentValue,
        String previousValue,
        int confidence,
        // Lifecycle timestamps
        Instant acknowledgedAt,
        String acknowledgedBy,
        String acknowledgeNotes,
        Instant escalatedAt,
        String escalatedBy,
        String escalationReason,
        String escalatedTo,
        Instant resolvedAt,
        String resolvedBy,
        String resolution,
        // Embedded audit trail
        List<AlertAuditEntryResponse> auditTrail,
        Instant createdAt
) {}

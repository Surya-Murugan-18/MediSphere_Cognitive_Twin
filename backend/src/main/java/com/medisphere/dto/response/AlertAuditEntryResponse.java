package com.medisphere.dto.response;

import java.time.Instant;

public record AlertAuditEntryResponse(
        String id,
        Instant timestamp,
        String actor,
        String actorId,
        String action
) {}

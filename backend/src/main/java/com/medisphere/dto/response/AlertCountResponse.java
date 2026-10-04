package com.medisphere.dto.response;

/** Response for GET /api/alerts/count?status=Unacknowledged */
public record AlertCountResponse(long count) {}

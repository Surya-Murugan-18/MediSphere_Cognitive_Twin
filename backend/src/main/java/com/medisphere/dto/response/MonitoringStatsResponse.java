package com.medisphere.dto.response;

/**
 * Response for GET /api/monitoring/stats
 *
 * alertsToday     — count of alerts detected since midnight UTC
 * wearablesOnline — count of wearable devices with status=Online
 * avgResponseMin  — average minutes between alert creation and acknowledgement
 * streamStatus    — human-readable stream status string
 */
public record MonitoringStatsResponse(
        long alertsToday,
        long wearablesOnline,
        double avgResponseMin,
        String streamStatus
) {}

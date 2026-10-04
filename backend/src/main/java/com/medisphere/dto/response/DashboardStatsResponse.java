package com.medisphere.dto.response;

/**
 * Single-call response for GET /api/dashboard/stats
 *
 * Contains all 8 KPI card values required by Dashboard.tsx.
 * Aggregated from multiple service/repository sources so the frontend
 * makes one request instead of eight.
 *
 * Per tasks.md B7.8 and the 8-KPI mapping in the pre-flight analysis.
 */
public record DashboardStatsResponse(

        // Row 1 — Primary KPIs
        /** Total patients onboarded */
        long patientsOnboarded,

        /** Total FHIR resources (lab results + vitals time-series approximation) */
        long fhirResources,

        /** Unacknowledged alert count */
        long activeAlerts,

        /** Wearable devices currently online */
        long wearablesOnline,

        // Row 2 — Secondary KPIs
        /** Patients with riskLevel = High */
        long highRiskPatients,

        /** Active (ACTIVE status) care plans */
        long activePlans,

        /** Average care-plan adherence across all active plans (0–100) */
        double avgAdherence,

        /** Average alert acknowledgement time in minutes */
        double avgAlertResponseMin,

        // Supplementary metadata
        /** Wearable devices currently offline */
        long wearablesOffline,

        /** Care plans awaiting approval (status = DRAFT) */
        long plansAwaitingApproval
) {}

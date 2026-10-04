package com.medisphere.domain;

/**
 * Roles available in MediSphere.
 * Each role maps to a Spring Security authority: ROLE_ADMIN, ROLE_CLINICIAN, etc.
 */
public enum ProviderRole {
    ADMIN,
    CLINICIAN,
    NURSE,
    ANALYST
}

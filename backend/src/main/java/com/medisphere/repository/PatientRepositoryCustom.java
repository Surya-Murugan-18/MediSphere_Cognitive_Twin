package com.medisphere.repository;

import com.medisphere.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientRepositoryCustom {

    Page<Patient> searchPatients(
            String search,
            String riskLevel,
            String condition,
            String status,
            String providerName,
            Pageable pageable
    );
}
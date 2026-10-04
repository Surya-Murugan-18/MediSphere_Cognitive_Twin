package com.medisphere.repository;

import com.medisphere.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface AlertRepositoryCustom {

    Page<Alert> findFiltered(
            String severity,
            String status,
            String patientId,
            String type,
            Instant from,
            Instant to,
            Pageable pageable
    );
}
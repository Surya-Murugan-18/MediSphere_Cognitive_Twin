package com.medisphere.repository;

import com.medisphere.domain.WearableDevice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WearableDeviceRepository extends MongoRepository<WearableDevice, String> {

    Optional<WearableDevice> findByPatientId(String patientId);

    long countByStatus(String status);
}

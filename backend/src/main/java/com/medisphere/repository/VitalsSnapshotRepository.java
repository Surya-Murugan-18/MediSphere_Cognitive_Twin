package com.medisphere.repository;

import com.medisphere.domain.VitalsSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VitalsSnapshotRepository extends MongoRepository<VitalsSnapshot, String> {

    /** Patient ID is the document _id — this is an alias for findById */
    default Optional<VitalsSnapshot> findByPatientId(String patientId) {
        return findById(patientId);
    }
}

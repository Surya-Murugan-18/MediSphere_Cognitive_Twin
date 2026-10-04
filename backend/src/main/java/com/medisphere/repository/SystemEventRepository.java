package com.medisphere.repository;

import com.medisphere.domain.SystemEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemEventRepository extends MongoRepository<SystemEvent, String> {

    /** Recent events, newest first */
    List<SystemEvent> findAllByOrderByTimestampDesc(Pageable pageable);
}

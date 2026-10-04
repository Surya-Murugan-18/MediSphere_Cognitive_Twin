package com.medisphere.repository;

import com.medisphere.domain.ReportJob;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportJobRepository extends MongoRepository<ReportJob, String> {

    /** Most recent job for a given reportId */
    Optional<ReportJob> findTopByReportIdOrderByRequestedAtDesc(String reportId);

    /** All jobs for a report, newest first */
    List<ReportJob> findByReportId(String reportId, Sort sort);
}

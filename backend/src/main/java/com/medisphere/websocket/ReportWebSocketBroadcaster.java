package com.medisphere.websocket;

import com.medisphere.domain.ReportJob;
import com.medisphere.dto.response.ReportJobStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts report generation completion/failure to WebSocket subscribers.
 *
 * Per design.md §8.1 and tasks.md B7.4:
 *   Destination: /topic/reports.{reportId}
 *
 * Inconsistency note (from pre-flight analysis):
 *   tasks.md B7.4 (backend) says {reportId} — this implementation follows B7.4.
 *   The frontend (F7.5) subscribes to /topic/reports.{reportId} using the reportId
 *   known at the time of the Generate button click (not the jobId).
 *   This is the correct, consistent contract.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push report job completion or failure to subscribers.
     *
     * @param job the completed (or failed) ReportJob
     */
    public void broadcastJobUpdate(ReportJob job) {
        try {
            ReportJobStatusResponse payload = ReportJobStatusResponse.from(job);
            String destination = "/topic/reports." + job.getReportId();
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("WS report broadcast — reportId={} status={} destination={}",
                    job.getReportId(), job.getStatus(), destination);
        } catch (Exception e) {
            log.warn("Report WS broadcast failed for job {}: {}", job.getId(), e.getMessage());
        }
    }
}

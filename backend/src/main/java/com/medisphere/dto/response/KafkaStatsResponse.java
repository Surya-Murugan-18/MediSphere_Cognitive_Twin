package com.medisphere.dto.response;

import java.util.List;

/**
 * Response for GET /api/monitoring/kafka-stats
 *
 * eventsPerSec  — rolling approximation of events per second (in-memory counter)
 * consumerLag   — simplified consumer lag indicator
 * latestEvents  — tail of recent Kafka events for the stream panel
 */
public record KafkaStatsResponse(
        double eventsPerSec,
        String consumerLag,
        List<KafkaEventEntry> latestEvents
) {
    public record KafkaEventEntry(
            String id,
            String time,
            String topic,
            String text
    ) {}
}

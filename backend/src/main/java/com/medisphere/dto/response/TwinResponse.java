package com.medisphere.dto.response;

import com.medisphere.domain.HealthTwin;

import java.time.Instant;
import java.util.List;

/**
 * Full digital health twin response.
 * Used by GET /api/twins/{twinId} and derived sub-endpoints.
 */
public record TwinResponse(
        String id,
        String patientId,
        String modelVersion,
        int completeness,
        String status,
        Instant lastUpdated,
        long stateVersion,
        DataSourcesResponse dataSources,
        List<BodyRegionResponse> bodyRegions,
        List<TimelineEventResponse> timeline
) {
    public static TwinResponse from(HealthTwin twin) {
        return new TwinResponse(
                twin.getId(),
                twin.getPatientId(),
                twin.getModelVersion(),
                twin.getCompleteness(),
                twin.getStatus(),
                twin.getLastUpdated(),
                twin.getStateVersion(),
                DataSourcesResponse.from(twin.getDataSources()),
                twin.getBodyRegions() == null ? List.of() :
                        twin.getBodyRegions().stream().map(BodyRegionResponse::from).toList(),
                twin.getTimeline() == null ? List.of() :
                        twin.getTimeline().stream().map(TimelineEventResponse::from).toList()
        );
    }

    public record BodyRegionResponse(String region, String label, String detail, String riskLevel) {
        public static BodyRegionResponse from(HealthTwin.BodyRegion r) {
            return new BodyRegionResponse(r.getRegion(), r.getLabel(), r.getDetail(), r.getRiskLevel());
        }
    }

    public record DataSourcesResponse(
            DataSourceEntryResponse ehr,
            DataSourceEntryResponse lab,
            DataSourceEntryResponse wearable,
            DataSourceEntryResponse kafka
    ) {
        public static DataSourcesResponse from(HealthTwin.DataSources ds) {
            if (ds == null) {
                var empty = new DataSourceEntryResponse(false, null);
                return new DataSourcesResponse(empty, empty, empty, empty);
            }
            return new DataSourcesResponse(
                    DataSourceEntryResponse.from(ds.getEhr()),
                    DataSourceEntryResponse.from(ds.getLab()),
                    DataSourceEntryResponse.from(ds.getWearable()),
                    DataSourceEntryResponse.from(ds.getKafka())
            );
        }
    }

    public record DataSourceEntryResponse(boolean connected, Instant lastSync) {
        public static DataSourceEntryResponse from(HealthTwin.DataSourceEntry entry) {
            if (entry == null) return new DataSourceEntryResponse(false, null);
            return new DataSourceEntryResponse(entry.isConnected(), entry.getLastSync());
        }
    }

    public record TimelineEventResponse(String id, Instant timestamp, String title, String detail, String tone) {
        public static TimelineEventResponse from(HealthTwin.TimelineEvent e) {
            return new TimelineEventResponse(e.getId(), e.getTimestamp(), e.getTitle(), e.getDetail(), e.getTone());
        }
    }
}

package com.nbamanager.web.dto;

import com.nbamanager.domain.SyncJob;
import java.time.LocalDateTime;

public record SyncJobResponse(
        Long jobId,
        String module,
        String triggerType,
        String status,
        int progress,
        String stage,
        String requestedBy,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime heartbeatAt,
        LocalDateTime dataAsOf,
        String source,
        int fetchedCount,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        String errorCode,
        String errorMessage,
        boolean cancelRequested,
        boolean reused
) {
    public static SyncJobResponse from(SyncJob job) {
        return new SyncJobResponse(
                job.getId(), job.getModule(), job.getTriggerType(), job.getStatus(),
                job.getProgress(), job.getStage(), job.getRequestedBy(), job.getCreatedAt(),
                job.getStartedAt(), job.getFinishedAt(), job.getHeartbeatAt(),
                job.getDataAsOf(), job.getSource(),
                job.getFetchedCount(), job.getInsertedCount(), job.getUpdatedCount(),
                job.getSkippedCount(), job.getErrorCode(), job.getErrorMessage(),
                job.isCancelRequested(), false
        );
    }

    public static SyncJobResponse from(SyncJob job, boolean reused) {
        SyncJobResponse response = from(job);
        return new SyncJobResponse(
                response.jobId(), response.module(), response.triggerType(), response.status(),
                response.progress(), response.stage(), response.requestedBy(), response.createdAt(),
                response.startedAt(), response.finishedAt(), response.heartbeatAt(),
                response.dataAsOf(), response.source(),
                response.fetchedCount(), response.insertedCount(), response.updatedCount(),
                response.skippedCount(), response.errorCode(), response.errorMessage(),
                response.cancelRequested(), reused
        );
    }
}

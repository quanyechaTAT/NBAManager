package com.nbamanager.web.dto;

import com.nbamanager.domain.SyncJob;
import java.time.LocalDateTime;
import java.util.List;

public record SyncJobDetailResponse(
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
        boolean reused,
        List<SyncJobStepResponse> steps
) {
    public static SyncJobDetailResponse from(SyncJob job, List<SyncJobStepResponse> steps) {
        return new SyncJobDetailResponse(
                job.getId(), job.getModule(), job.getTriggerType(), job.getStatus(),
                job.getProgress(), job.getStage(), job.getRequestedBy(), job.getCreatedAt(),
                job.getStartedAt(), job.getFinishedAt(), job.getHeartbeatAt(),
                job.getDataAsOf(), job.getSource(),
                job.getFetchedCount(), job.getInsertedCount(), job.getUpdatedCount(),
                job.getSkippedCount(), job.getErrorCode(), job.getErrorMessage(),
                job.isCancelRequested(), false, steps
        );
    }
}

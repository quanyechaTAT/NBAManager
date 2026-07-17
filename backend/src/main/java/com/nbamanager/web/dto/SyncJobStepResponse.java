package com.nbamanager.web.dto;

import com.nbamanager.domain.SyncJobStep;
import java.time.LocalDateTime;

public record SyncJobStepResponse(
        Long id,
        String module,
        int stepOrder,
        String status,
        int progress,
        String stage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int fetchedCount,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        String errorCode,
        String errorMessage
) {
    public static SyncJobStepResponse from(SyncJobStep step) {
        return new SyncJobStepResponse(
                step.getId(), step.getModule(), step.getStepOrder(),
                step.getStatus(), step.getProgress(), step.getStage(),
                step.getStartedAt(), step.getFinishedAt(),
                step.getFetchedCount(), step.getInsertedCount(),
                step.getUpdatedCount(), step.getSkippedCount(),
                step.getErrorCode(), step.getErrorMessage()
        );
    }
}

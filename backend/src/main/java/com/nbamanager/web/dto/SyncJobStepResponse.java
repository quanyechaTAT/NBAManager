package com.nbamanager.web.dto;

import com.nbamanager.domain.SyncJobStep;
import com.nbamanager.domain.SyncJob;
import java.util.Set;
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
    /** A terminated parent cannot leave its unfinished steps looking active forever. */
    public static SyncJobStepResponse from(SyncJobStep step, SyncJob parent) {
        SyncJobStepResponse dto = from(step);
        if (Set.of("QUEUED", "RUNNING", "CANCELLING").contains(parent.getStatus())
                || !Set.of("QUEUED", "RUNNING", "CANCELLING").contains(step.getStatus())) return dto;
        return new SyncJobStepResponse(dto.id(), dto.module(), dto.stepOrder(),
                "CANCELLED".equals(parent.getStatus()) ? "CANCELLED" : "INTERRUPTED",
                dto.progress(), dto.stage(), dto.startedAt(), parent.getFinishedAt(),
                dto.fetchedCount(), dto.insertedCount(), dto.updatedCount(), dto.skippedCount(),
                parent.getErrorCode(), parent.getErrorMessage());
    }

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

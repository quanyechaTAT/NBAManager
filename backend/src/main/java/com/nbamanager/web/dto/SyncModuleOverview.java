package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record SyncModuleOverview(
        String key,
        long count,
        LocalDateTime lastSuccessfulAt,
        SyncJobResponse latestJob,
        SyncJobResponse activeJob,
        SyncJobStepResponse activeFullStep
) {}

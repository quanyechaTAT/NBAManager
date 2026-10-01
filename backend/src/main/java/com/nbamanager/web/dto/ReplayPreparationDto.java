package com.nbamanager.web.dto;

import com.nbamanager.domain.replay.ReplayPageState;
import java.time.LocalDateTime;

public record ReplayPreparationDto(
        String gameId,
        ReplayPageState state,
        String operationId,
        boolean reused,
        Integer retryAfterSeconds,
        LocalDateTime startedAt,
        LocalDateTime leaseExpiresAt,
        ReplayErrorDto error) {
}

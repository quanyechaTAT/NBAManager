package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record ReplayErrorDto(
        String code,
        String message,
        boolean retryable,
        LocalDateTime lastAttemptAt,
        LocalDateTime nextRetryAt) {}

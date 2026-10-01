package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record SyncResult(
        String module,
        String status, // SUCCESS, PARTIAL_FAILED, FAILED
        int fetchedCount,
        int insertedCount,
        int updatedCount,
        int skippedCount,
        LocalDateTime dataAsOf,
        String source,
        String errorCode,
        String errorMessage
) {
    public static SyncResult success(String module, int fetched, int inserted, int updated, int skipped, LocalDateTime dataAsOf, String source) {
        return new SyncResult(module, "SUCCESS", fetched, inserted, updated, skipped, dataAsOf, source, null, null);
    }

    public static SyncResult partialFailed(String module, int fetched, int inserted, int updated, int skipped,
                                           LocalDateTime dataAsOf, String source, String errorCode, String errorMessage) {
        return new SyncResult(module, "PARTIAL_FAILED", fetched, inserted, updated, skipped, dataAsOf, source, errorCode, errorMessage);
    }

    public static SyncResult failed(String module, String errorCode, String errorMessage) {
        return new SyncResult(module, "FAILED", 0, 0, 0, 0, null, null, errorCode, errorMessage);
    }

    public static SyncResult cancelled(String module) {
        return new SyncResult(module, "CANCELLED", 0, 0, 0, 0, null, null,
                "CANCELLED", "任务已取消");
    }
}

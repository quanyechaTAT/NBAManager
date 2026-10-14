package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class DraftSyncWorker extends ModuleSyncWorker {
    private final DraftSyncService draftSyncService;
    public DraftSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, DraftSyncService draftSyncService) {
        super(repo, jobStateService); this.draftSyncService = draftSyncService;
    }
    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 20);
        if (isCancelled(jobId)) return SyncResult.cancelled("DRAFT");
        try {
            int year = LocalDateTime.now().getYear();
            java.util.Map<String, Object> result = draftSyncService.syncDraftYear(year, jobId);
            String status = (String) result.getOrDefault("status", "failed");
            if ("failed".equals(status)) {
                String error = (String) result.getOrDefault("error", "选秀数据同步失败");
                return SyncResult.failed("DRAFT", "SYNC_ERROR", error);
            }
            if ("syncing".equals(status)) {
                return SyncResult.partialFailed("DRAFT", 0, 0, 0, 0, LocalDateTime.now(), "nba_api",
                        "SKIPPED", "选秀同步正在进行中");
            }
            if (isCancelled(jobId)) return SyncResult.cancelled("DRAFT");
            updateProgress(jobId, 90);
            int fetched = ((Number) result.getOrDefault("pickCount", 0)).intValue();
            int upserted = ((Number) result.getOrDefault("upserted", 0)).intValue();
            if ("partial".equals(status)) {
                return SyncResult.partialFailed("DRAFT", fetched, 0, upserted, Math.max(0, fetched - upserted),
                        LocalDateTime.now(), "nba_api", "PARTIAL_DATA", String.valueOf(result.get("error")));
            }
            return SyncResult.success("DRAFT", fetched, 0, upserted, Math.max(0, fetched - upserted),
                    LocalDateTime.now(), "nba_api");
        } catch (Exception e) {
            log.error("选秀同步失败", e);
            return SyncResult.failed("DRAFT", "SYNC_ERROR", e.getMessage());
        }
    }
}

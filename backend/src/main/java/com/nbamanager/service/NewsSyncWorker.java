package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class NewsSyncWorker extends ModuleSyncWorker {
    private final NbaLiveSyncService liveSyncService;
    public NewsSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, NbaLiveSyncService liveSyncService) {
        super(repo, jobStateService); this.liveSyncService = liveSyncService;
    }
    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 5, "STARTING");
        if (isCancelled(jobId)) return SyncResult.cancelled("NEWS");
        SyncExecResult exec;
        try {
            exec = liveSyncService.syncNbaNewsWithResult(
                    jobId, (progress, stage) -> updateProgress(jobId, progress, stage));
        } catch (Exception e) {
            log.error("新闻同步异常", e);
            return SyncResult.failed("NEWS", "SYNC_ERROR", e.getMessage());
        }
        if (isCancelled(jobId)) return SyncResult.cancelled("NEWS");
        updateProgress(jobId, 95, "FINALIZING");
        if (!exec.providerSucceeded()) {
            return SyncResult.failed("NEWS", exec.errorCode(), exec.error());
        }
        if (exec.partial()) {
            return SyncResult.partialFailed("NEWS", exec.fetched(), exec.inserted(), 0,
                    Math.max(0, exec.fetched() - exec.inserted()),
                    LocalDateTime.now(), "multi-provider", exec.errorCode(), exec.error());
        }
        return SyncResult.success("NEWS", exec.fetched(), exec.inserted(), 0,
                Math.max(0, exec.fetched() - exec.inserted()),
                LocalDateTime.now(), "multi-provider");
    }
}

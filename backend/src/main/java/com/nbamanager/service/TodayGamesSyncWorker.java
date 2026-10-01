package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TodayGamesSyncWorker extends ModuleSyncWorker {

    private final NbaLiveSyncService liveSyncService;

    public TodayGamesSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, NbaLiveSyncService liveSyncService) {
        super(repo, jobStateService);
        this.liveSyncService = liveSyncService;
    }

    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 30);
        if (isCancelled(jobId)) return SyncResult.cancelled("TODAY_GAMES");
        SyncExecResult exec;
        try {
            exec = liveSyncService.syncTodayGamesWithResult(jobId);
        } catch (Exception e) {
            log.error("今日比赛同步异常", e);
            return SyncResult.failed("TODAY_GAMES", "SYNC_ERROR", e.getMessage());
        }
        if (isCancelled(jobId)) return SyncResult.cancelled("TODAY_GAMES");
        updateProgress(jobId, 90);
        if (!exec.providerSucceeded()) {
            return SyncResult.failed("TODAY_GAMES", exec.errorCode(), exec.error());
        }
        if (exec.fetched() == 0) {
            return SyncResult.success("TODAY_GAMES", 0, 0, 0, 0, LocalDateTime.now(), "espn");
        }
        return SyncResult.success("TODAY_GAMES", exec.fetched(), exec.inserted(), exec.updated(), 0,
                LocalDateTime.now(), "espn");
    }
}

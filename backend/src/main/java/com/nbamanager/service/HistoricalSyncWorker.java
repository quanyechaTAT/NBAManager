package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class HistoricalSyncWorker extends ModuleSyncWorker {
    private final HistoricalDataSyncService historicalDataSyncService;
    private final SeasonManagementService seasonManagementService;
    public HistoricalSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, HistoricalDataSyncService historicalDataSyncService, SeasonManagementService seasonManagementService) {
        super(repo, jobStateService); this.historicalDataSyncService = historicalDataSyncService; this.seasonManagementService = seasonManagementService;
    }
    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 30);
        if (isCancelled(jobId)) return SyncResult.cancelled("HISTORICAL");
        try {
            String season = seasonManagementService.getCurrentSeason();
            int count = historicalDataSyncService.syncSeasonDataSync(season, jobId);
            if (isCancelled(jobId)) return SyncResult.cancelled("HISTORICAL");
            updateProgress(jobId, 90);
            return SyncResult.success("HISTORICAL", count, 0, count, 0, LocalDateTime.now(), "nba_api");
        } catch (IllegalStateException e) {
            log.warn("历史赛季同步跳过: {}", e.getMessage());
            return SyncResult.partialFailed("HISTORICAL", 0, 0, 0, 0, LocalDateTime.now(), "nba_api",
                    "SKIPPED", e.getMessage());
        } catch (PartialSyncException e) {
            log.warn("历史赛季数据不完整: {}", e.getMessage());
            return SyncResult.partialFailed("HISTORICAL", 0, 0, 0, 0, LocalDateTime.now(), "nba_api",
                    "PARTIAL_DATA", e.getMessage());
        } catch (Exception e) {
            log.error("历史赛季同步失败", e);
            return SyncResult.failed("HISTORICAL", "SYNC_ERROR", e.getMessage());
        }
    }
}

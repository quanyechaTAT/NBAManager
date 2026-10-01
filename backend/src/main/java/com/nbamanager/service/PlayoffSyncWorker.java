package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class PlayoffSyncWorker extends ModuleSyncWorker {
    private final NbaDataService nbaDataService;
    private final SeasonManagementService seasonManagementService;
    public PlayoffSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, NbaDataService nbaDataService, SeasonManagementService seasonManagementService) {
        super(repo, jobStateService); this.nbaDataService = nbaDataService; this.seasonManagementService = seasonManagementService;
    }
    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 20);
        if (isCancelled(jobId)) return SyncResult.cancelled("PLAYOFF");
        try {
            String season = seasonManagementService.getCurrentSeason();
            Map<String, Object> result = nbaDataService.fetchAndImportPlayoffData(season, jobId);
            Integer imported = (Integer) result.get("imported");
            if (imported == null || imported == 0) {
                return SyncResult.partialFailed("PLAYOFF", 0, 0, 0, 0, LocalDateTime.now(), "nba_api",
                        "NO_DATA", "季后赛数据为空");
            }
            if (isCancelled(jobId)) return SyncResult.cancelled("PLAYOFF");
            updateProgress(jobId, 90);
            return SyncResult.success("PLAYOFF", imported, imported, 0, 0, LocalDateTime.now(), "nba_api");
        } catch (Exception e) {
            log.error("季后赛同步失败", e);
            return SyncResult.failed("PLAYOFF", "SYNC_ERROR", e.getMessage());
        }
    }
}

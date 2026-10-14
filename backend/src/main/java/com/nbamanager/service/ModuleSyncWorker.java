package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public abstract class ModuleSyncWorker {

    protected final SyncJobRepository syncJobRepository;
    protected final SyncJobStateService jobStateService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.cache.CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MatchCacheGeneration matchCacheGeneration;

    private final ThreadLocal<ProgressRange> progressRange = new ThreadLocal<>();

    public final SyncResult sync(Long jobId) {
        return sync(jobId, 0, 100);
    }

    public final SyncResult sync(Long jobId, int startProgress, int endProgress) {
        return sync(jobId, startProgress, endProgress, null);
    }

    public final SyncResult sync(Long jobId, int startProgress, int endProgress,
            BiConsumer<Integer, String> stepProgress) {
        progressRange.set(new ProgressRange(startProgress, endProgress, stepProgress));
        try {
            return doSync(jobId);
        } finally {
            progressRange.remove();
            if (matchCacheGeneration != null) matchCacheGeneration.advance();
            // Even partial writes or cancellation invalidate read caches. The final/self call cannot use AOP.
            if (cacheManager != null) for (String name : java.util.List.of("players", "playerList", "playerDetail", "playerCareer", "leagueAverages", "teams", "rankings", "dashboard", "historicalStats", "matchRecord", "matchRecords", "matchRecordsByTeam", "headToHead")) {
                var cache = cacheManager.getCache(name);
                if (cache != null) try { cache.clear(); } catch (RuntimeException ex) { log.warn("Cache invalidation failed for {}", name, ex); }
            }
        }
    }

    protected abstract SyncResult doSync(Long jobId);

    protected void updateProgress(Long jobId, int progress) {
        jobStateService.updateProgress(jobId, mapProgress(progress));
        reportStepProgress(progress, null);
    }

    protected void updateProgress(Long jobId, int progress, String stage) {
        jobStateService.updateProgress(jobId, mapProgress(progress), stage);
        reportStepProgress(progress, stage);
    }

    private int mapProgress(int progress) {
        ProgressRange range = progressRange.get();
        if (range == null) {
            return progress;
        }
        int localProgress = Math.max(0, Math.min(100, progress));
        return range.start() + (localProgress * (range.end() - range.start()) / 100);
    }

    protected boolean isCancelled(Long jobId) {
        return jobStateService.isCancelRequested(jobId);
    }

    private void reportStepProgress(int progress, String stage) {
        ProgressRange range = progressRange.get();
        if (range != null && range.stepProgress() != null)
            range.stepProgress().accept(Math.max(0, Math.min(100, progress)), stage);
    }

    private record ProgressRange(int start, int end, BiConsumer<Integer, String> stepProgress) {}
}

package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public abstract class ModuleSyncWorker {

    protected final SyncJobRepository syncJobRepository;
    protected final SyncJobStateService jobStateService;

    private final ThreadLocal<ProgressRange> progressRange = new ThreadLocal<>();

    public final SyncResult sync(Long jobId) {
        return sync(jobId, 0, 100);
    }

    public final SyncResult sync(Long jobId, int startProgress, int endProgress) {
        progressRange.set(new ProgressRange(startProgress, endProgress));
        try {
            return doSync(jobId);
        } finally {
            progressRange.remove();
        }
    }

    protected abstract SyncResult doSync(Long jobId);

    protected void updateProgress(Long jobId, int progress) {
        jobStateService.updateProgress(jobId, mapProgress(progress));
    }

    protected void updateProgress(Long jobId, int progress, String stage) {
        jobStateService.updateProgress(jobId, mapProgress(progress), stage);
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

    private record ProgressRange(int start, int end) {}
}

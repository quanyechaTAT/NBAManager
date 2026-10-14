package com.nbamanager.service;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncJobStateService {

    private final SyncJobRepository syncJobRepository;

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCESS", "PARTIAL_FAILED", "FAILED", "CANCELLED", "INTERRUPTED"
    );

    @Transactional
    public void markRunning(Long jobId) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return;
        if (TERMINAL_STATUSES.contains(job.getStatus())) return;
        // 已请求取消的 QUEUED 任务不能变为 RUNNING
        if (job.isCancelRequested()) {
            job.setStatus("CANCELLED");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorCode("CANCELLED");
            job.setErrorMessage("任务已取消");
            syncJobRepository.save(job);
            return;
        }
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setHeartbeatAt(LocalDateTime.now());
        syncJobRepository.save(job);
    }

    @Transactional
    public void updateProgress(Long jobId, int progress) {
        updateProgress(jobId, progress, null);
    }

    @Transactional
    public void updateProgress(Long jobId, int progress, String stage) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return;
        // 不更新终态
        if (TERMINAL_STATUSES.contains(job.getStatus())) return;
        // 限制 0-100，单调递增
        int clamped = Math.max(0, Math.min(100, progress));
        if (clamped <= job.getProgress()) return;
        job.setProgress(clamped);
        if (stage != null) job.setStage(stage);
        job.setHeartbeatAt(LocalDateTime.now());
        syncJobRepository.save(job);
    }

    @Transactional
    public void heartbeat(Long jobId) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || TERMINAL_STATUSES.contains(job.getStatus())) return;
        job.setHeartbeatAt(LocalDateTime.now());
        syncJobRepository.save(job);
    }

    @Transactional
    public void complete(Long jobId, SyncResult result) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return;
        // 终态保护
        if (TERMINAL_STATUSES.contains(job.getStatus())) {
            log.warn("任务已处于终态，跳过完成写入: jobId={}, status={}", jobId, job.getStatus());
            return;
        }
        // cancelRequested 优先
        if (job.isCancelRequested()) {
            job.setStatus("CANCELLED");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorCode("CANCELLED");
            job.setErrorMessage("任务已取消");
        } else {
            job.setStatus(result.status());
            job.setErrorCode(result.errorCode());
            job.setErrorMessage(result.errorMessage());
            job.setFinishedAt(LocalDateTime.now());
        }
        job.setDataAsOf(result.dataAsOf());
        job.setSource(result.source());
        job.setFetchedCount(result.fetchedCount());
        job.setInsertedCount(result.insertedCount());
        job.setUpdatedCount(result.updatedCount());
        job.setSkippedCount(result.skippedCount());
        if (!"CANCELLED".equals(job.getStatus())) {
            job.setProgress(100);
        }
        syncJobRepository.save(job);
    }

    @Transactional
    public void markFailed(Long jobId, String errorCode, String errorMessage) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return;
        if (TERMINAL_STATUSES.contains(job.getStatus())) {
            log.warn("任务已处于终态，跳过失败写入: jobId={}, status={}", jobId, job.getStatus());
            return;
        }
        job.setStatus("FAILED");
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        syncJobRepository.save(job);
    }

    @Transactional
    public void markLockLost(Long jobId, String errorMessage) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || TERMINAL_STATUSES.contains(job.getStatus())) return;
        job.setCancelRequested(true);
        job.setStatus("FAILED");
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorCode("LOCK_LOST");
        job.setErrorMessage(errorMessage);
        syncJobRepository.save(job);
    }

    @Transactional
    public void markCancelled(Long jobId) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return;
        if (TERMINAL_STATUSES.contains(job.getStatus())) return;
        job.setStatus("CANCELLED");
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorCode("CANCELLED");
        job.setErrorMessage("任务已取消");
        syncJobRepository.save(job);
    }

    /**
     * 请求取消：QUEUED/RUNNING → CANCELLING；已 CANCELLING 幂等；终态返回 false
     */
    @Transactional
    public boolean requestCancellation(Long jobId) {
        SyncJob job = syncJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null) return false;
        if (TERMINAL_STATUSES.contains(job.getStatus())) return false;
        if ("CANCELLING".equals(job.getStatus())) return true; // 幂等
        job.setCancelRequested(true);
        job.setStatus("CANCELLING");
        syncJobRepository.save(job);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isCancelRequested(Long jobId) {
        SyncJob job = syncJobRepository.findById(jobId).orElse(null);
        return job != null && job.isCancelRequested();
    }
}

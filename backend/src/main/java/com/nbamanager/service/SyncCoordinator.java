package com.nbamanager.service;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.exception.SyncJobConflictException;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncJobResponse;
import com.nbamanager.web.dto.SyncResult;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncCoordinator {

    private final SyncJobRepository syncJobRepository;
    private final SyncJobStateService jobStateService;
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor manualSyncExecutor;
    private final SyncLockService syncLockService;

    // Module workers
    private final TeamSyncWorker teamSyncWorker;
    private final PlayerSyncWorker playerSyncWorker;
    private final MatchSyncWorker matchSyncWorker;
    private final TodayGamesSyncWorker todayGamesSyncWorker;
    private final NewsSyncWorker newsSyncWorker;
    private final DraftSyncWorker draftSyncWorker;
    private final PlayoffSyncWorker playoffSyncWorker;
    private final HistoricalSyncWorker historicalSyncWorker;
    private final TranslationSyncWorker translationSyncWorker;
    private final PythonScriptRunner pythonScriptRunner;

    // Active processes for cancellation
    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    // Task-level lock-lost flags: jobId -> lost
    private final Map<Long, java.util.concurrent.atomic.AtomicBoolean> lockLostFlags = new ConcurrentHashMap<>();
    // Shared watchdog scheduler for lock renewal
    private final java.util.concurrent.ScheduledExecutorService watchdogScheduler =
            java.util.concurrent.Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "lock-watchdog");
                t.setDaemon(true);
                return t;
            });
    // Lock watchdog futures for renewal: lockKey -> future
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> watchdogTasks = new ConcurrentHashMap<>();
    // Reverse mapping: jobId -> list of lockKeys (for cleanup)
    private final Map<Long, java.util.List<String>> jobLockKeys = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Value("${app.sync.stale-job-timeout-seconds:300}")
    private long staleJobTimeoutSeconds = 300;

    public static final List<String> ALL_MODULES = List.of(
            "TEAMS", "PLAYERS", "MATCHES", "TODAY_GAMES", "NEWS",
            "DRAFT", "PLAYOFF", "HISTORICAL", "TRANSLATION"
    );

    @PostConstruct
    public void init() {
        recoverStaleJobs();
        pythonScriptRunner.setProcessRegistrar(this::registerProcess);
        pythonScriptRunner.setProcessUnregistrar(this::unregisterProcess);
        pythonScriptRunner.setCancellationChecker(jobStateService::isCancelRequested);
    }

    @Scheduled(
            fixedDelayString = "${app.sync.recovery-interval-ms:60000}",
            initialDelayString = "${app.sync.recovery-initial-delay-ms:60000}")
    public void recoverStaleJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleJobTimeoutSeconds);
        int interrupted = syncJobRepository.markStaleRunningAsInterrupted(cutoff);
        if (interrupted > 0) {
            log.warn("标记 {} 个心跳超时任务为 INTERRUPTED", interrupted);
        }
    }

    /**
     * Submit a manual sync job for a single module
     */
    public SyncJobResponse submitManual(String module, String requestedBy) {
        return submitJob(module, "MANUAL", requestedBy);
    }

    /**
     * Submit a scheduled sync job
     */
    public SyncJobResponse submitScheduled(String module) {
        return submitJob(module, "SCHEDULED", "scheduler");
    }

    /**
     * Submit a startup catchup job
     */
    public SyncJobResponse submitCatchup(String module) {
        return submitJob(module, "STARTUP_CATCHUP", "startup");
    }

    /**
     * Submit full sync (all modules sequentially)
     */
    public SyncJobResponse submitFullSync(String requestedBy) {
        return submitJob("FULL_SYNC", "MANUAL", requestedBy);
    }

    /**
     * Submit full sync with trigger type
     */
    public SyncSubmission submitFullSync(String triggerType, String requestedBy) {
        // Check for active tasks
        List<String> activeModules = findActiveModules();
        if (!activeModules.isEmpty()) {
            throw new SyncJobConflictException("SYNC_JOB_CONFLICT",
                    "全量同步无法启动，以下模块有活动任务: " + String.join(", ", activeModules));
        }
        SyncJobResponse response = submitJob("FULL_SYNC", triggerType, requestedBy);
        SyncJob job = syncJobRepository.findById(response.jobId()).orElseThrow();
        return new SyncSubmission(job, false);
    }

    /**
     * Unified submit: check for active task reuse, then create new
     */
    public SyncSubmission submit(SyncCommand command) {
        // First check: is there an active task for this module?
        SyncJob active = syncJobRepository.findActiveByModule(command.module()).orElse(null);
        if (active != null) {
            return new SyncSubmission(active, true);
        }

        // Second check: acquire submit gate
        String submitToken = java.util.UUID.randomUUID().toString();
        boolean gateAcquired;
        try {
            gateAcquired = syncLockService.tryAcquire(
                    "nba:sync:submit", submitToken, java.time.Duration.ofSeconds(15));
        } catch (Exception e) {
            throw new SyncJobConflictException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "SYNC_LOCK_UNAVAILABLE", "Redis 不可用，无法获取提交锁");
        }
        if (!gateAcquired) {
            throw new SyncJobConflictException("SYNC_JOB_CONFLICT", "提交锁被占用，请稍后重试");
        }

        try {
            // Double-check after acquiring gate
            SyncJob secondCheck = syncJobRepository.findActiveByModule(command.module()).orElse(null);
            if (secondCheck != null) {
                return new SyncSubmission(secondCheck, true);
            }

            // Check for full sync conflicts
            ensureNoActiveFullSync();

            // Create and dispatch
            SyncJobResponse response = submitJob(command.module(), command.triggerType(), command.requestedBy());
            SyncJob job = syncJobRepository.findById(response.jobId()).orElseThrow();
            return new SyncSubmission(job, false);
        } finally {
            syncLockService.release("nba:sync:submit", submitToken);
        }
    }

    private List<String> findActiveModules() {
        return syncJobRepository.findByStatusIn(
                java.util.List.of("QUEUED", "RUNNING", "CANCELLING"))
                .stream()
                .map(SyncJob::getModule)
                .toList();
    }

    private void ensureNoActiveFullSync() {
        List<String> activeModules = findActiveModules();
        if (!activeModules.isEmpty()) {
            throw new SyncJobConflictException("SYNC_JOB_CONFLICT",
                    "存在活动任务，无法启动全量同步: " + String.join(", ", activeModules));
        }
    }

    private SyncJobResponse submitJob(String module, String triggerType, String requestedBy) {
        String globalLockKey = "nba:sync:lock:MANUAL";
        String globalLockUuid = java.util.UUID.randomUUID().toString();
        String moduleLockKey = "nba:sync:lock:" + module;
        String moduleLockUuid = java.util.UUID.randomUUID().toString();
        long lockTtl = "FULL_SYNC".equals(module) ? 120 : 30;

        boolean globalHeld = false;
        boolean moduleHeld = false;
        boolean ownershipTransferred = false;
        Long jobId = null;

        try {
            // 1. 获取全局锁
            Boolean globalAcquired;
            try {
                globalAcquired = syncLockService.tryAcquire(
                        globalLockKey, globalLockUuid, java.time.Duration.ofMinutes(lockTtl));
            } catch (Exception e) {
                throw new SyncJobConflictException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "SYNC_LOCK_UNAVAILABLE", "Redis 不可用，无法获取同步锁");
            }
            if (globalAcquired == null || !globalAcquired) {
                throw new SyncJobConflictException("SYNC_JOB_CONFLICT", "手动同步锁被占用，模块 " + module + " 无法启动");
            }
            globalHeld = true;

            // 2. 获取模块锁
            Boolean moduleAcquired;
            try {
                moduleAcquired = syncLockService.tryAcquire(
                        moduleLockKey, moduleLockUuid, java.time.Duration.ofMinutes(lockTtl));
            } catch (Exception e) {
                throw new SyncJobConflictException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "SYNC_LOCK_UNAVAILABLE", "Redis 不可用，无法获取模块锁");
            }
            if (moduleAcquired == null || !moduleAcquired) {
                throw new SyncJobConflictException("SYNC_JOB_CONFLICT", "模块 " + module + " 已有任务在运行");
            }
            moduleHeld = true;

            // 3. 创建任务记录
            SyncJob job;
            try {
                job = new SyncJob();
                job.setModule(module);
                job.setTriggerType(triggerType);
                job.setStatus("QUEUED");
                job.setRequestedBy(requestedBy);
                syncJobRepository.save(job);
                jobId = job.getId();
            } catch (Exception e) {
                throw new SyncJobConflictException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "SYNC_JOB_PERSIST_FAILED", "创建同步任务失败: " + e.getMessage());
            }

            // 4. 提交线程池
            final Long jid = jobId;
            final String gKey = globalLockKey;
            final String gUuid = globalLockUuid;
            final String mKey = moduleLockKey;
            final String mUuid = moduleLockUuid;
            try {
                manualSyncExecutor.execute(() -> executeJob(jid, module, mKey, mUuid, gKey, gUuid));
            } catch (Exception e) {
                safelyDeleteQueuedJob(jobId);
                throw new SyncJobConflictException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "SYNC_QUEUE_FULL", "同步队列已满: " + e.getMessage());
            }
            ownershipTransferred = true;

            return SyncJobResponse.from(job);

        } catch (SyncJobConflictException e) {
            throw e;
        } catch (Exception e) {
            safelyDeleteQueuedJob(jobId);
            throw e;
        } finally {
            if (!ownershipTransferred) {
                if (moduleHeld) safeDeleteLock(moduleLockKey, moduleLockUuid);
                if (globalHeld) safeDeleteLock(globalLockKey, globalLockUuid);
            }
        }
    }

    private void safelyDeleteQueuedJob(Long jobId) {
        if (jobId == null) return;
        try { syncJobRepository.deleteById(jobId); } catch (Exception ignored) {}
    }

    /** 原子删除锁：仅当值匹配时删除 */
    private void safeDeleteLock(String lockKey, String lockUuid) {
        try {
            syncLockService.release(lockKey, lockUuid);
        } catch (Exception e) {
            log.warn("释放锁失败: key={}", lockKey, e);
        }
    }

    /**
     * 原子续期：仅当值匹配时 PEXPIRE
     * @return true  续期成功（锁值匹配，TTL 已刷新）
     *         false 锁已过期或被其他持有者抢占（锁值不匹配）
     * @throws RuntimeException Redis 不可用（临时故障，不应视为锁丢失）
     */
    private boolean renewLock(String lockKey, String lockUuid, long ttlMillis) {
        try {
            return syncLockService.renew(
                    lockKey, lockUuid, java.time.Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            // Redis 临时不可用 → 向上抛出，由调用方决定是否重试
            throw new RuntimeException("Redis 续期异常: " + e.getMessage(), e);
        }
    }

    private void executeJob(Long jobId, String module, String lockKey, String lockUuid,
                            String globalLockKey, String globalLockUuid) {
        // 启动锁续期 watchdog（每 60 秒续期一次）
        long ttlMs = ("FULL_SYNC".equals(module) ? 120 : 30) * 60 * 1000;
        long renewIntervalMs = 60_000;
        jobLockKeys.put(jobId, java.util.List.of(lockKey, globalLockKey));
        lockLostFlags.put(jobId, new AtomicBoolean(false));
        startLockWatchdog(lockKey, lockUuid, ttlMs, renewIntervalMs, jobId);
        startLockWatchdog(globalLockKey, globalLockUuid, ttlMs, renewIntervalMs, jobId);

        try {
            SyncJob job = syncJobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("任务不存在: jobId={}", jobId);
                return;
            }

            jobStateService.markRunning(jobId);

            // markRunning 可能因 cancelRequested 直接标记为 CANCELLED
            job = syncJobRepository.findById(jobId).orElse(null);
            if (job != null && "CANCELLED".equals(job.getStatus())) {
                return;
            }

            SyncResult result;
            try {
                if ("FULL_SYNC".equals(module)) {
                    result = executeFullSync(jobId);
                } else {
                    result = executeModule(module, jobId);
                }

                // 锁丢失后不覆盖为成功
                AtomicBoolean lockLost = lockLostFlags.get(jobId);
                if (lockLost != null && lockLost.get()) {
                    log.warn("任务已完成但锁已丢失，保持 LOCK_LOST 状态: jobId={}", jobId);
                } else {
                    jobStateService.complete(jobId, result);
                }

            } catch (Exception e) {
                log.error("同步任务异常: module={}, jobId={}", module, jobId, e);
                AtomicBoolean lockLost = lockLostFlags.get(jobId);
                if (lockLost == null || !lockLost.get()) {
                    jobStateService.markFailed(jobId, "EXCEPTION", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("executeJob 顶层异常: module={}, jobId={}", module, jobId, e);
            try { jobStateService.markFailed(jobId, "EXCEPTION", e.getMessage()); } catch (Exception ignored) {}
        } finally {
            if (jobId != null) {
                activeProcesses.remove(jobId);
                lockLostFlags.remove(jobId);
                jobLockKeys.remove(jobId);
            }
            stopLockWatchdog(lockKey);
            stopLockWatchdog(globalLockKey);
            safeDeleteLock(lockKey, lockUuid);
            safeDeleteLock(globalLockKey, globalLockUuid);
        }
    }

    private static final int LOCK_RENEW_MAX_RETRIES = 3;
    private static final long LOCK_RENEW_RETRY_DELAY_MS = 5_000;

    private void startLockWatchdog(String lockKey, String lockUuid, long ttlMs, long intervalMs, Long jobId) {
        java.util.concurrent.ScheduledFuture<?> future = watchdogScheduler.scheduleAtFixedRate(() -> {
            for (int attempt = 1; attempt <= LOCK_RENEW_MAX_RETRIES; attempt++) {
                try {
                    boolean renewed = renewLock(lockKey, lockUuid, ttlMs);
                    if (renewed) {
                        if ("nba:sync:lock:MANUAL".equals(lockKey)) {
                            jobStateService.heartbeat(jobId);
                        }
                        return; // 续期成功，本轮结束
                    }
                    // 锁值不匹配 → 锁已过期或被抢占，无需重试
                    log.error("锁续期失败（锁值不匹配），任务失去互斥保护: key={}, jobId={}", lockKey, jobId);
                    onLockLost(jobId);
                    return;
                } catch (RuntimeException e) {
                    // Redis 临时不可用 → 重试
                    log.warn("锁续期 Redis 异常，第 {}/{} 次重试: key={}, error={}",
                            attempt, LOCK_RENEW_MAX_RETRIES, lockKey, e.getMessage());
                    if (attempt < LOCK_RENEW_MAX_RETRIES) {
                        try { Thread.sleep(LOCK_RENEW_RETRY_DELAY_MS); } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("watchdog 异常: {}", e.getMessage());
                    return;
                }
            }
            // 全部重试失败 → 视为锁丢失
            log.error("锁续期 Redis 异常，{} 次重试均失败，任务失去互斥保护: key={}, jobId={}",
                    LOCK_RENEW_MAX_RETRIES, lockKey, jobId);
            onLockLost(jobId);
        }, intervalMs, intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        watchdogTasks.put(lockKey, future);
    }

    /** 任务级锁丢失处理：设置标志、停止两锁 watchdog、设置取消标记、终止进程、标记失败 */
    private void onLockLost(Long jobId) {
        if (jobId == null) return;
        AtomicBoolean flag = lockLostFlags.get(jobId);
        if (flag == null || !flag.compareAndSet(false, true)) return;

        // 2. 通过 jobId → lockKeys 映射停止两把锁的 watchdog
        java.util.List<String> keys = jobLockKeys.get(jobId);
        if (keys != null) {
            for (String key : keys) {
                java.util.concurrent.ScheduledFuture<?> f = watchdogTasks.remove(key);
                if (f != null) f.cancel(false);
            }
        }

        // 3. 终止该任务的 Python 进程
        Process proc = activeProcesses.get(jobId);
        if (proc != null && proc.isAlive()) {
            log.info("锁丢失，终止 Python 进程: jobId={}", jobId);
            terminateProcess(proc);
        }

        // 4. 原子设置取消标记并标记失败
        try {
            jobStateService.markLockLost(jobId, "锁续期失败，任务已失去互斥保护");
        } catch (Exception e) {
            log.warn("标记任务失败异常: {}", e.getMessage());
        }
    }

    private void stopLockWatchdog(String lockKey) {
        java.util.concurrent.ScheduledFuture<?> future = watchdogTasks.remove(lockKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    private SyncResult executeModule(String module, Long jobId) {
        return executeModule(module, jobId, 0, 100);
    }

    private SyncResult executeModule(String module, Long jobId, int startProgress, int endProgress) {
        if (jobStateService.isCancelRequested(jobId)) {
            return SyncResult.cancelled(module);
        }
        return switch (module) {
            case "TEAMS" -> teamSyncWorker.sync(jobId, startProgress, endProgress);
            case "PLAYERS" -> playerSyncWorker.sync(jobId, startProgress, endProgress);
            case "MATCHES" -> matchSyncWorker.sync(jobId, startProgress, endProgress);
            case "TODAY_GAMES" -> todayGamesSyncWorker.sync(jobId, startProgress, endProgress);
            case "NEWS" -> newsSyncWorker.sync(jobId, startProgress, endProgress);
            case "DRAFT" -> draftSyncWorker.sync(jobId, startProgress, endProgress);
            case "PLAYOFF" -> playoffSyncWorker.sync(jobId, startProgress, endProgress);
            case "HISTORICAL" -> historicalSyncWorker.sync(jobId, startProgress, endProgress);
            case "TRANSLATION" -> translationSyncWorker.sync(jobId, startProgress, endProgress);
            default -> SyncResult.failed(module, "UNKNOWN_MODULE", "未知模块: " + module);
        };
    }

    private SyncResult executeFullSync(Long jobId) {
        String[] modules = {"TEAMS", "PLAYERS", "MATCHES", "TODAY_GAMES", "NEWS", "DRAFT", "PLAYOFF", "HISTORICAL", "TRANSLATION"};
        int totalModules = modules.length;
        int successCount = 0;
        int failedCount = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < totalModules; i++) {
            if (jobStateService.isCancelRequested(jobId)) {
                return SyncResult.cancelled("FULL_SYNC");
            }

            int startProgress = 5 + (i * 90 / totalModules);
            int endProgress = 5 + ((i + 1) * 90 / totalModules);
            SyncResult result = executeModule(modules[i], jobId, startProgress, endProgress);
            if ("CANCELLED".equals(result.status()) || jobStateService.isCancelRequested(jobId)) {
                return SyncResult.cancelled("FULL_SYNC");
            }
            if ("SUCCESS".equals(result.status())) {
                successCount++;
            } else {
                failedCount++;
                if (errors.length() > 0) errors.append("; ");
                errors.append(modules[i]).append(": ").append(result.errorMessage());
            }
            jobStateService.updateProgress(jobId, endProgress);
        }

        LocalDateTime now = LocalDateTime.now();
        if (failedCount == 0) {
            return SyncResult.success("FULL_SYNC", 0, 0, 0, 0, now, "all-modules");
        } else if (successCount > 0) {
            return SyncResult.partialFailed("FULL_SYNC", 0, 0, 0, 0, now, "all-modules",
                    "PARTIAL", errors.toString());
        } else {
            return SyncResult.failed("FULL_SYNC", "ALL_FAILED", errors.toString());
        }
    }

    /**
     * Cancel a running job — atomic CANCELLING via SyncJobStateService
     * Returns: true if accepted, false if job not found or already terminal
     */
    public boolean cancelJob(Long jobId) {
        boolean accepted = jobStateService.requestCancellation(jobId);
        if (!accepted) return false;

        // Terminate active Python process if any
        Process proc = activeProcesses.get(jobId);
        if (proc != null && proc.isAlive()) {
            log.info("终止 Python 进程: jobId={}", jobId);
            terminateProcess(proc);
        }
        return true;
    }

    /**
     * Register an active process for cancellation support
     */
    public void registerProcess(Long jobId, Process process) {
        activeProcesses.put(jobId, process);
    }

    public void unregisterProcess(Long jobId, Process process) {
        activeProcesses.remove(jobId, process);
    }

    private void terminateProcess(Process process) {
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /**
     * Get job by ID
     */
    public SyncJobResponse getJob(Long jobId) {
        return syncJobRepository.findById(jobId).map(SyncJobResponse::from).orElse(null);
    }

    /**
     * Get recent jobs
     */
    public List<SyncJobResponse> getRecentJobs() {
        return syncJobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(SyncJobResponse::from)
                .toList();
    }

    /**
     * Get latest job for a module
     */
    public SyncJobResponse getLatestJob(String module) {
        return syncJobRepository.findTopByModuleOrderByCreatedAtDesc(module)
                .map(SyncJobResponse::from)
                .orElse(null);
    }

    /**
     * Check if a module has a running job
     */
    public boolean isRunning(String module) {
        return syncJobRepository.findActiveByModule(module).isPresent();
    }

    /**
     * Update heartbeat for a running job
     */
    public void heartbeat(Long jobId) {
        syncJobRepository.findById(jobId).ifPresent(job -> {
            job.setHeartbeatAt(LocalDateTime.now());
            syncJobRepository.save(job);
        });
    }
}

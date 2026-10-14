package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncJobResponse;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class SyncCoordinatorTest {

    @Mock private SyncJobRepository syncJobRepository;
    @Mock private SyncJobStateService jobStateService;
    @Mock private SyncJobStepStateService stepStateService;
    @Mock private com.nbamanager.repository.SyncJobStepRepository stepRepository;
    @Mock private ThreadPoolTaskExecutor manualSyncExecutor;
    @Mock private SyncLockService syncLockService;

    @Mock private TeamSyncWorker teamSyncWorker;
    @Mock private PlayerSyncWorker playerSyncWorker;
    @Mock private MatchSyncWorker matchSyncWorker;
    @Mock private TodayGamesSyncWorker todayGamesSyncWorker;
    @Mock private NewsSyncWorker newsSyncWorker;
    @Mock private DraftSyncWorker draftSyncWorker;
    @Mock private PlayoffSyncWorker playoffSyncWorker;
    @Mock private HistoricalSyncWorker historicalSyncWorker;
    @Mock private TranslationSyncWorker translationSyncWorker;
    @Mock private PythonScriptRunner pythonScriptRunner;

    private SyncCoordinator coordinator;

    @BeforeEach
    void setUp() {
        lenient().when(syncLockService.renew(anyString(), anyString(), any())).thenReturn(true);
        coordinator = new SyncCoordinator(
                syncJobRepository, jobStateService, stepStateService, stepRepository,
                manualSyncExecutor, syncLockService,
                teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker,
                playoffSyncWorker, historicalSyncWorker, translationSyncWorker,
                pythonScriptRunner
        );
    }

    // --- 模块锁被占用 ---

    @Test
    void moduleLockHeld_throwsConflict() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(com.nbamanager.exception.SyncJobConflictException.class);

        // 不应创建任务
        verify(syncJobRepository, never()).save(any());
    }

    // --- 模块锁 Redis 抛异常 ---

    @Test
    void moduleLockThrows_throwsConflict() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Redis connection lost"));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(RuntimeException.class);
    }

    // --- save 抛异常 ---

    @Test
    void saveThrows_releasesLock() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(RuntimeException.class);

        // 锁应被释放
        verify(syncLockService).release(anyString(), anyString());
    }

    // --- Executor 拒绝任务 ---

    @Test
    void executorRejects_releasesLockAndDeletesJob() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(42L);
            return j;
        });
        doThrow(new RuntimeException("queue full")).when(manualSyncExecutor).execute(any(Runnable.class));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("queue full");

        // 锁释放
        verify(syncLockService).release(anyString(), anyString());
        // 任务删除
        verify(syncJobRepository).deleteById(42L);
    }

    // --- Executor 成功接收 — 提交线程不释放锁 ---

    @Test
    void executorAccepts_submitThreadDoesNotReleaseLock() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        SyncJob job = new SyncJob();
        job.setId(1L);
        when(syncJobRepository.save(any(SyncJob.class))).thenReturn(job);

        coordinator.submitManual("TEAMS", "admin");

        // 提交线程不应释放锁（ownershipTransferred=true）
        verify(syncLockService, never()).release(anyString(), anyString());
    }

    // --- executeJob 查询不到任务仍正常退出 ---

    @Test
    void executeJob_jobNotFound_noException() throws Exception {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });

        AtomicReference<Runnable> captured = new AtomicReference<>();
        doAnswer(inv -> { captured.set(inv.getArgument(0)); return null; })
                .when(manualSyncExecutor).execute(any(Runnable.class));

        coordinator.submitManual("TEAMS", "admin");

        // 模拟任务已被删除
        when(syncJobRepository.findById(1L)).thenReturn(Optional.empty());
        // 不应抛异常
        captured.get().run();
    }

    // --- executeJob 正常完成 ---

    @Test
    void executeJob_completes_savesFinalStatus() throws Exception {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);

        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });
        SyncJob running = new SyncJob();
        running.setStatus("RUNNING");
        when(syncJobRepository.findById(1L)).thenReturn(Optional.of(running));

        AtomicReference<Runnable> captured = new AtomicReference<>();
        doAnswer(inv -> { captured.set(inv.getArgument(0)); return null; })
                .when(manualSyncExecutor).execute(any(Runnable.class));

        coordinator.submitManual("TEAMS", "admin");

        when(teamSyncWorker.sync(any(), eq(0), eq(100))).thenReturn(
                com.nbamanager.web.dto.SyncResult.success("TEAMS", 30, 2, 28, 0, java.time.LocalDateTime.now(), "nba_api"));

        captured.get().run();

        verify(syncJobRepository, atLeastOnce()).findById(1L);
    }

    // --- executeJob 最终保存失败不阻断流程 ---

    @Test
    void executeJob_finalSaveFails_noExceptionLeak() throws Exception {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);

        final int[] saveCount = {0};
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            j.setId(1L);
            saveCount[0]++;
            if (saveCount[0] > 2) {
                throw new RuntimeException("DB write failed");
            }
            return j;
        });
        SyncJob running = new SyncJob();
        running.setStatus("RUNNING");
        when(syncJobRepository.findById(1L)).thenReturn(Optional.of(running));

        AtomicReference<Runnable> captured = new AtomicReference<>();
        doAnswer(inv -> { captured.set(inv.getArgument(0)); return null; })
                .when(manualSyncExecutor).execute(any(Runnable.class));

        coordinator.submitManual("TEAMS", "admin");

        when(teamSyncWorker.sync(any(), eq(0), eq(100))).thenReturn(
                com.nbamanager.web.dto.SyncResult.success("TEAMS", 30, 2, 28, 0, java.time.LocalDateTime.now(), "nba_api"));

        // 不应抛异常到调用方
        captured.get().run();
    }

    // --- 取消 ---

    @Test
    void cancelJob_setsFlag() {
        when(jobStateService.requestCancellation(1L)).thenReturn(true);

        boolean cancelled = coordinator.cancelJob(1L);
        assertThat(cancelled).isTrue();
    }

    @Test
    void cancelJob_completedJob_returnsFalse() {
        when(jobStateService.requestCancellation(1L)).thenReturn(false);

        boolean cancelled = coordinator.cancelJob(1L);
        assertThat(cancelled).isFalse();
    }

    // --- LOCK_LOST 竞态 ---

    @Test
    void lockLost_callsMarkFailed() throws Exception {
        // onLockLost 应设置 flag、终止进程、标记失败
        java.lang.reflect.Method onLockLost = SyncCoordinator.class.getDeclaredMethod("onLockLost", Long.class);
        onLockLost.setAccessible(true);

        java.lang.reflect.Field flagsField = SyncCoordinator.class.getDeclaredField("lockLostFlags");
        flagsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, AtomicBoolean> flags = (Map<Long, AtomicBoolean>) flagsField.get(coordinator);
        flags.put(1L, new AtomicBoolean(false));

        onLockLost.invoke(coordinator, 1L);

        verify(jobStateService).markLockLost(1L, "锁续期失败，任务已失去互斥保护");
    }

    @Test
    void lockLostAfterJobCleanup_doesNothing() throws Exception {
        java.lang.reflect.Method onLockLost = SyncCoordinator.class.getDeclaredMethod("onLockLost", Long.class);
        onLockLost.setAccessible(true);

        onLockLost.invoke(coordinator, 1L);

        verify(jobStateService, never()).markLockLost(anyLong(), anyString());
    }

    @Test
    void initOnlyRecoversStaleJobs() {
        when(syncJobRepository.markStaleRunningAsInterrupted(any(LocalDateTime.class))).thenReturn(2);

        coordinator.init();

        verify(syncJobRepository).markStaleRunningAsInterrupted(any(LocalDateTime.class));
    }

    @Test
    void fullSyncCancellationDuringWorkerDoesNotAdvanceModuleProgress() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(invocation -> {
            SyncJob job = invocation.getArgument(0);
            job.setId(1L);
            return job;
        });
        SyncJob running = new SyncJob();
        running.setId(1L);
        running.setStatus("RUNNING");
        when(syncJobRepository.findById(1L)).thenReturn(Optional.of(running));

        AtomicReference<Runnable> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(manualSyncExecutor).execute(any(Runnable.class));
        when(jobStateService.isCancelRequested(1L)).thenReturn(false, false, true);
        when(teamSyncWorker.sync(eq(1L), eq(5), eq(15), any()))
                .thenAnswer(invocation -> {
                    java.util.function.BiConsumer<Integer, String> progress = invocation.getArgument(3);
                    progress.accept(50, "FETCHING");
                    return com.nbamanager.web.dto.SyncResult.failed("TEAMS", "PYTHON_FAILED", "cancelled");
                });

        coordinator.submitFullSync("admin");
        captured.get().run();

        verify(jobStateService, never()).updateProgress(1L, 15);
        verify(stepStateService).updateProgress(null, 50, "FETCHING");
        ArgumentCaptor<com.nbamanager.web.dto.SyncResult> resultCaptor =
                ArgumentCaptor.forClass(com.nbamanager.web.dto.SyncResult.class);
        verify(jobStateService).complete(eq(1L), resultCaptor.capture());
        assertThat(resultCaptor.getValue().status()).isEqualTo("CANCELLED");
        ArgumentCaptor<com.nbamanager.web.dto.SyncResult> stepResultCaptor =
                ArgumentCaptor.forClass(com.nbamanager.web.dto.SyncResult.class);
        verify(stepStateService).complete(isNull(), stepResultCaptor.capture());
        assertThat(stepResultCaptor.getValue().status()).isEqualTo("CANCELLED");
    }

}

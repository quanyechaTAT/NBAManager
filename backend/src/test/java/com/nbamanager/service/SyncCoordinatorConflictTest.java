package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.exception.SyncJobConflictException;
import com.nbamanager.repository.SyncJobRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class SyncCoordinatorConflictTest {

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
        coordinator = new SyncCoordinator(
                syncJobRepository, jobStateService, stepStateService, stepRepository,
                manualSyncExecutor, syncLockService,
                teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker,
                playoffSyncWorker, historicalSyncWorker, translationSyncWorker,
                pythonScriptRunner
        );
    }

    @Test
    void moduleLockHeld_throwsConflict() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("已有任务");

        verify(syncJobRepository, never()).save(any());
    }

    @Test
    void redisUnavailable_throwsServiceUnavailable() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("Redis");
    }

    @Test
    void saveFails_throwsServiceUnavailable() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("创建同步任务失败");

        // 锁释放
        verify(syncLockService).release(anyString(), anyString());
    }

    @Test
    void executorRejects_throwsServiceUnavailable() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            j.setId(1L);
            return j;
        });
        doThrow(new RuntimeException("queue full")).when(manualSyncExecutor).execute(any(Runnable.class));

        assertThatThrownBy(() -> coordinator.submitManual("TEAMS", "admin"))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("队列已满");

        // 锁释放 + 任务删除
        verify(syncLockService).release(anyString(), anyString());
        verify(syncJobRepository).deleteById(1L);
    }

    @Test
    void success_returns202WithJobId() {
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });

        var result = coordinator.submitManual("NEWS", "admin");

        assertThat(result).isNotNull();
        assertThat(result.jobId()).isEqualTo(42L);
        assertThat(result.module()).isEqualTo("NEWS");
        assertThat(result.status()).isEqualTo("QUEUED");
    }

    @Test
    void manualSubmissionReusesScheduledJobForSameModule() {
        SyncJob active = new SyncJob();
        active.setId(41L);
        active.setModule("NEWS");
        active.setTriggerType("SCHEDULED");
        active.setStatus("RUNNING");
        when(syncJobRepository.findActiveByModule("NEWS")).thenReturn(Optional.of(active));

        SyncSubmission result = coordinator.submit(SyncCommand.manual("NEWS", "admin"));

        assertThat(result.reused()).isTrue();
        assertThat(result.job().getId()).isEqualTo(41L);
        verify(manualSyncExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void differentModulesCanRunConcurrently() {
        SyncJob teamsJob = new SyncJob();
        teamsJob.setId(11L);
        teamsJob.setModule("TEAMS");
        teamsJob.setStatus("RUNNING");
        when(syncJobRepository.findActiveByModule("NEWS")).thenReturn(Optional.empty());
        when(syncJobRepository.findActiveByModule("FULL_SYNC")).thenReturn(Optional.empty());
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob job = inv.getArgument(0);
            job.setId(42L);
            return job;
        });
        SyncJob persisted = new SyncJob();
        persisted.setId(42L);
        persisted.setModule("NEWS");
        persisted.setStatus("QUEUED");
        when(syncJobRepository.findById(42L)).thenReturn(Optional.of(persisted));

        SyncSubmission result = coordinator.submit(SyncCommand.manual("NEWS", "admin"));

        assertThat(result.reused()).isFalse();
        assertThat(result.job().getModule()).isEqualTo("NEWS");
    }

    @Test
    void moduleSubmissionIsBlockedByActiveFullSync() {
        SyncJob fullSync = new SyncJob();
        fullSync.setId(9L);
        fullSync.setModule("FULL_SYNC");
        fullSync.setStatus("RUNNING");
        when(syncJobRepository.findActiveByModule("NEWS")).thenReturn(Optional.empty());
        when(syncJobRepository.findActiveByModule("FULL_SYNC")).thenReturn(Optional.of(fullSync));
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> coordinator.submit(SyncCommand.manual("NEWS", "admin")))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("FULL_SYNC");

        verify(syncJobRepository, never()).save(any());
    }

    @Test
    void fullSyncSubmissionUsesSubmitGate() {
        when(syncJobRepository.findByStatusIn(any())).thenReturn(java.util.List.of());
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
        when(syncJobRepository.save(any(SyncJob.class))).thenAnswer(inv -> {
            SyncJob job = inv.getArgument(0);
            job.setId(42L);
            return job;
        });
        SyncJob persisted = new SyncJob();
        persisted.setId(42L);
        persisted.setModule("FULL_SYNC");
        persisted.setStatus("QUEUED");
        when(syncJobRepository.findById(42L)).thenReturn(Optional.of(persisted));

        coordinator.submitFullSync("MANUAL", "admin");

        verify(syncLockService).tryAcquire(
                eq("nba:sync:submit"), anyString(), eq(java.time.Duration.ofSeconds(15)));
    }

    @Test
    void fullSyncSubmissionReusesActiveFullSync() {
        SyncJob active = new SyncJob();
        active.setId(77L);
        active.setModule("FULL_SYNC");
        active.setTriggerType("SCHEDULED");
        active.setStatus("RUNNING");
        when(syncJobRepository.findActiveByModule("FULL_SYNC")).thenReturn(Optional.of(active));

        SyncSubmission result = coordinator.submitFullSync("MANUAL", "admin");

        assertThat(result.reused()).isTrue();
        assertThat(result.job().getId()).isEqualTo(77L);
        verify(syncLockService, never()).tryAcquire(anyString(), anyString(), any());
    }

    @Test
    void fullSyncReturnsConflictingModules() {
        SyncJob newsJob = new SyncJob();
        newsJob.setId(11L);
        newsJob.setModule("NEWS");
        newsJob.setTriggerType("SCHEDULED");
        newsJob.setStatus("RUNNING");

        SyncJob draftJob = new SyncJob();
        draftJob.setId(12L);
        draftJob.setModule("DRAFT");
        draftJob.setTriggerType("MANUAL");
        draftJob.setStatus("QUEUED");

        when(syncJobRepository.findByStatusIn(any())).thenReturn(java.util.List.of(newsJob, draftJob));
        when(syncLockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> coordinator.submitFullSync("MANUAL", "admin"))
                .isInstanceOf(SyncJobConflictException.class)
                .hasMessageContaining("NEWS")
                .hasMessageContaining("DRAFT");
    }
}

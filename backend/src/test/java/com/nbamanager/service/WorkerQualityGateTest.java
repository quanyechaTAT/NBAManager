package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerQualityGateTest {

    @Mock private SyncJobRepository syncJobRepository;
    @Mock private SyncJobStateService jobStateService;
    @Mock private NbaLiveSyncService liveSyncService;
    @Mock private DraftSyncService draftSyncService;
    @Mock private HistoricalDataSyncService historicalDataSyncService;
    @Mock private SeasonManagementService seasonManagementService;
    @Mock private TranslationRetryService translationRetryService;
    @Mock private NbaDataService nbaDataService;

    private SyncJob job;

    @BeforeEach
    void setUp() {
        job = new SyncJob();
        job.setId(1L);
        job.setStatus("RUNNING");
        // Mock jobStateService for isCancelled
        org.mockito.Mockito.lenient().when(jobStateService.isCancelRequested(1L)).thenReturn(false);
    }

    // --- TodayGames 契约测试 ---

    @Test
    void todayGames_missingStatus_returnsFailed() {
        when(liveSyncService.syncTodayGamesWithResult(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(SyncExecResult.failed("Python返回缺少 todayGamesSourceStatus 字段"));

        TodayGamesSyncWorker worker = new TodayGamesSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("PROVIDER_FAILED");
    }

    @Test
    void todayGames_allProvidersFailed_returnsFailed() {
        when(liveSyncService.syncTodayGamesWithResult(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(SyncExecResult.failed("所有数据源失败"));

        TodayGamesSyncWorker worker = new TodayGamesSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void todayGames_emptyWithSuccess_returnsSuccess() {
        when(liveSyncService.syncTodayGamesWithResult(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(SyncExecResult.noGames());

        TodayGamesSyncWorker worker = new TodayGamesSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.fetchedCount()).isEqualTo(0);
    }

    @Test
    void todayGames_okWithData_returnsSuccess() {
        when(liveSyncService.syncTodayGamesWithResult(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(SyncExecResult.success(5, 0, 5));

        TodayGamesSyncWorker worker = new TodayGamesSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.fetchedCount()).isEqualTo(5);
        assertThat(result.updatedCount()).isEqualTo(5);
    }

    @Test
    void todayGames_protocolError_returnsFailed() {
        when(liveSyncService.syncTodayGamesWithResult(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(SyncExecResult.failed("状态ok但数据为空，协议错误"));

        TodayGamesSyncWorker worker = new TodayGamesSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    // --- News 契约测试 ---

    @Test
    void news_allProvidersFailed_returnsFailed() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.failed("所有新闻源失败"));

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("PROVIDER_FAILED");
    }

    @Test
    void news_emptyWithSuccess_returnsSuccess() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.noGames());

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.fetchedCount()).isEqualTo(0);
    }

    @Test
    void news_okWithData_returnsSuccess() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.success(20, 3, 0));

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.fetchedCount()).isEqualTo(20);
        assertThat(result.insertedCount()).isEqualTo(3);
    }

    @Test
    void news_partialProviderFailure_returnsPartialFailed() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.partial(12, 2, 0, "one provider failed"));

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.fetchedCount()).isEqualTo(12);
        assertThat(result.insertedCount()).isEqualTo(2);
        assertThat(result.errorCode()).isEqualTo("PROVIDER_PARTIAL");
    }

    @Test
    void news_protocolError_returnsFailed() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.failed("状态ok但数据为空，协议错误"));

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
    }

    @Test
    void news_workerNeverReturnsSuccessOnFailed() {
        when(liveSyncService.syncNbaNewsWithResult(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(SyncExecResult.failed("任何失败原因"));

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isNotEqualTo("SUCCESS");
    }

    @Test
    void news_reportsProviderAndPersistenceStages() {
        when(liveSyncService.syncNbaNewsWithResult(
                org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.BiConsumer<Integer, String> progress = invocation.getArgument(1);
                    progress.accept(10, "FETCHING");
                    progress.accept(70, "PERSISTING");
                    return SyncExecResult.success(4, 2, 0);
                });

        NewsSyncWorker worker = new NewsSyncWorker(syncJobRepository, jobStateService, liveSyncService);
        worker.sync(job.getId());

        verify(jobStateService).updateProgress(1L, 5, "STARTING");
        verify(jobStateService).updateProgress(1L, 10, "FETCHING");
        verify(jobStateService).updateProgress(1L, 70, "PERSISTING");
    }

    // --- Draft ---

    @Test
    void draft_syncing_returnsPartialFailed() {
        when(draftSyncService.syncDraftYear(2026, 1L))
                .thenReturn(java.util.Map.of("status", "syncing", "year", 2026));

        DraftSyncWorker worker = new DraftSyncWorker(syncJobRepository, jobStateService, draftSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.errorCode()).isEqualTo("SKIPPED");
    }

    @Test
    void draft_failed_returnsFailed() {
        when(draftSyncService.syncDraftYear(2026, 1L))
                .thenReturn(java.util.Map.of("status", "failed", "error", "API不可用"));

        DraftSyncWorker worker = new DraftSyncWorker(syncJobRepository, jobStateService, draftSyncService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("SYNC_ERROR");
    }

    // --- Historical ---

    @Test
    void historical_alreadyRunning_returnsPartialFailed() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        org.mockito.Mockito.doThrow(new IllegalStateException("已有任务"))
                .when(historicalDataSyncService).syncSeasonDataSync("2025-26", 1L);

        HistoricalSyncWorker worker = new HistoricalSyncWorker(syncJobRepository, jobStateService, historicalDataSyncService, seasonManagementService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.errorCode()).isEqualTo("SKIPPED");
    }

    @Test
    void historical_partialData_returnsPartialFailed() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        org.mockito.Mockito.doThrow(new PartialSyncException("球员50条, 球队0条"))
                .when(historicalDataSyncService).syncSeasonDataSync("2025-26", 1L);

        HistoricalSyncWorker worker = new HistoricalSyncWorker(syncJobRepository, jobStateService, historicalDataSyncService, seasonManagementService);
        SyncResult result = worker.sync(job.getId());

        assertThat(result.status()).isEqualTo("PARTIAL_FAILED");
        assertThat(result.errorCode()).isEqualTo("PARTIAL_DATA");
    }

    @Test
    void playoffPassesJobIdToSyncService() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        when(nbaDataService.fetchAndImportPlayoffData("2025-26", 1L))
                .thenReturn(java.util.Map.of("imported", 8));

        PlayoffSyncWorker worker = new PlayoffSyncWorker(
                syncJobRepository, jobStateService, nbaDataService, seasonManagementService);

        assertThat(worker.sync(1L).status()).isEqualTo("SUCCESS");
        verify(nbaDataService).fetchAndImportPlayoffData("2025-26", 1L);
    }

    @Test
    void translationPassesJobIdToSyncService() {
        TranslationSyncWorker worker = new TranslationSyncWorker(
                syncJobRepository, jobStateService, translationRetryService);

        assertThat(worker.sync(1L).status()).isEqualTo("SUCCESS");
        verify(translationRetryService).retryTranslations(1L);
    }
}

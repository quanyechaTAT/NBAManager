package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * 验证 NbaLiveSyncService.parseTodayGamesResult / parseNewsResult 的真实解析逻辑。
 * 直接调用包级可见的静态方法，不 Mock 任何东西。
 */
class SyncExecResultContractTest {

    // ====== todayGamesSourceStatus 契约 ======

    @Test
    void todayGames_missingStatus_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("todayGames", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("缺少");
    }

    @Test
    void todayGames_nullData_returnsFailed() {
        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(null);

        assertThat(r.providerSucceeded()).isFalse();
    }

    @Test
    void todayGames_failedStatus_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("todayGames", new JSONArray());
        data.put("todayGamesSourceStatus", "failed");
        data.put("todayGamesProviderResults", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("失败");
    }

    @Test
    void todayGames_emptyStatus_emptyArray_returnsSuccess() {
        JSONObject data = new JSONObject();
        data.put("todayGames", new JSONArray());
        data.put("todayGamesSourceStatus", "empty");
        data.put("todayGamesProviderResults", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(0);
    }

    @Test
    void todayGames_okStatus_withData_returnsSuccess() {
        JSONObject data = new JSONObject();
        JSONArray games = new JSONArray();
        games.put(new JSONObject().put("gameId", "001").put("homeTeam", "LAL"));
        data.put("todayGames", games);
        data.put("todayGamesSourceStatus", "ok");
        data.put("todayGamesProviderResults", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(1);
    }

    @Test
    void todayGames_okStatus_emptyArray_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("todayGames", new JSONArray());
        data.put("todayGamesSourceStatus", "ok");

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("协议错误");
    }

    @Test
    void todayGames_emptyStatus_withData_returnsFailed() {
        JSONObject data = new JSONObject();
        JSONArray games = new JSONArray();
        games.put(new JSONObject().put("gameId", "001"));
        data.put("todayGames", games);
        data.put("todayGamesSourceStatus", "empty");

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("协议错误");
    }

    @Test
    void todayGames_unknownStatus_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("todayGames", new JSONArray());
        data.put("todayGamesSourceStatus", "unknown_xyz");

        SyncExecResult r = NbaLiveSyncService.parseTodayGamesResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("未知状态");
    }

    // ====== newsSourceStatus 契约 ======

    @Test
    void news_missingStatus_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).contains("缺少");
    }

    @Test
    void news_failedStatus_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray());
        data.put("newsSourceStatus", "failed");
        data.put("newsProviderResults", new JSONArray());

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isFalse();
    }

    @Test
    void news_emptyStatus_emptyArray_returnsSuccess() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray());
        data.put("newsSourceStatus", "empty");

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(0);
    }

    @Test
    void news_okStatus_withData_returnsSuccess() {
        JSONObject data = new JSONObject();
        JSONArray news = new JSONArray();
        news.put(new JSONObject().put("headline", "Test"));
        data.put("news", news);
        data.put("newsSourceStatus", "ok");

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(1);
    }

    @Test
    void news_okStatus_emptyArray_returnsFailed() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray());
        data.put("newsSourceStatus", "ok");

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isFalse();
    }

    @Test
    void news_partialStatus_withData_returnsPartialSuccess() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray().put(new JSONObject().put("headline", "Test")));
        data.put("newsSourceStatus", "partial");
        data.put("newsProviderResults", new JSONArray()
                .put(new JSONObject().put("provider", "espn").put("succeeded", true))
                .put(new JSONObject().put("provider", "cbs").put("succeeded", false)
                        .put("error", "proxy reset")));

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.partial()).isTrue();
        assertThat(r.fetched()).isEqualTo(1);
        assertThat(r.errorCode()).isEqualTo("PROVIDER_PARTIAL");
        assertThat(r.error()).contains("cbs", "proxy reset");
    }

    @Test
    void news_partialStatus_emptyArray_returnsPartialSuccess() {
        JSONObject data = new JSONObject();
        data.put("news", new JSONArray());
        data.put("newsSourceStatus", "partial");
        data.put("newsProviderResults", new JSONArray()
                .put(new JSONObject().put("provider", "espn").put("succeeded", true))
                .put(new JSONObject().put("provider", "cbs").put("succeeded", false)));

        SyncExecResult r = NbaLiveSyncService.parseNewsResult(data);

        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.partial()).isTrue();
        assertThat(r.fetched()).isZero();
    }

    // ====== SyncExecResult 值对象 ======

    @Test
    void syncExecResult_success() {
        SyncExecResult r = SyncExecResult.success(10, 3, 7);
        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(10);
        assertThat(r.inserted()).isEqualTo(3);
        assertThat(r.updated()).isEqualTo(7);
        assertThat(r.error()).isNull();
    }

    @Test
    void syncExecResult_failed() {
        SyncExecResult r = SyncExecResult.failed("Provider down");
        assertThat(r.providerSucceeded()).isFalse();
        assertThat(r.error()).isEqualTo("Provider down");
    }

    @Test
    void syncExecResult_noGames() {
        SyncExecResult r = SyncExecResult.noGames();
        assertThat(r.providerSucceeded()).isTrue();
        assertThat(r.fetched()).isEqualTo(0);
    }

    // ====== SyncJobStateService 终态保护 ======

    @Test
    void complete_doesNotOverrideLockLost() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("FAILED");
        job.setErrorCode("LOCK_LOST");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        // 尝试用 SUCCESS 覆盖
        stateService.complete(1L, com.nbamanager.web.dto.SyncResult.success("TEAMS", 30, 2, 28, 0, java.time.LocalDateTime.now(), "nba_api"));

        // 应该保持 FAILED/LOCK_LOST
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorCode()).isEqualTo("LOCK_LOST");
    }

    @Test
    void complete_doesNotOverrideCancelled() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("CANCELLED");
        job.setErrorCode("CANCELLED");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.complete(1L, com.nbamanager.web.dto.SyncResult.success("TEAMS", 30, 2, 28, 0, java.time.LocalDateTime.now(), "nba_api"));

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void complete_allowsNormalSuccess() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("RUNNING");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.complete(1L, com.nbamanager.web.dto.SyncResult.success("TEAMS", 30, 2, 28, 0, java.time.LocalDateTime.now(), "nba_api"));

        assertThat(job.getStatus()).isEqualTo("SUCCESS");
    }

    // ====== markFailed 终态保护 ======

    @Test
    void markFailed_doesNotOverrideLockLost() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("FAILED");
        job.setErrorCode("LOCK_LOST");
        job.setErrorMessage("锁续期失败");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.markFailed(1L, "EXCEPTION", "Worker crashed");

        // 应保持 LOCK_LOST，不被 EXCEPTION 覆盖
        assertThat(job.getErrorCode()).isEqualTo("LOCK_LOST");
        assertThat(job.getErrorMessage()).isEqualTo("锁续期失败");
    }

    @Test
    void markFailed_doesNotOverrideExistingTerminalStatus() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("FAILED");
        job.setErrorCode("EXCEPTION");
        job.setErrorMessage("Worker crashed");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.markFailed(1L, "LOCK_LOST", "锁续期失败");

        // 已处于终态，不覆盖
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorCode()).isEqualTo("EXCEPTION");
    }

    @Test
    void markFailed_doesNotOverrideCancelled() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("CANCELLED");
        job.setErrorCode("CANCELLED");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.markFailed(1L, "EXCEPTION", "Worker crashed");

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void markFailed_lockLostDoesNotOverrideCancelled() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("CANCELLED");
        job.setErrorCode("CANCELLED");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.markFailed(1L, "LOCK_LOST", "锁续期失败");

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
        assertThat(job.getErrorCode()).isEqualTo("CANCELLED");
    }

    @Test
    void markFailed_allowsNormalFailure() {
        com.nbamanager.repository.SyncJobRepository mockRepo = org.mockito.Mockito.mock(com.nbamanager.repository.SyncJobRepository.class);
        SyncJobStateService stateService = new SyncJobStateService(mockRepo);

        com.nbamanager.domain.SyncJob job = new com.nbamanager.domain.SyncJob();
        job.setId(1L);
        job.setStatus("RUNNING");
        org.mockito.Mockito.when(mockRepo.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(job));

        stateService.markFailed(1L, "EXCEPTION", "Worker crashed");

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorCode()).isEqualTo("EXCEPTION");
    }
}

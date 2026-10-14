package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.repository.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualSyncRegressionTest {
    @Mock SyncJobRepository jobs;
    @Mock SyncJobStateService state;
    @Mock PythonScriptRunner runner;
    @Mock PlayerRepository players;
    @Mock TeamRepository teams;
    @Mock MatchRecordRepository matches;
    @Mock PythonTimeoutProperties timeouts;
    @InjectMocks PlayerSyncWorker playerWorker;
    @InjectMocks MatchSyncWorker matchWorker;

    @Test
    void playersRunTheirOwnActionWithConfiguredDeadline() {
        lenient().when(timeouts.getBulkSeconds()).thenReturn(777);
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(-1, "", "fixture", true, 120000));
        playerWorker.sync(1L);
        verify(runner).runForJob("nba_data_fetcher.py", new String[]{"players"}, 777, 1L);
    }

    @Test
    void matchesRunTheirOwnActionWithConfiguredDeadline() {
        lenient().when(timeouts.getBulkSeconds()).thenReturn(777);
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(-1, "", "fixture", true, 120000));
        matchWorker.sync(1L);
        verify(runner).runForJob("nba_data_fetcher.py", new String[]{"games"}, 777, 1L);
    }

    @Test
    void draftWithNoUsableProviderIsFailed() {
        DraftService drafts = mock(DraftService.class);
        when(runner.executeForJob("draft", new String[]{"2026"}, 180, 1L))
                .thenReturn(new JSONObject().put("draftPicks", new JSONArray())
                        .put("draftSourceStatus", "failed").put("draftErrors", new JSONArray().put("provider offline")));
        var result = new DraftSyncService(drafts, runner).syncDraftYear(2026, 1L);
        assertThat(result.get("status")).isEqualTo("failed");
        verifyNoInteractions(drafts);
    }

    @Test
    void failedNewsTranslationCannotReportSuccess() {
        when(players.findAll()).thenReturn(java.util.List.of());
        when(timeouts.getTranslationSeconds()).thenReturn(60);
        when(runner.runForJob(eq("translate_pending_news.py"), any(), eq(60), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(1, "", "fixture: service offline", false, 1));
        var translation = new TranslationRetryService(players, runner, timeouts);
        var worker = new TranslationSyncWorker(jobs, state, translation);
        assertThat(worker.sync(1L).status()).isEqualTo("FAILED");
    }

    @Test
    void matchProviderOutageDoesNotLookLikeAnEmptySuccessfulRefresh() {
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(0,
                        "{\"games\":[],\"gamesSourceStatus\":\"failed\",\"gamesErrors\":[\"upstream offline\"]}", "", false, 1));
        assertThat(matchWorker.sync(1L).status()).isEqualTo("FAILED");
        verifyNoInteractions(matches);
    }

    @Test
    void validEmptyScheduleSucceedsAndPartialDateCoverageIsReported() {
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(0, "{\"games\":[],\"gamesSourceStatus\":\"ok\"}", "", false, 1))
                .thenReturn(new PythonScriptRunner.PythonResult(0, "{\"games\":[],\"gamesSourceStatus\":\"partial\",\"gamesErrors\":[\"one date failed\"]}", "", false, 1));
        assertThat(matchWorker.sync(1L).status()).isEqualTo("SUCCESS");
        assertThat(matchWorker.sync(1L).status()).isEqualTo("PARTIAL_FAILED");
    }

    @Test
    void pythonTimeoutKeepsRootCauseInsteadOfStartupBanner() {
        var timeout = new PythonScriptRunner.PythonResult(-1, "", "startup\n".repeat(200) + "upstream timed out", true, 120000);
        var result = timeout.failure("PLAYERS");
        assertThat(result.errorCode()).isEqualTo("PYTHON_TIMEOUT");
        assertThat(result.errorMessage()).contains("120s").endsWith("upstream timed out");
        assertThat(new PythonScriptRunner.PythonResult(-2, "", "cancelled", false, 1).failure("PLAYERS").status()).isEqualTo("CANCELLED");
    }

    @Test
    void statisticsRefreshPreservesNamesAlreadyTranslatedByRetryJob() {
        var existing = new com.nbamanager.domain.Player();
        existing.setName("已翻译球员"); existing.setNameEn("Fixture Rookie");
        existing.setTranslationStatus("API_TRANSLATED"); existing.setNbaPlayerId(1L);
        var team = new com.nbamanager.domain.Team(); team.setId(1L);
        when(teams.findByName("fixture-team")).thenReturn(team);
        when(players.findByNbaPlayerId(1L)).thenReturn(existing);
        JSONArray rows = new JSONArray();
        for (int i = 1; i <= 100; i++) rows.put(new JSONObject().put("nbaPlayerId", i)
                .put("name", "Fixture Rookie").put("nameEn", "Fixture Rookie")
                .put("translationStatus", "UNTRANSLATED").put("team", "fixture-team"));
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L))).thenReturn(
                new PythonScriptRunner.PythonResult(0, new JSONObject().put("players", rows).toString(), "", false, 1));
        assertThat(playerWorker.sync(1L).status()).isEqualTo("SUCCESS");
        assertThat(existing.getName()).isEqualTo("已翻译球员");
        assertThat(existing.getTranslationStatus()).isEqualTo("API_TRANSLATED");
    }
}

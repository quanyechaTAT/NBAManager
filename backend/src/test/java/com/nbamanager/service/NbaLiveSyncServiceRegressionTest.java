package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.same;

import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.WebSocketController;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NbaLiveSyncServiceRegressionTest {

    @Mock private GameNewsRepository gameNewsRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private GameNewsService gameNewsService;
    @Mock private SmartDataService smartDataService;
    @Mock private PostService postService;
    @Mock private DraftService draftService;
    @Mock private NotificationService notificationService;
    @Mock private WebSocketController webSocketController;
    @Mock private NbaDataService nbaDataService;
    @Mock private GameIdMappingService gameIdMappingService;
    @Mock private PythonScriptRunner pythonScriptRunner;
    @Mock private PythonTimeoutProperties pythonTimeoutProperties;
    @Mock private ApplicationContext applicationContext;
    @Mock private NewsPersistenceService newsPersistenceService;

    @InjectMocks private NbaLiveSyncService service;

    @Test
    void newsTimeout_preservesTimeoutCause() {
        when(pythonTimeoutProperties.getLiveSeconds()).thenReturn(900);
        when(pythonScriptRunner.runForJob(
                        eq("nba_data_fetcher.py"), any(String[].class), eq(900), eq(99L)))
                .thenReturn(new PythonScriptRunner.PythonResult(
                        -1, "", "provider still running", true, 900_000));

        SyncExecResult result = service.syncNbaNewsWithResult(99L);

        assertThat(result.providerSucceeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PYTHON_TIMEOUT");
        assertThat(result.error()).contains("超时").contains("900秒");
        verify(gameNewsService, never()).cleanupOldNews(any(Integer.class));
    }

    @Test
    void newsSuccess_exportsKnownItemsUsesBatchPersistenceAndDeletesTempFile() {
        when(pythonTimeoutProperties.getLiveSeconds()).thenReturn(900);
        when(gameNewsRepository.findAllSourceUrls())
                .thenReturn(List.of("https://example.com/existing"));
        when(gameNewsRepository.findAllKnownTitles()).thenReturn(List.of("Existing title"));
        AtomicReference<Path> knownFile = new AtomicReference<>();
        when(pythonScriptRunner.runForJob(
                eq("nba_data_fetcher.py"), any(String[].class), eq(900), eq(99L)))
                .thenAnswer(invocation -> {
                    String[] args = invocation.getArgument(1);
                    assertThat(args).hasSize(3);
                    assertThat(args[0]).isEqualTo("news");
                    assertThat(args[1]).isEqualTo("40");
                    Path path = Path.of(args[2]);
                    knownFile.set(path);
                    String json = Files.readString(path);
                    assertThat(json).contains("https://example.com/existing", "Existing title");
                    return new PythonScriptRunner.PythonResult(
                            0,
                            "{\"news\":[{\"headline\":\"New\",\"sourceUrl\":\"https://example.com/new\"}],"
                                    + "\"newsSourceStatus\":\"ok\",\"newsProviderResults\":[]}",
                            "", false, 1000);
                });
        when(newsPersistenceService.persist(any(JSONArray.class)))
                .thenReturn(new NewsPersistenceService.PersistResult(1, 1, 0));

        SyncExecResult result = service.syncNbaNewsWithResult(99L);

        assertThat(result.providerSucceeded()).isTrue();
        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(knownFile.get()).isNotNull();
        assertThat(Files.exists(knownFile.get())).isFalse();
        verify(newsPersistenceService).persist(any(JSONArray.class));
        verify(gameNewsService, never()).cleanupOldNews(any(Integer.class));
    }

    @Test
    void newsProviderFailure_doesNotPersistOrCleanup() {
        when(pythonTimeoutProperties.getLiveSeconds()).thenReturn(900);
        when(gameNewsRepository.findAllSourceUrls()).thenReturn(List.of());
        when(gameNewsRepository.findAllKnownTitles()).thenReturn(List.of());
        when(pythonScriptRunner.runForJob(
                eq("nba_data_fetcher.py"), any(String[].class), eq(900), eq(99L)))
                .thenReturn(new PythonScriptRunner.PythonResult(
                        0,
                        "{\"news\":[],\"newsSourceStatus\":\"failed\","
                                + "\"newsProviderResults\":[{\"provider\":\"espn\",\"succeeded\":false}]}",
                        "", false, 1000));

        SyncExecResult result = service.syncNbaNewsWithResult(99L);

        assertThat(result.providerSucceeded()).isFalse();
        verify(newsPersistenceService, never()).persist(any(JSONArray.class));
        verify(gameNewsService, never()).cleanupOldNews(any(Integer.class));
    }

    @Test
    void newsEmptyProviderSuccess_stillEnforcesRetention() {
        when(pythonTimeoutProperties.getLiveSeconds()).thenReturn(900);
        when(gameNewsRepository.findAllSourceUrls()).thenReturn(List.of());
        when(gameNewsRepository.findAllKnownTitles()).thenReturn(List.of());
        when(pythonScriptRunner.runForJob(
                eq("nba_data_fetcher.py"), any(String[].class), eq(900), eq(99L)))
                .thenReturn(new PythonScriptRunner.PythonResult(
                        0,
                        "{\"news\":[],\"newsSourceStatus\":\"empty\","
                                + "\"newsProviderResults\":[{\"provider\":\"espn\",\"succeeded\":true}]}",
                        "", false, 1000));

        SyncExecResult result = service.syncNbaNewsWithResult(99L);

        assertThat(result.providerSucceeded()).isTrue();
        verify(gameNewsService).cleanupOldNews(100);
    }

    @Test
    void todayGamesSuccess_enforcesSameRetentionLimit() {
        when(pythonTimeoutProperties.getLiveSeconds()).thenReturn(900);
        when(pythonScriptRunner.runForJob(
                eq("nba_data_fetcher.py"), any(String[].class), eq(900), eq(99L)))
                .thenReturn(new PythonScriptRunner.PythonResult(
                        0,
                        "{\"todayGames\":[{\"homeTeam\":\"湖人\",\"awayTeam\":\"勇士\","
                                + "\"homeScore\":100,\"awayScore\":98,\"status\":\"FINISHED\","
                                + "\"gameId\":\"401000001\",\"category\":\"game\"}],"
                                + "\"todayGamesSourceStatus\":\"ok\",\"todayGamesProviderResults\":[]}",
                        "", false, 1000));
        when(smartDataService.parseGameStartTimePublic(any(org.json.JSONObject.class)))
                .thenReturn(LocalDateTime.of(2026, 7, 20, 12, 0));
        when(smartDataService.upsertGameNews(
                eq("湖人"), eq("勇士"), eq(100), eq(98), eq("FINISHED"),
                eq("401000001"), eq("game"), any(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(gameNewsRepository.findByNbaGameIdIsNullOrNbaGameIdEmpty()).thenReturn(List.of());

        SyncExecResult result = service.syncTodayGamesWithResult(99L);

        assertThat(result.providerSucceeded()).isTrue();
        verify(gameNewsService).cleanupOldNews(100);
    }

    @Test
    void backfill_skipsGameIdOwnedByAnotherNewsRecord() {
        GameNews target = gameNews(1L, null);
        GameNews owner = gameNews(2L, "401881845");
        MatchRecord match = match("401881845");

        when(gameNewsRepository.findByNbaGameIdIsNullOrNbaGameIdEmpty())
                .thenReturn(List.of(target));
        when(matchRecordRepository.findByHomeTeamAndAwayTeamAndMatchDate(
                        "湖人", "勇士", LocalDate.of(2026, 7, 17)))
                .thenReturn(Optional.of(match));
        when(gameNewsRepository.findByNbaGameId("401881845"))
                .thenReturn(List.of(owner));

        Integer updated = ReflectionTestUtils.invokeMethod(service, "backfillNbaGameId");

        assertThat(updated).isZero();
        assertThat(target.getNbaGameId()).isNull();
        verify(gameNewsRepository, never()).save(target);
    }

    @Test
    void updateMatchRecord_backfillsMissingGameTypeOnExistingRecord() {
        MatchRecord existing = new MatchRecord();
        existing.setHomeTeam("Lakers");
        existing.setAwayTeam("Grizzlies");
        existing.setHomeScore(90);
        existing.setAwayScore(88);
        existing.setMatchDate(LocalDate.of(2026, 7, 21));
        existing.setSeason("2025-26");
        existing.setStatus("LIVE");
        existing.setNbaGameId("401881845");

        when(matchRecordRepository.findByHomeTeamAndAwayTeamAndMatchDate(
                "Lakers", "Grizzlies", LocalDate.now()))
                .thenReturn(Optional.of(existing));

        ReflectionTestUtils.invokeMethod(service, "updateMatchRecord",
                "Lakers", "Grizzlies", 94, 90, "FINISHED", "401881845");

        assertThat(existing.getGameType()).isEqualTo(MatchGameTypeResolver.SUMMER_LEAGUE);
        verify(matchRecordRepository).save(same(existing));
    }

    @Test
    void saveMatchRecord_backfillsMissingGameTypeOnExistingRecord() {
        LocalDate matchDate = LocalDate.of(2026, 7, 21);
        MatchRecord existing = match("401881845");
        existing.setMatchDate(matchDate);
        existing.setGameType(null);

        when(matchRecordRepository.existsByHomeTeamAndAwayTeamAndMatchDate(
                "Lakers", "Grizzlies", matchDate)).thenReturn(true);
        when(matchRecordRepository.findByHomeTeamAndAwayTeamAndMatchDate(
                "Lakers", "Grizzlies", matchDate)).thenReturn(Optional.of(existing));

        ReflectionTestUtils.invokeMethod(service, "saveMatchRecord",
                new org.json.JSONObject()
                        .put("gameId", "401881845")
                        .put("status", "FINISHED"),
                "Lakers", "Grizzlies", matchDate);

        assertThat(existing.getGameType()).isEqualTo(MatchGameTypeResolver.SUMMER_LEAGUE);
        verify(matchRecordRepository).save(same(existing));
    }

    private static GameNews gameNews(Long id, String nbaGameId) {
        GameNews news = new GameNews();
        news.setId(id);
        news.setHomeTeam("湖人");
        news.setAwayTeam("勇士");
        news.setGameStartTime(LocalDateTime.of(2026, 7, 17, 19, 30));
        news.setNbaGameId(nbaGameId);
        return news;
    }

    private static MatchRecord match(String nbaGameId) {
        MatchRecord match = new MatchRecord();
        match.setHomeTeam("湖人");
        match.setAwayTeam("勇士");
        match.setMatchDate(LocalDate.of(2026, 7, 17));
        match.setNbaGameId(nbaGameId);
        return match;
    }
}

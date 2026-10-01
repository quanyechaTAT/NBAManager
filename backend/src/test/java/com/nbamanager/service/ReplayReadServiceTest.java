package com.nbamanager.service;

import static com.nbamanager.domain.replay.ReplayPageState.NOT_STARTED;
import static com.nbamanager.domain.replay.ReplayPageState.PARTIAL;
import static com.nbamanager.domain.replay.ReplayPageState.PREPARING;
import static com.nbamanager.domain.replay.ReplayPageState.READY;
import static com.nbamanager.domain.replay.ReplayPageState.UNAVAILABLE;
import static com.nbamanager.domain.replay.ReplayQualityLevel.FULL;
import static com.nbamanager.domain.replay.ReplayQualityLevel.SUMMARY_ONLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.AiGameSummary;
import com.nbamanager.domain.GameHighlight;
import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import com.nbamanager.repository.AiGameSummaryRepository;
import com.nbamanager.repository.GameHighlightRepository;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import com.nbamanager.repository.GameTurningPointRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.repository.ReplaySyncStateRepository;
import com.nbamanager.web.dto.ReplayBootstrapDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

@ExtendWith(MockitoExtension.class)
class ReplayReadServiceTest {

    private static final String GAME_ID = "0042500405";
    private static final LocalDateTime SYNCED_AT = LocalDateTime.parse("2026-07-20T21:00:00");

    @Mock private MatchRecordRepository matchRepository;
    @Mock private PlayByPlayRepository playByPlayRepository;
    @Mock private GameMomentumSnapshotRepository momentumRepository;
    @Mock private GameHighlightRepository highlightRepository;
    @Mock private GameTurningPointRepository turningPointRepository;
    @Mock private AiGameSummaryRepository summaryRepository;
    @Mock private ReplaySyncStateRepository syncStateRepository;

    private ReplayFactsHashService factsHashService;
    private ReplayReadService service;

    @BeforeEach
    void setUp() {
        factsHashService = new ReplayFactsHashService();
        ReplayQualityService qualityService = new ReplayQualityService(factsHashService);
        service = new ReplayReadService(
                matchRepository,
                playByPlayRepository,
                momentumRepository,
                highlightRepository,
                turningPointRepository,
                summaryRepository,
                syncStateRepository,
                qualityService);
    }

    @Test
    void scheduledMatchReturnsImmediatelyWithoutReadingReplayFacts() {
        MatchRecord match = match("SCHEDULED", 0, 0);
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(NOT_STARTED);
        assertThat(result.timeline()).isEmpty();
        assertThat(result.error()).isNull();
        verifyNoInteractions(
                playByPlayRepository,
                momentumRepository,
                highlightRepository,
                turningPointRepository,
                summaryRepository,
                syncStateRepository);
    }

    @Test
    void fullFactsReturnOneConsistentReadySnapshotAndMatchingCachedSummary() {
        MatchRecord match = match("FINISHED", 40, 30);
        List<PlayByPlay> events = completeEvents();
        ReplaySyncState syncState = syncState(ReplayPageState.READY, ReplayQualityLevel.FULL);
        GameMomentumSnapshot momentum = new GameMomentumSnapshot();
        momentum.setId(11L);
        momentum.setEventOrder(120);
        momentum.setPeriod(4);
        momentum.setGameClock("00:00");
        momentum.setHomeScore(40);
        momentum.setAwayScore(30);
        momentum.setScoreMargin(10);
        GameHighlight highlight = new GameHighlight();
        highlight.setId(12L);
        highlight.setEventId(120L);
        highlight.setPeriod(4);
        highlight.setGameClock("00:00");
        highlight.setHighlightType("CLUTCH");
        GameTurningPoint turningPoint = new GameTurningPoint();
        turningPoint.setId(13L);
        turningPoint.setPeriod(4);
        turningPoint.setGameClock("00:00");
        turningPoint.setType("LEAD_CHANGE");
        String factsHash = factsHashService.hash(events);
        AiGameSummary summary = new AiGameSummary();
        summary.setGameId(GAME_ID);
        summary.setSummaryType("FULL");
        summary.setFactsHash(factsHash);
        summary.setContent("Cached and grounded summary");
        summary.setCitedEventIdsJson("[120]");

        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.of(syncState));
        when(momentumRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of(momentum));
        when(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of(highlight));
        when(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID))
                .thenReturn(List.of(turningPoint));
        when(summaryRepository.findByGameIdAndSummaryType(GAME_ID, "FULL")).thenReturn(Optional.of(summary));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(READY);
        assertThat(result.quality().level()).isEqualTo(FULL);
        assertThat(result.quality().factsHash()).isEqualTo(factsHash);
        assertThat(result.timeline()).hasSize(120);
        assertThat(result.momentum()).hasSize(1);
        assertThat(result.highlights()).hasSize(1);
        assertThat(result.highlights().get(0).eventId()).isEqualTo(120L);
        assertThat(result.turningPoints()).hasSize(1);
        assertThat(result.cachedSummary()).isNotNull();
        assertThat(result.cachedSummary().content()).isEqualTo("Cached and grounded summary");
    }

    @Test
    void summaryOnlyFactsStayVisibleButDerivedAndAiDataAreSuppressed() {
        MatchRecord match = match("FINISHED", 100, 92);
        List<PlayByPlay> events = periodScores();
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.empty());

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.quality().level()).isEqualTo(SUMMARY_ONLY);
        assertThat(result.timeline()).hasSize(4);
        assertThat(result.momentum()).isEmpty();
        assertThat(result.highlights()).isEmpty();
        assertThat(result.turningPoints()).isEmpty();
        assertThat(result.cachedSummary()).isNull();
        verifyNoInteractions(momentumRepository, highlightRepository, turningPointRepository, summaryRepository);
    }

    @Test
    void noFactsUseAnActiveSyncLeaseAsPreparingState() {
        MatchRecord match = match("FINISHED", 100, 90);
        ReplaySyncState syncState = syncState(ReplayPageState.PREPARING, ReplayQualityLevel.UNAVAILABLE);
        syncState.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.of(syncState));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(PREPARING);
        assertThat(result.error()).isNull();
        verifyNoInteractions(momentumRepository, highlightRepository, turningPointRepository, summaryRepository);
    }

    @Test
    void noFactsExposeAStableUnavailableErrorWithoutLeakingStoredDetails() {
        MatchRecord match = match("FINISHED", 100, 90);
        ReplaySyncState syncState = syncState(ReplayPageState.UNAVAILABLE, ReplayQualityLevel.UNAVAILABLE);
        syncState.setLastErrorCode("REPLAY_SOURCE_UNAVAILABLE");
        syncState.setLastErrorMessage("provider response containing private upstream diagnostics");
        syncState.setLastErrorAt(SYNCED_AT);
        syncState.setNextRetryAt(SYNCED_AT.plusMinutes(5));
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.of(syncState));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(UNAVAILABLE);
        assertThat(result.error().code()).isEqualTo("REPLAY_SOURCE_UNAVAILABLE");
        assertThat(result.error().message()).doesNotContain("private upstream diagnostics");
        assertThat(result.error().retryable()).isTrue();
        assertThat(result.error().lastAttemptAt()).isEqualTo(SYNCED_AT);
        assertThat(result.error().nextRetryAt()).isEqualTo(SYNCED_AT.plusMinutes(5));
    }

    @Test
    void missingMatchReturnsStableNotFoundWithoutQueryingReplayRepositories() {
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.empty());

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(UNAVAILABLE);
        assertThat(result.error().code()).isEqualTo("REPLAY_NOT_FOUND");
        assertThat(result.error().retryable()).isFalse();
        verifyNoInteractions(
                playByPlayRepository,
                momentumRepository,
                highlightRepository,
                turningPointRepository,
                summaryRepository,
                syncStateRepository);
    }

    @Test
    void malformedGameIdIsRejectedBeforeAnyRepositoryQuery() {
        ReplayBootstrapDto result = service.bootstrap("not-a-game-id");

        assertThat(result.state()).isEqualTo(UNAVAILABLE);
        assertThat(result.error().code()).isEqualTo("REPLAY_INVALID_GAME_ID");
        assertThat(result.error().retryable()).isFalse();
        verifyNoInteractions(
                matchRepository,
                playByPlayRepository,
                momentumRepository,
                highlightRepository,
                turningPointRepository,
                summaryRepository,
                syncStateRepository);
    }

    @Test
    void staleSummaryFromDifferentFactsHashIsNotReturned() {
        MatchRecord match = match("FINISHED", 40, 30);
        List<PlayByPlay> events = completeEvents();
        AiGameSummary stale = new AiGameSummary();
        stale.setFactsHash("stale-hash");
        stale.setSummaryType("FULL");
        stale.setContent("Stale summary");
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(momentumRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());
        when(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(summaryRepository.findByGameIdAndSummaryType(GAME_ID, "FULL")).thenReturn(Optional.of(stale));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(READY);
        assertThat(result.cachedSummary()).isNull();
        verify(summaryRepository).findByGameIdAndSummaryType(GAME_ID, "FULL");
    }

    @Test
    void legacySummaryWithoutCitationsIsNotReturned() {
        MatchRecord match = match("FINISHED", 40, 30);
        List<PlayByPlay> events = completeEvents();
        AiGameSummary ungrounded = new AiGameSummary();
        ungrounded.setFactsHash(factsHashService.hash(events));
        ungrounded.setSummaryType("FULL");
        ungrounded.setContent("Unsupported legacy summary");
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(momentumRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());
        when(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(summaryRepository.findByGameIdAndSummaryType(GAME_ID, "FULL"))
                .thenReturn(Optional.of(ungrounded));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(READY);
        assertThat(result.cachedSummary()).isNull();
    }

    @Test
    void duplicateLegacySummaryRowsCannotBreakTheReplaySnapshot() {
        MatchRecord match = match("FINISHED", 40, 30);
        List<PlayByPlay> events = completeEvents();
        when(matchRepository.findByNbaGameId(GAME_ID)).thenReturn(Optional.of(match));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(syncStateRepository.findByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(momentumRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());
        when(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(GAME_ID)).thenReturn(List.of());
        when(summaryRepository.findByGameIdAndSummaryType(GAME_ID, "FULL"))
                .thenThrow(new IncorrectResultSizeDataAccessException(2));

        ReplayBootstrapDto result = service.bootstrap(GAME_ID);

        assertThat(result.state()).isEqualTo(READY);
        assertThat(result.cachedSummary()).isNull();
        assertThat(result.timeline()).hasSize(120);
    }

    private MatchRecord match(String status, int homeScore, int awayScore) {
        MatchRecord match = new MatchRecord();
        match.setNbaGameId(GAME_ID);
        match.setStatus(status);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }

    private ReplaySyncState syncState(ReplayPageState status, ReplayQualityLevel quality) {
        ReplaySyncState state = new ReplaySyncState();
        state.setGameId(GAME_ID);
        state.setStatus(status);
        state.setQuality(quality);
        state.setCompletedAt(SYNCED_AT);
        return state;
    }

    private List<PlayByPlay> completeEvents() {
        List<PlayByPlay> events = new ArrayList<>();
        int homeScore = 0;
        int awayScore = 0;
        for (int order = 1; order <= 120; order++) {
            boolean homeScored = order % 3 == 0;
            boolean awayScored = order % 4 == 0;
            if (homeScored) homeScore++;
            if (awayScored) awayScore++;
            PlayByPlay event = event(
                    order,
                    ((order - 1) / 30) + 1,
                    homeScored || awayScored ? "made_shot" : "rebound",
                    homeScore,
                    awayScore,
                    homeScored || awayScored);
            events.add(event);
        }
        return events;
    }

    private List<PlayByPlay> periodScores() {
        List<PlayByPlay> events = new ArrayList<>();
        for (int period = 1; period <= 4; period++) {
            PlayByPlay event = event(period, period, "period_score", period * 25, period * 23, false);
            event.setActionType("period_score");
            events.add(event);
        }
        return events;
    }

    private PlayByPlay event(
            int order,
            int period,
            String type,
            int homeScore,
            int awayScore,
            boolean scoring) {
        PlayByPlay event = new PlayByPlay();
        event.setId((long) order);
        event.setGameId(GAME_ID);
        event.setEventOrder(order);
        event.setPeriod(period);
        event.setGameClock("00:00");
        event.setEventType(type);
        event.setDescription("Event " + order);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        event.setScoreMargin(homeScore - awayScore);
        event.setIsScoringPlay(scoring);
        event.setSourceEventId("source-" + order);
        event.setSource("NBA_PLAY_BY_PLAY");
        return event;
    }
}

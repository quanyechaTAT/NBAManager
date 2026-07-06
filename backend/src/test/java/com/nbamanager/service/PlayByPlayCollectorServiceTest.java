package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayByPlayCollectorServiceTest {

    @Mock
    private PlayByPlayProviderManager providerManager;

    @Mock
    private PlayByPlayNormalizeService normalizeService;

    @Mock
    private PlayByPlayValidationService validationService;

    @Mock
    private PlayByPlaySyncLogService syncLogService;

    @Mock
    private MomentumComputeService momentumComputeService;

    @Mock
    private HighlightDetectService highlightDetectService;

    @Mock
    private TurningPointDetectService turningPointDetectService;

    @Mock
    private PlayByPlayRepository playByPlayRepository;

    @InjectMocks
    private PlayByPlayCollectorService collectorService;

    private PlayByPlayEvent createPlayByPlayEvent(int eventOrder, int period, String gameClock,
                                                   int homeScore, int awayScore, int scoreMargin) {
        return new PlayByPlayEvent(
                "event_" + eventOrder,
                period,
                gameClock,
                "SHOT",
                "JUMPSHOT",
                "Description",
                "Player",
                1L,
                "Team",
                1L,
                homeScore,
                awayScore,
                scoreMargin,
                true,
                false,
                eventOrder,
                "{}"
        );
    }

    @Test
    void collect_returnsSuccessWhenProviderSucceeds() {
        String gameId = "game_001";
        List<PlayByPlayEvent> events = List.of(
                createPlayByPlayEvent(0, 1, "12:00", 0, 0, 0),
                createPlayByPlayEvent(1, 1, "10:30", 5, 3, 2)
        );

        PlayByPlayFetchResult fetchResult = PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY, events, "{}", 100L);

        PlayByPlaySyncLog syncLog = new PlayByPlaySyncLog();
        syncLog.setId(1L);
        syncLog.setGameId(gameId);

        when(syncLogService.hasSuccessfulSync(gameId)).thenReturn(false);
        when(providerManager.fetch(eq(gameId), any(PlayByPlayFetchOptions.class))).thenReturn(fetchResult);
        when(syncLogService.startSync(gameId, "NBA_PLAY_BY_PLAY")).thenReturn(syncLog);

        when(validationService.filterValidEvents(events)).thenReturn(events);

        List<PlayByPlay> normalizedEntities = List.of(new PlayByPlay(), new PlayByPlay());
        when(normalizeService.normalizeAll(events, gameId, ProviderType.NBA_PLAY_BY_PLAY)).thenReturn(normalizedEntities);
        when(playByPlayRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        PlayByPlayCollectorService.CollectResult result = collectorService.collect(gameId);

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo("NBA_PLAY_BY_PLAY");
        assertThat(result.eventCount()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("Success");

        verify(syncLogService).hasSuccessfulSync(gameId);
        verify(providerManager).fetch(eq(gameId), any(PlayByPlayFetchOptions.class));
        verify(syncLogService).startSync(gameId, "NBA_PLAY_BY_PLAY");
        verify(validationService).filterValidEvents(events);
        verify(normalizeService).normalizeAll(events, gameId, ProviderType.NBA_PLAY_BY_PLAY);
        verify(playByPlayRepository).saveAll(normalizedEntities);
        verify(momentumComputeService).compute(gameId, events);
        verify(highlightDetectService).detect(gameId, events);
        verify(turningPointDetectService).detect(gameId, events);
        verify(syncLogService).logSuccess(syncLog, 2);
    }

    @Test
    void collect_returnsCachedWhenAlreadySynced() {
        String gameId = "game_002";

        when(syncLogService.hasSuccessfulSync(gameId)).thenReturn(true);

        PlayByPlayCollectorService.CollectResult result = collectorService.collect(gameId);

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo("cached");
        assertThat(result.eventCount()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("cached");

        verify(syncLogService).hasSuccessfulSync(gameId);
        verifyNoInteractions(providerManager);
        verifyNoInteractions(normalizeService);
        verifyNoInteractions(validationService);
        verifyNoInteractions(playByPlayRepository);
        verifyNoInteractions(momentumComputeService);
        verifyNoInteractions(highlightDetectService);
        verifyNoInteractions(turningPointDetectService);
    }
}

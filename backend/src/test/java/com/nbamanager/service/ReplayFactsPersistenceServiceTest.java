package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import com.nbamanager.web.dto.ReplayQualityDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayFactsPersistenceServiceTest {

    @Mock private PlayByPlayNormalizeService normalizeService;
    @Mock private PlayByPlayValidationService validationService;
    @Mock private PlayByPlaySyncLogService syncLogService;
    @Mock private MomentumComputeService momentumComputeService;
    @Mock private HighlightDetectService highlightDetectService;
    @Mock private TurningPointDetectService turningPointDetectService;
    @Mock private PlayByPlayRepository playByPlayRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private ReplayFactsHashService factsHashService;
    @Mock private ReplayQualityService qualityService;

    @InjectMocks
    private ReplayFactsPersistenceService service;

    @Test
    void persistAtomicallyReplacesFactsBeforeRebuildingDerivedData() {
        String gameId = "0022500001";
        PlayByPlayEvent event = event(1);
        PlayByPlayFetchResult fetch = fetch(event);
        PlayByPlay normalized = fact(1);
        PlayByPlaySyncLog log = new PlayByPlaySyncLog();
        ReplayQualityDto quality = quality();

        when(syncLogService.startSync(gameId, "NBA_PLAY_BY_PLAY", null)).thenReturn(log);
        when(validationService.filterValidEvents(List.of(event))).thenReturn(List.of(event));
        when(normalizeService.normalizeAll(List.of(event), gameId, ProviderType.NBA_PLAY_BY_PLAY))
                .thenReturn(List.of(normalized));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId)).thenReturn(List.of(fact(0)));
        when(factsHashService.hash(List.of(normalized))).thenReturn("new-hash");
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.empty());
        when(qualityService.evaluate(anyList(), nullable(MatchRecord.class), any())).thenReturn(quality);
        when(playByPlayRepository.saveAll(List.of(normalized))).thenReturn(List.of(normalized));

        ReplayFactsPersistenceService.PersistResult result = service.persist(gameId, fetch, null);

        assertThat(result.success()).isTrue();
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.factsHash()).isEqualTo("new-hash");
        InOrder order = inOrder(playByPlayRepository, momentumComputeService,
                highlightDetectService, turningPointDetectService, syncLogService);
        order.verify(playByPlayRepository).deleteByGameId(gameId);
        order.verify(playByPlayRepository).saveAll(List.of(normalized));
        order.verify(momentumComputeService).compute(gameId, List.of(event));
        order.verify(highlightDetectService).detect(gameId, List.of(event), List.of(normalized));
        order.verify(turningPointDetectService).detect(gameId, List.of(event), List.of(normalized));
        order.verify(syncLogService).logSuccess(log, 1);
    }

    @Test
    void persistShortCircuitsOnlyWhenHashAndPersistedCountBothMatch() {
        String gameId = "0022500002";
        PlayByPlayEvent event = event(1);
        PlayByPlayFetchResult fetch = fetch(event);
        PlayByPlay normalized = fact(1);
        PlayByPlaySyncLog log = new PlayByPlaySyncLog();

        when(syncLogService.startSync(gameId, "NBA_PLAY_BY_PLAY", null)).thenReturn(log);
        when(validationService.filterValidEvents(List.of(event))).thenReturn(List.of(event));
        when(normalizeService.normalizeAll(List.of(event), gameId, ProviderType.NBA_PLAY_BY_PLAY))
                .thenReturn(List.of(normalized));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId)).thenReturn(List.of(fact(1)));
        when(factsHashService.hash(anyList())).thenReturn("same-hash");
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.empty());
        when(qualityService.evaluate(anyList(), nullable(MatchRecord.class), any())).thenReturn(quality());

        ReplayFactsPersistenceService.PersistResult result = service.persist(gameId, fetch, null);

        assertThat(result.message()).isEqualTo("unchanged");
        verify(playByPlayRepository, never()).deleteByGameId(gameId);
        verify(playByPlayRepository, never()).saveAll(anyList());
        verify(momentumComputeService, never()).compute(any(), anyList());
        verify(highlightDetectService, never()).detect(any(), anyList(), anyList());
        verify(turningPointDetectService, never()).detect(any(), anyList(), anyList());
    }

    private PlayByPlayFetchResult fetch(PlayByPlayEvent event) {
        return PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY, List.of(event), "{}", 1L);
    }

    private PlayByPlayEvent event(int order) {
        return new PlayByPlayEvent(
                "evt-" + order, 1, "12:00", "SHOT", "JUMPSHOT", "shot",
                "Player", 1L, "Team", 2L, 2, 0, 2, true, false, order, "{}");
    }

    private PlayByPlay fact(int order) {
        PlayByPlay fact = new PlayByPlay();
        fact.setGameId("0022500001");
        fact.setEventOrder(order);
        fact.setPeriod(1);
        fact.setGameClock("12:00");
        fact.setHomeScore(order > 0 ? 2 : 0);
        fact.setAwayScore(0);
        fact.setSourceEventId("evt-" + order);
        fact.setSource("NBA_PLAY_BY_PLAY");
        return fact;
    }

    private ReplayQualityDto quality() {
        return new ReplayQualityDto(
                com.nbamanager.domain.replay.ReplayQualityLevel.PARTIAL,
                1, 1, List.of(1), "NBA_PLAY_BY_PLAY", "NBA official",
                false, false, true, null, List.of("LOW_EVENT_DENSITY"), "same-hash");
    }
}

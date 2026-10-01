package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
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
    private ReplayFactsPersistenceService persistenceService;

    @InjectMocks
    private PlayByPlayCollectorService collectorService;

    @Test
    void collectAlwaysFetchesAndDelegatesPersistenceWithoutLegacySuccessCache() {
        String gameId = "0022500001";
        PlayByPlayFetchResult fetchResult = PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY,
                List.of(event(1)),
                "{}",
                12L);
        ReplayFactsPersistenceService.PersistResult persisted =
                new ReplayFactsPersistenceService.PersistResult(
                        true, "NBA_PLAY_BY_PLAY", 1, "Success", "PARTIAL", "facts-1");

        when(providerManager.fetch(eq(gameId), any(PlayByPlayFetchOptions.class))).thenReturn(fetchResult);
        when(persistenceService.persist(gameId, fetchResult, null)).thenReturn(persisted);

        PlayByPlayCollectorService.CollectResult result = collectorService.collect(gameId);

        assertThat(result.success()).isTrue();
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.factsHash()).isEqualTo("facts-1");
        verify(providerManager).fetch(eq(gameId), any(PlayByPlayFetchOptions.class));
        verify(persistenceService).persist(gameId, fetchResult, null);
    }

    @Test
    void collectDoesNotPersistWhenProviderFails() {
        String gameId = "0022500002";
        PlayByPlayFetchResult fetchResult = PlayByPlayFetchResult.failure(
                ProviderType.NBA_PLAY_BY_PLAY, "UPSTREAM_TIMEOUT", "timeout", true, null, 10L);
        when(providerManager.fetch(eq(gameId), any(PlayByPlayFetchOptions.class))).thenReturn(fetchResult);

        PlayByPlayCollectorService.CollectResult result = collectorService.collect(gameId);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("timeout");
        verifyNoInteractions(persistenceService);
    }

    private PlayByPlayEvent event(int order) {
        return new PlayByPlayEvent(
                "event-" + order, 1, "12:00", "SHOT", "JUMPSHOT", "shot",
                "Player", 1L, "Team", 2L, 2, 0, 2, true, false, order, "{}");
    }
}

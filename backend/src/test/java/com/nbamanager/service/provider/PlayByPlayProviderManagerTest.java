package com.nbamanager.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayByPlayProviderManagerTest {

    @Mock
    private NbaPlayByPlayProvider nbaProvider;

    @Mock
    private EspnPlayByPlayProvider espnProvider;

    private static final String GAME_ID = "0022501001";

    @Test
    void fetch_usesFirstProviderWhenSuccess() {
        PlayByPlayFetchOptions options = PlayByPlayFetchOptions.defaults();
        PlayByPlayEvent event = createTestEvent(1, "10:00", "Jump Shot", "LeBron James");
        PlayByPlayFetchResult successResult = PlayByPlayFetchResult.success(
                ProviderType.NBA_PLAY_BY_PLAY, List.of(event), "{}", 50);

        when(nbaProvider.type()).thenReturn(ProviderType.NBA_PLAY_BY_PLAY);
        when(nbaProvider.supports(GAME_ID)).thenReturn(true);
        when(nbaProvider.fetch(eq(GAME_ID), any(PlayByPlayFetchOptions.class))).thenReturn(successResult);

        when(espnProvider.type()).thenReturn(ProviderType.ESPN_PLAY_BY_PLAY);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch(GAME_ID, options);

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo(ProviderType.NBA_PLAY_BY_PLAY);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).playerName()).isEqualTo("LeBron James");

        // ESPN should never be called when NBA succeeds
        verify(espnProvider, never()).supports(any());
        verify(espnProvider, never()).fetch(any(), any());
    }

    @Test
    void fetch_fallsBackToSecondProvider() {
        PlayByPlayFetchOptions options = PlayByPlayFetchOptions.defaults();

        PlayByPlayFetchResult failureResult = PlayByPlayFetchResult.failure(
                ProviderType.NBA_PLAY_BY_PLAY, "NBA API timeout", 100);

        PlayByPlayEvent event = createTestEvent(2, "5:30", "3PT", "Stephen Curry");
        PlayByPlayFetchResult successResult = PlayByPlayFetchResult.success(
                ProviderType.ESPN_PLAY_BY_PLAY, List.of(event), "{}", 200);

        when(nbaProvider.type()).thenReturn(ProviderType.NBA_PLAY_BY_PLAY);
        when(nbaProvider.supports(GAME_ID)).thenReturn(true);
        when(nbaProvider.fetch(eq(GAME_ID), any(PlayByPlayFetchOptions.class))).thenReturn(failureResult);

        when(espnProvider.type()).thenReturn(ProviderType.ESPN_PLAY_BY_PLAY);
        when(espnProvider.supports(GAME_ID)).thenReturn(true);
        when(espnProvider.fetch(eq(GAME_ID), any(PlayByPlayFetchOptions.class))).thenReturn(successResult);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch(GAME_ID, options);

        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo(ProviderType.ESPN_PLAY_BY_PLAY);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).playerName()).isEqualTo("Stephen Curry");
    }

    @Test
    void fetch_returnsFailureWhenAllFail() {
        PlayByPlayFetchOptions options = PlayByPlayFetchOptions.defaults();

        PlayByPlayFetchResult nbaFailure = PlayByPlayFetchResult.failure(
                ProviderType.NBA_PLAY_BY_PLAY, "NBA API timeout", 100);

        when(nbaProvider.type()).thenReturn(ProviderType.NBA_PLAY_BY_PLAY);
        when(nbaProvider.supports(GAME_ID)).thenReturn(true);
        when(nbaProvider.fetch(eq(GAME_ID), any(PlayByPlayFetchOptions.class))).thenReturn(nbaFailure);

        when(espnProvider.type()).thenReturn(ProviderType.ESPN_PLAY_BY_PLAY);
        when(espnProvider.supports(GAME_ID)).thenReturn(false);

        PlayByPlayProviderManager manager = new PlayByPlayProviderManager(List.of(nbaProvider, espnProvider));
        PlayByPlayFetchResult result = manager.fetch(GAME_ID, options);

        assertThat(result.success()).isFalse();
        assertThat(result.source()).isNull();
        assertThat(result.errorMessage()).contains("All providers failed");
        assertThat(result.errorMessage()).contains("NBA_PLAY_BY_PLAY: NBA API timeout");
        assertThat(result.fetchTimeMs()).isEqualTo(0);
    }

    private PlayByPlayEvent createTestEvent(int period, String clock, String eventType, String playerName) {
        return new PlayByPlayEvent(
                GAME_ID + "_" + period + "_" + clock,
                period,
                clock,
                eventType,
                null,
                eventType + " by " + playerName,
                playerName,
                123L,
                null,
                456L,
                100,
                95,
                5,
                true,
                false,
                1,
                "{}"
        );
    }
}

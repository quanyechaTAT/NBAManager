package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.AiGameSummary;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.repository.AiGameSummaryRepository;
import com.nbamanager.repository.AiReplayExplanationRepository;
import com.nbamanager.repository.GameBoxScoreRepository;
import com.nbamanager.repository.GameHighlightRepository;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import com.nbamanager.repository.GameTurningPointRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.deepseek.AiGroundingValidator;
import com.nbamanager.service.deepseek.DeepSeekClient;
import com.nbamanager.web.dto.GameSummaryDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiReplayServiceTest {

    private static final String GAME_ID = "0042500405";
    private static final String PROMPT_VERSION = "replay-ai-v2";

    @Mock private PlayByPlayRepository playByPlayRepository;
    @Mock private GameMomentumSnapshotRepository momentumRepository;
    @Mock private GameTurningPointRepository turningPointRepository;
    @Mock private GameHighlightRepository highlightRepository;
    @Mock private AiReplayExplanationRepository explanationRepository;
    @Mock private AiGameSummaryRepository summaryRepository;
    @Mock private GameBoxScoreRepository boxScoreRepository;
    @Mock private DeepSeekClient deepSeekClient;
    @Mock private PlayByPlayTranslationService translationService;

    private final AiGroundingValidator groundingValidator = new AiGroundingValidator();

    @Test
    void generateSummaryReturnsEmptyResultWithoutCallingDeepSeekWhenFactsAreMissing() {
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(List.of());

        GameSummaryDto result = service().generateSummary(GAME_ID);

        assertThat(result.content()).isNull();
        assertThat(result.citedEventIdsJson()).isNull();
        verifyNoInteractions(deepSeekClient, summaryRepository);
    }

    @Test
    void generateSummaryUsesGroundedCacheForTheCurrentFactsPromptAndModel() {
        List<PlayByPlay> events = List.of(event(1), event(2));
        AiGameSummary cached = new AiGameSummary();
        cached.setGameId(GAME_ID);
        cached.setSummaryType("FULL");
        cached.setContent("cached grounded summary");
        cached.setCitedEventIdsJson("[1]");
        cached.setModelProvider("deepseek");
        cached.setModelName("deepseek-test");
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(translationService.translateTexts(List.of("Event 1", "Event 2")))
                .thenReturn(List.of("Event 1", "Event 2"));
        when(boxScoreRepository.findByGameId(GAME_ID)).thenReturn(List.of());
        when(deepSeekClient.configuredModel()).thenReturn("deepseek-test");
        when(summaryRepository.findFirstByGameIdAndSummaryTypeAndFactsHashAndPromptVersionAndModelNameOrderByCreatedAtDesc(
                eq(GAME_ID), eq("FULL"), anyString(), eq(PROMPT_VERSION), eq("deepseek-test")))
                .thenReturn(Optional.of(cached));

        GameSummaryDto result = service().generateSummary(GAME_ID);

        assertThat(result.content()).isEqualTo("cached grounded summary");
        verify(deepSeekClient).configuredModel();
        verify(deepSeekClient, never()).chat(anyString(), anyString());
    }

    @Test
    void generateSummaryIgnoresUngroundedCacheAndSavesOnlyValidatedCitations() {
        List<PlayByPlay> events = List.of(event(1), event(2));
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(GAME_ID)).thenReturn(events);
        when(translationService.translateTexts(List.of("Event 1", "Event 2")))
                .thenReturn(List.of("Event 1", "Event 2"));
        when(boxScoreRepository.findByGameId(GAME_ID)).thenReturn(List.of());
        when(deepSeekClient.configuredModel()).thenReturn("deepseek-test");
        when(summaryRepository.findFirstByGameIdAndSummaryTypeAndFactsHashAndPromptVersionAndModelNameOrderByCreatedAtDesc(
                eq(GAME_ID), eq("FULL"), anyString(), eq(PROMPT_VERSION), eq("deepseek-test")))
                .thenReturn(Optional.of(ungroundedCache()));
        when(deepSeekClient.chat(anyString(), anyString()))
                .thenReturn(new DeepSeekClient.ChatResponse(
                        "{\"claims\":[{\"claimId\":\"c1\",\"text\":\"Event 1 changed the game.\",\"citationKeys\":[\"event:1\"]}]}",
                        "deepseek-test",
                        true,
                        null));
        ArgumentCaptor<AiGameSummary> saved = ArgumentCaptor.forClass(AiGameSummary.class);

        GameSummaryDto result = service().generateSummary(GAME_ID);

        assertThat(result.content()).isEqualTo("Event 1 changed the game.");
        assertThat(result.citedEventIdsJson()).isEqualTo("[1]");
        verify(summaryRepository).save(saved.capture());
        assertThat(saved.getValue().getCitedEventIdsJson()).isEqualTo("[1]");
        assertThat(saved.getValue().getPromptVersion()).isEqualTo(PROMPT_VERSION);
    }

    @Test
    void explainEventRejectsEventsThatDoNotBelongToTheRequestedGame() {
        when(playByPlayRepository.findByIdAndGameId(999L, GAME_ID)).thenReturn(Optional.empty());

        assertThat(service().explainEvent(GAME_ID, 999L)).isNull();

        verifyNoInteractions(deepSeekClient);
        verify(explanationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private AiReplayService service() {
        return new AiReplayService(
                playByPlayRepository,
                momentumRepository,
                turningPointRepository,
                highlightRepository,
                explanationRepository,
                summaryRepository,
                boxScoreRepository,
                deepSeekClient,
                groundingValidator,
                translationService);
    }

    private AiGameSummary ungroundedCache() {
        AiGameSummary cache = new AiGameSummary();
        cache.setGameId(GAME_ID);
        cache.setSummaryType("FULL");
        cache.setContent("legacy ungrounded summary");
        cache.setModelName("deepseek-test");
        return cache;
    }

    private PlayByPlay event(int order) {
        PlayByPlay event = new PlayByPlay();
        event.setId((long) order);
        event.setGameId(GAME_ID);
        event.setPeriod(4);
        event.setGameClock("0" + order + ":00");
        event.setEventType("made_shot");
        event.setActionType("jump_shot");
        event.setDescription("Event " + order);
        event.setPlayerName("Player " + order);
        event.setPlayerId((long) order);
        event.setTeamName("Team");
        event.setTeamId(1L);
        event.setHomeScore(100 + order);
        event.setAwayScore(98);
        event.setScoreMargin(order + 2);
        event.setIsScoringPlay(true);
        event.setIsHighlightCandidate(order == 1);
        event.setEventOrder(order);
        event.setSource("NBA_PLAY_BY_PLAY");
        event.setSourceEventId("source-" + order);
        return event;
    }
}

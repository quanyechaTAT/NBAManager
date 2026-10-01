package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.PlayByPlay;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ReplayFactsHashServiceTest {

    private final ReplayFactsHashService service = new ReplayFactsHashService();

    @Test
    void hashIsIndependentOfRepositoryOrderAndPersistenceMetadata() {
        PlayByPlay first = event(1, 1, "12:00", "period_start", 0, 0, "evt-1");
        PlayByPlay second = event(2, 1, "11:42", "made_shot", 2, 0, "evt-2");
        List<PlayByPlay> original = List.of(first, second);

        PlayByPlay sameFacts = event(2, 1, "11:42", "made_shot", 2, 0, "evt-2");
        sameFacts.setId(999L);
        sameFacts.setCreatedAt(LocalDateTime.parse("2026-07-20T21:00:00"));
        sameFacts.setUpdatedAt(LocalDateTime.parse("2026-07-20T21:01:00"));
        List<PlayByPlay> reordered = List.of(sameFacts, first);

        assertThat(service.hash(original))
                .isEqualTo(service.hash(reordered))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void everyAiVisibleReplayFactParticipatesInTheHash() {
        PlayByPlay original = event(1, 1, "12:00", "made_shot", 2, 0, "evt-1");
        original.setGameId("0042500405");
        original.setActionType("jump_shot");
        original.setPlayerId(23L);
        original.setPlayerName("Player One");
        original.setTeamId(10L);
        original.setTeamName("Home Team");
        original.setScoreMargin(2);
        original.setIsScoringPlay(true);
        original.setIsHighlightCandidate(false);
        original.setRawPayloadJson("{\"action\":\"made shot\"}");
        original.setRawPayloadHash("raw-hash-1");
        String baseline = service.hash(List.of(original));

        List<Consumer<PlayByPlay>> factMutations = List.of(
                event -> event.setGameId("0042500406"),
                event -> event.setPeriod(2),
                event -> event.setGameClock("11:59"),
                event -> event.setDescription("Changed factual description"),
                event -> event.setEventType("dunk"),
                event -> event.setActionType("dunk"),
                event -> event.setPlayerId(24L),
                event -> event.setPlayerName("Player Two"),
                event -> event.setTeamId(11L),
                event -> event.setTeamName("Away Team"),
                event -> event.setHomeScore(3),
                event -> event.setAwayScore(1),
                event -> event.setScoreMargin(1),
                event -> event.setIsScoringPlay(false),
                event -> event.setIsHighlightCandidate(true),
                event -> event.setEventOrder(2),
                event -> event.setSource("ESPN_PLAY_BY_PLAY"),
                event -> event.setSourceEventId("evt-2"),
                event -> event.setRawPayloadJson("{\"action\":\"dunk\"}"),
                event -> event.setRawPayloadHash("raw-hash-2"));

        for (Consumer<PlayByPlay> mutation : factMutations) {
            PlayByPlay changed = copy(original);
            mutation.accept(changed);
            assertThat(service.hash(List.of(changed))).isNotEqualTo(baseline);
        }
    }

    @Test
    void emptyFactsHaveAStableHash() {
        assertThat(service.hash(List.of()))
                .isEqualTo(service.hash(null))
                .hasSize(64);
    }

    private PlayByPlay event(
            int order,
            int period,
            String clock,
            String type,
            int homeScore,
            int awayScore,
            String sourceEventId) {
        PlayByPlay event = new PlayByPlay();
        event.setEventOrder(order);
        event.setPeriod(period);
        event.setGameClock(clock);
        event.setEventType(type);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        event.setSourceEventId(sourceEventId);
        event.setDescription("Original description");
        event.setSource("NBA_PLAY_BY_PLAY");
        event.setScoreMargin(homeScore - awayScore);
        event.setIsScoringPlay("made_shot".equals(type));
        event.setIsHighlightCandidate(false);
        return event;
    }

    private PlayByPlay copy(PlayByPlay source) {
        PlayByPlay copy = new PlayByPlay();
        copy.setGameId(source.getGameId());
        copy.setPeriod(source.getPeriod());
        copy.setGameClock(source.getGameClock());
        copy.setDescription(source.getDescription());
        copy.setHomeScore(source.getHomeScore());
        copy.setAwayScore(source.getAwayScore());
        copy.setEventType(source.getEventType());
        copy.setPlayerId(source.getPlayerId());
        copy.setPlayerName(source.getPlayerName());
        copy.setTeamId(source.getTeamId());
        copy.setSource(source.getSource());
        copy.setSourceEventId(source.getSourceEventId());
        copy.setEventOrder(source.getEventOrder());
        copy.setActionType(source.getActionType());
        copy.setTeamName(source.getTeamName());
        copy.setScoreMargin(source.getScoreMargin());
        copy.setIsScoringPlay(source.getIsScoringPlay());
        copy.setIsHighlightCandidate(source.getIsHighlightCandidate());
        copy.setRawPayloadJson(source.getRawPayloadJson());
        copy.setRawPayloadHash(source.getRawPayloadHash());
        return copy;
    }
}

package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayByPlayNormalizeServiceTest {

    private final PlayByPlayNormalizeService service = new PlayByPlayNormalizeService();

    @Test
    void normalize_mapsEventToEntity() {
        // Arrange
        PlayByPlayEvent event = new PlayByPlayEvent(
                "src-evt-001",
                2,
                "5:30",
                "FIELD_GOAL",
                "JUMPSHOT",
                "LeBron James makes 2-point jump shot",
                "LeBron James",
                2544L,
                "Lakers",
                1610612747L,
                54,
                51,
                3,
                true,
                false,
                1,
                "{\"shotType\":\"jumpshot\"}"
        );

        String gameId = "0022401001";
        ProviderType source = ProviderType.NBA_PLAY_BY_PLAY;

        // Act
        PlayByPlay entity = service.normalize(event, gameId, source);

        // Assert
        assertNotNull(entity);
        assertEquals(gameId, entity.getGameId());
        assertEquals(source.name(), entity.getSource());

        // Verify all mapped fields
        assertEquals(event.period(), entity.getPeriod());
        assertEquals(event.gameClock(), entity.getGameClock());
        assertEquals(event.eventType(), entity.getEventType());
        assertEquals(event.actionType(), entity.getActionType());
        assertEquals(event.description(), entity.getDescription());
        assertEquals(event.playerName(), entity.getPlayerName());
        assertEquals(event.playerId(), entity.getPlayerId());
        assertEquals(event.teamName(), entity.getTeamName());
        assertEquals(event.teamId(), entity.getTeamId());
        assertEquals(event.homeScore(), entity.getHomeScore());
        assertEquals(event.awayScore(), entity.getAwayScore());
        assertEquals(event.scoreMargin(), entity.getScoreMargin());
        assertEquals(event.isScoringPlay(), entity.getIsScoringPlay());
        assertEquals(event.isHighlightCandidate(), entity.getIsHighlightCandidate());
        assertEquals(event.eventOrder(), entity.getEventOrder());
        assertEquals(event.sourceEventId(), entity.getSourceEventId());
        assertEquals(event.rawPayloadJson(), entity.getRawPayloadJson());

        // Verify timestamps are set
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    void normalize_listPreservesOrder() {
        // Arrange
        PlayByPlayEvent event1 = new PlayByPlayEvent(
                "src-evt-001", 1, "10:00", "FIELD_GOAL", "JUMPSHOT",
                "First basket", "Player A", 100L, "Team A", 1000L,
                2, 0, 2, true, false, 1, "{}"
        );
        PlayByPlayEvent event2 = new PlayByPlayEvent(
                "src-evt-002", 1, "9:45", "FOUL", "PERSONAL",
                "Personal foul", "Player B", 200L, "Team B", 2000L,
                2, 0, 2, false, false, 2, "{}"
        );

        List<PlayByPlayEvent> events = List.of(event1, event2);
        String gameId = "0022401001";
        ProviderType source = ProviderType.ESPN_PLAY_BY_PLAY;

        // Act
        List<PlayByPlay> entities = service.normalizeAll(events, gameId, source);

        // Assert
        assertEquals(2, entities.size());

        // Verify order is preserved
        assertEquals("src-evt-001", entities.get(0).getSourceEventId());
        assertEquals("src-evt-002", entities.get(1).getSourceEventId());

        assertEquals("First basket", entities.get(0).getDescription());
        assertEquals("Personal foul", entities.get(1).getDescription());

        assertEquals(event1.eventOrder(), entities.get(0).getEventOrder());
        assertEquals(event2.eventOrder(), entities.get(1).getEventOrder());

        // Verify source
        assertEquals(ProviderType.ESPN_PLAY_BY_PLAY.name(), entities.get(0).getSource());
        assertEquals(ProviderType.ESPN_PLAY_BY_PLAY.name(), entities.get(1).getSource());
    }
}

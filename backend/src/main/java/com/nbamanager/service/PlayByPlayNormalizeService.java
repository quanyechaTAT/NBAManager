package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlayByPlayNormalizeService {

    public PlayByPlay normalize(PlayByPlayEvent event, String gameId, ProviderType source) {
        PlayByPlay entity = new PlayByPlay();

        // Map all fields from event to entity
        entity.setPeriod(event.period());
        entity.setGameClock(event.gameClock());
        entity.setEventType(event.eventType());
        entity.setActionType(event.actionType());
        entity.setDescription(event.description());
        entity.setPlayerName(event.playerName());
        entity.setPlayerId(event.playerId());
        entity.setTeamName(event.teamName());
        entity.setTeamId(event.teamId());
        entity.setHomeScore(event.homeScore());
        entity.setAwayScore(event.awayScore());
        entity.setScoreMargin(event.scoreMargin());
        entity.setIsScoringPlay(event.isScoringPlay());
        entity.setIsHighlightCandidate(event.isHighlightCandidate());
        entity.setEventOrder(event.eventOrder());
        entity.setSourceEventId(event.sourceEventId());
        entity.setRawPayloadJson(event.rawPayloadJson());

        // Set additional fields
        entity.setGameId(gameId);
        entity.setSource(source.name());

        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return entity;
    }

    public List<PlayByPlay> normalizeAll(List<PlayByPlayEvent> events, String gameId, ProviderType source) {
        List<PlayByPlay> entities = new ArrayList<>(events.size());
        for (PlayByPlayEvent event : events) {
            entities.add(normalize(event, gameId, source));
        }
        return entities;
    }
}

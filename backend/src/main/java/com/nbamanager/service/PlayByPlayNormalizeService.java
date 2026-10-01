package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        entity.setSourceEventId(stableSourceEventId(event, gameId, source));
        entity.setRawPayloadJson(event.rawPayloadJson());
        entity.setRawPayloadHash(sha256(event.rawPayloadJson()));

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

    private String stableSourceEventId(PlayByPlayEvent event, String gameId, ProviderType source) {
        if (event.sourceEventId() != null && !event.sourceEventId().isBlank()) {
            return event.sourceEventId().trim();
        }
        String canonical = String.join("|",
                safe(gameId),
                source == null ? "" : source.name(),
                Integer.toString(event.period()),
                safe(event.gameClock()),
                Integer.toString(event.eventOrder()),
                safe(event.eventType()),
                safe(event.actionType()),
                safe(event.description()),
                Integer.toString(event.homeScore()),
                Integer.toString(event.awayScore()));
        return "generated-" + sha256(canonical).substring(0, 32);
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                result.append(String.format("%02x", part));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

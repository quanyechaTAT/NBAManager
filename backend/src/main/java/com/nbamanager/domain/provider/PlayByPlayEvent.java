package com.nbamanager.domain.provider;

public record PlayByPlayEvent(
        String sourceEventId,
        int period,
        String gameClock,
        String eventType,
        String actionType,
        String description,
        String playerName,
        Long playerId,
        String teamName,
        Long teamId,
        int homeScore,
        int awayScore,
        int scoreMargin,
        boolean isScoringPlay,
        boolean isHighlightCandidate,
        int eventOrder,
        String rawPayloadJson
) {}

package com.nbamanager.web.dto;

public record TimelineEventDto(
        Long id,
        Integer period,
        String gameClock,
        String eventType,
        String actionType,
        String description,
        String playerName,
        Long playerId,
        String teamName,
        Long teamId,
        Integer homeScore,
        Integer awayScore,
        Integer scoreMargin,
        Boolean isScoringPlay,
        Boolean isHighlightCandidate,
        Integer eventOrder,
        String source
) {}

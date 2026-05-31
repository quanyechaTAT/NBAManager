package com.nbamanager.web.dto;

public record PlayByPlayDto(
        Integer period,
        String gameClock,
        String description,
        Integer homeScore,
        Integer awayScore,
        String eventType,
        Long playerId,
        String playerName
) {}

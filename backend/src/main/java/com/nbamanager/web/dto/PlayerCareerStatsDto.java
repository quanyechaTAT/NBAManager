package com.nbamanager.web.dto;

public record PlayerCareerStatsDto(
        String season,
        String teamName,
        Integer gamesPlayed,
        Double minutesPerGame,
        Double pointsPerGame,
        Double reboundsPerGame,
        Double assistsPerGame,
        Double stealsPerGame,
        Double blocksPerGame,
        Double fgPct,
        Double threePointPct,
        Double freeThrowPct,
        Double efficiency) {}

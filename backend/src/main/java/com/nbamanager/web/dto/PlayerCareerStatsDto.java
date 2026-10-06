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
        Double efficiency,
        DataMeta dataMeta) {
    public PlayerCareerStatsDto(String season,
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
        Double efficiency) {
        this(season, teamName, gamesPlayed, minutesPerGame, pointsPerGame, reboundsPerGame, assistsPerGame, stealsPerGame, blocksPerGame, fgPct, threePointPct, freeThrowPct, efficiency, DataMeta.unverified());
    }
}

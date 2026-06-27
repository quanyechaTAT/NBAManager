package com.nbamanager.web.dto;

public record TeamRankDto(
        String teamName,
        Integer wins,
        Integer losses,
        Double gamesBehind,
        String conference,
        String homeRecord,
        String awayRecord,
        Double pointsPerGame,
        Double oppPointsPerGame,
        Double netRating,
        String streak
) {
    /** 简化构造器（兼容旧代码） */
    public TeamRankDto(String teamName, Integer wins, Integer losses, Double gamesBehind, String conference) {
        this(teamName, wins, losses, gamesBehind, conference, null, null, null, null, null, null);
    }
}

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
        String streak,
        Double pointDifferential,
        DataMeta dataMeta,
        DataMeta officialRecordMeta
) {
    public TeamRankDto(String teamName, Integer wins, Integer losses, Double gamesBehind, String conference,
                       String homeRecord, String awayRecord, Double pointsPerGame, Double oppPointsPerGame,
                       Double netRating, String streak) {
        this(teamName, wins, losses, gamesBehind, conference, homeRecord, awayRecord, pointsPerGame,
                oppPointsPerGame, netRating, streak, netRating, DataMeta.unverified(), DataMeta.unverified());
    }
    /** 简化构造器（兼容旧代码） */
    public TeamRankDto(String teamName, Integer wins, Integer losses, Double gamesBehind, String conference) {
        this(teamName, wins, losses, gamesBehind, conference, null, null, null, null, null, null, null, DataMeta.unverified(), DataMeta.unverified());
    }
}

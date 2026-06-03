package com.nbamanager.web.dto;

public record PlayerGameLogDto(
        String gameId,
        String matchDate,
        String opponent,
        boolean isHome,
        String minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Double fgPct,
        Double threePct,
        Double ftPct,
        Integer plusMinus,
        String result) {}

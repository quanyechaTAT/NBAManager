package com.nbamanager.web.dto;

public record BoxScorePlayerDto(
        Long playerId,
        String playerName,
        String teamName,
        String minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Integer fgMade,
        Integer fgAttempted,
        Double fgPct,
        Integer threeMade,
        Integer threeAttempted,
        Double threePct,
        Integer ftMade,
        Integer ftAttempted,
        Double ftPct,
        Integer plusMinus,
        Boolean starter
) {}

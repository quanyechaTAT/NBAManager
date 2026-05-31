package com.nbamanager.web.dto;

import java.util.List;

public record BoxScoreDto(
        String gameId,
        String homeTeam,
        String awayTeam,
        List<BoxScorePlayerDto> homePlayers,
        List<BoxScorePlayerDto> awayPlayers,
        List<QuarterScoreDto> quarterScores
) {}

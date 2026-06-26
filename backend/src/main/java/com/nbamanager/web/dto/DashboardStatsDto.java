package com.nbamanager.web.dto;

import java.util.List;

public record DashboardStatsDto(int teamCount, int playerCount, List<TeamWinRow> teamWinRows, List<TopScorerRow> topScorers) {

    public record TeamWinRow(String name, int wins, int losses) {}

    public record TopScorerRow(long id, String playerName, double ppg, String teamName, Long nbaPlayerId) {}
}

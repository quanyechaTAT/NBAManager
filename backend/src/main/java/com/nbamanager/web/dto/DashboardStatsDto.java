package com.nbamanager.web.dto;

import java.util.List;

public record DashboardStatsDto(List<TeamWinRow> teamWinRows, List<TopScorerRow> topScorers) {

    public record TeamWinRow(String name, int wins, int losses) {}

    public record TopScorerRow(String playerName, double ppg, String teamName) {}
}

package com.nbamanager.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardOverviewDto(
        DashboardStatsDto stats,
        List<GameNewsDto> todayGames,
        List<TeamRankDto> westStandings,
        List<TeamRankDto> eastStandings,
        List<DashboardStatsDto.TopScorerRow> topScorers,
        List<PostDto> hotPosts,
        int docCount,
        LocalDateTime generatedAt,
        List<String> partialErrors) {}

package com.nbamanager.service;

import com.nbamanager.service.RAGService.RAGStats;
import com.nbamanager.web.dto.DashboardOverviewDto;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.GameNewsDto;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.TeamRankDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardOverviewService {

    private final DashboardService dashboardService;
    private final GameNewsService gameNewsService;
    private final TeamService teamService;
    private final PostService postService;
    private final RAGService ragService;

    @Cacheable(value = "dashboard", key = "'overview'")
    @Transactional(readOnly = true)
    public DashboardOverviewDto buildOverview() {
        List<String> partialErrors = new ArrayList<>();

        DashboardStatsDto stats = loadStats(partialErrors);
        List<GameNewsDto> todayGames = loadTodayGames(partialErrors);
        Map<String, List<TeamRankDto>> rankings = loadRankings(partialErrors);
        List<PostDto> hotPosts = loadHotPosts(partialErrors);
        int docCount = loadDocCount(partialErrors);

        List<TeamRankDto> westStandings = firstNonEmpty(rankings, "western", "西部", "瑗块儴");
        List<TeamRankDto> eastStandings = firstNonEmpty(rankings, "eastern", "东部", "涓滈儴");
        List<DashboardStatsDto.TopScorerRow> topScorers =
                stats == null || stats.topScorers() == null ? List.of() : stats.topScorers();

        return new DashboardOverviewDto(
                stats,
                todayGames,
                westStandings,
                eastStandings,
                topScorers,
                hotPosts,
                docCount,
                LocalDateTime.now(),
                partialErrors);
    }

    private DashboardStatsDto loadStats(List<String> partialErrors) {
        try {
            return dashboardService.buildStats();
        } catch (Exception e) {
            partialErrors.add("dashboardStats");
            return new DashboardStatsDto(0, 0, List.of(), List.of());
        }
    }

    private List<GameNewsDto> loadTodayGames(List<String> partialErrors) {
        try {
            // 直接从数据库读取（快速），实时数据由前端单独请求 /api/news/today
            return gameNewsService.getTodayGames().stream()
                    .filter(this::hasValidGameTeams)
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            partialErrors.add("todayGames");
            return List.of();
        }
    }

    private Map<String, List<TeamRankDto>> loadRankings(List<String> partialErrors) {
        try {
            return teamService.getConferenceRankings();
        } catch (Exception e) {
            partialErrors.add("rankings");
            return Map.of();
        }
    }

    private List<PostDto> loadHotPosts(List<String> partialErrors) {
        try {
            return postService.hot(PageRequest.of(0, 5)).getContent();
        } catch (Exception e) {
            partialErrors.add("hotPosts");
            return List.of();
        }
    }

    private int loadDocCount(List<String> partialErrors) {
        try {
            RAGStats stats = ragService.getStatsSnapshot();
            return stats.documentCount();
        } catch (Exception e) {
            partialErrors.add("ragStats");
            return 0;
        }
    }

    private boolean hasValidGameTeams(GameNewsDto game) {
        return game.nbaGameId() != null
                && !game.nbaGameId().isBlank()
                && isPresentTeam(game.homeTeam())
                && isPresentTeam(game.awayTeam());
    }

    private boolean isPresentTeam(String value) {
        return value != null
                && !value.isBlank()
                && !"TBD".equalsIgnoreCase(value.trim())
                && !"待定".equals(value.trim())
                && !"寰呭畾".equals(value.trim());
    }

    @SafeVarargs
    private final List<TeamRankDto> firstNonEmpty(Map<String, List<TeamRankDto>> rankings, String... keys) {
        for (String key : keys) {
            List<TeamRankDto> value = rankings.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return List.of();
    }
}

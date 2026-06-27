package com.nbamanager.web;

import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.domain.SeasonConfig;
import com.nbamanager.domain.TeamSeasonStats;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import com.nbamanager.service.HistoricalDataSyncService;
import com.nbamanager.service.SeasonManagementService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/historical")
@RequiredArgsConstructor
public class HistoricalDataController {

    private final HistoricalDataSyncService syncService;
    private final SeasonManagementService seasonService;
    private final PlayerSeasonStatsRepository playerStatsRepo;
    private final TeamSeasonStatsRepository teamStatsRepo;

    /**
     * 获取可用赛季列表
     */
    @GetMapping("/seasons")
    public ResponseEntity<Map<String, Object>> getAvailableSeasons() {
        String currentSeason = seasonService.getCurrentSeason();
        List<String> syncedSeasons = seasonService.getSyncedSeasons();
        List<SeasonConfig> allConfigs = seasonService.getAllSeasonConfigs();

        return ResponseEntity.ok(Map.of(
                "current", currentSeason,
                "available", syncedSeasons,
                "configs", allConfigs
        ));
    }

    /**
     * 获取指定赛季的联盟排名（从 team_season_stats 表）
     * 兼容 "东部"/"西部" 和 "East"/"West" 两种格式
     */
    @GetMapping("/rankings")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getRankings(
            @RequestParam String season) {
        List<TeamSeasonStats> teams = teamStatsRepo.findBySeasonOrderByWinsDesc(season);

        Map<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        Map<String, List<TeamSeasonStats>> grouped = teams.stream()
                .filter(t -> t.getConference() != null && !t.getConference().isBlank())
                .collect(Collectors.groupingBy(t -> normalizeConference(t.getConference())));

        for (String conf : List.of("东部", "西部")) {
            List<TeamSeasonStats> confTeams = grouped.getOrDefault(conf, List.of());
            confTeams.sort((a, b) -> b.getWins() - a.getWins());
            double firstDiff = confTeams.isEmpty() ? 0 : confTeams.get(0).getWins() - confTeams.get(0).getLosses();

            List<Map<String, Object>> ranks = new java.util.ArrayList<>();
            for (int i = 0; i < confTeams.size(); i++) {
                TeamSeasonStats t = confTeams.get(i);
                double gb = i == 0 ? 0 : (firstDiff - (t.getWins() - t.getLosses())) / 2.0;
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("teamName", t.getTeamName());
                row.put("wins", t.getWins());
                row.put("losses", t.getLosses());
                row.put("gamesBehind", Math.round(gb * 10.0) / 10.0);
                row.put("conference", conf);
                row.put("winPct", t.getWinPct() != null ? t.getWinPct().doubleValue() : 0);
                // 历史赛季可能没有得分数据，返回 null 而非 0
                double ppg = t.getPointsPerGame() != null ? t.getPointsPerGame().doubleValue() : 0;
                double oppg = t.getOpponentsPointsPerGame() != null ? t.getOpponentsPointsPerGame().doubleValue() : 0;
                row.put("pointsPerGame", ppg > 0 ? ppg : null);
                row.put("oppPointsPerGame", oppg > 0 ? oppg : null);
                ranks.add(row);
            }
            result.put(conf, ranks);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定赛季的分区排名
     * 兼容英文和中文赛区名
     */
    @GetMapping("/division-rankings")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getDivisionRankings(
            @RequestParam String season) {
        List<TeamSeasonStats> teams = teamStatsRepo.findBySeasonOrderByWinsDesc(season);

        Map<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        Map<String, List<TeamSeasonStats>> grouped = teams.stream()
                .filter(t -> t.getDivision() != null && !t.getDivision().isBlank())
                .collect(Collectors.groupingBy(t -> normalizeDivision(t.getDivision())));

        List<String> divisionOrder = List.of("大西洋", "中部", "东南", "西北", "太平洋", "西南");
        for (String div : divisionOrder) {
            List<TeamSeasonStats> divTeams = grouped.getOrDefault(div, List.of());
            if (divTeams.isEmpty()) continue;

            divTeams.sort((a, b) -> b.getWins() - a.getWins());
            double firstDiff = divTeams.get(0).getWins() - divTeams.get(0).getLosses();
            List<Map<String, Object>> ranks = new java.util.ArrayList<>();
            for (int i = 0; i < divTeams.size(); i++) {
                TeamSeasonStats t = divTeams.get(i);
                double gb = i == 0 ? 0 : (firstDiff - (t.getWins() - t.getLosses())) / 2.0;
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("teamName", t.getTeamName());
                row.put("wins", t.getWins());
                row.put("losses", t.getLosses());
                row.put("gamesBehind", Math.round(gb * 10.0) / 10.0);
                row.put("conference", normalizeConference(t.getConference()));
                row.put("winPct", t.getWinPct() != null ? t.getWinPct().doubleValue() : 0);
                // 历史赛季可能没有得分数据，返回 null 而非 0
                double ppg = t.getPointsPerGame() != null ? t.getPointsPerGame().doubleValue() : 0;
                double oppg = t.getOpponentsPointsPerGame() != null ? t.getOpponentsPointsPerGame().doubleValue() : 0;
                row.put("pointsPerGame", ppg > 0 ? ppg : null);
                row.put("oppPointsPerGame", oppg > 0 ? oppg : null);
                ranks.add(row);
            }
            result.put(div, ranks);
        }
        return ResponseEntity.ok(result);
    }

    /** 标准化联盟名（兼容中英文） */
    private String normalizeConference(String conf) {
        if (conf == null) return "未知";
        return switch (conf.toLowerCase()) {
            case "east", "东部", "东区" -> "东部";
            case "west", "西部", "西区" -> "西部";
            default -> conf;
        };
    }

    /** 标准化赛区名（兼容中英文） */
    private String normalizeDivision(String div) {
        if (div == null) return "未知";
        return switch (div.toLowerCase()) {
            case "atlantic", "大西洋" -> "大西洋";
            case "central", "中部" -> "中部";
            case "southeast", "东南" -> "东南";
            case "northwest", "西北" -> "西北";
            case "pacific", "太平洋" -> "太平洋";
            case "southwest", "西南" -> "西南";
            default -> div;
        };
    }

    /**
     * 获取指定赛季球员数据
     */
    @GetMapping("/players")
    public ResponseEntity<List<PlayerSeasonStats>> getPlayers(
            @RequestParam String season,
            @RequestParam(defaultValue = "pointsPerGame") String sortBy,
            @RequestParam(defaultValue = "50") int limit) {

        PageRequest page = PageRequest.of(0, limit);
        List<PlayerSeasonStats> players;

        switch (sortBy) {
            case "reboundsPerGame":
                players = playerStatsRepo.findTopRebounders(season, page);
                break;
            case "assistsPerGame":
                players = playerStatsRepo.findTopAssisters(season, page);
                break;
            case "stealsPerGame":
                players = playerStatsRepo.findTopStealers(season, page);
                break;
            case "blocksPerGame":
                players = playerStatsRepo.findTopBlockers(season, page);
                break;
            default:
                players = playerStatsRepo.findTopScorers(season, page);
        }

        return ResponseEntity.ok(players);
    }

    /**
     * 获取指定赛季球队数据
     */
    @GetMapping("/teams")
    public ResponseEntity<List<TeamSeasonStats>> getTeams(
            @RequestParam String season) {
        return ResponseEntity.ok(teamStatsRepo.findBySeasonOrderByWinsDesc(season));
    }

    /**
     * 获取指定赛季数据领袖
     */
    @GetMapping("/leaders")
    public ResponseEntity<Map<String, List<PlayerSeasonStats>>> getLeaders(
            @RequestParam String season) {

        Map<String, List<PlayerSeasonStats>> leaders = new HashMap<>();
        PageRequest top10 = PageRequest.of(0, 10);

        leaders.put("scoring", playerStatsRepo.findTopScorers(season, top10));
        leaders.put("rebounds", playerStatsRepo.findTopRebounders(season, top10));
        leaders.put("assists", playerStatsRepo.findTopAssisters(season, top10));
        leaders.put("steals", playerStatsRepo.findTopStealers(season, top10));
        leaders.put("blocks", playerStatsRepo.findTopBlockers(season, top10));

        return ResponseEntity.ok(leaders);
    }

    /**
     * 获取球员历史赛季列表
     */
    @GetMapping("/player/{nbaPlayerId}/seasons")
    public ResponseEntity<List<String>> getPlayerSeasons(
            @PathVariable Long nbaPlayerId) {
        List<String> seasons = playerStatsRepo.findByNbaPlayerIdOrderBySeasonDesc(nbaPlayerId)
                .stream()
                .map(PlayerSeasonStats::getSeason)
                .distinct()
                .toList();
        return ResponseEntity.ok(seasons);
    }

    /**
     * 获取同步状态
     */
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        return ResponseEntity.ok(Map.of(
                "syncing", syncService.isSyncing(),
                "seasons", seasonService.getAllSeasonConfigs()
        ));
    }

    /**
     * 管理员：同步指定赛季数据
     */
    @PostMapping("/sync/{season}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncSeason(
            @PathVariable String season) {
        if (syncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "数据同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        syncService.syncSeasonData(season);
        return ResponseEntity.ok(Map.of(
                "message", season + " 赛季数据同步已启动",
                "status", "started"
        ));
    }

    /**
     * 管理员：同步所有历史赛季
     */
    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncAllSeasons() {
        if (syncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "数据同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        String currentSeason = seasonService.getCurrentSeason();
        int startYear = Integer.parseInt(currentSeason.split("-")[0]);

        // 同步最近5个赛季
        for (int i = 0; i < 5; i++) {
            String season = (startYear - i) + "-" +
                    String.valueOf(startYear - i + 1).substring(2);
            syncService.syncSeasonData(season);
        }

        return ResponseEntity.ok(Map.of(
                "message", "最近5个赛季数据同步已启动",
                "status", "started"
        ));
    }

    /**
     * 管理员：清理旧赛季数据
     */
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupOldSeasons() {
        int deleted = seasonService.cleanupOldSeasons();
        return ResponseEntity.ok(Map.of(
                "message", "已清理 " + deleted + " 个旧赛季数据",
                "deleted", deleted
        ));
    }
}

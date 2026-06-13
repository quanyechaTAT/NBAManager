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

package com.nbamanager.web;

import com.nbamanager.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final NbaDataSyncService dataSyncService;
    private final NbaLiveSyncService liveSyncService;
    private final NbaDataService nbaDataService;
    private final MatchRecordService matchRecordService;

    // 各模块同步状态跟踪
    private final Map<String, String> moduleSyncStatus = new ConcurrentHashMap<>();

    /**
     * 获取数据同步状态
     */
    @GetMapping("/sync-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncing", dataSyncService.isSyncing());
        status.put("lastSyncTime", dataSyncService.getLastSyncTime());
        status.put("lastSyncStatus", dataSyncService.getLastSyncStatus());
        status.put("result", dataSyncService.getSyncResult());
        status.put("modules", moduleSyncStatus);
        return ResponseEntity.ok(status);
    }

    /**
     * 手动触发全量数据同步
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerSync() {
        if (dataSyncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "数据同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        dataSyncService.syncAll();

        return ResponseEntity.ok(Map.of(
                "message", "全量数据同步已触发",
                "status", "started"
        ));
    }

    /**
     * 同步球队数据
     */
    @PostMapping("/sync/teams")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncTeams() {
        return triggerModuleSync("teams", "球队数据");
    }

    /**
     * 同步球员数据
     */
    @PostMapping("/sync/players")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncPlayers() {
        return triggerModuleSync("players", "球员数据");
    }

    /**
     * 同步比赛记录
     */
    @PostMapping("/sync/matches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncMatches() {
        return triggerModuleSync("matches", "比赛记录");
    }

    /**
     * 同步新闻资讯（异步执行，立即返回）
     */
    @PostMapping("/sync/news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncNews() {
        // 异步执行同步，立即返回
        new Thread(() -> {
            try {
                log.info("开始同步新闻...");
                moduleSyncStatus.put("news", "syncing");
                liveSyncService.syncNbaNews();
                moduleSyncStatus.put("news", "success");
                log.info("新闻同步完成");
            } catch (Exception e) {
                log.error("新闻同步失败: {}", e.getMessage(), e);
                moduleSyncStatus.put("news", "failed: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", "新闻同步已启动，请稍后刷新查看",
                "status", "started"
        ));
    }

    /**
     * 同步季后赛数据
     */
    @PostMapping("/sync/playoff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncPlayoff() {
        try {
            moduleSyncStatus.put("playoff", "syncing");
            String season = getCurrentSeason();
            Map<String, Object> result = nbaDataService.fetchAndImportPlayoffData(season);
            Integer imported = (Integer) result.get("imported");
            moduleSyncStatus.put("playoff", "success");
            return ResponseEntity.ok(Map.of(
                    "message", "季后赛同步完成，导入 " + (imported != null ? imported : 0) + " 组对阵",
                    "status", "success"
            ));
        } catch (Exception e) {
            moduleSyncStatus.put("playoff", "failed: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "message", "季后赛同步失败: " + e.getMessage(),
                    "status", "failed"
            ));
        }
    }

    /**
     * 同步选秀数据
     */
    @PostMapping("/sync/draft")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncDraft() {
        return triggerModuleSync("draft", "选秀数据");
    }

    /**
     * 同步今日比赛（异步执行，立即返回）
     */
    @PostMapping("/sync/today")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncTodayGames() {
        // 异步执行同步，立即返回
        new Thread(() -> {
            try {
                moduleSyncStatus.put("today", "syncing");
                liveSyncService.syncTodayGames();
                moduleSyncStatus.put("today", "success");
            } catch (Exception e) {
                moduleSyncStatus.put("today", "failed: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", "今日比赛同步已启动，请稍后刷新查看",
                "status", "started"
        ));
    }

    /**
     * 通用模块同步触发方法
     */
    private ResponseEntity<Map<String, String>> triggerModuleSync(String module, String moduleName) {
        if (dataSyncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "全量同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        // 异步执行单模块同步
        moduleSyncStatus.put(module, "syncing");
        new Thread(() -> {
            try {
                dataSyncService.syncAll(); // 目前使用全量同步，后续可拆分为独立同步
                moduleSyncStatus.put(module, "success");
            } catch (Exception e) {
                moduleSyncStatus.put(module, "failed: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", moduleName + "同步已触发",
                "status", "started"
        ));
    }

    /**
     * 获取当前赛季
     */
    private String getCurrentSeason() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        if (month >= 10) {
            return year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        }
    }

    /**
     * 清理旧比赛记录（3个月前的数据）
     */
    @PostMapping("/clean/matches/old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanOldMatches() {
        try {
            int cleaned = matchRecordService.cleanOldData();
            return ResponseEntity.ok(Map.of(
                    "message", "已清理 " + cleaned + " 条旧比赛记录",
                    "cleaned", cleaned,
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "message", "清理失败: " + e.getMessage(),
                    "status", "failed"
            ));
        }
    }

    /**
     * 清理无效比赛记录（无比分或状态异常）
     */
    @PostMapping("/clean/matches/invalid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanInvalidMatches() {
        try {
            int cleaned = matchRecordService.cleanInvalidData();
            return ResponseEntity.ok(Map.of(
                    "message", "已清理 " + cleaned + " 条无效比赛记录",
                    "cleaned", cleaned,
                    "status", "success"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "message", "清理失败: " + e.getMessage(),
                    "status", "failed"
            ));
        }
    }
}

package com.nbamanager.web;

import com.nbamanager.repository.*;
import com.nbamanager.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class DataManagementController {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final DraftPickRepository draftPickRepository;
    private final GameNewsRepository gameNewsRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;
    private final TeamSeasonStatsRepository teamSeasonStatsRepository;
    private final SeasonConfigRepository seasonConfigRepository;

    private final NbaDataSyncService dataSyncService;
    private final NbaLiveSyncService liveSyncService;
    private final NbaDataService nbaDataService;
    private final HistoricalDataSyncService historicalDataSyncService;
    private final SeasonManagementService seasonManagementService;
    private final TranslationRetryService translationRetryService;

    // 各模块同步状态跟踪
    private static final Map<String, ModuleSyncInfo> moduleStatus = new ConcurrentHashMap<>();

    static {
        moduleStatus.put("teams", new ModuleSyncInfo("球队数据", "🏀"));
        moduleStatus.put("players", new ModuleSyncInfo("球员数据", "👤"));
        moduleStatus.put("matches", new ModuleSyncInfo("比赛记录", "📊"));
        moduleStatus.put("draft", new ModuleSyncInfo("选秀数据", "📋"));
        moduleStatus.put("news", new ModuleSyncInfo("新闻资讯", "📰"));
        moduleStatus.put("playoff", new ModuleSyncInfo("季后赛", "🏆"));
        moduleStatus.put("historical", new ModuleSyncInfo("历史赛季", "📅"));
        moduleStatus.put("today", new ModuleSyncInfo("今日比赛", "🔴"));
        moduleStatus.put("translation", new ModuleSyncInfo("翻译重试", "🌐"));
    }

    /**
     * 获取所有模块的数据统计和同步状态
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> result = new HashMap<>();

        // 各模块数据统计
        List<Map<String, Object>> modules = new ArrayList<>();
        String currentSeason = seasonManagementService.getCurrentSeason();

        // 球队数据
        modules.add(buildModuleInfo("teams", teamRepository.count()));

        // 球员数据
        modules.add(buildModuleInfo("players", playerRepository.count()));

        // 比赛记录
        modules.add(buildModuleInfo("matches", matchRecordRepository.count()));

        // 选秀数据
        modules.add(buildModuleInfo("draft", draftPickRepository.count()));

        // 新闻资讯
        modules.add(buildModuleInfo("news", gameNewsRepository.count()));

        // 季后赛
        long playoffCount = playoffMatchupRepository.findBySeasonOrderByRoundAsc(currentSeason).size();
        modules.add(buildModuleInfo("playoff", playoffCount));

        // 历史赛季
        long historicalCount = playerSeasonStatsRepository.countBySeason(currentSeason);
        modules.add(buildModuleInfo("historical", historicalCount));

        // 今日比赛
        modules.add(buildModuleInfo("today", 0L));

        // 翻译状态
        long untranslatedCount = playerRepository.findAll().stream()
                .filter(p -> "UNTRANSLATED".equals(p.getTranslationStatus()))
                .count();
        Map<String, Object> translationModule = buildModuleInfo("translation", untranslatedCount);
        translationModule.put("description", "待翻译球员名");
        modules.add(translationModule);

        result.put("modules", modules);
        result.put("currentSeason", currentSeason);
        result.put("syncing", dataSyncService.isSyncing());
        result.put("lastSyncTime", dataSyncService.getLastSyncTime());

        return ResponseEntity.ok(result);
    }

    /**
     * 同步指定模块
     */
    @PostMapping("/sync/{module}")
    public ResponseEntity<Map<String, String>> syncModule(@PathVariable String module) {
        ModuleSyncInfo info = moduleStatus.get(module);
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "未知模块: " + module,
                    "status", "error"
            ));
        }

        // 检查该模块是否正在同步
        if ("syncing".equals(info.status)) {
            return ResponseEntity.ok(Map.of(
                    "message", info.name + "正在同步中",
                    "status", "syncing"
            ));
        }

        // 标记开始同步
        info.status = "syncing";
        info.progress = 0;
        info.lastSyncTime = LocalDateTime.now();

        // 异步执行同步
        new Thread(() -> {
            try {
                switch (module) {
                    case "teams":
                    case "players":
                    case "matches":
                    case "draft":
                        // 这些模块通过全量同步一起更新
                        info.progress = 20;
                        dataSyncService.syncAll();
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    case "news":
                        info.progress = 10;
                        Thread.sleep(500);
                        info.progress = 30;
                        liveSyncService.syncNbaNews();
                        info.progress = 80;
                        Thread.sleep(500);
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    case "playoff":
                        info.progress = 10;
                        Thread.sleep(500);
                        info.progress = 30;
                        String season = seasonManagementService.getCurrentSeason();
                        nbaDataService.fetchAndImportPlayoffData(season);
                        info.progress = 80;
                        Thread.sleep(500);
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    case "historical":
                        info.progress = 10;
                        Thread.sleep(500);
                        info.progress = 30;
                        String currentSeason = seasonManagementService.getCurrentSeason();
                        historicalDataSyncService.syncSeasonData(currentSeason);
                        info.progress = 80;
                        Thread.sleep(500);
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    case "today":
                        info.progress = 20;
                        Thread.sleep(500);
                        info.progress = 50;
                        liveSyncService.syncTodayGames();
                        info.progress = 80;
                        Thread.sleep(500);
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    case "translation":
                        info.progress = 20;
                        Thread.sleep(500);
                        info.progress = 50;
                        // 调用翻译重试服务
                        translationRetryService.retryTranslations();
                        info.progress = 80;
                        Thread.sleep(500);
                        info.progress = 100;
                        info.status = "success";
                        info.lastSyncTime = LocalDateTime.now();
                        break;

                    default:
                        info.status = "error";
                        info.errorMessage = "未实现的同步模块";
                }
            } catch (Exception e) {
                log.error("同步模块 {} 失败: {}", module, e.getMessage(), e);
                info.status = "error";
                info.errorMessage = e.getMessage();
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", info.name + "同步已启动",
                "status", "started"
        ));
    }

    /**
     * 全量同步所有模块
     */
    @PostMapping("/sync-all")
    public ResponseEntity<Map<String, String>> syncAll() {
        if (dataSyncService.isSyncing()) {
            return ResponseEntity.ok(Map.of(
                    "message", "同步正在进行中，请稍后再试",
                    "status", "syncing"
            ));
        }

        // 标记所有模块开始同步
        moduleStatus.values().forEach(info -> {
            info.status = "pending";
            info.progress = 0;
        });

        // 异步执行全量同步
        new Thread(() -> {
            try {
                String season = seasonManagementService.getCurrentSeason();

                // 1. 全量同步（球队、球员、比赛、选秀）
                moduleStatus.get("teams").status = "syncing";
                moduleStatus.get("teams").progress = 10;
                moduleStatus.get("players").status = "syncing";
                moduleStatus.get("players").progress = 10;
                moduleStatus.get("matches").status = "syncing";
                moduleStatus.get("matches").progress = 10;
                moduleStatus.get("draft").status = "syncing";
                moduleStatus.get("draft").progress = 10;

                dataSyncService.syncAll();

                moduleStatus.get("teams").status = "success";
                moduleStatus.get("teams").progress = 100;
                moduleStatus.get("teams").lastSyncTime = LocalDateTime.now();
                moduleStatus.get("players").status = "success";
                moduleStatus.get("players").progress = 100;
                moduleStatus.get("players").lastSyncTime = LocalDateTime.now();
                moduleStatus.get("matches").status = "success";
                moduleStatus.get("matches").progress = 100;
                moduleStatus.get("matches").lastSyncTime = LocalDateTime.now();
                moduleStatus.get("draft").status = "success";
                moduleStatus.get("draft").progress = 100;
                moduleStatus.get("draft").lastSyncTime = LocalDateTime.now();

                // 2. 新闻同步
                moduleStatus.get("news").status = "syncing";
                moduleStatus.get("news").progress = 20;
                liveSyncService.syncNbaNews();
                moduleStatus.get("news").status = "success";
                moduleStatus.get("news").progress = 100;
                moduleStatus.get("news").lastSyncTime = LocalDateTime.now();

                // 3. 季后赛同步
                moduleStatus.get("playoff").status = "syncing";
                moduleStatus.get("playoff").progress = 20;
                nbaDataService.fetchAndImportPlayoffData(season);
                moduleStatus.get("playoff").status = "success";
                moduleStatus.get("playoff").progress = 100;
                moduleStatus.get("playoff").lastSyncTime = LocalDateTime.now();

                // 4. 历史赛季同步
                moduleStatus.get("historical").status = "syncing";
                moduleStatus.get("historical").progress = 20;
                historicalDataSyncService.syncSeasonData(season);
                moduleStatus.get("historical").status = "success";
                moduleStatus.get("historical").progress = 100;
                moduleStatus.get("historical").lastSyncTime = LocalDateTime.now();

                // 5. 今日比赛同步
                moduleStatus.get("today").status = "syncing";
                moduleStatus.get("today").progress = 30;
                liveSyncService.syncTodayGames();
                moduleStatus.get("today").status = "success";
                moduleStatus.get("today").progress = 100;
                moduleStatus.get("today").lastSyncTime = LocalDateTime.now();

            } catch (Exception e) {
                log.error("全量同步失败: {}", e.getMessage(), e);
                moduleStatus.values().forEach(info -> {
                    if ("syncing".equals(info.status) || "pending".equals(info.status)) {
                        info.status = "error";
                        info.errorMessage = e.getMessage();
                    }
                });
            }
        }).start();

        return ResponseEntity.ok(Map.of(
                "message", "全量同步已启动",
                "status", "started"
        ));
    }

    /**
     * 获取指定模块的同步进度
     */
    @GetMapping("/progress/{module}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String module) {
        ModuleSyncInfo info = moduleStatus.get(module);
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "未知模块"));
        }

        return ResponseEntity.ok(Map.of(
                "status", info.status,
                "progress", info.progress,
                "lastSyncTime", info.lastSyncTime != null ? info.lastSyncTime.toString() : "",
                "errorMessage", info.errorMessage != null ? info.errorMessage : "",
                "paused", info.paused
        ));
    }

    /**
     * 获取所有模块的同步进度
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Map<String, Object>>> getAllProgress() {
        Map<String, Map<String, Object>> result = new HashMap<>();

        moduleStatus.forEach((key, info) -> {
            Map<String, Object> moduleInfo = new HashMap<>();
            moduleInfo.put("name", info.name);
            moduleInfo.put("icon", info.icon);
            moduleInfo.put("status", info.status);
            moduleInfo.put("progress", info.progress);
            moduleInfo.put("lastSyncTime", info.lastSyncTime != null ? info.lastSyncTime.toString() : "");
            moduleInfo.put("errorMessage", info.errorMessage != null ? info.errorMessage : "");
            moduleInfo.put("paused", info.paused);
            result.put(key, moduleInfo);
        });

        return ResponseEntity.ok(result);
    }

    /**
     * 构建模块信息
     */
    private Map<String, Object> buildModuleInfo(String key, long count) {
        ModuleSyncInfo info = moduleStatus.get(key);
        Map<String, Object> module = new HashMap<>();
        module.put("key", key);
        module.put("name", info != null ? info.name : key);
        module.put("icon", info != null ? info.icon : "📊");
        module.put("count", count);

        // 如果DataSyncService正在同步，且是相关模块，显示为syncing
        String status = info != null ? info.status : "idle";
        int progress = info != null ? info.progress : 0;
        if (dataSyncService.isSyncing() && isAffectedByFullSync(key)) {
            status = "syncing";
            // 根据同步状态估算进度
            if (progress == 0) {
                progress = estimateProgress(key);
            }
        }

        module.put("status", status);
        module.put("progress", progress);
        module.put("lastSyncTime", info != null && info.lastSyncTime != null ? info.lastSyncTime.toString() : "");
        return module;
    }

    /**
     * 判断模块是否受全量同步影响
     */
    private boolean isAffectedByFullSync(String key) {
        return "teams".equals(key) || "players".equals(key) || "matches".equals(key) || "draft".equals(key);
    }

    /**
     * 根据同步状态估算进度
     */
    private int estimateProgress(String key) {
        // 根据DataSyncService的同步结果估算进度
        Map<String, Object> result = dataSyncService.getSyncResult();
        if (result.isEmpty()) return 10;

        // 如果有结果，说明同步正在进行中
        if (result.containsKey("teams")) return 30;
        if (result.containsKey("players")) return 50;
        if (result.containsKey("games")) return 70;
        if (result.containsKey("draftPicks")) return 90;
        return 20;
    }

    /**
     * 暂停指定模块同步
     */
    @PostMapping("/pause/{module}")
    public ResponseEntity<Map<String, String>> pauseModule(@PathVariable String module) {
        ModuleSyncInfo info = moduleStatus.get(module);
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "未知模块"));
        }

        if ("syncing".equals(info.status)) {
            info.paused = true;
            info.status = "paused";
            log.info("模块 {} 已暂停", module);
            return ResponseEntity.ok(Map.of("message", info.name + "已暂停", "status", "paused"));
        }

        return ResponseEntity.ok(Map.of("message", "模块未在同步中", "status", info.status));
    }

    /**
     * 继续指定模块同步
     */
    @PostMapping("/resume/{module}")
    public ResponseEntity<Map<String, String>> resumeModule(@PathVariable String module) {
        ModuleSyncInfo info = moduleStatus.get(module);
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "未知模块"));
        }

        if ("paused".equals(info.status)) {
            info.paused = false;
            info.status = "syncing";
            log.info("模块 {} 已继续", module);
            return ResponseEntity.ok(Map.of("message", info.name + "已继续", "status", "syncing"));
        }

        return ResponseEntity.ok(Map.of("message", "模块未暂停", "status", info.status));
    }

    /**
     * 取消指定模块同步
     */
    @PostMapping("/cancel/{module}")
    public ResponseEntity<Map<String, String>> cancelModule(@PathVariable String module) {
        ModuleSyncInfo info = moduleStatus.get(module);
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "未知模块"));
        }

        info.cancelled = true;
        info.paused = false;
        info.status = "idle";
        info.progress = 0;
        log.info("模块 {} 已取消", module);
        return ResponseEntity.ok(Map.of("message", info.name + "已取消", "status", "idle"));
    }

    /**
     * 暂停所有模块
     */
    @PostMapping("/pause-all")
    public ResponseEntity<Map<String, String>> pauseAll() {
        moduleStatus.forEach((key, info) -> {
            if ("syncing".equals(info.status)) {
                info.paused = true;
                info.status = "paused";
            }
        });
        return ResponseEntity.ok(Map.of("message", "所有同步已暂停", "status", "paused"));
    }

    /**
     * 取消所有模块
     */
    @PostMapping("/cancel-all")
    public ResponseEntity<Map<String, String>> cancelAll() {
        moduleStatus.forEach((key, info) -> {
            if ("syncing".equals(info.status) || "paused".equals(info.status) || "pending".equals(info.status)) {
                info.cancelled = true;
                info.paused = false;
                info.status = "idle";
                info.progress = 0;
            }
        });
        return ResponseEntity.ok(Map.of("message", "所有同步已取消", "status", "idle"));
    }

    /**
     * 模块同步信息内部类
     */
    private static class ModuleSyncInfo {
        String name;
        String icon;
        String status = "idle";
        int progress = 0;
        LocalDateTime lastSyncTime;
        String errorMessage = "";
        boolean cancelled = false;
        boolean paused = false;

        ModuleSyncInfo(String name, String icon) {
            this.name = name;
            this.icon = icon;
        }
    }
}

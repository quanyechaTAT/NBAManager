package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.WebSocketController;
import com.nbamanager.web.dto.DraftPickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;

/**
 * NBA实时数据同步服务
 * - 今日比赛/新闻的实时同步执行体（由 TodayGamesSyncWorker/NewsSyncWorker 调用）
 * - 重复新闻清理与 ESPN↔NBA gameId 映射维护
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NbaLiveSyncService {

    private final GameNewsRepository gameNewsRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final GameNewsService gameNewsService;
    private final SmartDataService smartDataService;
    private final PostService postService;
    private final DraftService draftService;
    private final NotificationService notificationService;
    private final WebSocketController webSocketController;
    private final NbaDataService nbaDataService;
    private final GameIdMappingService gameIdMappingService;
    private final PythonScriptRunner pythonScriptRunner;
    private final PythonTimeoutProperties pythonTimeoutProperties;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final NewsPersistenceService newsPersistenceService;

    /**
     * 启动时清理重复新闻
     */
    @PostConstruct
    public void init() {
        try {
            cleanDuplicateNews();
        } catch (Exception e) {
            log.warn("启动时清理重复新闻失败: {}", e.getMessage());
        }
    }

    /**
     * 每6小时清理一次重复新闻
     */
    @Scheduled(fixedRate = 21600000)
    public void scheduledCleanDuplicates() {
        try {
            cleanDuplicateNews();
        } catch (Exception e) {
            log.warn("定时清理重复新闻失败: {}", e.getMessage());
        }
    }



    /**
     * 获取当前赛季（10月前为上赛季，10月后为本赛季）
     */
    private String getCurrentSeason() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        if (month >= 10) {
            return year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        }
    }

    /**
     * 每小时同步NBA新闻
     */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public void syncNbaNews() {
        syncNbaNewsWithResult();
    }

    /**
     * 解析新闻 JSON 结构为 SyncExecResult（包级可见，供测试调用）
     */
    static SyncExecResult parseNewsResult(JSONObject data) {
        if (data == null || !data.has("news")) {
            return SyncExecResult.failed("Python返回无数据");
        }

        if (!data.has("newsSourceStatus")) {
            return SyncExecResult.failed("Python返回缺少 newsSourceStatus 字段");
        }
        String sourceStatus = data.getString("newsSourceStatus");
        String providerSummary = data.has("newsProviderResults")
                ? data.getJSONArray("newsProviderResults").toString() : "";

        if ("failed".equals(sourceStatus)) {
            return SyncExecResult.failed("所有新闻源失败: " + providerSummary);
        }

        JSONArray newsArray = data.getJSONArray("news");

        if ("ok".equals(sourceStatus) && newsArray.isEmpty()) {
            return SyncExecResult.failed("状态ok但数据为空，协议错误");
        }
        if ("empty".equals(sourceStatus) && !newsArray.isEmpty()) {
            return SyncExecResult.failed("状态empty但有数据，协议错误");
        }
        if (!"ok".equals(sourceStatus) && !"empty".equals(sourceStatus)
                && !"partial".equals(sourceStatus)) {
            return SyncExecResult.failed("未知状态: " + sourceStatus);
        }

        if ("partial".equals(sourceStatus)) {
            return SyncExecResult.partial(newsArray.length(), 0, 0,
                    "部分新闻源失败: " + providerSummary);
        }

        if (newsArray.isEmpty()) {
            return SyncExecResult.noGames();
        }

        return SyncExecResult.success(newsArray.length(), 0, 0);
    }

    /**
     * 同步新闻并返回结构化结果（供 Worker 使用，手动同步传 jobId 注册进程）
     */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public SyncExecResult syncNbaNewsWithResult() {
        return syncNbaNewsWithResult(null);
    }

    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public SyncExecResult syncNbaNewsWithResult(Long jobId) {
        return syncNbaNewsWithResult(jobId, (progress, stage) -> {});
    }

    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public SyncExecResult syncNbaNewsWithResult(
            Long jobId, java.util.function.BiConsumer<Integer, String> progress) {
        try {
            progress.accept(10, "FETCHING");
            PythonJsonResult fetched = fetchNewsData(jobId);
            if (fetched.failure() != null) {
                return fetched.failure();
            }
            JSONObject data = fetched.data();
            SyncExecResult parsed = parseNewsResult(data);
            if (!parsed.providerSucceeded()) {
                return parsed;
            }
            if (parsed.fetched() == 0) {
                progress.accept(90, "RETAINING");
                gameNewsService.cleanupOldNews(100);
                return parsed;
            }

            progress.accept(55, "DEDUPLICATING");
            JSONArray newsArray = data.getJSONArray("news");
            progress.accept(70, "PERSISTING");
            NewsPersistenceService.PersistResult persisted = newsPersistenceService.persist(newsArray);
            int added = persisted.inserted();

            if (added > 0) {
                log.info("NBA新闻同步完成: 新增{}条新闻", added);
                try {
                    Map<String, Object> newsData = new HashMap<>();
                    newsData.put("timestamp", LocalDateTime.now().toString());
                    newsData.put("added", added);
                    webSocketController.broadcastNews(newsData);
                } catch (Exception wsEx) {
                    log.warn("WebSocket广播失败: {}", wsEx.getMessage());
                }
            }

            progress.accept(90, "RETAINING");
            postService.cleanupOldPosts(100);

            if (parsed.partial()) {
                return SyncExecResult.partial(
                        persisted.fetched(), added, 0, parsed.error());
            }
            return SyncExecResult.success(persisted.fetched(), added, 0);

        } catch (Exception e) {
            log.error("NBA新闻同步失败", e);
            return SyncExecResult.failed("SYNC_ERROR", "新闻同步失败，请查看服务端任务日志");
        }
    }

    /**
     * 清理重复新闻：同一sourceUrl只保留翻译质量最好的一条
     */
    private void cleanDuplicateNews() {
        List<GameNews> allNews = gameNewsRepository.findAll();
        // 按sourceUrl分组
        Map<String, List<GameNews>> byUrl = allNews.stream()
                .filter(n -> n.getSourceUrl() != null && !n.getSourceUrl().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(GameNews::getSourceUrl));

        int deleted = 0;
        for (Map.Entry<String, List<GameNews>> entry : byUrl.entrySet()) {
            List<GameNews> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;

            // 按中文字符数量排序（越多翻译越好），保留最佳版本
            duplicates.sort((a, b) -> {
                int cnA = countChineseChars(a.getTitle());
                int cnB = countChineseChars(b.getTitle());
                return Integer.compare(cnB, cnA);
            });

            // 删除除第一条外的所有重复
            for (int i = 1; i < duplicates.size(); i++) {
                gameNewsRepository.delete(duplicates.get(i));
                deleted++;
            }
        }

        // 也清理标题完全相同的重复（无sourceUrl的情况）
        Map<String, List<GameNews>> byTitle = allNews.stream()
                .filter(n -> n.getTitle() != null && !n.getTitle().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(GameNews::getTitle));

        for (Map.Entry<String, List<GameNews>> entry : byTitle.entrySet()) {
            List<GameNews> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;
            for (int i = 1; i < duplicates.size(); i++) {
                gameNewsRepository.delete(duplicates.get(i));
                deleted++;
            }
        }

        if (deleted > 0) {
            log.info("清理重复新闻: 删除{}条重复记录", deleted);
        }
    }

    /**
     * 如果新数据是中文版本而旧的是英文版本，升级为中文版本。
     * 本系统面向中文用户，优先保留中文内容。
     */
    private void upgradeToChinese(GameNews old, String headline, String description, String content) {
        if (headline == null || headline.isEmpty()) return;
        if (old.getTitle() != null && old.getTitle().equals(headline)) return;
        // 新标题含中文且旧标题不含中文 → 升级
        boolean newHasChinese = headline.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        boolean oldHasChinese = old.getTitle() != null && old.getTitle().chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        if (newHasChinese && !oldHasChinese) {
            log.info("升级为中文版本: {} → {}", old.getTitle(), headline);
            old.setTitle(headline);
            if (description != null && !description.isEmpty()) {
                old.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
            }
            if (content != null && !content.isEmpty()) {
                old.setContent(content);
            }
            gameNewsRepository.save(old);
        }
    }

    /**
     * 计算字符串中中文字符的数量
     */
    private int countChineseChars(String text) {
        if (text == null) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) count++;
        }
        return count;
    }

    /**
     * 从ESPN数据创建新闻
     */
    private int createNewsFromESPN(JSONObject newsItem) {
        String headline = newsItem.optString("headline", "");
        String headlineEn = newsItem.optString("headlineEn", headline);
        String description = newsItem.optString("description", "");
        String content = newsItem.optString("content", description);
        String contentEn = newsItem.optString("contentEn", content);
        String imageUrl = newsItem.optString("imageUrl", "");
        String sourceUrl = newsItem.optString("sourceUrl", "");
        String published = newsItem.optString("published", "");
        String category = newsItem.optString("category", "general");
        String homeTeam = newsItem.optString("homeTeam", "");
        String awayTeam = newsItem.optString("awayTeam", "");
        String nbaGameId = newsItem.optString("nbaGameId", "");

        // 按 nbaGameId 查重
        if (nbaGameId != null && !nbaGameId.isEmpty()) {
            List<GameNews> byGameId = gameNewsRepository.findByNbaGameId(nbaGameId.trim());
            if (!byGameId.isEmpty()) {
                // 如果新数据是中文版本，尝试更新为中文
                GameNews old = byGameId.get(0);
                upgradeToChinese(old, headline, description, content);
                return 0;
            }
        }

        // 按 sourceUrl 查重
        List<GameNews> existing;
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            existing = gameNewsRepository.findByTitleOrSourceUrl(headline, sourceUrl);
        } else {
            existing = gameNewsRepository.findByTitle(headline);
        }

        if (!existing.isEmpty()) {
            GameNews old = existing.get(0);
            // 补充 nbaGameId（检查是否已被其他记录使用）
            if (nbaGameId != null && !nbaGameId.isEmpty()) {
                if (old.getNbaGameId() == null || old.getNbaGameId().isEmpty()) {
                    // 检查该nbaGameId是否已被其他记录使用
                    List<GameNews> conflict = gameNewsRepository.findByNbaGameId(nbaGameId.trim());
                    if (conflict.isEmpty() || conflict.get(0).getId().equals(old.getId())) {
                        old.setNbaGameId(nbaGameId);
                    }
                }
            }
            // 如果新数据是中文版本，尝试更新为中文
            upgradeToChinese(old, headline, description, content);
            gameNewsRepository.save(old);
            return 0;
        }

        // 解析发布时间
        LocalDateTime publishTime = LocalDateTime.now();
        if (published != null && !published.isEmpty()) {
            try {
                // ESPN格式: 2026-05-31T17:16:52Z
                published = published.replace("Z", "");
                publishTime = LocalDateTime.parse(published);
            } catch (Exception e) {
                // 解析失败使用当前时间
            }
        }

        // 创建新闻记录
        GameNews news = new GameNews();
        news.setTitle(headline);
        news.setTitleEn(headlineEn);
        news.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
        news.setContent(content);
        news.setContentEn(contentEn);
        // 只有真正的比赛才设置主客队，一般新闻留空
        boolean hasRealTeams = !homeTeam.isEmpty() && !awayTeam.isEmpty()
                && !"待定".equals(homeTeam) && !"待定".equals(awayTeam);
        news.setHomeTeam(hasRealTeams ? homeTeam : "");
        news.setAwayTeam(hasRealTeams ? awayTeam : "");
        news.setHomeScore(null);
        news.setAwayScore(null);
        news.setGameStartTime(publishTime);
        news.setGameEndTime(publishTime.plusHours(2));
        news.setCreateTime(LocalDateTime.now());
        news.setStatus("FINISHED"); // 新闻默认为已结束状态
        news.setNbaGameId(nbaGameId.isEmpty() ? null : nbaGameId);
        news.setCategory(category);
        news.setSourceUrl(sourceUrl);
        news.setImageUrl(imageUrl);

        gameNewsRepository.save(news);

        // 尝试建立gameId映射（ESPN格式 -> NBA格式）
        if (nbaGameId != null && !nbaGameId.isEmpty() && nbaGameId.startsWith("401")) {
            tryToCreateGameIdMapping(nbaGameId, homeTeam, awayTeam, publishTime.toLocalDate());
        }

        return 1;
    }

    /**
     * 尝试创建gameId映射
     * 通过球队名称和日期在比赛记录中查找匹配的NBA gameId
     * 如果本地没有，从NBA API获取真实比赛数据
     */
    private void tryToCreateGameIdMapping(String espnGameId, String homeTeam, String awayTeam, LocalDate matchDate) {
        if (homeTeam.isEmpty() || awayTeam.isEmpty() || "待定".equals(homeTeam) || "待定".equals(awayTeam)) {
            return;
        }

        // 在比赛记录中查找匹配的比赛
        MatchRecord match = gameIdMappingService.findMatchByTeamsAndDate(homeTeam, awayTeam, matchDate);
        if (match != null && match.getNbaGameId() != null && !match.getNbaGameId().isEmpty()) {
            // 找到匹配的比赛，建立映射
            gameIdMappingService.addMapping(espnGameId, match.getNbaGameId(), homeTeam, awayTeam, matchDate);
            log.info("自动建立gameId映射: ESPN {} -> NBA {}, {} vs {}",
                    espnGameId, match.getNbaGameId(), homeTeam, awayTeam);
        } else {
            // 本地没有匹配的比赛记录，从NBA API获取真实数据
            log.info("本地未找到匹配比赛，尝试从NBA API获取: {} vs {}, {}", homeTeam, awayTeam, matchDate);
            fetchAndCreateMapping(espnGameId, homeTeam, awayTeam, matchDate);
        }
    }

    /**
     * 从NBA API获取比赛数据并建立映射
     */
    private void fetchAndCreateMapping(String espnGameId, String homeTeam, String awayTeam, LocalDate matchDate) {
        try {
            // 调用Python脚本获取指定日期的比赛数据
            String dateStr = matchDate.toString().replace("-", "");
            JSONObject data = fetchGamesByDate(dateStr);

            if (data == null || !data.has("games")) {
                log.warn("从NBA API获取比赛数据失败: {}", dateStr);
                return;
            }

            JSONArray games = data.getJSONArray("games");
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.getJSONObject(i);
                String gameHomeTeam = game.optString("homeTeam", "");
                String gameAwayTeam = game.optString("awayTeam", "");

                // 检查球队名称是否匹配
                if (isTeamMatch(homeTeam, gameHomeTeam) && isTeamMatch(awayTeam, gameAwayTeam)) {
                    String nbaGameId = game.optString("gameId", "");
                    if (!nbaGameId.isEmpty()) {
                        // 找到匹配的比赛，建立映射
                        gameIdMappingService.addMapping(espnGameId, nbaGameId, homeTeam, awayTeam, matchDate);

                        // 同时保存比赛记录到数据库
                        saveMatchRecord(game, homeTeam, awayTeam, matchDate);

                        log.info("从NBA API获取并建立映射: ESPN {} -> NBA {}, {} vs {}",
                                espnGameId, nbaGameId, homeTeam, awayTeam);
                        return;
                    }
                }
            }

            log.warn("NBA API中未找到匹配比赛: {} vs {}, {}", homeTeam, awayTeam, matchDate);

        } catch (Exception e) {
            log.error("从NBA API获取比赛数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查球队名称是否匹配
     */
    private boolean isTeamMatch(String team1, String team2) {
        if (team1 == null || team2 == null) return false;
        // 完全匹配
        if (team1.equals(team2)) return true;
        // 包含匹配（处理翻译差异）
        if (team1.contains(team2) || team2.contains(team1)) return true;
        return false;
    }

    /**
     * 保存比赛记录到数据库
     */
    private void saveMatchRecord(JSONObject game, String homeTeam, String awayTeam, LocalDate matchDate) {
        try {
            String nbaGameId = game.optString("gameId", "");
            int homeScore = game.optInt("homeScore", 0);
            int awayScore = game.optInt("awayScore", 0);
            String status = game.optString("status", "FINISHED");

            // 检查是否已存在；历史记录缺少比赛类型时顺便补齐
            if (matchRecordRepository.existsByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, matchDate)) {
                MatchRecord existing = matchRecordRepository
                        .findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, matchDate)
                        .orElse(null);
                if (existing != null
                        && (existing.getGameType() == null || existing.getGameType().isBlank())) {
                    existing.setGameType(MatchGameTypeResolver.resolve(
                            game.optString("gameType", null), nbaGameId, matchDate));
                    matchRecordRepository.save(existing);
                }
                return;
            }

            MatchRecord record = new MatchRecord();
            record.setHomeTeam(homeTeam);
            record.setAwayTeam(awayTeam);
            record.setHomeScore(homeScore);
            record.setAwayScore(awayScore);
            record.setMatchDate(matchDate);
            record.setSeason(getCurrentSeason());
            record.setStatus(status);
            record.setNbaGameId(nbaGameId);
            record.setGameType(MatchGameTypeResolver.resolve(
                    game.optString("gameType", null), nbaGameId, matchDate));

            matchRecordRepository.save(record);
            log.info("保存比赛记录: {} vs {}, {}-{}, {}", homeTeam, awayTeam, homeScore, awayScore, matchDate);

        } catch (Exception e) {
            log.error("保存比赛记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从NBA API获取指定日期的比赛数据
     */
    private JSONObject fetchGamesByDate(String dateStr) {
        return fetchGamesByDate(dateStr, null);
    }

    private JSONObject fetchGamesByDate(String dateStr, Long jobId) {
        int timeoutSeconds = pythonTimeoutProperties.getLiveSeconds();
        PythonScriptRunner.PythonResult result = jobId != null
                ? pythonScriptRunner.runForJob("nba_data_fetcher.py", new String[]{"games_by_date", dateStr}, timeoutSeconds, jobId)
                : pythonScriptRunner.run("nba_data_fetcher.py", new String[]{"games_by_date", dateStr}, timeoutSeconds);
        if (!result.success()) {
            log.warn("调用Python脚本获取比赛数据失败: exitCode={}, stderr={}", result.exitCode(), result.stderr().substring(0, Math.min(200, result.stderr().length())));
            return null;
        }
        String jsonStr = result.stdout().trim();
        if (jsonStr.isEmpty()) return null;
        try { return new JSONObject(jsonStr); } catch (Exception e) { return null; }
    }

    /**
     * 调用Python脚本获取新闻数据
     */
    private PythonJsonResult fetchNewsData() {
        return fetchNewsData(null);
    }

    private PythonJsonResult fetchNewsData(Long jobId) {
        int timeoutSeconds = pythonTimeoutProperties.getLiveSeconds();
        Path knownFile = null;
        try {
            knownFile = createKnownNewsFile();
            String[] args = {"news", "40", knownFile.toString()};
            PythonScriptRunner.PythonResult result = jobId != null
                    ? pythonScriptRunner.runForJob("nba_data_fetcher.py", args, timeoutSeconds, jobId)
                    : pythonScriptRunner.run("nba_data_fetcher.py", args, timeoutSeconds);
            return parsePythonJsonResult(result, "新闻", timeoutSeconds);
        } catch (IOException e) {
            return PythonJsonResult.failed(SyncExecResult.failed(
                    "KNOWN_NEWS_EXPORT_FAILED", "导出已知新闻集合失败"));
        } finally {
            if (knownFile != null) {
                try {
                    Files.deleteIfExists(knownFile);
                } catch (IOException e) {
                    log.warn("删除新闻增量临时文件失败: {}", knownFile, e);
                }
            }
        }
    }

    private Path createKnownNewsFile() throws IOException {
        List<String> sourceUrls = gameNewsRepository.findAllSourceUrls();
        List<String> titles = gameNewsRepository.findAllKnownTitles();
        JSONObject known = new JSONObject();
        known.put("urls", sourceUrls == null ? List.of() : sourceUrls);
        known.put("titles", titles == null ? List.of() : titles);
        Path file = Files.createTempFile("nba-known-news-", ".json");
        Files.writeString(file, known.toString(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * 调用Python脚本获取选秀数据
     */
    private JSONObject fetchDraftData(int year) {
        PythonScriptRunner.PythonResult result = pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"draft", String.valueOf(year)},
                pythonTimeoutProperties.getBulkSeconds());
        if (!result.success()) {
            log.warn("Python脚本获取选秀数据失败: exitCode={}, stderr={}", result.exitCode(), result.stderr().substring(0, Math.min(200, result.stderr().length())));
            return null;
        }
        String jsonStr = result.stdout().trim();
        if (jsonStr.isEmpty()) return null;
        try { return new JSONObject(jsonStr); } catch (Exception e) { return null; }
    }

    /**
     * 每5分钟同步今日比赛比分（仅在有比赛时执行）
     */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public void syncTodayGames() {
        syncTodayGamesWithResult();
    }

    /**
     * 解析今日比赛 JSON 结构为 SyncExecResult（包级可见，供测试调用）
     */
    static SyncExecResult parseTodayGamesResult(JSONObject data) {
        if (data == null || !data.has("todayGames")) {
            return SyncExecResult.failed("Python返回无数据");
        }

        if (!data.has("todayGamesSourceStatus")) {
            return SyncExecResult.failed("Python返回缺少 todayGamesSourceStatus 字段");
        }
        String sourceStatus = data.getString("todayGamesSourceStatus");
        String providerSummary = data.has("todayGamesProviderResults")
                ? data.getJSONArray("todayGamesProviderResults").toString() : "";

        if ("failed".equals(sourceStatus)) {
            return SyncExecResult.failed("所有数据源失败: " + providerSummary);
        }

        JSONArray games = data.getJSONArray("todayGames");

        if ("ok".equals(sourceStatus) && games.isEmpty()) {
            return SyncExecResult.failed("状态ok但数据为空，协议错误");
        }
        if ("empty".equals(sourceStatus) && !games.isEmpty()) {
            return SyncExecResult.failed("状态empty但有数据，协议错误");
        }
        if (!"ok".equals(sourceStatus) && !"empty".equals(sourceStatus)) {
            return SyncExecResult.failed("未知状态: " + sourceStatus);
        }

        if (games.isEmpty()) {
            return SyncExecResult.noGames();
        }

        return SyncExecResult.success(games.length(), 0, 0);
    }

    /**
     * 同步今日比赛并返回结构化结果（供 Worker 使用，手动同步传 jobId 注册进程）
     */
    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public SyncExecResult syncTodayGamesWithResult() {
        return syncTodayGamesWithResult(null);
    }

    @CacheEvict(value = {"news", "todayGames", "dashboard"}, allEntries = true)
    public SyncExecResult syncTodayGamesWithResult(Long jobId) {
        try {
            PythonJsonResult fetched = fetchTodayGamesData(jobId);
            if (fetched.failure() != null) {
                return fetched.failure();
            }
            JSONObject data = fetched.data();
            SyncExecResult parsed = parseTodayGamesResult(data);
            if (!parsed.providerSucceeded()) {
                return parsed;
            }
            if (parsed.fetched() == 0) {
                gameNewsService.cleanupOldNews(100);
                return parsed;
            }

            JSONArray games = data.getJSONArray("todayGames");
            NbaLiveSyncService transactionalSelf = applicationContext == null
                    ? null : applicationContext.getBean(NbaLiveSyncService.class);
            TodayGamesPersistResult persisted = transactionalSelf == null
                    ? persistTodayGames(games) : transactionalSelf.persistTodayGames(games);
            int updated = persisted.updated();
            int backfilled = persisted.backfilled();

            if (updated > 0 || backfilled > 0) {
                log.info("今日比赛同步完成: 更新{}条记录, 补充{}条nbaGameId", updated, backfilled);
                try {
                    Map<String, Object> scoreData = new HashMap<>();
                    scoreData.put("timestamp", LocalDateTime.now().toString());
                    scoreData.put("updated", updated);
                    scoreData.put("games", games.toList());
                    webSocketController.broadcastScoreUpdate(scoreData);
                } catch (Exception wsEx) {
                    log.warn("WebSocket广播失败: {}", wsEx.getMessage());
                }
            }

            return SyncExecResult.success(games.length(), 0, updated);

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("今日比赛同步数据冲突", e);
            return SyncExecResult.failed(
                    "DATA_CONFLICT", "比赛数据存在重复的 NBA 比赛 ID，请检查数据一致性");
        } catch (Exception e) {
            log.error("今日比赛同步失败", e);
            return SyncExecResult.failed("SYNC_ERROR", "今日比赛同步失败，请查看服务端任务日志");
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TodayGamesPersistResult persistTodayGames(JSONArray games) {
        int updated = 0;
        for (int i = 0; i < games.length(); i++) {
            JSONObject game = games.getJSONObject(i);
            String homeTeam = game.optString("homeTeam", "");
            String awayTeam = game.optString("awayTeam", "");
            int homeScore = game.optInt("homeScore", 0);
            int awayScore = game.optInt("awayScore", 0);
            String status = game.optString("status", "SCHEDULED");
            String gameId = game.optString("gameId", null);
            String category = game.optString("category", "game");
            String source = game.optString("source", null);

            LocalDateTime startTime = smartDataService.parseGameStartTimePublic(game);
            updated += smartDataService.upsertGameNews(homeTeam, awayTeam, homeScore, awayScore,
                    status, gameId, category, source, startTime);
            updateMatchRecord(homeTeam, awayTeam, homeScore, awayScore, status, gameId);
        }

        int backfilled = backfillNbaGameId();
        gameNewsService.cleanupOldNews(100);
        return new TodayGamesPersistResult(updated, backfilled);
    }

    /**
     * 从MatchRecord中补充GameNews缺失的nbaGameId
     */
    private int backfillNbaGameId() {
        int count = 0;
        List<GameNews> newsWithoutGameId = gameNewsRepository.findByNbaGameIdIsNullOrNbaGameIdEmpty();

        for (GameNews news : newsWithoutGameId) {
            if (news.getHomeTeam() == null
                    || news.getAwayTeam() == null
                    || news.getHomeTeam().equals("待定")
                    || news.getGameStartTime() == null) {
                continue;
            }

            MatchRecord match = matchRecordRepository
                    .findByHomeTeamAndAwayTeamAndMatchDate(
                            news.getHomeTeam(),
                            news.getAwayTeam(),
                            news.getGameStartTime().toLocalDate())
                    .orElse(null);
            if (match == null || match.getNbaGameId() == null || match.getNbaGameId().isBlank()) {
                continue;
            }

            String nbaGameId = match.getNbaGameId().trim();
            boolean ownedByAnotherRecord = gameNewsRepository.findByNbaGameId(nbaGameId).stream()
                    .anyMatch(owner -> !java.util.Objects.equals(owner.getId(), news.getId()));
            if (ownedByAnotherRecord) {
                log.warn("跳过 nbaGameId 回填，ID 已被占用: gameNewsId={}, nbaGameId={}",
                        news.getId(), nbaGameId);
                continue;
            }

            try {
                news.setNbaGameId(nbaGameId);
                gameNewsRepository.save(news);
                count++;
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                news.setNbaGameId(null);
                log.warn("跳过并发冲突的 nbaGameId 回填: gameNewsId={}, nbaGameId={}",
                        news.getId(), nbaGameId);
            }
        }
        return count;
    }

    /**
     * 调用Python脚本获取今日比赛数据
     */
    private PythonJsonResult fetchTodayGamesData() {
        return fetchTodayGamesData(null);
    }

    private PythonJsonResult fetchTodayGamesData(Long jobId) {
        int timeoutSeconds = pythonTimeoutProperties.getLiveSeconds();
        PythonScriptRunner.PythonResult result = jobId != null
                ? pythonScriptRunner.runForJob("nba_data_fetcher.py", new String[]{"today"}, timeoutSeconds, jobId)
                : pythonScriptRunner.run("nba_data_fetcher.py", new String[]{"today"}, timeoutSeconds);
        return parsePythonJsonResult(result, "今日比赛", timeoutSeconds);
    }

    private PythonJsonResult parsePythonJsonResult(
            PythonScriptRunner.PythonResult result, String label, int timeoutSeconds) {
        if (result.timedOut()) {
            return PythonJsonResult.failed(SyncExecResult.failed(
                    "PYTHON_TIMEOUT", label + "脚本执行超时（" + timeoutSeconds + "秒）"));
        }
        if (result.exitCode() == -2) {
            return PythonJsonResult.failed(SyncExecResult.failed("CANCELLED", "任务已取消"));
        }
        if (!result.success()) {
            String stderr = safeErrorSummary(result.stderr());
            String message = label + "脚本执行失败（退出码 " + result.exitCode() + "）";
            if (!stderr.isBlank()) {
                message += ": " + stderr;
            }
            return PythonJsonResult.failed(SyncExecResult.failed("PYTHON_EXIT_ERROR", message));
        }

        String json = result.stdout() == null ? "" : result.stdout().trim();
        if (json.isEmpty()) {
            return PythonJsonResult.failed(SyncExecResult.failed(
                    "PYTHON_EMPTY_OUTPUT", label + "脚本未返回数据"));
        }
        try {
            return PythonJsonResult.success(new JSONObject(json));
        } catch (Exception e) {
            return PythonJsonResult.failed(SyncExecResult.failed(
                    "PYTHON_PROTOCOL_ERROR", label + "脚本返回了无效 JSON"));
        }
    }

    private String safeErrorSummary(String stderr) {
        if (stderr == null) {
            return "";
        }
        String normalized = stderr.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    public record TodayGamesPersistResult(int updated, int backfilled) {}

    private record PythonJsonResult(JSONObject data, SyncExecResult failure) {
        private static PythonJsonResult success(JSONObject data) {
            return new PythonJsonResult(data, null);
        }

        private static PythonJsonResult failed(SyncExecResult failure) {
            return new PythonJsonResult(null, failure);
        }
    }

    /**
     * 更新或创建GameNews记录
     */
    private int updateOrCreateGameNews(String homeTeam, String awayTeam,
                                        int homeScore, int awayScore, String status, String gameId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 查找今天的比赛（按球队名匹配）
        List<GameNews> todayNews = gameNewsRepository.findByHomeTeamAndAwayTeamAndDate(homeTeam, awayTeam, today);

        if (!todayNews.isEmpty()) {
            // 更新现有记录
            GameNews news = todayNews.get(0);
            if ("LIVE".equals(status) || "FINISHED".equals(status)) {
                news.setHomeScore(homeScore);
                news.setAwayScore(awayScore);
                // 更新标题和摘要为实时比分
                news.setTitle(String.format("%s %d : %d %s", homeTeam, homeScore, awayScore, awayTeam));
                news.setSummary(String.format("今日比赛: %s %d : %d %s", homeTeam, homeScore, awayScore, awayTeam));
            }
            if (!news.getStatus().equals(status)) {
                news.setStatus(status);
            }
            // 确保nbaGameId被设置
            if (gameId != null && !gameId.isEmpty()) {
                if (news.getNbaGameId() == null || news.getNbaGameId().isEmpty()) {
                    news.setNbaGameId(gameId);
                }
            }
            gameNewsRepository.save(news);
            return 1;
        } else {
            // 创建新记录（包括SCHEDULED的比赛，展示今日赛程）
            GameNews news = new GameNews();
            if ("LIVE".equals(status) || "FINISHED".equals(status)) {
                news.setTitle(String.format("%s %d : %d %s", homeTeam, homeScore, awayScore, awayTeam));
                news.setSummary(String.format("今日比赛: %s vs %s, 比分 %d:%d", homeTeam, awayTeam, homeScore, awayScore));
                news.setContent(String.format("今日比赛: %s vs %s\n比分: %d : %d\n状态: %s",
                        homeTeam, awayTeam, homeScore, awayScore,
                        "LIVE".equals(status) ? "进行中" : "已结束"));
            } else {
                // SCHEDULED比赛
                news.setTitle(String.format("%s vs %s", homeTeam, awayTeam));
                news.setSummary(String.format("今日赛事: %s vs %s", homeTeam, awayTeam));
                news.setContent(String.format("今日赛事: %s vs %s\n状态: 未开始", homeTeam, awayTeam));
            }
            news.setHomeTeam(homeTeam);
            news.setAwayTeam(awayTeam);
            news.setHomeScore(homeScore);
            news.setAwayScore(awayScore);
            news.setGameStartTime(now.minusHours(2));
            news.setGameEndTime(now.plusHours(3));
            news.setCreateTime(now);
            news.setStatus(status);
            news.setNbaGameId(gameId);
            news.setCategory("game");
            gameNewsRepository.save(news);
            return 1;
        }
    }

    /**
     * 更新MatchRecord
     */
    private void updateMatchRecord(String homeTeam, String awayTeam,
                                    int homeScore, int awayScore, String status, String gameId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 查找今天或昨天的比赛记录（处理时区差异）
        MatchRecord record = matchRecordRepository
                .findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, today)
                .orElse(null);
        if (record == null) {
            record = matchRecordRepository
                    .findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, yesterday)
                    .orElse(null);
        }

        if (record != null) {
            boolean wasFinished = "FINISHED".equals(record.getStatus());
            if ("LIVE".equals(status) || "FINISHED".equals(status)) {
                record.setHomeScore(homeScore);
                record.setAwayScore(awayScore);
            }
            record.setStatus(status);
            if (gameId != null && !gameId.isEmpty()
                    && (record.getNbaGameId() == null || record.getNbaGameId().isBlank())) {
                record.setNbaGameId(gameId);
            }
            if (record.getGameType() == null || record.getGameType().isBlank()) {
                LocalDate matchDate = record.getMatchDate() == null ? today : record.getMatchDate();
                record.setGameType(MatchGameTypeResolver.resolve(null, gameId, matchDate));
            }
            matchRecordRepository.save(record);

            // 比赛刚结束时通知（避免重复通知）
            if ("FINISHED".equals(status) && !wasFinished) {
                notificationService.notifyTeamFollowersNewMatch(homeTeam, awayTeam, homeScore, awayScore, record.getId());
                notificationService.notifyTeamFollowersNewMatch(awayTeam, homeTeam, awayScore, homeScore, record.getId());
            }
        } else if ("LIVE".equals(status) || "FINISHED".equals(status)) {
            // 先用gameId查重，防止重复创建
            if (gameId != null && !gameId.isEmpty()) {
                MatchRecord byGameId = matchRecordRepository.findByNbaGameId(gameId).orElse(null);
                if (byGameId != null) {
                    byGameId.setHomeScore(homeScore);
                    byGameId.setAwayScore(awayScore);
                    byGameId.setStatus(status);
                    if (byGameId.getGameType() == null || byGameId.getGameType().isBlank()) {
                        LocalDate matchDate = byGameId.getMatchDate() == null ? today : byGameId.getMatchDate();
                        byGameId.setGameType(MatchGameTypeResolver.resolve(null, gameId, matchDate));
                    }
                    matchRecordRepository.save(byGameId);
                    return;
                }
            }
            MatchRecord newRecord = new MatchRecord();
            newRecord.setHomeTeam(homeTeam);
            newRecord.setAwayTeam(awayTeam);
            newRecord.setHomeScore(homeScore);
            newRecord.setAwayScore(awayScore);
            newRecord.setMatchDate(today);
            newRecord.setSeason(getCurrentSeason());
            newRecord.setStatus(status);
            newRecord.setNbaGameId(gameId);
            newRecord.setGameType(MatchGameTypeResolver.resolve(null, gameId, today));
            matchRecordRepository.save(newRecord);
        }
    }
}

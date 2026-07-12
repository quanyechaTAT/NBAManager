package com.nbamanager.service;

import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.web.dto.GameNewsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 智能数据服务
 * 根据数据实时性要求采用不同的缓存策略
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmartDataService {

    private final GameNewsRepository gameNewsRepository;
    private final GameNewsService gameNewsService;
    private final MatchDetailService matchDetailService;

    /**
     * 获取今日赛事（实时从API获取）
     * 每次请求都从NBA API获取最新比分
     */
    public List<GameNewsDto> getTodayGamesRealtime() {
        try {
            // 1. 从Python脚本获取实时数据
            JSONObject data = fetchTodayGamesFromApi();
            if (data == null || !data.has("todayGames")) {
                // 降级：从数据库读取
                log.warn("从API获取今日赛事失败，降级从数据库读取");
                return gameNewsService.getTodayGames();
            }

            JSONArray games = data.getJSONArray("todayGames");
            if (games.isEmpty()) {
                return gameNewsService.getTodayGames();
            }

            // 2. 转换为DTO
            List<GameNewsDto> result = new ArrayList<>();
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.getJSONObject(i);
                String homeTeam = game.optString("homeTeam", "待定");
                String awayTeam = game.optString("awayTeam", "待定");
                int homeScore = game.optInt("homeScore", 0);
                int awayScore = game.optInt("awayScore", 0);
                String status = game.optString("status", "SCHEDULED");
                String gameId = game.optString("gameId", null);
                String category = game.optString("category", "game");
                String source = game.optString("source", null);
                String league = game.optString("league", "");
                LocalDateTime startTime = parseGameStartTime(game);
                LocalDateTime endTime = startTime.plusHours(3);
                String eventLabel = "summer_game".equals(category) ? "NBA夏季联赛" : "今日比赛";

                GameNewsDto dto = new GameNewsDto(
                        (long) i,
                        String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam),
                        String.format("%s: %s vs %s", eventLabel, awayTeam, homeTeam),
                        String.format("%s: %s vs %s\n比分: %d : %d\n状态: %s%s",
                                eventLabel, awayTeam, homeTeam, awayScore, homeScore,
                                statusLabel(status),
                                league == null || league.isBlank() ? "" : "\n联赛: " + league),
                        homeTeam,
                        awayTeam,
                        homeScore,
                        awayScore,
                        startTime,
                        endTime,
                        status,
                        LocalDateTime.now(),
                        gameId,
                        category,
                        source,
                        null,
                        0,
                        0,
                        false
                );
                result.add(dto);
            }

            // 3. 同时更新数据库（异步）
            updateDatabaseFromApi(games);

            return result;
        } catch (Exception e) {
            log.error("获取今日赛事失败: {}", e.getMessage());
            // 降级：从数据库读取
            return gameNewsService.getTodayGames();
        }
    }

    /**
     * 获取新闻列表（带缓存）
     * 缓存30分钟，过期则从API获取
     */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** 公开方法：解析比赛开始时间（供 NbaLiveSyncService 调用） */
    public LocalDateTime parseGameStartTimePublic(JSONObject game) {
        return parseGameStartTime(game);
    }

    /** 公开方法：更新或创建比赛记录（供 NbaLiveSyncService 调用） */
    public int upsertGameNews(String homeTeam, String awayTeam,
                              int homeScore, int awayScore, String status, String gameId,
                              String category, String source, LocalDateTime startTime) {
        return updateOrCreateGameNews(homeTeam, awayTeam, homeScore, awayScore, status, gameId, category, source, startTime);
    }

    private LocalDateTime parseGameStartTime(JSONObject game) {
        String raw = game.optString("startTime", "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.replace("Z", "+00:00"))
                    .atZoneSameInstant(SHANGHAI)
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("解析比赛开始时间失败: raw={}, error={}", raw, e.getMessage());
            return null;
        }
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "LIVE" -> "进行中";
            case "FINISHED" -> "已结束";
            case "SCHEDULED", "UPCOMING" -> "未开始";
            default -> status == null || status.isBlank() ? "未开始" : status;
        };
    }

    @Cacheable(value = "news", key = "'list:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<GameNewsDto> getNewsWithCache(String keyword, Long userId, Pageable pageable) {
        return gameNewsService.list(keyword, userId, pageable);
    }

    /**
     * 强制刷新新闻（从API获取）
     */
    @CacheEvict(value = "news", allEntries = true)
    public int refreshNewsFromApi() {
        try {
            JSONObject data = fetchNewsFromApi();
            if (data == null || !data.has("news")) {
                return 0;
            }

            JSONArray newsArray = data.getJSONArray("news");
            int added = 0;
            for (int i = 0; i < newsArray.length(); i++) {
                JSONObject newsItem = newsArray.getJSONObject(i);
                // 这里需要调用NbaLiveSyncService的createNewsFromESPN方法
                // 但由于循环依赖，我们直接在这里处理
                added += createNewsFromJson(newsItem);
            }

            // 清理旧新闻
            gameNewsService.cleanupOldNews(100);

            log.info("从API刷新新闻完成: 新增{}条", added);
            return added;
        } catch (Exception e) {
            log.error("从API刷新新闻失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 获取比赛详情（实时从API获取）
     */
    public JSONObject getMatchDetailRealtime(String gameId) {
        try {
            return fetchBoxScoreFromApi(gameId);
        } catch (Exception e) {
            log.error("获取比赛详情失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取逐节比分（实时从API获取）
     */
    public JSONObject getQuarterScoresRealtime(String gameId) {
        try {
            return fetchQuarterScoresFromApi(gameId);
        } catch (Exception e) {
            log.error("获取逐节比分失败: {}", e.getMessage());
            return null;
        }
    }

    // ========== 私有方法：调用Python脚本 ==========

    private JSONObject fetchTodayGamesFromApi() {
        return executePythonScript("today");
    }

    private JSONObject fetchNewsFromApi() {
        return executePythonScript("news", "20");
    }

    private JSONObject fetchBoxScoreFromApi(String gameId) {
        return executePythonScript("boxscore", gameId);
    }

    private JSONObject fetchQuarterScoresFromApi(String gameId) {
        return executePythonScript("quarters", gameId);
    }

    private JSONObject executePythonScript(String... args) {
        try {
            String scriptPath = findScriptPath();
            File scriptFile = new File(scriptPath);
            String absolutePath = scriptFile.getAbsolutePath();
            String workingDir = scriptFile.getParentFile().getAbsolutePath();

            // 获取Python可执行文件（优先使用虚拟环境）
            String pythonExec = getPythonExecutable();

            List<String> command = new ArrayList<>();
            command.add(pythonExec);
            command.add(absolutePath);
            for (String arg : args) {
                command.add(arg);
            }

            log.info("执行Python脚本: {} {} 参数: {}", pythonExec, absolutePath, String.join(" ", args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            pb.directory(new File(workingDir));
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            // 代理配置
            String proxyHost = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_HOST");
            String proxyPort = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_PORT");
            if (proxyHost != null && !proxyHost.isEmpty()) {
                pb.environment().put("NBA_PROXY_HOST", proxyHost);
            }
            if (proxyPort != null && !proxyPort.isEmpty()) {
                pb.environment().put("NBA_PROXY_PORT", proxyPort);
            }

            Process process = pb.start();

            // 读取stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取stderr
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Python脚本执行超时(30s)");
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python脚本执行失败, exitCode={}, error={}", exitCode, errorOutput.toString().trim());
                return null;
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                return null;
            }

            return new JSONObject(jsonStr);
        } catch (Exception e) {
            log.error("执行Python脚本失败: {}", e.getMessage());
            return null;
        }
    }

    private String findScriptPath() {
        String[] possiblePaths = {
                "scripts/nba_data_fetcher.py",
                "backend/scripts/nba_data_fetcher.py",
                "../scripts/nba_data_fetcher.py",
                "nba_data_fetcher.py"
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                log.info("找到Python脚本: {}", file.getAbsolutePath());
                return path;
            }
        }
        log.warn("未找到Python脚本，使用默认路径");
        return "scripts/nba_data_fetcher.py";
    }

    /**
     * 获取Python可执行文件路径（优先使用虚拟环境）
     */
    private String getPythonExecutable() {
        // 检查虚拟环境Python
        String[] venvPaths = {
                "backend/scripts/venv312/Scripts/python.exe",
                "backend/scripts/venv312/bin/python",
                "scripts/venv312/Scripts/python.exe",
                "scripts/venv312/bin/python",
                "backend/scripts/venv/Scripts/python.exe",
                "backend/scripts/venv/bin/python",
                "scripts/venv/Scripts/python.exe",
                "scripts/venv/bin/python"
        };

        for (String venvPath : venvPaths) {
            File venvPython = new File(venvPath);
            if (venvPython.exists()) {
                log.info("使用虚拟环境Python: {}", venvPython.getAbsolutePath());
                return venvPython.getAbsolutePath();
            }
        }

        // 降级使用系统Python
        log.warn("未找到虚拟环境Python，使用系统Python");
        return "python";
    }

    /**
     * 从API数据更新数据库
     */
    private void updateDatabaseFromApi(JSONArray games) {
        // 异步更新，不阻塞主流程
        new Thread(() -> {
            try {
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
                    LocalDateTime startTime = parseGameStartTime(game);

                    // 更新或创建GameNews记录
                    updateOrCreateGameNews(homeTeam, awayTeam, homeScore, awayScore, status, gameId, category, source, startTime);
                }
            } catch (Exception e) {
                log.error("异步更新数据库失败: {}", e.getMessage());
            }
        }).start();
    }

    private int updateOrCreateGameNews(String homeTeam, String awayTeam,
                                       int homeScore, int awayScore, String status, String gameId,
                                       String category, String source, LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now();
        // startTime 为 null 时不伪造，使用 now 作为安全回退（仅用于 endTime 计算）
        LocalDateTime safeStartTime = startTime != null ? startTime : now;
        LocalDateTime safeEndTime = safeStartTime.plusHours(3);
        String safeCategory = category == null || category.isBlank() ? "game" : category;
        String label = "summer_game".equals(safeCategory) ? "NBA夏季联赛" : "今日比赛";
        boolean isScheduled = "SCHEDULED".equals(status) || "UPCOMING".equals(status);

        // 优先用 nbaGameId 查重（避免因日期不一致导致重复创建）
        com.nbamanager.domain.GameNews existing = null;
        if (gameId != null && !gameId.isBlank()) {
            List<com.nbamanager.domain.GameNews> byGameId = gameNewsRepository.findByNbaGameId(gameId);
            if (!byGameId.isEmpty()) {
                existing = byGameId.get(0);
            }
        }
        // 降级：按主客队+日期查重
        if (existing == null) {
            LocalDate today = safeStartTime.toLocalDate();
            List<com.nbamanager.domain.GameNews> todayNews = gameNewsRepository
                    .findByHomeTeamAndAwayTeamAndDate(homeTeam, awayTeam, today);
            if (!todayNews.isEmpty()) {
                existing = todayNews.get(0);
            }
        }

        if (existing != null) {
            // 只有上游有真实比分时才覆盖（LIVE/FINISHED 且非 0:0，或 SCHEDULED 时保持 null）
            if (!isScheduled && (homeScore > 0 || awayScore > 0)) {
                existing.setHomeScore(homeScore);
                existing.setAwayScore(awayScore);
                existing.setTitle(String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam));
                existing.setSummary(String.format("%s: %s %d : %d %s", label, awayTeam, awayScore, homeScore, homeTeam));
                existing.setContent(String.format("%s: %s %d : %d %s\n状态: %s",
                        label, awayTeam, awayScore, homeScore, homeTeam,
                        "LIVE".equals(status) ? "进行中" : "已结束"));
            } else if (isScheduled) {
                // SCHEDULED：比分设为 null，不写 0:0
                existing.setHomeScore(null);
                existing.setAwayScore(null);
                existing.setTitle(String.format("%s vs %s", awayTeam, homeTeam));
                existing.setSummary(String.format("%s: %s vs %s, 未开始", label, awayTeam, homeTeam));
                existing.setContent(String.format("%s: %s vs %s\n状态: 未开始", label, awayTeam, homeTeam));
            } else {
                // LIVE/FINISHED 但上游比分为空（0:0），只更新状态文案
                existing.setTitle(String.format("%s vs %s", awayTeam, homeTeam));
                existing.setSummary(String.format("%s: %s vs %s, %s", label, awayTeam, homeTeam,
                        "LIVE".equals(status) ? "进行中" : "已结束"));
                existing.setContent(String.format("%s: %s vs %s\n状态: %s", label, awayTeam, homeTeam,
                        "LIVE".equals(status) ? "进行中" : "已结束"));
            }
            if (!existing.getStatus().equals(status)) {
                existing.setStatus(status);
            }
            if (gameId != null && !gameId.isEmpty()) {
                existing.setNbaGameId(gameId);
            }
            if (startTime != null) {
                existing.setGameStartTime(safeStartTime);
                existing.setGameEndTime(safeEndTime);
            }
            existing.setHomeTeam(homeTeam);
            existing.setAwayTeam(awayTeam);
            existing.setCategory(safeCategory);
            if (source != null && !source.isBlank()) {
                existing.setSourceUrl(source);
            }
            gameNewsRepository.save(existing);
            return 1;
        } else {
            log.info("创建比赛记录: {} vs {}, gameId={}, category={}", homeTeam, awayTeam, gameId, safeCategory);
            com.nbamanager.domain.GameNews news = new com.nbamanager.domain.GameNews();
            if (isScheduled) {
                news.setTitle(String.format("%s vs %s", awayTeam, homeTeam));
                news.setSummary(String.format("%s: %s vs %s, 未开始", label, awayTeam, homeTeam));
                news.setHomeScore(null);
                news.setAwayScore(null);
            } else {
                news.setTitle(String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam));
                news.setSummary(String.format("%s: %s vs %s, 比分 %d:%d", label, awayTeam, homeTeam, awayScore, homeScore));
                news.setHomeScore(homeScore);
                news.setAwayScore(awayScore);
            }
            news.setContent(news.getSummary());
            news.setHomeTeam(homeTeam);
            news.setAwayTeam(awayTeam);
            news.setGameStartTime(startTime != null ? safeStartTime : null);
            news.setGameEndTime(startTime != null ? safeEndTime : null);
            news.setCreateTime(now);
            news.setStatus(status);
            news.setNbaGameId(gameId);
            news.setCategory(safeCategory);
            news.setSourceUrl(source);
            gameNewsRepository.save(news);
            return 1;
        }
    }

    /**
     * 如果新数据是中文版本而旧的是英文版本，升级为中文版本。
     * 本系统面向中文用户，优先保留中文内容。
     */
    private void upgradeToChinese(com.nbamanager.domain.GameNews old, String headline, String description, String content) {
        if (headline == null || headline.isEmpty()) return;
        if (old.getTitle() != null && old.getTitle().equals(headline)) return;
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

    private int createNewsFromJson(JSONObject newsItem) {
        String headline = newsItem.optString("headline", "");
        String description = newsItem.optString("description", "");
        String content = newsItem.optString("content", description);
        String imageUrl = newsItem.optString("imageUrl", "");
        String sourceUrl = newsItem.optString("sourceUrl", "");
        String category = newsItem.optString("category", "general");
        String homeTeam = newsItem.optString("homeTeam", "");
        String awayTeam = newsItem.optString("awayTeam", "");
        String nbaGameId = newsItem.optString("nbaGameId", "");

        // 按 nbaGameId 查重
        if (nbaGameId != null && !nbaGameId.isEmpty()) {
            List<com.nbamanager.domain.GameNews> byGameId = gameNewsRepository.findByNbaGameId(nbaGameId.trim());
            if (!byGameId.isEmpty()) {
                // 如果新数据是中文版本，尝试升级
                upgradeToChinese(byGameId.get(0), headline, description, content);
                return 0;
            }
        }
        // 按 sourceUrl 查重
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            List<com.nbamanager.domain.GameNews> existing = gameNewsRepository.findByTitleOrSourceUrl(headline, sourceUrl);
            if (!existing.isEmpty()) {
                upgradeToChinese(existing.get(0), headline, description, content);
                return 0;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("创建新闻: title={}, nbaGameId={}, category={}", headline, nbaGameId, category);
        com.nbamanager.domain.GameNews news = new com.nbamanager.domain.GameNews();
        news.setTitle(headline);
        news.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
        news.setContent(content);
        // 只有真正的比赛才设置主客队，一般新闻留空避免显示"待定 vs 待定"
        boolean hasRealTeams = !homeTeam.isEmpty() && !awayTeam.isEmpty()
                && !"待定".equals(homeTeam) && !"待定".equals(awayTeam);
        news.setHomeTeam(hasRealTeams ? homeTeam : "");
        news.setAwayTeam(hasRealTeams ? awayTeam : "");
        // 优先使用真实比赛时间，避免用 now() 导致比赛被错误归入今天
        LocalDateTime gameStartTime = parseGameStartTime(newsItem);
        news.setGameStartTime(gameStartTime);
        news.setGameEndTime(gameStartTime.plusHours(2));
        news.setCreateTime(now);
        news.setStatus("FINISHED");
        news.setNbaGameId(nbaGameId.isEmpty() ? null : nbaGameId);
        news.setCategory(category);
        news.setSourceUrl(sourceUrl);
        news.setImageUrl(imageUrl);

        gameNewsRepository.save(news);
        return 1;
    }
}

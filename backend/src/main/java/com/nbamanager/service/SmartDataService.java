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

                GameNewsDto dto = new GameNewsDto(
                        (long) i,
                        String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam),
                        String.format("今日比赛: %s vs %s", awayTeam, homeTeam),
                        String.format("今日比赛: %s vs %s\n比分: %d : %d\n状态: %s",
                                awayTeam, homeTeam, awayScore, homeScore,
                                "LIVE".equals(status) ? "进行中" : "已结束"),
                        homeTeam,
                        awayTeam,
                        homeScore,
                        awayScore,
                        LocalDateTime.now().minusHours(2),
                        LocalDateTime.now().plusHours(3),
                        status,
                        LocalDateTime.now(),
                        gameId,
                        "game",
                        null,
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

                    // 更新或创建GameNews记录
                    updateOrCreateGameNews(homeTeam, awayTeam, homeScore, awayScore, status, gameId);
                }
            } catch (Exception e) {
                log.error("异步更新数据库失败: {}", e.getMessage());
            }
        }).start();
    }

    private int updateOrCreateGameNews(String homeTeam, String awayTeam,
                                       int homeScore, int awayScore, String status, String gameId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 查找今天的比赛
        List<com.nbamanager.domain.GameNews> todayNews = gameNewsRepository
                .findByHomeTeamAndAwayTeamAndDate(homeTeam, awayTeam, today);

        if (!todayNews.isEmpty()) {
            com.nbamanager.domain.GameNews news = todayNews.get(0);
            if ("LIVE".equals(status) || "FINISHED".equals(status)) {
                news.setHomeScore(homeScore);
                news.setAwayScore(awayScore);
                news.setTitle(String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam));
            }
            if (!news.getStatus().equals(status)) {
                news.setStatus(status);
            }
            if (gameId != null && !gameId.isEmpty() && (news.getNbaGameId() == null || news.getNbaGameId().isEmpty())) {
                news.setNbaGameId(gameId);
            }
            gameNewsRepository.save(news);
            return 1;
        } else if ("LIVE".equals(status) || "FINISHED".equals(status)) {
            com.nbamanager.domain.GameNews news = new com.nbamanager.domain.GameNews();
            news.setTitle(String.format("%s %d : %d %s", awayTeam, awayScore, homeScore, homeTeam));
            news.setSummary(String.format("今日比赛: %s vs %s, 比分 %d:%d", awayTeam, homeTeam, awayScore, homeScore));
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
        return 0;
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

        // 检查是否已存在
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            List<com.nbamanager.domain.GameNews> existing = gameNewsRepository.findByTitleOrSourceUrl(headline, sourceUrl);
            if (!existing.isEmpty()) {
                return 0;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        com.nbamanager.domain.GameNews news = new com.nbamanager.domain.GameNews();
        news.setTitle(headline);
        news.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
        news.setContent(content);
        news.setHomeTeam(homeTeam.isEmpty() ? "待定" : homeTeam);
        news.setAwayTeam(awayTeam.isEmpty() ? "待定" : awayTeam);
        news.setGameStartTime(now);
        news.setGameEndTime(now.plusHours(2));
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

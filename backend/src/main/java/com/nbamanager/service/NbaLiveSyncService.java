package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;

/**
 * NBA实时数据同步服务
 * - 每日凌晨3点同步全量数据（球队、球员、比赛）
 * - 比赛日每5分钟同步今日比赛比分
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NbaLiveSyncService {

    private final NbaDataSyncService nbaDataSyncService;
    private final GameNewsRepository gameNewsRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final GameNewsService gameNewsService;
    private final PostService postService;

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
     * 每日凌晨3点执行全量数据同步
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledFullSync() {
        log.info("========== 定时全量同步开始 ==========");
        nbaDataSyncService.syncAll();
        log.info("========== 定时全量同步结束 ==========");
    }

    /**
     * 每小时同步NBA新闻
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void syncNbaNews() {
        try {
            // 先清理已存在的英文/中文重复新闻
            cleanDuplicateNews();

            JSONObject data = fetchNewsData();
            if (data == null || !data.has("news")) {
                return;
            }

            JSONArray newsArray = data.getJSONArray("news");
            if (newsArray.isEmpty()) {
                return;
            }

            int added = 0;
            for (int i = 0; i < newsArray.length(); i++) {
                JSONObject newsItem = newsArray.getJSONObject(i);
                added += createNewsFromESPN(newsItem);
            }

            if (added > 0) {
                log.info("NBA新闻同步完成: 新增{}条新闻", added);
            }

            // 清理超出100条的旧资讯
            int cleanedNews = gameNewsService.cleanupOldNews(100);
            if (cleanedNews > 0) {
                log.info("清理旧资讯: 删除{}条", cleanedNews);
            }

            // 清理超出100条的旧帖子
            int cleanedPosts = postService.cleanupOldPosts(100);
            if (cleanedPosts > 0) {
                log.info("清理旧帖子: 删除{}条", cleanedPosts);
            }

        } catch (Exception e) {
            log.warn("NBA新闻同步失败: {}", e.getMessage());
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
        String description = newsItem.optString("description", "");
        String content = newsItem.optString("content", description);
        String imageUrl = newsItem.optString("imageUrl", "");
        String sourceUrl = newsItem.optString("sourceUrl", "");
        String published = newsItem.optString("published", "");
        String category = newsItem.optString("category", "general");
        String homeTeam = newsItem.optString("homeTeam", "");
        String awayTeam = newsItem.optString("awayTeam", "");
        String nbaGameId = newsItem.optString("nbaGameId", "");

        // 检查是否已存在相同标题或相同来源URL的新闻（防止英文版和翻译版重复）
        List<GameNews> existing = gameNewsRepository.findAll().stream()
                .filter(n -> n.getTitle().equals(headline)
                        || (sourceUrl != null && !sourceUrl.isEmpty() && sourceUrl.equals(n.getSourceUrl())))
                .toList();

        if (!existing.isEmpty()) {
            // 如果已存在但没有nbaGameId，尝试补充
            GameNews old = existing.get(0);
            if (nbaGameId != null && !nbaGameId.isEmpty()) {
                if (old.getNbaGameId() == null || old.getNbaGameId().isEmpty()) {
                    old.setNbaGameId(nbaGameId);
                    gameNewsRepository.save(old);
                }
            }
            // 如果新数据是中文版本而旧的是英文版本，更新为中文版本
            if (headline != null && !headline.isEmpty()
                    && old.getTitle() != null && !old.getTitle().equals(headline)
                    && headline.chars().anyMatch(c -> c > 0x4E00 && c < 0x9FFF)) {
                old.setTitle(headline);
                if (description != null && !description.isEmpty()) {
                    old.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
                }
                if (content != null && !content.isEmpty()) {
                    old.setContent(content);
                }
                gameNewsRepository.save(old);
            }
            return 0; // 已存在，跳过
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
        news.setSummary(description.length() > 300 ? description.substring(0, 297) + "..." : description);
        news.setContent(content);
        news.setHomeTeam(homeTeam.isEmpty() ? "待定" : homeTeam);
        news.setAwayTeam(awayTeam.isEmpty() ? "待定" : awayTeam);
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
        return 1;
    }

    /**
     * 调用Python脚本获取新闻数据
     */
    private JSONObject fetchNewsData() {
        try {
            String scriptPath = findScriptPath();
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "news", "20");
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("NBA_PROXY_HOST", "127.0.0.1");
            pb.environment().put("NBA_PROXY_PORT", "7890");

            // 传递翻译API配置（从系统环境变量或.env文件读取）
            String mimoApiKey = com.nbamanager.config.EnvFileReader.get("MIMO_API_KEY");
            String mimoBaseUrl = com.nbamanager.config.EnvFileReader.get("MIMO_BASE_URL");
            if (mimoApiKey != null && !mimoApiKey.isEmpty()) {
                pb.environment().put("MIMO_API_KEY", mimoApiKey);
            }
            if (mimoBaseUrl != null && !mimoBaseUrl.isEmpty()) {
                pb.environment().put("MIMO_BASE_URL", mimoBaseUrl);
            }

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                return null;
            }

            return new JSONObject(jsonStr);

        } catch (Exception e) {
            log.warn("调用Python脚本获取新闻失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 每5分钟同步今日比赛比分（仅在有比赛时执行）
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void syncTodayGames() {
        try {
            JSONObject data = fetchTodayGamesData();
            if (data == null || !data.has("todayGames")) {
                return;
            }

            JSONArray games = data.getJSONArray("todayGames");
            if (games.isEmpty()) {
                return;
            }

            int updated = 0;
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.getJSONObject(i);
                String homeTeam = game.getString("homeTeam");
                String awayTeam = game.getString("awayTeam");
                int homeScore = game.optInt("homeScore", 0);
                int awayScore = game.optInt("awayScore", 0);
                String status = game.optString("status", "SCHEDULED");
                String gameId = game.optString("gameId", null);

                // 更新或创建GameNews记录（包括SCHEDULED的比赛，用于展示今日赛程）
                updated += updateOrCreateGameNews(homeTeam, awayTeam, homeScore, awayScore, status, gameId);

                // 更新MatchRecord
                updateMatchRecord(homeTeam, awayTeam, homeScore, awayScore, status, gameId);
            }

            // 补充已有新闻的nbaGameId（从MatchRecord中匹配）
            int backfilled = backfillNbaGameId();

            if (updated > 0 || backfilled > 0) {
                log.info("今日比赛同步完成: 更新{}条记录, 补充{}条nbaGameId", updated, backfilled);
            }

        } catch (Exception e) {
            log.warn("今日比赛同步失败: {}", e.getMessage());
        }
    }

    /**
     * 从MatchRecord中补充GameNews缺失的nbaGameId
     */
    private int backfillNbaGameId() {
        int count = 0;
        List<GameNews> newsWithoutGameId = gameNewsRepository.findAll().stream()
                .filter(n -> (n.getNbaGameId() == null || n.getNbaGameId().isEmpty())
                        && n.getHomeTeam() != null && !n.getHomeTeam().equals("待定"))
                .toList();

        List<MatchRecord> allMatches = matchRecordRepository.findAll();

        for (GameNews news : newsWithoutGameId) {
            for (MatchRecord match : allMatches) {
                if (match.getNbaGameId() != null && !match.getNbaGameId().isEmpty()
                        && match.getHomeTeam().equals(news.getHomeTeam())
                        && match.getAwayTeam().equals(news.getAwayTeam())) {
                    news.setNbaGameId(match.getNbaGameId());
                    gameNewsRepository.save(news);
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * 调用Python脚本获取今日比赛数据
     */
    private JSONObject fetchTodayGamesData() {
        try {
            String scriptPath = findScriptPath();
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "today");
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("NBA_PROXY_HOST", "127.0.0.1");
            pb.environment().put("NBA_PROXY_PORT", "7890");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                return null;
            }

            return new JSONObject(jsonStr);

        } catch (Exception e) {
            log.warn("调用Python脚本获取今日比赛失败: {}", e.getMessage());
            return null;
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
        List<GameNews> todayNews = gameNewsRepository.findAll().stream()
                .filter(n -> n.getHomeTeam().equals(homeTeam)
                        && n.getAwayTeam().equals(awayTeam)
                        && n.getGameStartTime() != null
                        && n.getGameStartTime().toLocalDate().equals(today))
                .toList();

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

        // 查找今天的比赛记录
        List<MatchRecord> existing = matchRecordRepository.findAll().stream()
                .filter(m -> m.getHomeTeam().equals(homeTeam)
                        && m.getAwayTeam().equals(awayTeam)
                        && m.getMatchDate().equals(today))
                .toList();

        if (!existing.isEmpty()) {
            MatchRecord record = existing.get(0);
            if ("LIVE".equals(status) || "FINISHED".equals(status)) {
                record.setHomeScore(homeScore);
                record.setAwayScore(awayScore);
            }
            record.setStatus(status);
            if (gameId != null && !gameId.isEmpty() && record.getNbaGameId() == null) {
                record.setNbaGameId(gameId);
            }
            matchRecordRepository.save(record);
        } else if ("LIVE".equals(status) || "FINISHED".equals(status)) {
            MatchRecord record = new MatchRecord();
            record.setHomeTeam(homeTeam);
            record.setAwayTeam(awayTeam);
            record.setHomeScore(homeScore);
            record.setAwayScore(awayScore);
            record.setMatchDate(today);
            record.setSeason("2025-26");
            record.setStatus(status);
            record.setNbaGameId(gameId);
            matchRecordRepository.save(record);
        }
    }

    private String findScriptPath() {
        String[] possiblePaths = {
                "backend/scripts/nba_data_fetcher.py",
                "scripts/nba_data_fetcher.py",
                "../scripts/nba_data_fetcher.py",
                "nba_data_fetcher.py"
        };

        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File file = new File(baseDir, path);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return new File(baseDir, "backend/scripts/nba_data_fetcher.py").getAbsolutePath();
    }
}

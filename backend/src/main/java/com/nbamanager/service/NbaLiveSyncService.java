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

                // 更新或创建GameNews记录
                updated += updateOrCreateGameNews(homeTeam, awayTeam, homeScore, awayScore, status);

                // 更新MatchRecord
                updateMatchRecord(homeTeam, awayTeam, homeScore, awayScore, status);
            }

            if (updated > 0) {
                log.info("今日比赛同步完成: 更新{}条记录", updated);
            }

        } catch (Exception e) {
            log.warn("今日比赛同步失败: {}", e.getMessage());
        }
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
                                        int homeScore, int awayScore, String status) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 查找今天的比赛
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
            }
            if (!news.getStatus().equals(status)) {
                news.setStatus(status);
            }
            gameNewsRepository.save(news);
            return 1;
        } else if ("LIVE".equals(status) || "FINISHED".equals(status)) {
            // 创建新记录（仅对进行中或已结束的比赛）
            GameNews news = new GameNews();
            news.setTitle(String.format("%s vs %s", homeTeam, awayTeam));
            news.setSummary(String.format("%s %d : %d %s", homeTeam, homeScore, awayScore, awayTeam));
            news.setContent(String.format("今日比赛: %s vs %s, 比分 %d:%d", homeTeam, awayTeam, homeScore, awayScore));
            news.setHomeTeam(homeTeam);
            news.setAwayTeam(awayTeam);
            news.setHomeScore(homeScore);
            news.setAwayScore(awayScore);
            news.setGameStartTime(now.minusHours(2));
            news.setGameEndTime(now);
            news.setCreateTime(now);
            news.setStatus(status);
            gameNewsRepository.save(news);
            return 1;
        }

        return 0;
    }

    /**
     * 更新MatchRecord
     */
    private void updateMatchRecord(String homeTeam, String awayTeam,
                                    int homeScore, int awayScore, String status) {
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

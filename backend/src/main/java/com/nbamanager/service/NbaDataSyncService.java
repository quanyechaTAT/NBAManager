package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class NbaDataSyncService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRecordRepository matchRecordRepository;

    // 同步状态管理
    private volatile boolean syncing = false;
    private volatile LocalDateTime lastSyncTime = null;
    private volatile String lastSyncStatus = "未同步";
    private final Map<String, Object> syncResult = new ConcurrentHashMap<>();

    /**
     * 异步执行数据同步
     */
    @Async("taskExecutor")
    @Transactional
    public void syncAll() {
        if (syncing) {
            log.info("数据同步正在进行中，跳过本次同步");
            return;
        }

        syncing = true;
        lastSyncStatus = "同步中...";
        long startTime = System.currentTimeMillis();

        try {
            log.info("========== 开始NBA数据同步 ==========");

            // 1. 调用Python脚本获取数据
            JSONObject data = fetchAllData();
            if (data == null) {
                throw new RuntimeException("获取数据失败");
            }

            // 2. 更新球队数据
            int teamsUpdated = 0;
            int teamsAdded = 0;
            if (data.has("teams")) {
                JSONArray teams = data.getJSONArray("teams");
                int[] result = updateTeams(teams);
                teamsUpdated = result[0];
                teamsAdded = result[1];
            }

            // 3. 更新球员数据
            int playersUpdated = 0;
            int playersAdded = 0;
            if (data.has("players")) {
                JSONArray players = data.getJSONArray("players");
                int[] result = updatePlayers(players);
                playersUpdated = result[0];
                playersAdded = result[1];
            }

            // 4. 更新比赛记录
            int gamesAdded = 0;
            if (data.has("games")) {
                JSONArray games = data.getJSONArray("games");
                gamesAdded = updateGames(games);
            }

            // 5. 记录同步结果
            long duration = System.currentTimeMillis() - startTime;
            lastSyncTime = LocalDateTime.now();
            lastSyncStatus = "同步成功";

            syncResult.put("teamsUpdated", teamsUpdated);
            syncResult.put("teamsAdded", teamsAdded);
            syncResult.put("playersUpdated", playersUpdated);
            syncResult.put("playersAdded", playersAdded);
            syncResult.put("gamesAdded", gamesAdded);
            syncResult.put("duration", duration);
            syncResult.put("timestamp", lastSyncTime.toString());

            log.info("同步完成: 球队(更新{},新增{}), 球员(更新{},新增{}), 比赛(新增{})",
                    teamsUpdated, teamsAdded, playersUpdated, playersAdded, gamesAdded);
            log.info("耗时: {}ms", duration);
            log.info("========== NBA数据同步结束 ==========");

        } catch (Exception e) {
            log.error("数据同步失败", e);
            lastSyncStatus = "同步失败: " + e.getMessage();
            syncResult.put("error", e.getMessage());
        } finally {
            syncing = false;
        }
    }

    /**
     * 调用Python脚本获取数据
     */
    private JSONObject fetchAllData() {
        try {
            // 确定脚本路径
            String scriptPath = findScriptPath();
            log.info("Python脚本路径: {}", scriptPath);

            // 构建进程
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "all");
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            // 强制Python使用UTF-8编码输出，防止Windows下GBK乱码
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            // 执行脚本
            Process process = pb.start();

            // 读取标准输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            // 等待进程完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python脚本执行失败, exitCode={}, error={}", exitCode, errorOutput.toString());
                return null;
            }

            log.info("Python脚本输出: {}", errorOutput.toString().trim());

            // 解析JSON
            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出为空");
                return null;
            }

            return new JSONObject(jsonStr);

        } catch (Exception e) {
            log.error("调用Python脚本失败", e);
            return null;
        }
    }

    /**
     * 查找Python脚本路径
     */
    private String findScriptPath() {
        // 尝试多个可能的路径
        String[] possiblePaths = {
                "backend/scripts/nba_data_fetcher.py",
                "scripts/nba_data_fetcher.py",
                "../scripts/nba_data_fetcher.py",
                "nba_data_fetcher.py"
        };

        File baseDir = new File(System.getProperty("user.dir"));
        log.info("当前工作目录: {}", baseDir.getAbsolutePath());

        for (String path : possiblePaths) {
            File file = new File(baseDir, path);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }

        // 如果找不到，返回默认路径
        return new File(baseDir, "backend/scripts/nba_data_fetcher.py").getAbsolutePath();
    }

    /**
     * 更新球队数据
     */
    private int[] updateTeams(JSONArray teams) {
        int updated = 0;
        int added = 0;

        for (int i = 0; i < teams.length(); i++) {
            JSONObject teamData = teams.getJSONObject(i);
            String name = teamData.getString("name");
            String city = teamData.getString("city");
            String conference = teamData.getString("conference");
            int wins = teamData.getInt("wins");
            int losses = teamData.getInt("losses");

            Team existingTeam = teamRepository.findByName(name);
            if (existingTeam != null) {
                // 更新现有球队
                existingTeam.setCity(city);
                existingTeam.setConference(conference);
                existingTeam.setWins(wins);
                existingTeam.setLosses(losses);
                teamRepository.save(existingTeam);
                updated++;
            } else {
                // 添加新球队
                Team newTeam = new Team();
                newTeam.setName(name);
                newTeam.setCity(city);
                newTeam.setConference(conference);
                newTeam.setWins(wins);
                newTeam.setLosses(losses);
                teamRepository.save(newTeam);
                added++;
            }
        }

        log.info("球队更新完成: 更新{}, 新增{}", updated, added);
        return new int[]{updated, added};
    }

    /**
     * 更新球员数据
     */
    private int[] updatePlayers(JSONArray players) {
        int updated = 0;
        int added = 0;

        for (int i = 0; i < players.length(); i++) {
            JSONObject playerData = players.getJSONObject(i);
            String name = playerData.getString("name");
            String teamName = playerData.getString("team");
            String position = playerData.getString("position");
            double ppg = playerData.getDouble("ppg");
            double rpg = playerData.getDouble("rpg");
            double apg = playerData.getDouble("apg");
            double spg = playerData.getDouble("spg");

            // 查找球队
            Team team = teamRepository.findByName(teamName);
            if (team == null) {
                log.warn("球队不存在: {}, 跳过球员: {}", teamName, name);
                continue;
            }

            // 查找现有球员
            Player existingPlayer = findPlayerByNameAndTeam(name, team);
            if (existingPlayer != null) {
                // 更新现有球员
                existingPlayer.setPosition(position);
                existingPlayer.setPointsPerGame(ppg);
                existingPlayer.setReboundsPerGame(rpg);
                existingPlayer.setAssistsPerGame(apg);
                existingPlayer.setStealsPerGame(spg);
                playerRepository.save(existingPlayer);
                updated++;
            } else {
                // 添加新球员
                Player newPlayer = new Player();
                newPlayer.setName(name);
                newPlayer.setTeam(team);
                newPlayer.setPosition(position);
                newPlayer.setPointsPerGame(ppg);
                newPlayer.setReboundsPerGame(rpg);
                newPlayer.setAssistsPerGame(apg);
                newPlayer.setStealsPerGame(spg);
                playerRepository.save(newPlayer);
                added++;
            }
        }

        log.info("球员更新完成: 更新{}, 新增{}", updated, added);
        return new int[]{updated, added};
    }

    /**
     * 根据名字和球队查找球员
     */
    private Player findPlayerByNameAndTeam(String name, Team team) {
        return playerRepository.findAll().stream()
                .filter(p -> p.getName().equals(name) && p.getTeam().getId().equals(team.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新比赛记录
     */
    private int updateGames(JSONArray games) {
        int added = 0;

        for (int i = 0; i < games.length(); i++) {
            JSONObject gameData = games.getJSONObject(i);
            String homeTeam = gameData.getString("homeTeam");
            String awayTeam = gameData.getString("awayTeam");
            int homeScore = gameData.getInt("homeScore");
            int awayScore = gameData.getInt("awayScore");
            String dateStr = gameData.getString("date");
            String status = gameData.optString("status", "FINISHED");

            LocalDate matchDate = LocalDate.parse(dateStr);

            // 检查是否已存在相同比赛
            boolean exists = matchRecordRepository.findAll().stream()
                    .anyMatch(m -> m.getHomeTeam().equals(homeTeam)
                            && m.getAwayTeam().equals(awayTeam)
                            && m.getMatchDate().equals(matchDate));

            if (!exists) {
                MatchRecord record = new MatchRecord();
                record.setHomeTeam(homeTeam);
                record.setAwayTeam(awayTeam);
                record.setHomeScore(homeScore);
                record.setAwayScore(awayScore);
                record.setMatchDate(matchDate);
                record.setSeason("2025-26");
                record.setStatus(status);
                matchRecordRepository.save(record);
                added++;
            }
        }

        log.info("比赛记录更新完成: 新增{}", added);
        return added;
    }

    // ========== 状态查询方法 ==========

    public boolean isSyncing() {
        return syncing;
    }

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public Map<String, Object> getSyncResult() {
        return syncResult;
    }
}

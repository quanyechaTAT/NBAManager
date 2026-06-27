package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NbaDataSyncService {


    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final DraftPickRepository draftPickRepository;

    // 同步状态管理 - 使用AtomicBoolean保证原子性
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private volatile LocalDateTime lastSyncTime = null;
    private volatile String lastSyncStatus = "未同步";
    private final Map<String, Object> syncResult = new ConcurrentHashMap<>();

    /**
     * 异步执行数据同步
     */
    @Async("taskExecutor")
    @Transactional
    @CacheEvict(value = {"teams", "players", "rankings", "dashboard", "news", "todayGames",
            "matchRecords", "matchRecordsByTeam", "headToHead"}, allEntries = true, beforeInvocation = true)
    public void syncAll() {
        // 使用compareAndSet保证原子性，防止重复执行
        if (!syncing.compareAndSet(false, true)) {
            log.info("数据同步正在进行中，跳过本次同步");
            return;
        }
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

            // 3. 清理重复球员和旧数据
            int duplicatesRemoved = cleanupDuplicatePlayers();
            log.info("清理重复球员: {} 条", duplicatesRemoved);
            int oldDataRemoved = cleanupOldPlayers();
            log.info("清理无nbaPlayerId的旧球员: {} 条", oldDataRemoved);

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

            // 5. 更新选秀记录
            int draftsAdded = 0;
            if (data.has("draftPicks")) {
                JSONArray drafts = data.getJSONArray("draftPicks");
                draftsAdded = updateDrafts(drafts);
            }

            // 7. 记录同步结果
            long duration = System.currentTimeMillis() - startTime;
            lastSyncTime = LocalDateTime.now();
            lastSyncStatus = "同步成功";

            syncResult.put("teamsUpdated", teamsUpdated);
            syncResult.put("teamsAdded", teamsAdded);
            syncResult.put("playersUpdated", playersUpdated);
            syncResult.put("playersAdded", playersAdded);
            syncResult.put("gamesAdded", gamesAdded);
            syncResult.put("draftsAdded", draftsAdded);
            syncResult.put("duration", duration);
            syncResult.put("timestamp", lastSyncTime.toString());

            log.info("同步完成: 球队(更新{},新增{}), 球员(更新{},新增{}), 比赛(新增{}), 选秀(新增{})",
                    teamsUpdated, teamsAdded, playersUpdated, playersAdded, gamesAdded, draftsAdded);
            log.info("耗时: {}ms", duration);
            log.info("========== NBA数据同步结束 ==========");

        } catch (Exception e) {
            log.error("数据同步失败", e);
            lastSyncStatus = "同步失败: " + e.getMessage();
            syncResult.put("error", e.getMessage());
        } finally {
            syncing.set(false);
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
            pb.redirectErrorStream(true); // 合并stdout和stderr，避免死锁
            pb.directory(new File(scriptPath).getParentFile());
            // 强制Python使用UTF-8编码输出，防止Windows下GBK乱码
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            // 代理配置（可选）
            String proxyHost = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_HOST");
            String proxyPort = com.nbamanager.config.EnvFileReader.get("NBA_PROXY_PORT");
            if (proxyHost != null && !proxyHost.isEmpty()) {
                pb.environment().put("NBA_PROXY_HOST", proxyHost);
            }
            if (proxyPort != null && !proxyPort.isEmpty()) {
                pb.environment().put("NBA_PROXY_PORT", proxyPort);
            }

            // 传递翻译API配置
            String mimoApiKey = com.nbamanager.config.EnvFileReader.get("MIMO_API_KEY");
            String mimoBaseUrl = com.nbamanager.config.EnvFileReader.get("MIMO_BASE_URL");
            if (mimoApiKey != null && !mimoApiKey.isEmpty()) {
                pb.environment().put("MIMO_API_KEY", mimoApiKey);
            }
            if (mimoBaseUrl != null && !mimoBaseUrl.isEmpty()) {
                pb.environment().put("MIMO_BASE_URL", mimoBaseUrl);
            }

            // 执行脚本
            Process process = pb.start();

            // 读取合并后的输出（stdout+stderr）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 带超时等待进程完成
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Python脚本执行超时(120s)");
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python脚本执行失败, exitCode={}, output={}", exitCode, output.toString().trim());
                return null;
            }

            log.info("Python脚本输出: {}", output.toString().trim());

            // 从文件读取JSON（避免Windows编码问题）
            File outputFile = new File(new File(scriptPath).getParentFile(), "output.json");
            if (!outputFile.exists()) {
                log.error("Python脚本输出文件不存在: {}", outputFile.getAbsolutePath());
                return null;
            }

            String jsonStr = new String(java.nio.file.Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出文件为空");
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
            String nameEn = teamData.optString("nameEn", "");
            String abbreviation = teamData.optString("abbreviation", "");
            String city = teamData.getString("city");
            String conference = teamData.getString("conference");
            int wins = teamData.getInt("wins");
            int losses = teamData.getInt("losses");

            Team existingTeam = teamRepository.findByName(name);
            if (existingTeam != null) {
                // 更新现有球队
                existingTeam.setNameEn(nameEn);
                existingTeam.setAbbreviation(abbreviation);
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
                newTeam.setNameEn(nameEn);
                newTeam.setAbbreviation(abbreviation);
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
     * 清理没有nbaPlayerId的旧球员数据
     */
    private int cleanupOldPlayers() {
        List<Player> allPlayers = playerRepository.findAll();
        int removed = 0;
        for (Player p : allPlayers) {
            if (p.getNbaPlayerId() == null || p.getNbaPlayerId() == 0) {
                playerRepository.delete(p);
                removed++;
            }
        }
        return removed;
    }

    /**
     * 清理重复球员（保留最新的记录）
     */
    private int cleanupDuplicatePlayers() {
        List<Player> allPlayers = playerRepository.findAll();
        int removed = 0;

        // 1. 按nbaPlayerId去重（优先）
        Map<Long, List<Player>> byNbaId = allPlayers.stream()
                .filter(p -> p.getNbaPlayerId() != null && p.getNbaPlayerId() > 0)
                .collect(Collectors.groupingBy(Player::getNbaPlayerId));
        for (Map.Entry<Long, List<Player>> entry : byNbaId.entrySet()) {
            List<Player> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;
            // 保留最新的（最后添加的）
            Player keep = duplicates.get(duplicates.size() - 1);
            for (Player p : duplicates) {
                if (!p.getId().equals(keep.getId())) {
                    playerRepository.delete(p);
                    removed++;
                }
            }
        }

        // 2. 按名字去重（处理没有nbaPlayerId的情况）
        List<Player> remaining = playerRepository.findAll();
        Map<String, List<Player>> byName = remaining.stream()
                .collect(Collectors.groupingBy(Player::getName));
        for (Map.Entry<String, List<Player>> entry : byName.entrySet()) {
            List<Player> duplicates = entry.getValue();
            if (duplicates.size() <= 1) continue;
            Player keep = duplicates.stream()
                    .filter(p -> p.getNbaPlayerId() != null && p.getNbaPlayerId() > 0)
                    .findFirst()
                    .orElse(duplicates.get(duplicates.size() - 1));
            for (Player p : duplicates) {
                if (!p.getId().equals(keep.getId())) {
                    playerRepository.delete(p);
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * 更新球员数据
     */
    private int[] updatePlayers(JSONArray players) {
        int updated = 0;
        int added = 0;

        for (int i = 0; i < players.length(); i++) {
            JSONObject playerData = players.getJSONObject(i);
            long nbaPlayerId = playerData.optLong("nbaPlayerId", 0);
            String name = playerData.getString("name");
            String nameEn = playerData.optString("nameEn", "");
            String translationStatus = playerData.optString("translationStatus", "UNTRANSLATED");
            String teamName = playerData.getString("team");
            String position = playerData.getString("position");
            double ppg = playerData.getDouble("ppg");
            double rpg = playerData.getDouble("rpg");
            double apg = playerData.getDouble("apg");
            double spg = playerData.getDouble("spg");

            // 扩展字段
            int gp = playerData.optInt("gp", 65);
            double mpg = playerData.optDouble("mpg", 28.0);
            double fgPct = playerData.optDouble("fgPct", 0.460);
            double threePct = playerData.optDouble("threePct", 0.350);
            double ftPct = playerData.optDouble("ftPct", 0.800);
            double bpg = playerData.optDouble("bpg", 0.5);
            double tpg = playerData.optDouble("tpg", 1.5);
            double per = playerData.optDouble("per", 110.0);
            double tsPct = playerData.optDouble("tsPct", fgPct);
            double usgPct = playerData.optDouble("usgPct", 20.0);
            String jersey = playerData.optString("jersey", "0");

            // 查找球队
            Team team = teamRepository.findByName(teamName);
            if (team == null) {
                log.warn("球队不存在: {}, 跳过球员: {}", teamName, name);
                continue;
            }

            // 查找现有球员 - 优先使用nbaPlayerId（处理转会），回退到name+team
            Player existingPlayer = null;
            if (nbaPlayerId > 0) {
                existingPlayer = playerRepository.findByNbaPlayerId(nbaPlayerId);
            }
            if (existingPlayer == null) {
                existingPlayer = findPlayerByNameAndTeam(name, team);
            }
            // 如果按name+team找不到，尝试按name在所有球队中查找（处理转会情况）
            if (existingPlayer == null && nbaPlayerId <= 0) {
                existingPlayer = findPlayerByName(name);
            }
            if (existingPlayer != null) {
                // 更新现有球员
                existingPlayer.setNbaPlayerId(nbaPlayerId > 0 ? nbaPlayerId : existingPlayer.getNbaPlayerId());
                existingPlayer.setName(name);
                existingPlayer.setNameEn(nameEn);
                existingPlayer.setTranslationStatus(translationStatus);
                existingPlayer.setPosition(position);
                existingPlayer.setPointsPerGame(ppg);
                existingPlayer.setReboundsPerGame(rpg);
                existingPlayer.setAssistsPerGame(apg);
                existingPlayer.setStealsPerGame(spg);
                existingPlayer.setGamesPlayed(gp);
                existingPlayer.setMinutesPerGame(mpg);
                existingPlayer.setFieldGoalPct(fgPct);
                existingPlayer.setThreePointPct(threePct);
                existingPlayer.setFreeThrowPct(ftPct);
                existingPlayer.setBlocksPerGame(bpg);
                existingPlayer.setTurnoversPerGame(tpg);
                existingPlayer.setEfficiency(per);
                existingPlayer.setTrueShootingPct(tsPct);
                existingPlayer.setUsagePct(usgPct);
                existingPlayer.setJerseyNumber(jersey);
                playerRepository.save(existingPlayer);
                updated++;
            } else {
                // 添加新球员
                Player newPlayer = new Player();
                newPlayer.setNbaPlayerId(nbaPlayerId > 0 ? nbaPlayerId : null);
                newPlayer.setName(name);
                newPlayer.setNameEn(nameEn);
                newPlayer.setTranslationStatus(translationStatus);
                newPlayer.setTeam(team);
                newPlayer.setPosition(position);
                newPlayer.setPointsPerGame(ppg);
                newPlayer.setReboundsPerGame(rpg);
                newPlayer.setAssistsPerGame(apg);
                newPlayer.setStealsPerGame(spg);
                newPlayer.setGamesPlayed(gp);
                newPlayer.setMinutesPerGame(mpg);
                newPlayer.setFieldGoalPct(fgPct);
                newPlayer.setThreePointPct(threePct);
                newPlayer.setFreeThrowPct(ftPct);
                newPlayer.setBlocksPerGame(bpg);
                newPlayer.setTurnoversPerGame(tpg);
                newPlayer.setEfficiency(per);
                newPlayer.setTrueShootingPct(tsPct);
                newPlayer.setUsagePct(usgPct);
                newPlayer.setJerseyNumber(jersey);
                newPlayer.setHeight("6-6");
                newPlayer.setWeight(210);
                newPlayer.setCountry("美国");
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
        return playerRepository.findByNameAndTeamId(name, team.getId());
    }

    /**
     * 根据名字查找球员（处理转会情况）
     */
    private Player findPlayerByName(String name) {
        return playerRepository.findByName(name);
    }

    /**
     * 更新比赛记录
     */
    private int updateGames(JSONArray games) {
        int added = 0;

        for (int i = 0; i < games.length(); i++) {
            JSONObject gameData = games.getJSONObject(i);
            String homeTeam = gameData.getString("homeTeam");
            String homeTeamEn = gameData.optString("homeTeamEn", "");
            String awayTeam = gameData.getString("awayTeam");
            String awayTeamEn = gameData.optString("awayTeamEn", "");
            int homeScore = gameData.getInt("homeScore");
            int awayScore = gameData.getInt("awayScore");
            String dateStr = gameData.getString("date");
            String status = gameData.optString("status", "FINISHED");
            String nbaGameId = gameData.optString("gameId", null);

            LocalDate matchDate = LocalDate.parse(dateStr);

            // 检查是否已存在相同比赛
            MatchRecord existing = matchRecordRepository
                    .findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, matchDate)
                    .orElse(null);

            if (existing != null) {
                // 如果已存在但没有nbaGameId，更新它
                if (nbaGameId != null && !nbaGameId.isEmpty() && existing.getNbaGameId() == null) {
                    existing.setNbaGameId(nbaGameId);
                    matchRecordRepository.save(existing);
                }
            } else {
                MatchRecord record = new MatchRecord();
                record.setHomeTeam(homeTeam);
                record.setHomeTeamEn(homeTeamEn);
                record.setAwayTeam(awayTeam);
                record.setAwayTeamEn(awayTeamEn);
                record.setHomeScore(homeScore);
                record.setAwayScore(awayScore);
                record.setMatchDate(matchDate);
                record.setSeason(getCurrentSeason());
                record.setStatus(status);
                record.setNbaGameId(nbaGameId);
                matchRecordRepository.save(record);
                added++;
            }
        }

        log.info("比赛记录更新完成: 新增{}", added);
        return added;
    }


    /**
     * 更新选秀记录
     */
    private int updateDrafts(JSONArray drafts) {
        int added = 0;

        for (int i = 0; i < drafts.length(); i++) {
            JSONObject draftData = drafts.getJSONObject(i);
            int year = draftData.optInt("year", 2024);
            int round = draftData.optInt("round", 1);
            int pickNumber = draftData.optInt("pickNumber", i + 1);
            String teamName = draftData.optString("teamName", "");
            String teamNameEn = draftData.optString("teamNameEn", "");
            String playerName = draftData.optString("playerName", "");
            String playerNameEn = draftData.optString("playerNameEn", "");
            Long nbaPlayerId = draftData.has("nbaPlayerId") ? draftData.getLong("nbaPlayerId") : null;
            String playerPosition = draftData.optString("playerPosition", "");
            String fromTeamName = draftData.optString("fromTeamName", "");
            String notes = draftData.optString("notes", "");

            // 检查是否已存在（按年份+轮次+顺位去重）
            boolean exists = draftPickRepository.existsByYearAndRoundAndPickNumber(year, round, pickNumber);

            if (!exists) {
                com.nbamanager.domain.DraftPick pick = new com.nbamanager.domain.DraftPick();
                pick.setYear(year);
                pick.setRound(round);
                pick.setPickNumber(pickNumber);
                pick.setTeamName(teamName);
                pick.setTeamNameEn(teamNameEn);
                if (!playerName.isEmpty()) pick.setPlayerName(playerName);
                if (!playerNameEn.isEmpty()) pick.setPlayerNameEn(playerNameEn);
                if (nbaPlayerId != null) pick.setNbaPlayerId(nbaPlayerId);
                if (!notes.isEmpty()) pick.setNotes(notes);
                draftPickRepository.save(pick);
                added++;
            }
        }

        log.info("选秀记录更新完成: 新增{}", added);
        return added;
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

    // ========== 状态查询方法 ==========

    public boolean isSyncing() {
        return syncing.get();
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

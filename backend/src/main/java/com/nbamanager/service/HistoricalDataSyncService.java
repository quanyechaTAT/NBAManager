package com.nbamanager.service;

import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.domain.TeamSeasonStats;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import com.nbamanager.service.PythonScriptRunner;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalDataSyncService {

    private final SeasonManagementService seasonService;
    private final PlayerSeasonStatsRepository playerStatsRepo;
    private final TeamSeasonStatsRepository teamStatsRepo;
    private final PythonScriptRunner scriptRunner;

    // 同步状态锁
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    /**
     * 同步指定赛季的常规赛数据
     */
    @Async("taskExecutor")
    @Transactional
    public void syncSeasonData(String season) {
        if (!syncing.compareAndSet(false, true)) {
            log.info("历史数据同步正在进行中，跳过: {}", season);
            return;
        }

        log.info("开始同步 {} 赛季常规赛数据", season);

        // 更新同步状态
        seasonService.updateSyncStatus(season, "SYNCING", 0, 0);

        try {
            // 1. 获取球员常规赛数据
            JSONObject playerData = scriptRunner.execute(
                    "historical_players",
                    new String[]{season},
                    180
            );

            int playerCount = 0;
            if (playerData != null && playerData.has("players")) {
                playerCount = savePlayerStats(playerData.getJSONArray("players"), season);
            }

            // 2. 获取球队常规赛数据
            JSONObject teamData = scriptRunner.execute(
                    "historical_teams",
                    new String[]{season},
                    120
            );

            int teamCount = 0;
            if (teamData != null && teamData.has("teams")) {
                teamCount = saveTeamStats(teamData.getJSONArray("teams"), season);
            }

            // 3. 更新同步状态
            seasonService.updateSyncStatus(season, "COMPLETED", playerCount, teamCount);

            log.info("{} 赛季数据同步完成: {} 名球员, {} 支球队", season, playerCount, teamCount);

        } catch (Exception e) {
            log.error("{} 赛季数据同步失败", season, e);
            seasonService.updateSyncStatus(season, "FAILED", 0, 0);
        } finally {
            syncing.set(false);
        }
    }

    /**
     * 同步版本：供 Worker 调用，等待完成后返回
     */
    public void syncSeasonDataSync(String season) {
        syncSeasonDataSync(season, null);
    }

    public void syncSeasonDataSync(String season, Long jobId) {
        if (!syncing.compareAndSet(false, true)) {
            throw new IllegalStateException("历史数据同步正在进行中，跳过: " + season);
        }
        log.info("开始同步 {} 赛季常规赛数据（同步模式）", season);
        seasonService.updateSyncStatus(season, "SYNCING", 0, 0);
        try {
            JSONObject playerData = executeForJob("historical_players", season, 180, jobId);
            int playerCount = 0;
            if (playerData != null && playerData.has("players")) {
                playerCount = savePlayerStats(playerData.getJSONArray("players"), season);
            }
            JSONObject teamData = executeForJob("historical_teams", season, 180, jobId);
            int teamCount = 0;
            if (teamData != null && teamData.has("teams")) {
                teamCount = saveTeamStats(teamData.getJSONArray("teams"), season);
            }
            if (playerCount == 0 && teamCount == 0) {
                seasonService.updateSyncStatus(season, "FAILED", 0, 0);
                throw new RuntimeException("历史赛季数据为空: " + season);
            }
            if (playerCount == 0 || teamCount == 0) {
                seasonService.updateSyncStatus(season, "PARTIAL", playerCount, teamCount);
                throw new PartialSyncException(String.format("历史赛季数据不完整: %s, 球员%d条, 球队%d条", season, playerCount, teamCount));
            }
            seasonService.updateSyncStatus(season, "COMPLETED", playerCount, teamCount);
            log.info("同步完成: {} 赛季, 球员{}条, 球队{}条", season, playerCount, teamCount);
        } catch (PartialSyncException e) {
            // 部分失败：状态已在上方写入 PARTIAL，不再覆盖
            log.warn("同步 {} 赛季数据不完整: {}", season, e.getMessage());
            throw e;
        } catch (IllegalStateException e) {
            // 已有任务在运行：不修改状态
            throw e;
        } catch (Exception e) {
            log.error("同步 {} 赛季数据失败", season, e);
            seasonService.updateSyncStatus(season, "FAILED", 0, 0);
            throw e;
        } finally {
            syncing.set(false);
        }
    }

    private JSONObject executeForJob(String action, String season, int timeoutSeconds, Long jobId) {
        return jobId == null
                ? scriptRunner.execute(action, new String[]{season}, timeoutSeconds)
                : scriptRunner.executeForJob(action, new String[]{season}, timeoutSeconds, jobId);
    }

    public void syncCurrentSeason() {
        // 检查是否需要轮转赛季
        seasonService.checkAndRotateSeasons();

        // 同步当前赛季数据
        String currentSeason = seasonService.getCurrentSeason();
        syncSeasonData(currentSeason);
    }

    /**
     * 同步缺失的历史赛季数据（最多保留5个赛季）
     */
    public void syncMissingSeasons() {
        List<String> synced = seasonService.getSyncedSeasons();
        if (synced.size() >= 5) return;

        String currentSeason = seasonService.getCurrentSeason();
        int startYear = Integer.parseInt(currentSeason.split("-")[0]);

        for (int i = 0; i < 5; i++) {
            String season = (startYear - i) + "-" + String.valueOf(startYear - i + 1).substring(2);
            if (!synced.contains(season)) {
                log.info("自动同步缺失赛季: {}", season);
                syncSeasonData(season);
            }
        }
    }

    /**
     * 保存球员统计数据
     */
    private int savePlayerStats(JSONArray players, String season) {
        int saved = 0;

        for (int i = 0; i < players.length(); i++) {
            JSONObject p = players.getJSONObject(i);
            Long nbaPlayerId = p.getLong("nbaPlayerId");

            // 查找或创建记录
            PlayerSeasonStats stats = playerStatsRepo
                    .findByNbaPlayerIdAndSeason(nbaPlayerId, season)
                    .orElse(new PlayerSeasonStats());

            // 更新字段
            stats.setNbaPlayerId(nbaPlayerId);
            stats.setSeason(season);
            stats.setPlayerName(p.optString("name", ""));
            stats.setPlayerNameEn(p.optString("nameEn", ""));
            stats.setTeamName(p.optString("teamName", ""));
            stats.setTeamNameEn(p.optString("teamNameEn", ""));
            stats.setPosition(p.optString("position", ""));
            stats.setJerseyNumber(p.optString("jersey", ""));

            // 常规赛数据
            stats.setGamesPlayed(p.optInt("gp", 0));
            stats.setGamesStarted(p.optInt("gs", 0));
            stats.setMinutesPerGame(BigDecimal.valueOf(p.optDouble("mpg", 0)));
            stats.setPointsPerGame(BigDecimal.valueOf(p.optDouble("ppg", 0)));
            stats.setReboundsPerGame(BigDecimal.valueOf(p.optDouble("rpg", 0)));
            stats.setAssistsPerGame(BigDecimal.valueOf(p.optDouble("apg", 0)));
            stats.setStealsPerGame(BigDecimal.valueOf(p.optDouble("spg", 0)));
            stats.setBlocksPerGame(BigDecimal.valueOf(p.optDouble("bpg", 0)));
            stats.setFieldGoalPct(BigDecimal.valueOf(p.optDouble("fgPct", 0)));
            stats.setThreePointPct(BigDecimal.valueOf(p.optDouble("threePct", 0)));
            stats.setFreeThrowPct(BigDecimal.valueOf(p.optDouble("ftPct", 0)));
            stats.setTurnoversPerGame(BigDecimal.valueOf(p.optDouble("topg", 0)));

            playerStatsRepo.save(stats);
            saved++;
        }

        log.info("{} 赛季球员数据保存完成: {} 名球员", season, saved);
        return saved;
    }

    /**
     * 保存球队统计数据
     */
    private int saveTeamStats(JSONArray teams, String season) {
        int saved = 0;

        for (int i = 0; i < teams.length(); i++) {
            JSONObject t = teams.getJSONObject(i);
            String teamName = t.getString("teamName");

            // 查找或创建记录
            TeamSeasonStats stats = teamStatsRepo
                    .findByTeamNameAndSeason(teamName, season)
                    .orElse(new TeamSeasonStats());

            // 更新字段
            stats.setSeason(season);
            stats.setTeamName(teamName);
            stats.setTeamNameEn(t.optString("teamNameEn", ""));
            stats.setNbaTeamId(t.optLong("teamId", 0));
            stats.setConference(t.optString("conference", ""));
            stats.setDivision(t.optString("division", ""));
            stats.setWins(t.optInt("wins", 0));
            stats.setLosses(t.optInt("losses", 0));
            stats.setWinPct(BigDecimal.valueOf(t.optDouble("winPct", 0)));
            stats.setConferenceRank(t.optInt("conferenceRank", 0));
            stats.setDivisionRank(t.optInt("divisionRank", 0));
            stats.setLeagueRank(t.optInt("leagueRank", 0));
            stats.setPointsPerGame(BigDecimal.valueOf(t.optDouble("ppg", 0)));
            stats.setOpponentsPointsPerGame(BigDecimal.valueOf(t.optDouble("oppg", 0)));
            stats.setReboundsPerGame(BigDecimal.valueOf(t.optDouble("rpg", 0)));
            stats.setAssistsPerGame(BigDecimal.valueOf(t.optDouble("apg", 0)));

            teamStatsRepo.save(stats);
            saved++;
        }

        log.info("{} 赛季球队数据保存完成: {} 支球队", season, saved);
        return saved;
    }

    /**
     * 检查是否正在同步
     */
    public boolean isSyncing() {
        return syncing.get();
    }
}

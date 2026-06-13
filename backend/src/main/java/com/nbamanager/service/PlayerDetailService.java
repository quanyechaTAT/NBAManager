package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerCareerStats;
import com.nbamanager.domain.PlayerGameLog;
import com.nbamanager.domain.Team;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerCareerStatsRepository;
import com.nbamanager.repository.PlayerGameLogRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.PlayerCareerStatsDto;
import com.nbamanager.web.dto.PlayerDetailDto;
import com.nbamanager.web.dto.PlayerGameLogDto;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerDetailService {

    private final PlayerRepository playerRepository;
    private final PlayerGameLogRepository playerGameLogRepository;
    private final PlayerCareerStatsRepository playerCareerStatsRepository;
    private final TeamRepository teamRepository;
    private final MatchRecordRepository matchRecordRepository;

    @Cacheable(value = "playerDetail", key = "#playerId")
    @Transactional(readOnly = true)
    public PlayerDetailDto getDetail(Long playerId) {
        Player p = playerRepository.findById(playerId).orElse(null);
        // 如果DB ID找不到，尝试用nbaPlayerId查找（兼容历史数据跳转）
        if (p == null) {
            p = playerRepository.findByNbaPlayerId(playerId);
        }
        if (p == null) {
            throw notFound(playerId);
        }
        Team t = p.getTeam();
        return new PlayerDetailDto(
                p.getId(),
                p.getNbaPlayerId(),
                p.getName(),
                t.getId(),
                t.getName(),
                p.getPosition(),
                p.getJerseyNumber(),
                p.getHeight(),
                p.getWeight(),
                p.getCountry(),
                p.getGamesPlayed(),
                p.getMinutesPerGame(),
                p.getPointsPerGame(),
                p.getReboundsPerGame(),
                p.getAssistsPerGame(),
                p.getStealsPerGame(),
                p.getBlocksPerGame(),
                p.getTurnoversPerGame(),
                p.getFieldGoalPct(),
                p.getThreePointPct(),
                p.getFreeThrowPct(),
                p.getEfficiency(),
                p.getTrueShootingPct(),
                p.getUsagePct(),
                p.getPer(),
                p.getWinShares(),
                p.getVorp(),
                p.getBpm(),
                p.getOffensiveRating(),
                p.getDefensiveRating());
    }

    @Cacheable(value = "playerCareer", key = "#playerId")
    @Transactional
    public List<PlayerCareerStatsDto> getCareerStats(Long playerId) {
        // 兼容nbaPlayerId查找
        Player lookupPlayer = playerRepository.findById(playerId).orElse(null);
        if (lookupPlayer == null) {
            lookupPlayer = playerRepository.findByNbaPlayerId(playerId);
        }
        Long actualId = (lookupPlayer != null) ? lookupPlayer.getId() : playerId;

        List<PlayerCareerStats> records = playerCareerStatsRepository.findByPlayerIdOrderBySeasonAsc(actualId);

        if (records.isEmpty()) {
            // 使用 nbaPlayerId 调用 Python 脚本
            Long nbaPlayerId = (lookupPlayer != null && lookupPlayer.getNbaPlayerId() != null) ? lookupPlayer.getNbaPlayerId() : playerId;
            fetchFromPython("player_career", String.valueOf(nbaPlayerId), actualId);
            records = playerCareerStatsRepository.findByPlayerIdOrderBySeasonAsc(actualId);
        }

        return records.stream().map(this::toCareerStatsDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PlayerGameLogDto> getGameLog(Long playerId, String season, Pageable pageable) {
        // 获取球员信息（兼容nbaPlayerId查找）
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) {
            player = playerRepository.findByNbaPlayerId(playerId);
        }
        if (player == null || player.getTeam() == null) {
            return Page.empty(pageable);
        }

        String teamName = player.getTeam().getName();

        // 优先从球队比赛记录中获取（这是真实可靠的数据）
        List<MatchRecord> teamMatches = matchRecordRepository.findByTeamName(teamName);

        if (!teamMatches.isEmpty()) {
            // 有球队比赛记录，使用球队数据
            List<PlayerGameLogDto> gameLogList = teamMatches.stream()
                    .filter(m -> "FINISHED".equals(m.getStatus())
                            && m.getHomeScore() != null && m.getHomeScore() > 0
                            && m.getAwayScore() != null && m.getAwayScore() > 0)
                    .sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
                    .map(m -> toGameLogFromMatch(m, teamName))
                    .collect(Collectors.toList());

            // 手动分页
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), gameLogList.size());
            List<PlayerGameLogDto> pageContent = start < gameLogList.size()
                    ? gameLogList.subList(start, end)
                    : List.of();

            return new org.springframework.data.domain.PageImpl<>(
                    pageContent, pageable, gameLogList.size());
        }

        // 没有球队比赛记录，回退到球员个人数据（过滤掉没上场的比赛）
        Page<PlayerGameLog> page;
        if (season != null && !season.isBlank()) {
            page = playerGameLogRepository.findByPlayerIdAndSeasonOrderByMatchDateDesc(playerId, season, pageable);
        } else {
            page = playerGameLogRepository.findByPlayerIdOrderByMatchDateDesc(playerId, pageable);
        }

        // 如果数据库没有数据，从Python脚本获取
        if (page.isEmpty()) {
            Long nbaPlayerId = player.getNbaPlayerId() != null ? player.getNbaPlayerId() : playerId;
            fetchFromPython("player_gamelog", String.valueOf(nbaPlayerId), playerId);
            if (season != null && !season.isBlank()) {
                page = playerGameLogRepository.findByPlayerIdAndSeasonOrderByMatchDateDesc(playerId, season, pageable);
            } else {
                page = playerGameLogRepository.findByPlayerIdOrderByMatchDateDesc(playerId, pageable);
            }
        }

        // 过滤掉球员没上场的比赛（得分、篮板、助攻都为0的比赛）
        Page<PlayerGameLog> filteredPage = page.map(g -> g);
        List<PlayerGameLogDto> filteredContent = page.getContent().stream()
                .filter(g -> g.getPoints() > 0 || g.getRebounds() > 0 || g.getAssists() > 0)
                .map(this::toGameLogDto)
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                filteredContent, pageable, filteredContent.size());
    }

    /**
     * 从球队比赛记录转换为球员比赛日志格式
     */
    private PlayerGameLogDto toGameLogFromMatch(MatchRecord m, String teamName) {
        boolean isHome = teamName.equals(m.getHomeTeam());
        String opponent = isHome ? m.getAwayTeam() : m.getHomeTeam();
        int teamScore = isHome ? m.getHomeScore() : m.getAwayScore();
        int opponentScore = isHome ? m.getAwayScore() : m.getHomeScore();
        String result = teamScore > opponentScore ? "W" : "L";

        return new PlayerGameLogDto(
                m.getNbaGameId() != null ? m.getNbaGameId() : "",
                m.getMatchDate() != null ? m.getMatchDate().toString() : "",
                opponent,
                isHome,
                "",  // 球队比赛记录无个人分钟数
                0,   // 球队比赛记录无个人得分
                0,   // 篮板
                0,   // 助攻
                0,   // 抢断
                0,   // 盖帽
                0,   // 失误
                0.0, // 命中率
                0.0, // 三分
                0.0, // 罚球
                0,   // 正负值
                result,
                teamScore,
                opponentScore
        );
    }

    /**
     * 调用Python脚本获取球员详细数据
     * @param dataType 数据类型
     * @param nbaPlayerId NBA球员ID（用于调用Python脚本）
     * @param dbPlayerId 数据库中的球员ID（用于保存数据）
     */
    private void fetchFromPython(String dataType, String nbaPlayerId, Long dbPlayerId) {
        try {
            String scriptPath = findScriptPath();
            File scriptFile = new File(scriptPath);
            File outputDir = scriptFile.getParentFile();
            log.info("调用Python脚本获取{}数据, nbaPlayerId={}, dbPlayerId={}", dataType, nbaPlayerId, dbPlayerId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, dataType, nbaPlayerId);
            pb.redirectErrorStream(false);
            pb.directory(outputDir);
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

            // 删除旧的输出文件
            File outputFile = new File(outputDir, "output.json");
            if (outputFile.exists()) {
                outputFile.delete();
            }

            Process process = pb.start();

            // 读取stdout（避免缓冲区满导致死锁）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 读取错误输出（用于日志）
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python脚本执行失败, exitCode={}, error={}", exitCode, errorOutput.toString());
                return;
            }

            // 从文件读取JSON（与NbaDataSyncService保持一致，避免编码问题）
            if (!outputFile.exists()) {
                log.error("Python脚本输出文件不存在: {}", outputFile.getAbsolutePath());
                return;
            }

            String jsonStr = new String(java.nio.file.Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出文件为空");
                return;
            }

            JSONObject data = new JSONObject(jsonStr);
            if ("player_career".equals(dataType) && data.has("career")) {
                saveCareerStats(data.getJSONArray("career"), dbPlayerId);
            } else if ("player_gamelog".equals(dataType) && data.has("gameLog")) {
                saveGameLog(data.getJSONArray("gameLog"), dbPlayerId);
            }

            log.info("{}数据获取成功, nbaPlayerId={}, dbPlayerId={}", dataType, nbaPlayerId, dbPlayerId);

        } catch (Exception e) {
            log.error("调用Python脚本失败, dataType={}, nbaPlayerId={}, dbPlayerId={}", dataType, nbaPlayerId, dbPlayerId, e);
        }
    }

    private void saveCareerStats(JSONArray careerArray, Long playerId) {
        for (int i = 0; i < careerArray.length(); i++) {
            JSONObject obj = careerArray.getJSONObject(i);
            PlayerCareerStats stats = new PlayerCareerStats();
            stats.setPlayerId(playerId);
            stats.setSeason(obj.getString("season"));
            stats.setTeamName(obj.getString("teamName"));
            stats.setGamesPlayed(obj.getInt("gamesPlayed"));
            stats.setMinutesPerGame(obj.getDouble("minutesPerGame"));
            stats.setPointsPerGame(obj.getDouble("pointsPerGame"));
            stats.setReboundsPerGame(obj.getDouble("reboundsPerGame"));
            stats.setAssistsPerGame(obj.getDouble("assistsPerGame"));
            stats.setStealsPerGame(obj.getDouble("stealsPerGame"));
            stats.setBlocksPerGame(obj.getDouble("blocksPerGame"));
            stats.setFgPct(obj.optDouble("fgPct", 0.0));
            stats.setThreePct(obj.optDouble("threePct", 0.0));
            stats.setFtPct(obj.optDouble("ftPct", 0.0));
            stats.setEfficiency(obj.optDouble("efficiency", 0.0));
            playerCareerStatsRepository.save(stats);
        }
        log.info("保存career数据: {}条记录, playerId={}", careerArray.length(), playerId);
    }

    private void saveGameLog(JSONArray gameLogArray, Long playerId) {
        for (int i = 0; i < gameLogArray.length(); i++) {
            JSONObject obj = gameLogArray.getJSONObject(i);
            PlayerGameLog gameLog = new PlayerGameLog();
            gameLog.setPlayerId(playerId);
            gameLog.setGameId(obj.getString("gameId"));
            gameLog.setMatchDate(obj.getString("matchDate"));
            gameLog.setOpponent(obj.getString("opponent"));
            gameLog.setHome(obj.optBoolean("isHome", false));
            gameLog.setMinutes(obj.optString("minutes", ""));
            gameLog.setPoints(obj.optInt("points", 0));
            gameLog.setRebounds(obj.optInt("rebounds", 0));
            gameLog.setAssists(obj.optInt("assists", 0));
            gameLog.setSteals(obj.optInt("steals", 0));
            gameLog.setBlocks(obj.optInt("blocks", 0));
            gameLog.setTurnovers(obj.optInt("turnovers", 0));
            gameLog.setFgPct(obj.optDouble("fgPct", 0.0));
            gameLog.setThreePct(obj.optDouble("threePct", 0.0));
            gameLog.setFtPct(obj.optDouble("ftPct", 0.0));
            gameLog.setPlusMinus(obj.optInt("plusMinus", 0));
            gameLog.setResult(obj.optString("result", ""));
            gameLog.setSeason(obj.optString("season", ""));
            playerGameLogRepository.save(gameLog);
        }
        log.info("保存gamelog数据: {}条记录, playerId={}", gameLogArray.length(), playerId);
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

    private PlayerCareerStatsDto toCareerStatsDto(PlayerCareerStats s) {
        return new PlayerCareerStatsDto(
                s.getSeason(),
                s.getTeamName(),
                s.getGamesPlayed(),
                s.getMinutesPerGame(),
                s.getPointsPerGame(),
                s.getReboundsPerGame(),
                s.getAssistsPerGame(),
                s.getStealsPerGame(),
                s.getBlocksPerGame(),
                s.getFgPct(),
                s.getThreePct(),
                s.getFtPct(),
                s.getEfficiency());
    }

    private PlayerGameLogDto toGameLogDto(PlayerGameLog g) {
        return new PlayerGameLogDto(
                g.getGameId(),
                g.getMatchDate(),
                g.getOpponent(),
                g.isHome(),
                g.getMinutes(),
                g.getPoints(),
                g.getRebounds(),
                g.getAssists(),
                g.getSteals(),
                g.getBlocks(),
                g.getTurnovers(),
                g.getFgPct(),
                g.getThreePct(),
                g.getFtPct(),
                g.getPlusMinus(),
                g.getResult(),
                0,  // 旧格式无球队比分
                0);
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球员不存在: " + id);
    }
}

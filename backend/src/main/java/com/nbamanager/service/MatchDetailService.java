package com.nbamanager.service;

import com.nbamanager.domain.GameBoxScore;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.repository.GameBoxScoreRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.BoxScorePlayerDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchDetailService {

    private final GameBoxScoreRepository gameBoxScoreRepository;
    private final PlayByPlayRepository playByPlayRepository;

    @Cacheable(value = "boxScore", key = "#gameId", unless = "#result.homeTeam.isEmpty()")
    @Transactional
    public BoxScoreDto getBoxScore(String gameId) {
        List<GameBoxScore> records = gameBoxScoreRepository.findByGameId(gameId);

        if (records.isEmpty()) {
            fetchFromPython("boxscore", gameId);
            records = gameBoxScoreRepository.findByGameId(gameId);
        }

        if (records.isEmpty()) {
            return new BoxScoreDto(gameId, "", "", List.of(), List.of(), List.of());
        }

        // 翻译未翻译的球员名
        translatePlayerNames(records);

        // 使用 isHome 字段分组主客队
        List<GameBoxScore> homeRecords = records.stream()
                .filter(GameBoxScore::isHome)
                .collect(Collectors.toList());
        List<GameBoxScore> awayRecords = records.stream()
                .filter(r -> !r.isHome())
                .collect(Collectors.toList());

        String homeTeam = "";
        String awayTeam = "";
        List<BoxScorePlayerDto> homePlayers = List.of();
        List<BoxScorePlayerDto> awayPlayers = List.of();

        if (!homeRecords.isEmpty()) {
            homeTeam = homeRecords.get(0).getTeamName();
            homePlayers = homeRecords.stream().map(this::toBoxScorePlayerDto).collect(Collectors.toList());
        }
        if (!awayRecords.isEmpty()) {
            awayTeam = awayRecords.get(0).getTeamName();
            awayPlayers = awayRecords.stream().map(this::toBoxScorePlayerDto).collect(Collectors.toList());
        }

        List<QuarterScoreDto> quarterScores = getQuarterScores(gameId);

        return new BoxScoreDto(gameId, homeTeam, awayTeam, homePlayers, awayPlayers, quarterScores);
    }

    /**
     * 翻译未翻译的球员名（包含大量英文字母的视为未翻译）
     */
    private void translatePlayerNames(List<GameBoxScore> records) {
        // 收集需要翻译的球员名
        List<String> toTranslate = new ArrayList<>();
        for (GameBoxScore r : records) {
            String name = r.getPlayerName();
            if (name != null && !name.isEmpty() && needsTranslation(name)) {
                toTranslate.add(name);
            }
        }

        if (toTranslate.isEmpty()) {
            return;
        }

        try {
            // 调用Python脚本批量翻译
            Map<String, String> nameMap = translateNamesViaPython(toTranslate);
            if (nameMap.isEmpty()) {
                return;
            }

            // 更新记录（批量保存）
            List<GameBoxScore> toSave = new ArrayList<>();
            for (GameBoxScore r : records) {
                String cn = nameMap.get(r.getPlayerName());
                if (cn != null && !cn.isEmpty()) {
                    r.setPlayerName(cn);
                    toSave.add(r);
                }
            }
            if (!toSave.isEmpty()) {
                gameBoxScoreRepository.saveAll(toSave);
            }
            log.info("翻译了 {} 个球员名", nameMap.size());
        } catch (Exception e) {
            log.warn("翻译球员名失败: {}", e.getMessage());
        }
    }

    /**
     * 判断文本是否需要翻译（含有大量英文字母）
     */
    private boolean needsTranslation(String text) {
        if (text == null || text.isEmpty()) return false;
        int alphaCount = 0;
        for (char c : text.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                alphaCount++;
            }
        }
        return alphaCount > text.length() * 0.5;
    }

    /**
     * 调用Python脚本批量翻译球员名
     */
    private Map<String, String> translateNamesViaPython(List<String> names) throws Exception {
        String scriptPath = findScriptPath().replace("nba_data_fetcher.py", "translate_names.py");
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            log.warn("翻译脚本不存在: {}", scriptPath);
            return Map.of();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add(scriptPath);
        cmd.addAll(names);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        pb.directory(scriptFile.getParentFile());
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("翻译脚本执行失败, exitCode={}", exitCode);
            return Map.of();
        }

        String jsonStr = output.toString().trim();
        if (jsonStr.isEmpty() || jsonStr.equals("{}")) {
            return Map.of();
        }

        // 解析JSON结果
        JSONObject json = new JSONObject(jsonStr);
        Map<String, String> result = new HashMap<>();
        for (String key : json.keySet()) {
            result.put(key, json.getString(key));
        }
        return result;
    }

    @Cacheable(value = "playByPlay", key = "#gameId + ':' + #period", unless = "#result.isEmpty()")
    @Transactional
    public List<PlayByPlayDto> getPlayByPlay(String gameId, Integer period) {
        List<PlayByPlay> records;
        if (period != null) {
            records = playByPlayRepository.findByGameIdAndPeriodOrderByGameClockDesc(gameId, period);
        } else {
            records = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        }

        if (records.isEmpty()) {
            fetchFromPython("playbyplay", gameId);
            if (period != null) {
                records = playByPlayRepository.findByGameIdAndPeriodOrderByGameClockDesc(gameId, period);
            } else {
                records = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
            }
        }

        return records.stream().map(this::toPlayByPlayDto).collect(Collectors.toList());
    }

    @Cacheable(value = "quarterScores", key = "#gameId", unless = "#result.isEmpty()")
    @Transactional
    public List<QuarterScoreDto> getQuarterScores(String gameId) {
        // 先从数据库获取Play-by-Play数据
        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);

        // 如果数据库没有数据，调用Python脚本获取
        if (allEvents.isEmpty()) {
            fetchFromPython("playbyplay", gameId);
            allEvents = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        }

        // 从Play-by-Play数据计算逐节比分
        Map<Integer, PlayByPlay> lastByPeriod = new LinkedHashMap<>();
        for (PlayByPlay event : allEvents) {
            lastByPeriod.put(event.getPeriod(), event);
        }

        List<QuarterScoreDto> result = lastByPeriod.entrySet().stream()
                .map(e -> new QuarterScoreDto(e.getKey(), e.getValue().getHomeScore(), e.getValue().getAwayScore()))
                .collect(Collectors.toList());

        // 如果Play-by-Play没有数据，尝试从ScoreboardV3获取
        if (result.isEmpty()) {
            result = fetchQuarterScoresFromScoreboard(gameId);
        }

        return result;
    }

    /**
     * 从ScoreboardV3获取逐节比分（备选方案）
     */
    private List<QuarterScoreDto> fetchQuarterScoresFromScoreboard(String gameId) {
        try {
            String scriptPath = findScriptPath();
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "quarters", gameId);
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
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
            process.waitFor();

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) return List.of();

            org.json.JSONObject root = new org.json.JSONObject(jsonStr);
            org.json.JSONArray arr = root.optJSONArray("quarterScores");
            if (arr == null) return List.of();

            List<QuarterScoreDto> scores = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                scores.add(new QuarterScoreDto(
                        obj.getInt("period"),
                        obj.getInt("homeScore"),
                        obj.getInt("awayScore")));
            }
            return scores;
        } catch (Exception e) {
            log.warn("从ScoreboardV3获取逐节比分失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 调用Python脚本获取比赛详细数据
     */
    private void fetchFromPython(String dataType, String gameId) {
        try {
            String scriptPath = findScriptPath();
            log.info("调用Python脚本获取{}数据, gameId={}", dataType, gameId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, dataType, gameId);
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
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

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            // 等待进程完成，最多60秒
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                log.error("Python脚本执行超时, dataType={}, gameId={}", dataType, gameId);
                process.destroyForcibly();
                return;
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python脚本执行失败, exitCode={}, error={}", exitCode, errorOutput.toString());
                return;
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出为空");
                return;
            }

            JSONObject data = new JSONObject(jsonStr);
            if ("boxscore".equals(dataType) && data.has("boxscore") && !data.isNull("boxscore")) {
                JSONObject boxscore = data.getJSONObject("boxscore");
                if (boxscore.has("players") && !boxscore.isNull("players")) {
                    saveBoxScore(boxscore.getJSONArray("players"), gameId);
                }
            } else if ("playbyplay".equals(dataType) && data.has("playByPlay") && !data.isNull("playByPlay")) {
                savePlayByPlay(data.getJSONArray("playByPlay"), gameId);
            }

            log.info("{}数据获取成功, gameId={}", dataType, gameId);

        } catch (Exception e) {
            log.error("调用Python脚本失败, dataType={}, gameId={}", dataType, gameId, e);
        }
    }

    private void saveBoxScore(JSONArray boxScoreArray, String gameId) {
        List<GameBoxScore> records = new ArrayList<>();
        for (int i = 0; i < boxScoreArray.length(); i++) {
            JSONObject obj = boxScoreArray.getJSONObject(i);
            GameBoxScore bs = new GameBoxScore();
            bs.setGameId(gameId);
            bs.setPlayerId(obj.getLong("playerId"));
            bs.setPlayerName(obj.getString("playerName"));
            bs.setTeamId(obj.getLong("teamId"));
            bs.setTeamName(obj.getString("teamName"));
            bs.setMinutes(obj.optString("minutes", ""));
            bs.setPoints(obj.optInt("points", 0));
            bs.setRebounds(obj.optInt("rebounds", 0));
            bs.setAssists(obj.optInt("assists", 0));
            bs.setSteals(obj.optInt("steals", 0));
            bs.setBlocks(obj.optInt("blocks", 0));
            bs.setTurnovers(obj.optInt("turnovers", 0));
            bs.setFgMade(obj.optInt("fgMade", 0));
            bs.setFgAttempted(obj.optInt("fgAttempted", 0));
            bs.setFgPct(obj.optDouble("fgPct", 0.0));
            bs.setThreeMade(obj.optInt("threeMade", 0));
            bs.setThreeAttempted(obj.optInt("threeAttempted", 0));
            bs.setThreePct(obj.optDouble("threePct", 0.0));
            bs.setFtMade(obj.optInt("ftMade", 0));
            bs.setFtAttempted(obj.optInt("ftAttempted", 0));
            bs.setFtPct(obj.optDouble("ftPct", 0.0));
            bs.setPlusMinus(obj.optInt("plusMinus", 0));
            bs.setStarter(obj.optBoolean("starter", false));
            bs.setHome(obj.optBoolean("isHome", false));
            records.add(bs);
        }
        gameBoxScoreRepository.saveAll(records);
        log.info("保存boxscore数据: {}条记录, gameId={}", records.size(), gameId);
    }

    private void savePlayByPlay(JSONArray playByPlayArray, String gameId) {
        List<PlayByPlay> records = new ArrayList<>();
        for (int i = 0; i < playByPlayArray.length(); i++) {
            JSONObject obj = playByPlayArray.getJSONObject(i);
            PlayByPlay pbp = new PlayByPlay();
            pbp.setGameId(gameId);
            pbp.setPeriod(obj.getInt("period"));
            pbp.setGameClock(obj.getString("gameClock"));
            pbp.setDescription(obj.optString("description", ""));
            pbp.setHomeScore(obj.optInt("homeScore", 0));
            pbp.setAwayScore(obj.optInt("awayScore", 0));
            pbp.setEventType(obj.optString("eventType", ""));
            pbp.setPlayerId(obj.has("playerId") && !obj.isNull("playerId") ? obj.getLong("playerId") : null);
            pbp.setPlayerName(obj.optString("playerName", ""));
            pbp.setTeamId(obj.has("teamId") && !obj.isNull("teamId") ? obj.getLong("teamId") : null);
            records.add(pbp);
        }
        playByPlayRepository.saveAll(records);
        log.info("保存play-by-play数据: {}条记录, gameId={}", records.size(), gameId);
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

    private BoxScorePlayerDto toBoxScorePlayerDto(GameBoxScore bs) {
        return new BoxScorePlayerDto(
                bs.getPlayerId(),
                bs.getPlayerName(),
                bs.getTeamName(),
                bs.getMinutes(),
                bs.getPoints(),
                bs.getRebounds(),
                bs.getAssists(),
                bs.getSteals(),
                bs.getBlocks(),
                bs.getTurnovers(),
                bs.getFgMade(),
                bs.getFgAttempted(),
                bs.getFgPct(),
                bs.getThreeMade(),
                bs.getThreeAttempted(),
                bs.getThreePct(),
                bs.getFtMade(),
                bs.getFtAttempted(),
                bs.getFtPct(),
                bs.getPlusMinus(),
                bs.getStarter()
        );
    }

    private PlayByPlayDto toPlayByPlayDto(PlayByPlay pbp) {
        return new PlayByPlayDto(
                pbp.getPeriod(),
                pbp.getGameClock(),
                pbp.getDescription(),
                pbp.getHomeScore(),
                pbp.getAwayScore(),
                pbp.getEventType(),
                pbp.getPlayerId(),
                pbp.getPlayerName()
        );
    }
}

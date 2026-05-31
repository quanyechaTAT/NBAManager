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

    @Cacheable(value = "boxScore", key = "#gameId")
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

        // 根据 teamId 分组，第一支队伍为主队，第二支为客队
        Map<Long, List<GameBoxScore>> grouped = records.stream()
                .collect(Collectors.groupingBy(GameBoxScore::getTeamId, LinkedHashMap::new, Collectors.toList()));

        List<List<GameBoxScore>> teams = new ArrayList<>(grouped.values());
        String homeTeam = "";
        String awayTeam = "";
        List<BoxScorePlayerDto> homePlayers = List.of();
        List<BoxScorePlayerDto> awayPlayers = List.of();

        if (teams.size() >= 1) {
            homeTeam = teams.get(0).get(0).getTeamName();
            homePlayers = teams.get(0).stream().map(this::toBoxScorePlayerDto).collect(Collectors.toList());
        }
        if (teams.size() >= 2) {
            awayTeam = teams.get(1).get(0).getTeamName();
            awayPlayers = teams.get(1).stream().map(this::toBoxScorePlayerDto).collect(Collectors.toList());
        }

        List<QuarterScoreDto> quarterScores = getQuarterScores(gameId);

        return new BoxScoreDto(gameId, homeTeam, awayTeam, homePlayers, awayPlayers, quarterScores);
    }

    @Cacheable(value = "playByPlay", key = "#gameId + ':' + #period")
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

    @Cacheable(value = "quarterScores", key = "#gameId")
    @Transactional(readOnly = true)
    public List<QuarterScoreDto> getQuarterScores(String gameId) {
        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);

        if (allEvents.isEmpty()) {
            fetchFromPython("playbyplay", gameId);
            allEvents = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        }

        // 每节取最后一个事件的比分
        Map<Integer, PlayByPlay> lastByPeriod = new LinkedHashMap<>();
        for (PlayByPlay event : allEvents) {
            lastByPeriod.put(event.getPeriod(), event);
        }

        return lastByPeriod.entrySet().stream()
                .map(e -> new QuarterScoreDto(e.getKey(), e.getValue().getHomeScore(), e.getValue().getAwayScore()))
                .collect(Collectors.toList());
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

            int exitCode = process.waitFor();
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
            if ("boxscore".equals(dataType) && data.has("boxscore")) {
                JSONObject boxscore = data.getJSONObject("boxscore");
                if (boxscore.has("players")) {
                    saveBoxScore(boxscore.getJSONArray("players"), gameId);
                }
            } else if ("playbyplay".equals(dataType) && data.has("playByPlay")) {
                savePlayByPlay(data.getJSONArray("playByPlay"), gameId);
            }

            log.info("{}数据获取成功, gameId={}", dataType, gameId);

        } catch (Exception e) {
            log.error("调用Python脚本失败, dataType={}, gameId={}", dataType, gameId, e);
        }
    }

    private void saveBoxScore(JSONArray boxScoreArray, String gameId) {
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
            gameBoxScoreRepository.save(bs);
        }
        log.info("保存boxscore数据: {}条记录, gameId={}", boxScoreArray.length(), gameId);
    }

    private void savePlayByPlay(JSONArray playByPlayArray, String gameId) {
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
            playByPlayRepository.save(pbp);
        }
        log.info("保存play-by-play数据: {}条记录, gameId={}", playByPlayArray.length(), gameId);
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

package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.domain.GameBoxScore;
import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.repository.GameBoxScoreRepository;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.BoxScorePlayerDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final GameIdMappingService gameIdMappingService;
    private final MatchRecordRepository matchRecordRepository;
    private final GameNewsRepository gameNewsRepository;
    private final PythonScriptRunner pythonScriptRunner;
    private final PlayerRepository playerRepository;
    private final PythonTimeoutProperties pythonTimeoutProperties;

    @Cacheable(value = "boxScore", key = "#gameId", unless = "#result.homeTeam.isEmpty() || #result.quarterScores.isEmpty()")
    @Transactional(readOnly = true)
    public BoxScoreDto getBoxScore(String gameId) {
        // 转换gameId（ESPN格式 -> NBA格式）
        String nbaGameId = gameIdMappingService.getNbaGameId(gameId);
        log.info("获取BoxScore: 原始gameId={}, 转换后gameId={}", gameId, nbaGameId);

        String fallbackHomeTeam = "";
        String fallbackAwayTeam = "";
        Integer fallbackHomeScore = null;
        Integer fallbackAwayScore = null;

        Optional<MatchRecord> matchOpt = findMatchRecord(gameId, nbaGameId);
        if (matchOpt.isPresent()) {
            MatchRecord match = matchOpt.get();
            fallbackHomeTeam = match.getHomeTeam() != null ? match.getHomeTeam() : "";
            fallbackAwayTeam = match.getAwayTeam() != null ? match.getAwayTeam() : "";
            if ("SCHEDULED".equalsIgnoreCase(match.getStatus())) {
                return new BoxScoreDto(gameId, fallbackHomeTeam, fallbackAwayTeam,
                        List.of(), List.of(), List.of(), null, null);
            }
            fallbackHomeScore = match.getHomeScore();
            fallbackAwayScore = match.getAwayScore();
        } else {
            Optional<GameNews> newsOpt = findGameNews(gameId, nbaGameId);
            if (newsOpt.isPresent()) {
                GameNews news = newsOpt.get();
                fallbackHomeTeam = news.getHomeTeam() != null ? news.getHomeTeam() : "";
                fallbackAwayTeam = news.getAwayTeam() != null ? news.getAwayTeam() : "";
                if ("SCHEDULED".equalsIgnoreCase(news.getStatus())) {
                    return new BoxScoreDto(gameId, fallbackHomeTeam, fallbackAwayTeam,
                            List.of(), List.of(), List.of(), null, null);
                }
                fallbackHomeScore = news.getHomeScore();
                fallbackAwayScore = news.getAwayScore();
            }
        }

        List<QuarterScoreDto> quarterScores = getQuarterScores(gameId);
        List<GameBoxScore> records = gameBoxScoreRepository.findByGameId(nbaGameId);

        if (records.isEmpty()) {
            // 即使没有boxscore球员记录，也尝试获取比分与主客队名及节末比分
            ScorePair scores = resolveScores(nbaGameId);
            Integer finalHome = scores.home() != null ? scores.home() : fallbackHomeScore;
            Integer finalAway = scores.away() != null ? scores.away() : fallbackAwayScore;
            return new BoxScoreDto(gameId, fallbackHomeTeam, fallbackAwayTeam, List.of(), List.of(), quarterScores, finalHome, finalAway);
        }

        // 使用 isHome 字段分组主客队
        List<GameBoxScore> homeRecords = records.stream()
                .filter(GameBoxScore::isHome)
                .collect(Collectors.toList());
        List<GameBoxScore> awayRecords = records.stream()
                .filter(r -> !r.isHome())
                .collect(Collectors.toList());

        // 构建 nbaPlayerId 映射表（从 Player 表查找）
        Map<Long, Long> nbaPlayerIdMap = buildNbaPlayerIdMap(records);

        String homeTeam = "";
        String awayTeam = "";
        List<BoxScorePlayerDto> homePlayers = List.of();
        List<BoxScorePlayerDto> awayPlayers = List.of();

        if (!homeRecords.isEmpty()) {
            homeTeam = homeRecords.get(0).getTeamName();
            homePlayers = homeRecords.stream()
                    .map(r -> toBoxScorePlayerDto(r, nbaPlayerIdMap))
                    .collect(Collectors.toList());
        }
        if (!awayRecords.isEmpty()) {
            awayTeam = awayRecords.get(0).getTeamName();
            awayPlayers = awayRecords.stream()
                    .map(r -> toBoxScorePlayerDto(r, nbaPlayerIdMap))
                    .collect(Collectors.toList());
        }

        if (homeTeam.isBlank()) {
            homeTeam = fallbackHomeTeam;
        }
        if (awayTeam.isBlank()) {
            awayTeam = fallbackAwayTeam;
        }

        // 从MatchRecord/GameNews获取总比分
        ScorePair scores = resolveScores(nbaGameId);
        Integer finalHome = scores.home() != null ? scores.home() : fallbackHomeScore;
        Integer finalAway = scores.away() != null ? scores.away() : fallbackAwayScore;

        return new BoxScoreDto(gameId, homeTeam, awayTeam, homePlayers, awayPlayers, quarterScores, finalHome, finalAway);
    }

    private Optional<MatchRecord> findMatchRecord(String gameId, String nbaGameId) {
        if (nbaGameId != null && !nbaGameId.isBlank()) {
            Optional<MatchRecord> match = matchRecordRepository.findByNbaGameId(nbaGameId);
            if (match.isPresent()) {
                return match;
            }
        }
        if (gameId != null && !gameId.isBlank() && !gameId.equals(nbaGameId)) {
            Optional<MatchRecord> match = matchRecordRepository.findByNbaGameId(gameId);
            if (match.isPresent()) {
                return match;
            }
        }
        if (gameId != null && !gameId.isBlank()) {
            try {
                Long id = Long.parseLong(gameId);
                return matchRecordRepository.findById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<GameNews> findGameNews(String gameId, String nbaGameId) {
        if (nbaGameId != null && !nbaGameId.isBlank()) {
            List<GameNews> newsList = gameNewsRepository.findByNbaGameId(nbaGameId);
            if (!newsList.isEmpty()) {
                return Optional.of(newsList.get(0));
            }
        }
        if (gameId != null && !gameId.isBlank() && !gameId.equals(nbaGameId)) {
            List<GameNews> newsList = gameNewsRepository.findByNbaGameId(gameId);
            if (!newsList.isEmpty()) {
                return Optional.of(newsList.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * 从MatchRecord或GameNews解析总比分
     */
    private ScorePair resolveScores(String nbaGameId) {
        // 优先从MatchRecord获取
        if (nbaGameId != null && !nbaGameId.isBlank()) {
            Optional<MatchRecord> match = matchRecordRepository.findByNbaGameId(nbaGameId);
            if (match.isPresent()) {
                if ("SCHEDULED".equalsIgnoreCase(match.get().getStatus())) {
                    return new ScorePair(null, null);
                }
                log.info("从MatchRecord获取比分: gameId={}, {}-{}", nbaGameId, match.get().getHomeScore(), match.get().getAwayScore());
                return new ScorePair(match.get().getHomeScore(), match.get().getAwayScore());
            }
        }

        // 其次从GameNews获取
        if (nbaGameId != null && !nbaGameId.isBlank()) {
            List<GameNews> newsList = gameNewsRepository.findByNbaGameId(nbaGameId);
            if (!newsList.isEmpty()) {
                GameNews news = newsList.get(0);
                if (!"SCHEDULED".equalsIgnoreCase(news.getStatus())) {
                    log.info("从GameNews获取比分: gameId={}, {}-{}", nbaGameId, news.getHomeScore(), news.getAwayScore());
                    return new ScorePair(news.getHomeScore(), news.getAwayScore());
                }
            }
        }

        // 无法从数据库获取比分
        return new ScorePair(null, null);
    }

    private record ScorePair(Integer home, Integer away) {}

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

        PythonScriptRunner.PythonResult pyResult = pythonScriptRunner.run(
                "translate_names.py", names.toArray(new String[0]),
                pythonTimeoutProperties.getTranslationSeconds());
        if (!pyResult.success()) {
            log.warn("翻译脚本执行失败, exitCode={}", pyResult.exitCode());
            return Map.of();
        }
        String jsonStr = pyResult.stdout().trim();
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
    @Transactional(readOnly = true)
    public List<PlayByPlayDto> getPlayByPlay(String gameId, Integer period) {
        String resolvedGameId = gameIdMappingService.getNbaGameId(gameId);
        List<PlayByPlay> records;
        if (period != null) {
            records = playByPlayRepository.findByGameIdAndPeriodOrderByEventOrderAsc(resolvedGameId, period);
        } else {
            records = playByPlayRepository.findByGameIdOrderByEventOrderAsc(resolvedGameId);
        }

        return records.stream().map(this::toPlayByPlayDto).collect(Collectors.toList());
    }

    @Cacheable(value = "quarterScores", key = "#gameId", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<QuarterScoreDto> getQuarterScores(String gameId) {
        // 转换gameId（ESPN格式 -> NBA格式）
        String nbaGameId = gameIdMappingService.getNbaGameId(gameId);
        log.info("获取逐节比分: 原始gameId={}, 转换后gameId={}", gameId, nbaGameId);

        if (findMatchRecord(gameId, nbaGameId)
                .map(match -> "SCHEDULED".equalsIgnoreCase(match.getStatus())).orElse(false)) {
            return List.of();
        }

        // Public GET endpoints must be pure reads. Replay preparation is the only path
        // allowed to call providers/Python and persist fresh facts.
        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByEventOrderAsc(nbaGameId);
        List<QuarterScoreDto> result = computeQuarterScoresFromPlayByPlay(allEvents);

        return isQuarterScoresAllZero(result) ? List.of() : result;
    }

    /**
     * 从Play-by-Play事件计算逐节比分
     * 取每节最后一个有有效累计比分的事件
     */
    private List<QuarterScoreDto> computeQuarterScoresFromPlayByPlay(List<PlayByPlay> allEvents) {
        Map<Integer, PlayByPlay> lastValidByPeriod = new LinkedHashMap<>();
        for (PlayByPlay event : allEvents) {
            // 只使用有有效累计比分的事件（至少一方得分>0）
            if (event.getHomeScore() > 0 || event.getAwayScore() > 0) {
                lastValidByPeriod.put(event.getPeriod(), event);
            }
        }

        return lastValidByPeriod.entrySet().stream()
                .map(e -> new QuarterScoreDto(e.getKey(), e.getValue().getHomeScore(), e.getValue().getAwayScore()))
                .collect(Collectors.toList());
    }

    /**
     * 验证逐节比分是否有效
     */
    private boolean isQuarterScoresValid(List<QuarterScoreDto> scores, String nbaGameId) {
        if (scores.isEmpty()) {
            return false;
        }

        // 所有节都是0:0 → 无效
        boolean allZero = scores.stream().allMatch(s -> s.homeScore() == 0 && s.awayScore() == 0);
        if (allZero) {
            return false;
        }

        // 已结束比赛：逐节总分不应明显低于MatchRecord中的总分
        Optional<MatchRecord> match = matchRecordRepository.findByNbaGameId(nbaGameId);
        if (match.isPresent() && "FINISHED".equals(match.get().getStatus())) {
            int totalHome = scores.stream().mapToInt(QuarterScoreDto::homeScore).sum();
            int totalAway = scores.stream().mapToInt(QuarterScoreDto::awayScore).sum();
            int recordHome = match.get().getHomeScore() != null ? match.get().getHomeScore() : 0;
            int recordAway = match.get().getAwayScore() != null ? match.get().getAwayScore() : 0;

            // 如果逐节总分明显低于记录比分（差距>5分），视为无效
            if (recordHome > 0 && (recordHome - totalHome > 5 || recordAway - totalAway > 5)) {
                log.warn("逐节比分总分({}-{})明显低于记录比分({}-{})", totalHome, totalAway, recordHome, recordAway);
                return false;
            }
        }

        return true;
    }

    private boolean isQuarterScoresAllZero(List<QuarterScoreDto> scores) {
        return !scores.isEmpty()
                && scores.stream().allMatch(s -> s.homeScore() == 0 && s.awayScore() == 0);
    }

    /**
     * 解析比赛日期（从MatchRecord获取）
     */
    private String resolveMatchDate(String nbaGameId) {
        if (nbaGameId == null || nbaGameId.isBlank()) {
            return null;
        }
        Optional<MatchRecord> match = matchRecordRepository.findByNbaGameId(nbaGameId);
        if (match.isPresent() && match.get().getMatchDate() != null) {
            return match.get().getMatchDate().toString();
        }
        return null;
    }

    /**
     * 从ScoreboardV3获取逐节比分（备选方案）
     * @param gameId NBA比赛ID
     * @param matchDate 比赛日期（可选，格式：YYYY-MM-DD），用于缩小搜索范围
     */
    private List<QuarterScoreDto> fetchQuarterScoresFromScoreboard(String gameId, String matchDate) {
        try {
            List<String> args = new ArrayList<>();
            args.add("quarters");
            args.add(gameId);
            if (matchDate != null && !matchDate.isBlank()) {
                args.add(matchDate);
            }

            PythonScriptRunner.PythonResult pyResult = pythonScriptRunner.run(
                    "nba_data_fetcher.py", args.toArray(new String[0]),
                    pythonTimeoutProperties.getDetailSeconds());
            if (!pyResult.success()) {
                log.warn("节比分脚本执行失败, exitCode={}", pyResult.exitCode());
                return List.of();
            }

            String jsonStr = pyResult.stdout().trim();
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
            log.info("调用Python脚本获取{}数据, gameId={}", dataType, gameId);

            PythonScriptRunner.PythonResult pyResult = pythonScriptRunner.run(
                    "nba_data_fetcher.py", new String[]{dataType, gameId},
                    pythonTimeoutProperties.getDetailSeconds());
            if (!pyResult.success()) {
                log.error("Python脚本执行失败, exitCode={}, error={}", pyResult.exitCode(), pyResult.stderr().substring(0, Math.min(200, pyResult.stderr().length())));
                return;
            }

            String jsonStr = pyResult.stdout().trim();
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出为空");
                return;
            }

            JSONObject data = new JSONObject(jsonStr);
            if ("boxscore".equals(dataType) || "espn_summer_boxscore".equals(dataType)) {
                // 支持两种格式：{boxscore: {players: [...]}} 或 {players: [...]}
                JSONArray playersArray = null;
                if (data.has("boxscore") && !data.isNull("boxscore")) {
                    JSONObject boxscore = data.getJSONObject("boxscore");
                    if (boxscore.has("players") && !boxscore.isNull("players")) {
                        playersArray = boxscore.getJSONArray("players");
                    }
                } else if (data.has("players") && !data.isNull("players")) {
                    playersArray = data.getJSONArray("players");
                }
                if (playersArray != null) {
                    saveBoxScore(playersArray, gameId);
                }
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

    private boolean isEspnSummerGameId(String gameId) {
        return gameId != null && gameId.startsWith("401");
    }

    private String resolveBoxScoreAction(String gameId) {
        return isEspnSummerGameId(gameId) ? "espn_summer_boxscore" : "boxscore";
    }

    /**
     * 从 Player 表批量查找 nbaPlayerId 映射
     */
    private Map<Long, Long> buildNbaPlayerIdMap(List<GameBoxScore> records) {
        Map<Long, Long> map = new HashMap<>();
        for (GameBoxScore bs : records) {
            Long playerId = bs.getPlayerId();
            if (playerId != null && !map.containsKey(playerId)) {
                com.nbamanager.domain.Player player = playerRepository.findByNbaPlayerId(playerId);
                if (player != null && player.getNbaPlayerId() != null) {
                    map.put(playerId, player.getNbaPlayerId());
                }
            }
        }
        return map;
    }

    private BoxScorePlayerDto toBoxScorePlayerDto(GameBoxScore bs, Map<Long, Long> nbaPlayerIdMap) {
        Long nbaPlayerId = nbaPlayerIdMap.get(bs.getPlayerId());
        return new BoxScorePlayerDto(
                bs.getPlayerId(),
                nbaPlayerId,
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
                pbp.getPlayerName(),
                pbp.getSource(),
                pbp.getActionType(),
                pbp.getTeamName(),
                pbp.getScoreMargin(),
                pbp.getIsScoringPlay(),
                pbp.getIsHighlightCandidate(),
                pbp.getEventOrder()
        );
    }
}

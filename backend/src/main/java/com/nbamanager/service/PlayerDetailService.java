package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
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
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.PlayerCareerStatsDto;
import com.nbamanager.web.dto.PlayerDetailDto;
import com.nbamanager.web.dto.PlayerGameLogDto;
import com.nbamanager.web.support.PageRequestSanitizer;
import com.nbamanager.web.dto.LeagueAveragesDto;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerDetailService {

    private static final HttpClient ESPN_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    private static final DateTimeFormatter ESPN_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter NBA_LOG_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    /**
     * 比赛日志分页参数：无用户可视排序字段（仓储方法已固定 matchDate 倒序），
     * 因此白名单为空——只校验 page/size 边界，sort 一律拒绝。
     */
    private static final java.util.Set<String> GAMELOG_ALLOWED_SORTS = java.util.Set.of();

    private static final Map<String, String> ESPN_TEAM_NAMES = Map.ofEntries(
            Map.entry("ATL", "老鹰"), Map.entry("BOS", "凯尔特人"), Map.entry("BKN", "篮网"),
            Map.entry("CHA", "黄蜂"), Map.entry("CHI", "公牛"), Map.entry("CLE", "骑士"),
            Map.entry("DAL", "独行侠"), Map.entry("DEN", "掘金"), Map.entry("DET", "活塞"),
            Map.entry("GS", "勇士"), Map.entry("HOU", "火箭"), Map.entry("IND", "步行者"),
            Map.entry("LAC", "快船"), Map.entry("LAL", "湖人"), Map.entry("MEM", "灰熊"),
            Map.entry("MIA", "热火"), Map.entry("MIL", "雄鹿"), Map.entry("MIN", "森林狼"),
            Map.entry("NO", "鹈鹕"), Map.entry("NY", "尼克斯"), Map.entry("OKC", "雷霆"),
            Map.entry("ORL", "魔术"), Map.entry("PHI", "76人"), Map.entry("PHX", "太阳"),
            Map.entry("POR", "开拓者"), Map.entry("SA", "马刺"), Map.entry("SAC", "国王"),
            Map.entry("TOR", "猛龙"), Map.entry("UTAH", "爵士"), Map.entry("WSH", "奇才"));

    private final PlayerRepository playerRepository;
    private final PlayerGameLogRepository playerGameLogRepository;
    private final PlayerCareerStatsRepository playerCareerStatsRepository;
    private final TeamRepository teamRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;
    private final PythonScriptRunner pythonScriptRunner;
    private final PythonTimeoutProperties pythonTimeoutProperties;

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
                p.getDefensiveRating(), p.getDataMeta());
    }

    @Cacheable(value = "leagueAverages", key = "#season")
    @Transactional(readOnly = true)
    public LeagueAveragesDto getLeagueAverages(String season) {
        var agg = playerSeasonStatsRepository.findLeagueAggregate(season);
        if (agg == null || agg.getPlayerCount() == null || agg.getPlayerCount() == 0) {
            return LeagueAveragesDto.empty();
        }
        return new LeagueAveragesDto(
                agg.getAvgPoints(), agg.getAvgRebounds(), agg.getAvgAssists(),
                agg.getAvgSteals(), agg.getAvgBlocks(), agg.getAvgFieldGoalPct(),
                agg.getAvgThreePointPct(),
                agg.getMaxPoints(), agg.getMaxRebounds(), agg.getMaxAssists(),
                agg.getMaxSteals(), agg.getMaxBlocks(), agg.getMaxFieldGoalPct(),
                agg.getMaxThreePointPct(),
                agg.getPlayerCount(), new com.nbamanager.web.dto.DataMeta("LOCAL_PLAYER_SAMPLE", null, season,
                        "REGULAR_SEASON_MIN_10_GAMES", null, null, "PARTIAL", Math.toIntExact(agg.getPlayerCount()), Map.ofEntries(
                            Map.entry("avgPoints", aggregateState(agg.getAvgPoints())),
                            Map.entry("avgRebounds", aggregateState(agg.getAvgRebounds())),
                            Map.entry("avgAssists", aggregateState(agg.getAvgAssists())),
                            Map.entry("avgSteals", aggregateState(agg.getAvgSteals())),
                            Map.entry("avgBlocks", aggregateState(agg.getAvgBlocks())),
                            Map.entry("avgFieldGoalPct", aggregateState(agg.getAvgFieldGoalPct())),
                            Map.entry("avgThreePointPct", aggregateState(agg.getAvgThreePointPct())),
                            Map.entry("maxPoints", aggregateState(agg.getMaxPoints())),
                            Map.entry("maxRebounds", aggregateState(agg.getMaxRebounds())),
                            Map.entry("maxAssists", aggregateState(agg.getMaxAssists())),
                            Map.entry("maxSteals", aggregateState(agg.getMaxSteals())),
                            Map.entry("maxBlocks", aggregateState(agg.getMaxBlocks())),
                            Map.entry("maxFieldGoalPct", aggregateState(agg.getMaxFieldGoalPct())),
                            Map.entry("maxThreePointPct", aggregateState(agg.getMaxThreePointPct())))));
    }

    private static String aggregateState(Object value) {
        return value == null ? "MISSING" : "DERIVED";
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

        if (records.isEmpty() || records.stream().anyMatch(row -> !row.hasRetainedLegacyValues()
                && "UNVERIFIED".equals(row.getDataMeta().quality()))) {
            // 使用 nbaPlayerId 调用 Python 脚本
            Long nbaPlayerId = (lookupPlayer != null && lookupPlayer.getNbaPlayerId() != null) ? lookupPlayer.getNbaPlayerId() : playerId;
            fetchFromPython("player_career", String.valueOf(nbaPlayerId), actualId);
            records = playerCareerStatsRepository.findByPlayerIdOrderBySeasonAsc(actualId);
        }

        return records.stream().map(this::toCareerStatsDto).collect(Collectors.toList());
    }

    @Transactional
    public Page<PlayerGameLogDto> getGameLog(Long playerId, String season, Pageable pageable) {
        // page/size 边界与非法 sort 统一在此拦截（此前非法 sort 会在 JPA 层变成 500）
        Pageable safePageable = PageRequestSanitizer.sanitize(
                pageable, GAMELOG_ALLOWED_SORTS, java.util.Map.of(), null);
        // 获取球员信息（兼容nbaPlayerId查找）
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) {
            player = playerRepository.findByNbaPlayerId(playerId);
        }
        if (player == null || player.getTeam() == null) {
            return Page.empty(safePageable);
        }

        Long actualId = player.getId();
        Page<PlayerGameLog> page;
        if (season != null && !season.isBlank()) {
            page = playerGameLogRepository.findByPlayerIdAndSeasonOrderByMatchDateDesc(actualId, season, safePageable);
        } else {
            page = playerGameLogRepository.findByPlayerIdOrderByMatchDateDesc(actualId, safePageable);
        }

        // 如果数据库没有数据，从Python脚本获取
        if (page.isEmpty()) {
            Long nbaPlayerId = player.getNbaPlayerId() != null ? player.getNbaPlayerId() : playerId;
            fetchFromPython("player_gamelog", String.valueOf(nbaPlayerId), actualId, season);
            if (season != null && !season.isBlank()) {
                page = playerGameLogRepository.findByPlayerIdAndSeasonOrderByMatchDateDesc(actualId, season, safePageable);
            } else {
                page = playerGameLogRepository.findByPlayerIdOrderByMatchDateDesc(actualId, safePageable);
            }
        }

        // 保留真实零值；只使用个人记录，不以球队比赛生成个人统计。
        String playerTeamName = player.getTeam().getName();
        List<String> gameIds = page.getContent().stream()
                .map(PlayerGameLog::getGameId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, MatchRecord> matchesByGameId = gameIds.isEmpty()
                ? Map.of()
                : matchRecordRepository.findByNbaGameIdIn(gameIds).stream()
                        .collect(Collectors.toMap(MatchRecord::getNbaGameId, m -> m, (first, ignored) -> first));
        Map<String, List<EspnGameScore>> espnGamesByDate = loadEspnScoresByDate(page.getContent(), matchesByGameId);
        List<PlayerGameLogDto> filteredContent = page.getContent().stream()
                .map(g -> toGameLogDto(g, playerTeamName, matchesByGameId.get(g.getGameId()), espnGamesByDate))
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                filteredContent, safePageable, page.getTotalElements());
    }

    /**
     * 调用Python脚本获取球员详细数据
     * @param dataType 数据类型
     * @param nbaPlayerId NBA球员ID（用于调用Python脚本）
     * @param dbPlayerId 数据库中的球员ID（用于保存数据）
     */
    private void fetchFromPython(String dataType, String nbaPlayerId, Long dbPlayerId, String... season) {
        try {
            log.info("调用Python脚本获取{}数据, nbaPlayerId={}, dbPlayerId={}", dataType, nbaPlayerId, dbPlayerId);
            PythonScriptRunner.PythonResult result = pythonScriptRunner.run(
                    "nba_data_fetcher.py", season.length > 0 && season[0] != null && !season[0].isBlank() ? new String[]{dataType, nbaPlayerId, season[0]} : new String[]{dataType, nbaPlayerId},
                    pythonTimeoutProperties.getDetailSeconds());
            if (!result.success()) {
                log.error("Python脚本执行失败, exitCode={}, timedOut={}, error={}",
                        result.exitCode(), result.timedOut(), result.stderr());
                return;
            }

            String jsonStr = result.stdout().trim();
            if (jsonStr.isEmpty()) {
                log.error("Python脚本输出为空");
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
            PlayerCareerStats stats = playerCareerStatsRepository.findFirstByPlayerIdAndSeasonAndTeamName(playerId, obj.getString("season"), obj.getString("teamName")).orElse(new PlayerCareerStats());
            stats.setPlayerId(playerId);
            stats.setSeason(obj.getString("season"));
            stats.setTeamName(obj.getString("teamName"));
            stats.setGamesPlayed(nullableInteger(obj, "gamesPlayed"));
            stats.setMinutesPerGame(PlayerSnapshotMapper.number(obj, "minutesPerGame"));
            stats.setPointsPerGame(PlayerSnapshotMapper.number(obj, "pointsPerGame"));
            stats.setReboundsPerGame(PlayerSnapshotMapper.number(obj, "reboundsPerGame"));
            stats.setAssistsPerGame(PlayerSnapshotMapper.number(obj, "assistsPerGame"));
            stats.setStealsPerGame(PlayerSnapshotMapper.number(obj, "stealsPerGame"));
            stats.setBlocksPerGame(PlayerSnapshotMapper.number(obj, "blocksPerGame"));
            stats.setFgPct(PlayerSnapshotMapper.number(obj, "fgPct"));
            stats.setThreePct(PlayerSnapshotMapper.number(obj, "threePct"));
            stats.setFtPct(PlayerSnapshotMapper.number(obj, "ftPct"));
            stats.setEfficiency(PlayerSnapshotMapper.number(obj, "efficiency"));
            stats.markData("NBA_API", stats.getSeason(), "REGULAR_SEASON", "gamesPlayed", "minutesPerGame", "pointsPerGame", "reboundsPerGame", "assistsPerGame", "stealsPerGame", "blocksPerGame", "fgPct", "threePct", "ftPct", "efficiency");
            playerCareerStatsRepository.save(stats);
        }
        log.info("保存career数据: {}条记录, playerId={}", careerArray.length(), playerId);
    }

    private void saveGameLog(JSONArray gameLogArray, Long playerId) {
        for (int i = 0; i < gameLogArray.length(); i++) {
            JSONObject obj = gameLogArray.getJSONObject(i);
            PlayerGameLog gameLog = playerGameLogRepository.findFirstByPlayerIdAndGameId(playerId, obj.getString("gameId")).orElse(new PlayerGameLog());
            gameLog.setPlayerId(playerId);
            gameLog.setGameId(obj.getString("gameId"));
            gameLog.setMatchDate(obj.getString("matchDate"));
            gameLog.setOpponent(obj.getString("opponent"));
            gameLog.setHome(obj.optBoolean("isHome", false));
            gameLog.setMinutes(obj.optString("minutes", ""));
            gameLog.setPoints(nullableInteger(obj, "points"));
            gameLog.setRebounds(nullableInteger(obj, "rebounds"));
            gameLog.setAssists(nullableInteger(obj, "assists"));
            gameLog.setSteals(nullableInteger(obj, "steals"));
            gameLog.setBlocks(nullableInteger(obj, "blocks"));
            gameLog.setTurnovers(nullableInteger(obj, "turnovers"));
            gameLog.setFgPct(PlayerSnapshotMapper.number(obj, "fgPct"));
            gameLog.setThreePct(PlayerSnapshotMapper.number(obj, "threePct"));
            gameLog.setFtPct(PlayerSnapshotMapper.number(obj, "ftPct"));
            gameLog.setPlusMinus(nullableInteger(obj, "plusMinus"));
            gameLog.setResult(obj.optString("result", ""));
            gameLog.setSeason(obj.optString("season", ""));
            gameLog.markData("NBA_API", gameLog.getSeason(), "REGULAR_SEASON", "points", "rebounds", "assists", "steals", "blocks", "turnovers", "fgPct", "threePct", "ftPct", "plusMinus");
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
                s.getEfficiency(), s.getDataMeta());
    }

    private PlayerGameLogDto toGameLogDto(
            PlayerGameLog g,
            String teamName,
            MatchRecord match,
            Map<String, List<EspnGameScore>> espnGamesByDate) {
        Integer teamScore = null;
        Integer opponentScore = null;
        if (match != null && match.getDataMeta().publishes("homeScore") && match.getDataMeta().publishes("awayScore")) {
            boolean isHome = teamName.equals(match.getHomeTeam());
            teamScore = isHome ? match.getHomeScore() : match.getAwayScore();
            opponentScore = isHome ? match.getAwayScore() : match.getHomeScore();
        } else {
            Optional<GameScore> espnScore = resolveEspnScore(g, teamName, espnGamesByDate);
            if (espnScore.isPresent()) {
                teamScore = espnScore.get().teamScore();
                opponentScore = espnScore.get().opponentScore();
            }
        }

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
                teamScore,
                opponentScore, g.getDataMeta());
    }

    private Map<String, List<EspnGameScore>> loadEspnScoresByDate(
            List<PlayerGameLog> logs,
            Map<String, MatchRecord> matchesByGameId) {
        Set<String> missingScoreDates = logs.stream()
                .filter(g -> !matchesByGameId.containsKey(g.getGameId()))
                .map(PlayerGameLog::getMatchDate)
                .map(PlayerDetailService::parseGameLogDate)
                .flatMap(Optional::stream)
                .map(ESPN_DATE::format)
                .collect(Collectors.toSet());
        Map<String, List<EspnGameScore>> scoresByDate = new HashMap<>();
        for (String date : missingScoreDates) {
            fetchEspnGamesByDate(date).ifPresent(games -> scoresByDate.put(date, games));
        }
        return scoresByDate;
    }

    private Optional<List<EspnGameScore>> fetchEspnGamesByDate(String date) {
        String url = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates="
                + date + "&limit=100";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = ESPN_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("ESPN scoreboard returned status {} for {}", response.statusCode(), date);
                return Optional.empty();
            }
            return Optional.of(parseEspnScoreboardGames(response.body()));
        } catch (Exception e) {
            log.warn("ESPN scoreboard fetch failed for {}: {}", date, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<GameScore> resolveEspnScore(
            PlayerGameLog log,
            String teamName,
            Map<String, List<EspnGameScore>> espnGamesByDate) {
        Optional<LocalDate> matchDate = parseGameLogDate(log.getMatchDate());
        if (matchDate.isEmpty()) {
            return Optional.empty();
        }
        List<EspnGameScore> games = espnGamesByDate.getOrDefault(ESPN_DATE.format(matchDate.get()), List.of());
        return games.stream()
                .filter(g -> Objects.equals(g.status(), "FINISHED"))
                .filter(g -> isSameMatch(log, teamName, g))
                .findFirst()
                .map(g -> {
                    boolean teamIsHome = teamName.equals(g.homeTeam());
                    return new GameScore(
                            teamIsHome ? g.homeScore() : g.awayScore(),
                            teamIsHome ? g.awayScore() : g.homeScore());
                });
    }

    private static boolean isSameMatch(PlayerGameLog log, String teamName, EspnGameScore game) {
        boolean sameTeams = (teamName.equals(game.homeTeam()) && log.getOpponent().equals(game.awayTeam()))
                || (teamName.equals(game.awayTeam()) && log.getOpponent().equals(game.homeTeam()));
        if (!sameTeams) {
            return false;
        }
        boolean teamIsHome = teamName.equals(game.homeTeam());
        return log.isHome() == teamIsHome;
    }

    static List<EspnGameScore> parseEspnScoreboardGames(String json) {
        JSONObject root = new JSONObject(json);
        JSONArray events = root.optJSONArray("events");
        if (events == null) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, events.length())
                .mapToObj(events::getJSONObject)
                .map(PlayerDetailService::toEspnGameScore)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private static Optional<EspnGameScore> toEspnGameScore(JSONObject event) {
        JSONArray competitions = event.optJSONArray("competitions");
        if (competitions == null || competitions.isEmpty()) {
            return Optional.empty();
        }
        JSONArray competitors = competitions.getJSONObject(0).optJSONArray("competitors");
        if (competitors == null || competitors.length() < 2) {
            return Optional.empty();
        }
        JSONObject home = null;
        JSONObject away = null;
        for (int i = 0; i < competitors.length(); i++) {
            JSONObject competitor = competitors.getJSONObject(i);
            if ("home".equals(competitor.optString("homeAway"))) {
                home = competitor;
            } else if ("away".equals(competitor.optString("homeAway"))) {
                away = competitor;
            }
        }
        if (home == null || away == null) {
            return Optional.empty();
        }
        String status = competitions.getJSONObject(0)
                .optJSONObject("status")
                .optJSONObject("type")
                .optString("name", "");
        return Optional.of(new EspnGameScore(
                teamNameFromEspn(home),
                teamNameFromEspn(away),
                home.optInt("score", 0),
                away.optInt("score", 0),
                "STATUS_FINAL".equals(status) ? "FINISHED" : status));
    }

    private static String teamNameFromEspn(JSONObject competitor) {
        JSONObject team = competitor.optJSONObject("team");
        if (team == null) {
            return "";
        }
        String abbreviation = team.optString("abbreviation", "");
        return ESPN_TEAM_NAMES.getOrDefault(abbreviation, team.optString("shortDisplayName", ""));
    }

    private static Optional<LocalDate> parseGameLogDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rawDate));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(LocalDate.parse(rawDate, NBA_LOG_DATE));
            } catch (DateTimeParseException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    record GameScore(Integer teamScore, Integer opponentScore) {}

    record EspnGameScore(String homeTeam, String awayTeam, Integer homeScore, Integer awayScore, String status) {}

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球员不存在: " + id);
    }
    private static Integer nullableInteger(JSONObject data, String key) {
        Double value = PlayerSnapshotMapper.number(data, key);
        return value == null ? null : value.intValue();
    }
}

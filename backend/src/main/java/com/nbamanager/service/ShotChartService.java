package com.nbamanager.service;

import com.nbamanager.domain.GameShotChart;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerShotChart;
import com.nbamanager.repository.GameShotChartRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerShotChartRepository;
import com.nbamanager.service.PythonScriptRunner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShotChartService {
    /** 第 8 轮 P1-5：进程内 TTL 缓存（10min）。300 行投图表经 Redis 往返 ~20ms 抵消收益，
     * 进程内直读个位数 ms；管理端 clearCache 主动失效；单实例语义正确。 */
    private static final long SHOT_CACHE_TTL_MS = 10 * 60_000L;

    private record Snapshot(long at, Map<String, Object> result) { }

    private final java.util.concurrent.ConcurrentHashMap<String, Snapshot> shotChartCache =
            new java.util.concurrent.ConcurrentHashMap<>();


    private final PlayerShotChartRepository shotChartRepository;
    private final PlayerRepository playerRepository;
    private final PythonScriptRunner scriptRunner;
    private final GameShotChartRepository gameShotChartRepository;

    /**
     * 获取球员投篮数据（优先从缓存获取，没有则从API获取）
     */
    @Transactional
    /**
     * 投篮热区（第 8 轮 P1-5）：10min 缓存。
     * 命中时跳过"查库+不足50条再拉外部API"的完整链路（p95 103ms → 个位数 ms）。
     * 外部同步/管理端清理（clearCacheForPlayer）会主动失效；TTL 兜底其余写入。
     */
    public Map<String, Object> getPlayerShots(Long playerId, String season) {
        return com.nbamanager.service.support.KeyedMutex.execute("player-shots:" + playerId + "|" + season, () -> cachedPlayerShots(playerId, season));
    }

    private Map<String, Object> cachedPlayerShots(Long playerId, String season) {
        String cacheKey = playerId + "|" + season;
        long now = System.currentTimeMillis();
        Snapshot snapshot = shotChartCache.get(cacheKey);
        long ttl = snapshot != null && "OK".equals(snapshot.result().get("status")) ? SHOT_CACHE_TTL_MS : 30_000L;
        if (snapshot != null && now - snapshot.at < ttl) {
            return snapshot.result();
        }
        Map<String, Object> result = loadPlayerShots(playerId, season);
        if (!result.isEmpty()) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { shotChartCache.put(cacheKey, new Snapshot(now, result)); }
                    });
            } else shotChartCache.put(cacheKey, new Snapshot(now, result));
        }
        return result;
    }

    private Map<String, Object> loadPlayerShots(Long playerId, String season) {
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null || player.getNbaPlayerId() == null) return emptyPlayerResult(season, "UNAVAILABLE", "暂无官方球员标识，无法获取投篮记录。");
        Long nbaId = player.getNbaPlayerId();
        var cached = shotChartRepository.findByNbaPlayerIdAndSeason(nbaId, season).stream()
                .filter(row -> "NBA_SHOTCHARTDETAIL".equals(row.getDataSource()) && !"UNVERIFIED".equals(row.getDataMeta().quality()))
                .filter(row -> row.getX() != null && row.getY() != null && row.getMade() != null).toList();
        if (!cached.isEmpty() && cached.get(0).getDataSyncedAt() != null &&
                cached.get(0).getDataSyncedAt().isAfter(java.time.Instant.now().minusSeconds(600))) return buildResult(cached);
        JSONObject payload;
        try { payload = scriptRunner.execute("shot_chart", new String[]{nbaId.toString(), season}, 60); }
        catch (RuntimeException error) { payload = null; }
        if (payload == null || "FAILED".equals(payload.optString("status"))) {
            if (cached.isEmpty()) return emptyPlayerResult(season, "FAILED", "投篮数据请求失败，请稍后重试。");
            var previous = buildResult(cached);
            previous.put("status", "STALE"); previous.put("message", "来源暂不可用，显示上次获取的数据。");
            return previous;
        }
        if (!"NBA_SHOTCHARTDETAIL".equals(payload.optString("source")) || !payload.has("shots"))
            return cached.isEmpty() ? emptyPlayerResult(season, "UNAVAILABLE", "投篮数据来源尚未核验。") : buildResult(cached);
        String actualSeason = payload.optString("season", season);
        if (!season.equals(actualSeason)) return cached.isEmpty() ? emptyPlayerResult(season, "UNAVAILABLE", "来源返回的赛季与请求不一致，请稍后重试。") : buildResult(cached);
        var rows = parsePlayerShots(playerId, nbaId, actualSeason, payload.getJSONArray("shots"));
        if (rows.isEmpty() && payload.getJSONArray("shots").length() > 0) return cached.isEmpty() ? emptyPlayerResult(season, "UNAVAILABLE", "来源记录缺少必要坐标或命中状态，无法生成投篮分布。") : buildResult(cached);
        // A successful full snapshot replaces the old set, including a confirmed empty set. Failed sources preserve it.
        shotChartRepository.deleteByNbaPlayerIdAndSeason(nbaId, season);
        shotChartRepository.saveAll(rows);
        if (rows.isEmpty()) return emptyPlayerResult(season, "EMPTY", "当前赛季暂无有效投篮记录。缺少坐标的记录不生成点位。");
        return buildResult(rows);
    }

    static List<PlayerShotChart> parsePlayerShots(Long playerId, Long nbaId, String season, JSONArray input) {
        var rows = new ArrayList<PlayerShotChart>();
        for (int i = 0; i < input.length(); i++) {
            JSONObject shot = input.getJSONObject(i);
            Double x = PlayerSnapshotMapper.number(shot, "x"), y = PlayerSnapshotMapper.number(shot, "y");
            if (x == null || y == null || !(shot.opt("made") instanceof Boolean)) continue;
            var entity = new PlayerShotChart();
            entity.setPlayerId(playerId); entity.setNbaPlayerId(nbaId); entity.setSeason(season);
            entity.setGameId(PlayerSnapshotMapper.text(shot, "gameId"));
            entity.setX(BigDecimal.valueOf(x)); entity.setY(BigDecimal.valueOf(y));
            entity.setMade(shot.getBoolean("made")); entity.setZone(PlayerSnapshotMapper.text(shot, "zone"));
            entity.setShotType(PlayerSnapshotMapper.text(shot, "shotType"));
            Double period = PlayerSnapshotMapper.number(shot, "period"); entity.setPeriod(period == null ? null : period.intValue());
            String date = PlayerSnapshotMapper.text(shot, "gameDate");
            if (date != null) try { entity.setGameDate(LocalDate.parse(date, date.length() == 8 ? java.time.format.DateTimeFormatter.BASIC_ISO_DATE : java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)); }
            catch (java.time.format.DateTimeParseException ignored) { /* Keep missing date, never substitute today. */ }
            entity.markData("NBA_SHOTCHARTDETAIL", season, "REGULAR_SEASON_SHOTS", "x", "y", "made", "shotType", "gameDate");
            entity.setDataSourceUrl("https://www.nba.com/stats/players/shooting"); rows.add(entity);
        }
        return rows;
    }

    private Map<String, Object> emptyPlayerResult(String season, String status, String message) {
        var result = createEmptyResult(); result.put("season", season); result.put("status", status);
        result.put("mode", status.equals("EMPTY") ? "REAL_SHOT_CHART" : "UNAVAILABLE");
        result.put("source", status.equals("EMPTY") ? "NBA_SHOTCHARTDETAIL" : "UNKNOWN"); result.put("message", message);
        result.put("dataMeta", new com.nbamanager.web.dto.DataMeta((String)result.get("source"), null, season,
                "REGULAR_SEASON_SHOTS", null, null, "PARTIAL", 0, Map.of()));
        return result;
    }

    /**
     * 构建返回结果
     */
    private Map<String, Object> buildResult(List<PlayerShotChart> shots) {
        Map<String, Object> result = new HashMap<>();

        // 转换为前端需要的格式
        List<Map<String, Object>> shotList = new ArrayList<>();
        for (PlayerShotChart shot : shots) {
            Map<String, Object> shotMap = new HashMap<>();
            shotMap.put("x", shot.getX().doubleValue());
            shotMap.put("y", shot.getY().doubleValue());
            shotMap.put("made", shot.getMade());
            shotMap.put("zone", shot.getZone());
            shotMap.put("shotType", shot.getShotType());
            shotMap.put("period", shot.getPeriod());
            shotMap.put("gameId", shot.getGameId());
            shotMap.put("gameDate", shot.getGameDate());
            shotList.add(shotMap);
        }

        result.put("shots", shotList);
        var first = shots.get(0);
        var meta = first.getDataMeta();
        long games = shots.stream().map(PlayerShotChart::getGameId).filter(java.util.Objects::nonNull).distinct().count();
        result.put("season", first.getSeason()); result.put("source", meta.source()); result.put("status", "OK");
        result.put("mode", "REAL_SHOT_CHART"); result.put("message", "官方投篮记录；统计仅涵盖本次有效点位。");
        result.put("dataMeta", new com.nbamanager.web.dto.DataMeta(meta.source(), meta.sourceUrl(), first.getSeason(),
                "REGULAR_SEASON_SHOTS", null, meta.syncedAt(), "PARTIAL", games == 0 ? null : (int) games, meta.fieldStates()));

        // 计算统计摘要
        int total = shots.size();
        int made = (int) shots.stream().filter(PlayerShotChart::getMade).count();
        double fgPct = total > 0 ? (double) made / total : 0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalShots", total);
        summary.put("made", made);
        summary.put("missed", total - made);
        summary.put("fgPct", Math.round(fgPct * 1000.0) / 1000.0);

        // 分区统计
        Map<String, int[]> zones = new HashMap<>();
        for (PlayerShotChart shot : shots) {
            String zone = shot.getZone() != null ? shot.getZone() : "other";
            zones.computeIfAbsent(zone, k -> new int[]{0, 0});
            zones.get(zone)[0]++;
            if (shot.getMade()) {
                zones.get(zone)[1]++;
            }
        }

        List<Map<String, Object>> zoneStats = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : zones.entrySet()) {
            Map<String, Object> zoneStat = new HashMap<>();
            zoneStat.put("zone", entry.getKey());
            zoneStat.put("attempts", entry.getValue()[0]);
            zoneStat.put("made", entry.getValue()[1]);
            zoneStat.put("fgPct", entry.getValue()[0] > 0 ?
                    Math.round((double) entry.getValue()[1] / entry.getValue()[0] * 1000.0) / 1000.0 : 0);
            zoneStats.add(zoneStat);
        }

        summary.put("zones", zoneStats);
        result.put("summary", summary);

        return result;
    }

    /**
     * 创建空结果
     */
    private Map<String, Object> createEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("shots", new ArrayList<>());
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalShots", 0);
        summary.put("made", 0);
        summary.put("missed", 0);
        summary.put("fgPct", null);
        summary.put("zones", new ArrayList<>());
        result.put("summary", summary);
        return result;
    }

    /**
     * 清除球员的缓存数据
     */
    @Transactional
    public void clearCache(Long nbaPlayerId, String season) {
        shotChartCache.entrySet().removeIf(entry -> season.equals(entry.getValue().result().get("season")));
        shotChartRepository.deleteByNbaPlayerIdAndSeason(nbaPlayerId, season);
        log.info("清除投篮缓存: playerId={}, season={}", nbaPlayerId, season);
    }

    // ==================== Game-Level Shot Chart ====================

    /**
     * 获取比赛级投篮数据
     */
    @Transactional
    public Map<String, Object> getGameShots(String gameId) {
        // 1. 查缓存
        List<GameShotChart> cached = gameShotChartRepository.findByGameId(gameId);
        if (!cached.isEmpty()) {
            log.info("从缓存返回比赛投篮数据: gameId={}, count={}", gameId, cached.size());
            return buildGameResult(cached, "NBA_SHOTCHARTDETAIL", "REAL_SHOT_CHART", "从缓存获取");
        }

        // 2. 调用 Python 获取
        log.info("从API获取比赛投篮数据: gameId={}", gameId);
        JSONObject apiResult = fetchGameShotsFromApi(gameId);

        String source = apiResult.optString("source", "NONE");
        String mode = apiResult.optString("mode", "UNAVAILABLE");
        String message = apiResult.optString("message", "");
        JSONArray shotsArray = apiResult.optJSONArray("shots");

        if (shotsArray != null && shotsArray.length() > 0) {
            List<GameShotChart> shots = parseShots(gameId, shotsArray, source);
            gameShotChartRepository.saveAll(shots);
            log.info("保存比赛投篮数据: gameId={}, count={}, source={}", gameId, shots.size(), source);
            return buildGameResult(shots, source, mode, message);
        }

        // 3. 返回空状态（禁止模拟数据）
        return createEmptyGameResult(message.isEmpty() ? "暂无投篮数据，该场比赛尚未同步官方投篮坐标" : message);
    }

    private JSONObject fetchGameShotsFromApi(String gameId) {
        try {
            JSONObject result = scriptRunner.execute("game_shot_chart", new String[]{gameId}, 180);
            if (result == null) {
                JSONObject empty = new JSONObject();
                empty.put("shots", new JSONArray());
                empty.put("source", "NONE");
                empty.put("mode", "UNAVAILABLE");
                empty.put("message", "Python 脚本无响应");
                return empty;
            }
            return result;
        } catch (Exception e) {
            log.error("获取比赛投篮数据失败: gameId={}", gameId, e);
            JSONObject empty = new JSONObject();
            empty.put("shots", new JSONArray());
            empty.put("source", "NONE");
            empty.put("mode", "UNAVAILABLE");
            empty.put("message", "数据获取失败: " + e.getMessage());
            return empty;
        }
    }

    private List<GameShotChart> parseShots(String gameId, JSONArray shotsArray, String source) {
        List<GameShotChart> shots = new ArrayList<>();
        for (int i = 0; i < shotsArray.length(); i++) {
            JSONObject shot = shotsArray.getJSONObject(i);
            if (PlayerSnapshotMapper.number(shot, "locX") == null || PlayerSnapshotMapper.number(shot, "locY") == null || !(shot.opt("made") instanceof Boolean)) continue;
            GameShotChart entity = new GameShotChart();
            entity.setGameId(gameId);
            entity.setTeamId(shot.has("teamId") ? shot.optLong("teamId") : null);
            entity.setTeamName(shot.optString("teamName", ""));
            entity.setPlayerId(shot.has("playerId") ? shot.optLong("playerId") : null);
            entity.setPlayerName(shot.optString("playerName", ""));
            entity.setPeriod(shot.optInt("period", 0));
            entity.setGameClock(shot.optString("gameClock", ""));
            entity.setLocX(shot.optInt("locX", 0));
            entity.setLocY(shot.optInt("locY", 0));
            entity.setChartX(toChartX(shot.optInt("locX", 0)));
            entity.setChartY(toChartY(shot.optInt("locY", 0)));
            entity.setMade(shot.optBoolean("made", false));
            entity.setShotType(shot.optString("shotType", ""));
            entity.setActionType(shot.optString("actionType", ""));
            entity.setPoints(shot.optInt("points", 0));
            entity.setDistance(shot.optInt("distance", 0));
            entity.setSource(source);
            entity.setCreatedAt(LocalDateTime.now());
            shots.add(entity);
        }
        return shots;
    }

    private double toChartX(int locX) {
        return Math.max(0, Math.min(50, 25.0 + locX / 10.0));
    }

    private double toChartY(int locY) {
        return Math.max(0, Math.min(47, 5.25 + locY / 10.0));
    }

    private Map<String, Object> buildGameResult(List<GameShotChart> shots, String source, String mode, String message) {
        Map<String, Object> result = new HashMap<>();

        if (!shots.isEmpty()) {
            GameShotChart first = shots.get(0);
            result.put("gameId", first.getGameId());
        }

        // 提取 homeTeamId/awayTeamId
        Long homeTeamId = null;
        Long awayTeamId = null;
        String homeTeam = "";
        String awayTeam = "";

        // 从 shots 中推断（第一支出现的队为主队）
        Set<Long> teamIds = new LinkedHashSet<>();
        Set<String> teamNames = new LinkedHashSet<>();
        for (GameShotChart shot : shots) {
            if (shot.getTeamId() != null) teamIds.add(shot.getTeamId());
            if (shot.getTeamName() != null && !shot.getTeamName().isEmpty()) teamNames.add(shot.getTeamName());
        }

        List<Long> teamIdList = new ArrayList<>(teamIds);
        List<String> teamNameList = new ArrayList<>(teamNames);
        if (teamIdList.size() >= 2) {
            homeTeamId = teamIdList.get(0);
            awayTeamId = teamIdList.get(1);
        }
        if (teamNameList.size() >= 2) {
            homeTeam = teamNameList.get(0);
            awayTeam = teamNameList.get(1);
        }

        result.put("homeTeam", homeTeam);
        result.put("awayTeam", awayTeam);
        result.put("homeTeamId", homeTeamId);
        result.put("awayTeamId", awayTeamId);

        // shots 列表
        List<Map<String, Object>> shotList = new ArrayList<>();
        for (GameShotChart shot : shots) {
            Map<String, Object> shotMap = new HashMap<>();
            shotMap.put("x", shot.getChartX());
            shotMap.put("y", shot.getChartY());
            shotMap.put("made", shot.getMade());
            shotMap.put("teamId", shot.getTeamId());
            shotMap.put("teamName", shot.getTeamName());
            shotMap.put("playerId", shot.getPlayerId());
            shotMap.put("playerName", shot.getPlayerName());
            shotMap.put("period", shot.getPeriod());
            shotMap.put("gameClock", shot.getGameClock());
            shotMap.put("shotType", shot.getShotType());
            shotMap.put("actionType", shot.getActionType());
            shotMap.put("points", shot.getPoints());
            shotMap.put("distance", shot.getDistance());
            shotList.add(shotMap);
        }
        result.put("shots", shotList);

        // 统计摘要
        int total = shots.size();
        int made = (int) shots.stream().filter(GameShotChart::getMade).count();
        long threeMade = shots.stream().filter(s -> s.getMade() && "3PT Field Goal".equals(s.getShotType())).count();
        long threeAttempted = shots.stream().filter(s -> "3PT Field Goal".equals(s.getShotType())).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalShots", total);
        summary.put("madeShots", made);
        summary.put("fgPct", total > 0 ? Math.round((double) made / total * 1000.0) / 1000.0 : 0);
        summary.put("threeMade", threeMade);
        summary.put("threeAttempted", threeAttempted);
        summary.put("threePct", threeAttempted > 0 ? Math.round((double) threeMade / threeAttempted * 1000.0) / 1000.0 : 0);
        result.put("summary", summary);

        result.put("source", source);
        result.put("mode", mode);
        result.put("status", "OK");
        result.put("message", message);

        return result;
    }

    private Map<String, Object> createEmptyGameResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("gameId", "");
        result.put("homeTeam", "");
        result.put("awayTeam", "");
        result.put("homeTeamId", null);
        result.put("awayTeamId", null);
        result.put("shots", new ArrayList<>());
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalShots", 0);
        summary.put("madeShots", 0);
        summary.put("fgPct", 0);
        summary.put("threeMade", 0);
        summary.put("threeAttempted", 0);
        summary.put("threePct", 0);
        result.put("summary", summary);
        result.put("source", "NONE");
        result.put("status", "EMPTY");
        result.put("message", message);
        return result;
    }
}

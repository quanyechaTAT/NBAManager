package com.nbamanager.service;

import com.nbamanager.domain.GameShotChart;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerShotChart;
import com.nbamanager.repository.GameShotChartRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerShotChartRepository;
import com.nbamanager.util.PythonScriptRunner;
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

    private final PlayerShotChartRepository shotChartRepository;
    private final PlayerRepository playerRepository;
    private final PythonScriptRunner scriptRunner;
    private final GameShotChartRepository gameShotChartRepository;

    /**
     * 获取球员投篮数据（优先从缓存获取，没有则从API获取）
     */
    @Transactional
    public Map<String, Object> getPlayerShots(Long playerId, String season) {
        // 查找球员
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null || player.getNbaPlayerId() == null) {
            return createEmptyResult();
        }

        Long nbaPlayerId = player.getNbaPlayerId();

        // 先从数据库获取缓存数据
        List<PlayerShotChart> cachedShots = shotChartRepository.findByNbaPlayerIdAndSeason(nbaPlayerId, season);

        // 如果缓存数据足够（至少50条），直接返回
        if (cachedShots.size() >= 50) {
            log.info("从缓存返回投篮数据: playerId={}, count={}", nbaPlayerId, cachedShots.size());
            return buildResult(cachedShots);
        }

        // 缓存不足，从API获取
        log.info("从API获取投篮数据: playerId={}, season={}", nbaPlayerId, season);
        List<PlayerShotChart> apiShots = fetchFromApi(nbaPlayerId, season);

        if (!apiShots.isEmpty()) {
            // 保存到数据库
            shotChartRepository.saveAll(apiShots);
            log.info("保存投篮数据: {} 条", apiShots.size());
            return buildResult(apiShots);
        }

        // API也获取不到，返回缓存的少量数据
        if (!cachedShots.isEmpty()) {
            return buildResult(cachedShots);
        }

        return createEmptyResult();
    }

    /**
     * 从NBA API获取投篮数据
     */
    private List<PlayerShotChart> fetchFromApi(Long nbaPlayerId, String season) {
        try {
            JSONObject result = scriptRunner.execute("shot_chart", new String[]{String.valueOf(nbaPlayerId), season}, 60);

            if (result == null || !result.has("shots")) {
                log.warn("API返回的投篮数据为空: playerId={}", nbaPlayerId);
                return new ArrayList<>();
            }

            JSONArray shotsArray = result.getJSONArray("shots");
            List<PlayerShotChart> shots = new ArrayList<>();

            for (int i = 0; i < shotsArray.length(); i++) {
                JSONObject shot = shotsArray.getJSONObject(i);

                PlayerShotChart entity = new PlayerShotChart();
                entity.setPlayerId(nbaPlayerId);
                entity.setNbaPlayerId(nbaPlayerId);
                entity.setSeason(season);
                entity.setGameId(shot.optString("gameId", ""));
                entity.setX(BigDecimal.valueOf(shot.optDouble("x", 25)));
                entity.setY(BigDecimal.valueOf(shot.optDouble("y", 25)));
                entity.setMade(shot.optBoolean("made", false));
                entity.setZone(shot.optString("zone", ""));
                entity.setShotType(shot.optString("shotType", ""));
                entity.setPeriod(shot.optInt("period", 0));

                // 解析比赛日期
                String dateStr = shot.optString("gameDate", "");
                if (!dateStr.isEmpty()) {
                    try {
                        entity.setGameDate(LocalDate.parse(dateStr));
                    } catch (Exception e) {
                        entity.setGameDate(LocalDate.now());
                    }
                } else {
                    entity.setGameDate(LocalDate.now());
                }

                shots.add(entity);
            }

            return shots;

        } catch (Exception e) {
            log.error("从API获取投篮数据失败: playerId={}", nbaPlayerId, e);
            return new ArrayList<>();
        }
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
            shotList.add(shotMap);
        }

        result.put("shots", shotList);

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
        summary.put("fgPct", 0);
        summary.put("zones", new ArrayList<>());
        result.put("summary", summary);
        return result;
    }

    /**
     * 清除球员的缓存数据
     */
    @Transactional
    public void clearCache(Long nbaPlayerId, String season) {
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

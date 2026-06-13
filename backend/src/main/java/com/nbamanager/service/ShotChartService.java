package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerShotChart;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerShotChartRepository;
import com.nbamanager.util.PythonScriptRunner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}

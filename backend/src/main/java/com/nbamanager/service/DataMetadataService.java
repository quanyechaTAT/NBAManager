package com.nbamanager.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataMetadataService {
    private final JdbcTemplate jdbc;
    private final SeasonManagementService seasons;
    public Map<String, Object> overview() {
        List<Map<String, Object>> modules = new ArrayList<>();
        var tables = new LinkedHashMap<String, String>();
        tables.put("players", "players"); tables.put("teams", "teams"); tables.put("matches", "match_records");
        tables.put("history", "player_season_stats"); tables.put("shots", "player_shot_chart");
        for (var entry : tables.entrySet()) {
            // One aggregate per module, independent of row count. Never infer verification from a numeric value.
            var row = jdbc.queryForMap("SELECT COUNT(*) AS recordCount, COALESCE(SUM(data_quality IS NULL OR data_quality='UNVERIFIED'),0) AS unverifiedCount, COALESCE(SUM(data_field_states LIKE '%MISSING%'),0) AS missingCount, COALESCE(SUM(data_quality='PARTIAL' OR data_field_states LIKE '%UNVERIFIED%'),0) AS partialCount, MAX(data_synced_at) AS syncedAt, MAX(data_as_of) AS dataAsOf, COUNT(DISTINCT data_source) AS sourceCount, MAX(data_source) AS source, COUNT(DISTINCT data_season) AS seasonCount, MAX(data_season) AS season FROM `" + entry.getValue() + "`");
            long count = ((Number)row.get("recordCount")).longValue();
            long unknown = ((Number)row.get("unverifiedCount")).longValue();
            row.put("module", entry.getKey()); row.put("quality", count == unknown ? "UNVERIFIED" : unknown > 0 || ((Number)row.get("missingCount")).longValue() > 0 || ((Number)row.get("partialCount")).longValue() > 0 ? "PARTIAL" : "COMPLETE");
            row.put("source", ((Number)row.remove("sourceCount")).intValue() > 1 ? "MULTI_PROVIDER" : Objects.requireNonNullElse(row.get("source"), "UNKNOWN"));
            if (((Number)row.remove("seasonCount")).intValue() > 1) row.put("season", null);
            row.put("sourceUrl", null); row.put("scope", entry.getKey().equals("history") ? "HISTORICAL_REGULAR_SEASON" : "RECORDED_DATA");
            row.put("fieldStates", Map.of()); row.put("sampleSize", null);
            modules.add(row);
        }
        return Map.of("currentSeason", seasons.getCurrentSeason(), "modules", modules);
    }
}

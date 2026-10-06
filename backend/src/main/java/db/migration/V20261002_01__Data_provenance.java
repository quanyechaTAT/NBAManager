package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.json.JSONObject;

/** Additive migration: archive legacy values before enabling field-level publication. No ID changes. */
public class V20261002_01__Data_provenance extends BaseJavaMigration {
    private static final List<String> TABLES = List.of("players", "player_season_stats", "player_career_stats",
            "player_game_log", "teams", "team_season_stats", "match_records", "player_shot_chart");
    private static final Map<String, String> COLUMNS = Map.of(
            "data_source", "VARCHAR(64)", "data_source_url", "VARCHAR(512)", "data_season", "VARCHAR(16)",
            "data_scope", "VARCHAR(32)", "data_as_of", "DATETIME(6)", "data_synced_at", "DATETIME(6)",
            "data_quality", "VARCHAR(16)", "data_field_states", "TEXT");
    private static final Set<String> NULLABLE = Set.of("position", "jersey_number", "height", "weight", "country",
            "games_played", "games_started", "minutes_per_game", "points_per_game", "rebounds_per_game", "assists_per_game",
            "steals_per_game", "blocks_per_game", "turnovers_per_game", "field_goal_pct", "three_point_pct", "free_throw_pct",
            "true_shooting_pct", "usage_pct", "efficiency", "per", "win_shares", "vorp", "bpm", "offensive_rating", "defensive_rating",
            "points", "rebounds", "assists", "steals", "blocks", "turnovers", "plus_minus", "fg_pct", "three_pct", "ft_pct", "wins", "losses", "win_pct", "opponents_points_per_game");

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        execute(connection, "CREATE TABLE IF NOT EXISTS data_verification_snapshots (module VARCHAR(64) NOT NULL, record_id BIGINT NOT NULL, migration_version VARCHAR(32) NOT NULL, original_data LONGTEXT NOT NULL, captured_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(module, record_id, migration_version))");
        for (String table : TABLES) {
            Map<String, String> present = columns(connection, table);
            if (present.isEmpty()) continue; // Fresh deployments may let JPA create the table after Flyway.
            snapshot(connection, table);
            for (var entry : COLUMNS.entrySet()) if (!present.containsKey(entry.getKey()))
                execute(connection, "ALTER TABLE `" + table + "` ADD COLUMN `" + entry.getKey() + "` " + entry.getValue() + " NULL");
            if (!table.equals("match_records") && !table.equals("player_shot_chart"))
                for (var column : present.entrySet()) if (NULLABLE.contains(column.getKey()))
                    execute(connection, "ALTER TABLE `" + table + "` MODIFY COLUMN `" + column.getKey() + "` " + column.getValue() + " NULL");
            execute(connection, "UPDATE `" + table + "` SET data_quality='UNVERIFIED', data_source='UNKNOWN' WHERE data_quality IS NULL");
        }
    }
    private Map<String, String> columns(Connection connection, String table) throws SQLException {
        var columns = new LinkedHashMap<String, String>();
        try (var statement = connection.prepareStatement("SELECT COLUMN_NAME, COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) { while (rows.next()) columns.put(rows.getString(1), rows.getString(2)); }
        }
        return columns;
    }
    private void snapshot(Connection connection, String table) throws SQLException {
        long last = 0;
        while (true) {
            var batch = new ArrayList<Map.Entry<Long, String>>();
            try (var statement = connection.prepareStatement("SELECT * FROM `" + table + "` WHERE id>? ORDER BY id LIMIT 500")) {
                statement.setLong(1, last);
                try (var rows = statement.executeQuery()) {
                    var meta = rows.getMetaData();
                    while (rows.next()) {
                        var raw = new LinkedHashMap<String, Object>();
                        for (int i=1; i<=meta.getColumnCount(); i++) {
                            Object value = rows.getObject(i);
                            if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) value = value.toString();
                            raw.put(meta.getColumnName(i), value == null ? JSONObject.NULL : value);
                        }
                        last = rows.getLong("id"); batch.add(Map.entry(last, new JSONObject(raw).toString()));
                    }
                }
            }
            if (batch.isEmpty()) break;
            try (var statement = connection.prepareStatement("INSERT IGNORE INTO data_verification_snapshots(module,record_id,migration_version,original_data) VALUES(?,?,'20261002_01',?)")) {
                for (var item : batch) { statement.setString(1, table); statement.setLong(2, item.getKey()); statement.setString(3, item.getValue()); statement.addBatch(); }
                statement.executeBatch();
            }
        }
    }
    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}

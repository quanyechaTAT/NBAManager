package com.nbamanager.service;

import db.migration.V20261002_01__Data_provenance;
import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Runs only against an explicitly supplied, isolated local verification database. */
@EnabledIfEnvironmentVariable(named = "COURTSIDE_MIGRATION_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13307/redesign_verification.*")
class DataProvenanceMigrationTest {
    @Test void flywayArchivesOriginalValuesPreservesIdsAndAllowsMissingStatistics() throws Exception {
        String url = System.getenv("COURTSIDE_MIGRATION_JDBC_URL");
        try (var connection = DriverManager.getConnection(url, "root", "")) {
            var tables = List.of("players", "player_season_stats", "player_career_stats", "player_game_log", "teams", "team_season_stats", "match_records", "player_shot_chart");
            try (var statement = connection.createStatement()) {
                for (String table : tables) {
                    statement.execute("CREATE TABLE `" + table + "` (id BIGINT PRIMARY KEY, usage_pct DOUBLE NOT NULL, points_per_game DOUBLE NOT NULL, wins INT NOT NULL, losses INT NOT NULL)");
                    statement.execute("INSERT INTO `" + table + "` VALUES (7,20,0,0,0),(8,110,20,20,0),(9,0,110,0,20)");
                }
            }
            var migration = new V20261002_01__Data_provenance();
            var flyway = Flyway.configure().dataSource(url, "root", "").locations(new String[0]).javaMigrations(migration)
                    .baselineOnMigrate(true).baselineVersion("20261002.0").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (var statement = connection.createStatement()) {
                for (String table : tables) {
                    try (var rows = statement.executeQuery("SELECT * FROM `" + table + "` ORDER BY id")) {
                        assertThat(rows.next()).isTrue(); assertThat(rows.getLong("id")).isEqualTo(7L);
                        assertThat(rows.getDouble("usage_pct")).isEqualTo(20); assertThat(rows.getDouble("points_per_game")).isZero();
                        assertThat(rows.getString("data_quality")).isEqualTo("UNVERIFIED"); assertThat(rows.getString("data_source")).isEqualTo("UNKNOWN");
                        assertThat(rows.getTimestamp("data_synced_at")).isNull();
                    }
                }
                try (var rows = statement.executeQuery("SELECT original_data FROM data_verification_snapshots WHERE module='players' AND record_id=7")) {
                    assertThat(rows.next()).isTrue(); var original = new JSONObject(rows.getString(1));
                    assertThat(original.getDouble("usage_pct")).isEqualTo(20); assertThat(original.getDouble("points_per_game")).isZero();
                    assertThat(original.has("data_quality")).isFalse();
                }
                statement.execute("UPDATE players SET usage_pct=NULL, data_quality='PARTIAL', data_source='MANUAL' WHERE id=7");
            }
            // Rerunning the guarded DDL must preserve both the original snapshot and later verification.
            Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection); migration.migrate(context);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM data_verification_snapshots")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(24);
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT data_quality, usage_pct FROM players WHERE id=7")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("PARTIAL"); assertThat(rows.getObject(2)).isNull();
            }
        }
    }
}

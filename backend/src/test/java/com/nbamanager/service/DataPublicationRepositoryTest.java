package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamRepository;
import java.math.BigDecimal;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "COURTSIDE_MIGRATION_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13307/redesign_verification.*")
class DataPublicationRepositoryTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("COURTSIDE_MIGRATION_JDBC_URL");
        try (var connection = DriverManager.getConnection(url, "root", ""); var statement = connection.createStatement()) { statement.execute("CREATE DATABASE IF NOT EXISTS redesign_publication"); }
        registry.add("spring.datasource.url", () -> url.replace("redesign_verification", "redesign_publication"));
        registry.add("spring.datasource.username", () -> "root"); registry.add("spring.datasource.password", () -> "");
    }
    @Autowired PlayerRepository players;
    @Autowired TeamRepository teams;
    @Autowired PlayerSeasonStatsRepository history;

    private PlayerSeasonStats historical(long nbaId, int points, String fieldState) {
        var row = new PlayerSeasonStats(); row.setNbaPlayerId(nbaId); row.setPlayerName("P" + nbaId); row.setTeamName("A"); row.setSeason("2025-26");
        row.setGamesPlayed(20); row.setPointsPerGame(BigDecimal.valueOf(points)); row.setReboundsPerGame(null);
        row.markData("NBA_API", "2025-26", "REGULAR_SEASON", "gamesPlayed", "pointsPerGame", "reboundsPerGame");
        row.setDataFieldStates("{\"gamesPlayed\":\"OBSERVED\",\"pointsPerGame\":\"" + fieldState + "\",\"reboundsPerGame\":\"MISSING\"}");
        return history.saveAndFlush(row);
    }
    @Test void leaderboardAndAveragesUseOnlyEachFieldsPublishedValues() {
        historical(1L, 0, "OBSERVED"); historical(2L, 20, "OBSERVED"); historical(3L, 110, "UNVERIFIED");
        var leaders = history.findTopScorers("2025-26", PageRequest.of(0, 10));
        assertThat(leaders).extracting(PlayerSeasonStats::getNbaPlayerId).containsExactly(2L, 1L);
        var averages = history.findLeagueAggregate("2025-26");
        assertThat(averages.getAvgPoints()).isEqualByComparingTo("10"); assertThat(averages.getAvgRebounds()).isNull();
    }
    @Test void nullableNumericSortingPutsUnverifiedFieldsLastInBothDirections() {
        var team = new Team(); team.setName("A"); team.setCity("A"); team.setConference("东部"); teams.saveAndFlush(team);
        for (int points : new int[]{0, 20, 110}) {
            var row = new Player(); row.setName("P" + points); row.setTeam(team); row.setPointsPerGame((double)points);
            row.markData("NBA_API", "2025-26", "REGULAR_SEASON", "pointsPerGame");
            if (points == 110) { row.setDataQuality("PARTIAL"); row.setDataFieldStates("{\"pointsPerGame\":\"UNVERIFIED\"}"); }
            players.saveAndFlush(row);
        }
        var service = new PlayerService(players, teams, mock(PlayerListCache.class));
        var descending = service.queryPage(null, null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "pointsPerGame")));
        var ascending = service.queryPage(null, null, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "pointsPerGame")));
        assertThat(descending.content()).extracting(com.nbamanager.web.dto.PlayerDto::pointsPerGame).containsExactly(20.0, 0.0, null);
        assertThat(ascending.content()).extracting(com.nbamanager.web.dto.PlayerDto::pointsPerGame).containsExactly(0.0, 20.0, null);
    }
}

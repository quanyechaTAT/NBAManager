package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TeamMatchStatisticsTest {
    private MatchRecord match(int day, int home, int away, String season, String type) {
        var row = new MatchRecord(); row.setHomeTeam("A"); row.setAwayTeam("B"); row.setHomeScore(home); row.setAwayScore(away);
        row.setStatus("FINISHED"); row.setMatchDate(LocalDate.of(2026, 1, day)); row.setSeason(season); row.setGameType(type);
        row.markData("NBA_API", season, type, "homeScore", "awayScore"); return row;
    }
    @Test void anEndedStreakDoesNotTruncateTheRemainingSample() {
        var stats = TeamMatchStatistics.calculate("A", "2025-26", List.of(match(4, 110, 100, "2025-26", "REGULAR_SEASON"), match(3, 90, 100, "2025-26", "REGULAR_SEASON"), match(2, 120, 100, "2025-26", "REGULAR_SEASON"), match(1, 100, 90, "2025-26", "REGULAR_SEASON")));
        assertThat(stats.streak()).isEqualTo("1连胜"); assertThat(stats.homeRecord()).isEqualTo("3-1");
        assertThat(stats.pointsPerGame()).isEqualTo(105); assertThat(stats.pointDifferential()).isEqualTo(7.5);
        assertThat(stats.dataMeta().sampleSize()).isEqualTo(4);
    }
    @Test void excludesOtherSeasonsPlayoffsAndUnverifiedRecords() {
        var legacy = match(1, 130, 90, "2025-26", "REGULAR_SEASON"); legacy.setDataQuality("UNVERIFIED");
        var stats = TeamMatchStatistics.calculate("A", "2025-26", List.of(match(4, 0, 100, "2025-26", "REGULAR_SEASON"), match(3, 120, 90, "2024-25", "REGULAR_SEASON"), match(2, 120, 90, "2025-26", "PLAYOFF"), legacy));
        assertThat(stats.dataMeta().sampleSize()).isEqualTo(1); assertThat(stats.pointsPerGame()).isZero();
        assertThat(stats.pointDifferential()).isEqualTo(-100); assertThat(stats.homeRecord()).isEqualTo("0-1");
    }
    @Test void returnsMissingMetricsWhenThereIsNoValidSample() {
        var stats = TeamMatchStatistics.calculate("A", "2025-26", List.of());
        assertThat(stats.pointsPerGame()).isNull(); assertThat(stats.streak()).isNull();
        assertThat(stats.dataMeta().fieldStates()).containsEntry("pointDifferential", "MISSING");
    }
}

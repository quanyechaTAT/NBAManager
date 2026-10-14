package com.nbamanager.service;

import com.nbamanager.domain.DataTracked;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerCareerStats;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** A frontend rollback must still be able to read the values retained by the migration. */
class LegacySnapshotDisplayTest {
    private <T extends DataTracked> T legacy(T row) {
        row.setDataSource("UNKNOWN");
        row.setDataQuality("UNVERIFIED");
        return row;
    }

    private Team team(String name, int wins, int losses) {
        var row = legacy(new Team());
        row.setName(name); row.setConference("西部"); row.setWins(wins); row.setLosses(losses);
        return row;
    }

    @Test void retainedPlayerValuesAreReadableWithoutChangingTheirVerificationStatus() {
        var player = legacy(new Player());
        player.setPointsPerGame(33.5); player.setUsagePct(20.0); player.setBlocksPerGame(0.0);
        assertThat(player.getPointsPerGame()).isEqualTo(33.5);
        assertThat(player.getUsagePct()).isEqualTo(20.0);
        assertThat(player.getBlocksPerGame()).isZero();
        assertThat(player.getTrueShootingPct()).isNull();
        assertThat(player.getDataMeta().quality()).isEqualTo("UNVERIFIED");
        assertThat(player.getDataMeta().publishes("pointsPerGame")).isFalse();
    }

    @Test void anExplicitlyRejectedFieldStillStaysHidden() {
        var player = legacy(new Player());
        player.setPointsPerGame(110.0);
        player.setDataFieldStates("{\"pointsPerGame\":\"UNVERIFIED\"}");
        assertThat(player.getPointsPerGame()).isNull();
    }

    @Test void historicalPlayerValuesAlsoSurviveTheFrontendRollback() {
        var row = legacy(new PlayerCareerStats());
        row.setPointsPerGame(27.3);
        assertThat(row.getPointsPerGame()).isEqualTo(27.3);
    }

    @Test void dashboardRestoresNineScorersAndStoredTeamRecords() {
        var teams = mock(TeamRepository.class);
        var players = mock(PlayerRepository.class);
        var team = team("雷霆", 64, 18);
        when(teams.findAll()).thenReturn(List.of(team));
        var rows = IntStream.range(0, 12).mapToObj(i -> {
            var player = legacy(new Player()); player.setId((long) i + 1);
            player.setName("球员" + i); player.setTeam(team); player.setPointsPerGame(40.0 - i);
            return player;
        }).toList();
        when(players.count()).thenReturn((long) rows.size());
        when(players.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(rows.subList(0, 9)));
        var result = new DashboardService(teams, players).buildStats();
        assertThat(result.topScorers()).hasSize(9);
        assertThat(result.topScorers().get(0).ppg()).isEqualTo(40.0);
        assertThat(result.teamWinRows().get(0).wins()).isEqualTo(64);
    }

    @Test void standingsUseTheRetainedRecordsInsteadOfAlphabeticalOrder() {
        var teams = mock(TeamRepository.class);
        var matches = mock(MatchRecordRepository.class);
        var seasons = mock(SeasonManagementService.class);
        when(seasons.getCurrentSeason()).thenReturn("2025-26");
        when(matches.findByStatus("FINISHED")).thenReturn(List.of());
        when(teams.findAll()).thenReturn(List.of(team("勇士", 48, 34), team("湖人", 53, 29),
                team("雷霆", 64, 18), team("火箭", 52, 30), team("掘金", 54, 28), team("马刺", 62, 20)));
        var service = new TeamService(teams, matches);
        ReflectionTestUtils.setField(service, "seasonService", seasons);
        var west = service.getConferenceRankings().get("西部");
        assertThat(west).extracting(com.nbamanager.web.dto.TeamRankDto::teamName)
                .containsExactly("雷霆", "马刺", "掘金", "湖人", "火箭", "勇士");
        assertThat(west.get(0).wins()).isEqualTo(64);
        assertThat(west.get(0).losses()).isEqualTo(18);
    }
}

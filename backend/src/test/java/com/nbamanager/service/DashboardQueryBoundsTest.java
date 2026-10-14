package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardQueryBoundsTest {
    private Player player() {
        var team = new Team(); team.setId(1L); team.setName("马刺");
        var player = new Player(); player.setId(1L); player.setName("文班亚马"); player.setTeam(team);
        player.setPointsPerGame(0.0); player.markData("NBA_API", "2026-27", "REGULAR_SEASON", "pointsPerGame");
        return player;
    }

    @Test void homepageLoadsOnlyNinePlayersAndCountsWithoutLoadingTheArchive() {
        var teams = mock(TeamRepository.class); var players = mock(PlayerRepository.class);
        when(teams.findAll()).thenReturn(List.of()); when(players.count()).thenReturn(10_000L);
        when(players.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(player())));
        var stats = new DashboardService(teams, players).buildStats();
        assertThat(stats.playerCount()).isEqualTo(10_000);
        assertThat(stats.topScorers().get(0).ppg()).isZero();
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(players).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(9);
        assertThat(page.getValue().getSort()).containsExactly(Sort.Order.desc("pointsPerGame"), Sort.Order.asc("id"));
        verify(players, never()).findAll();
    }

    @Test void leaderboardReturnsDatabaseTotalsAndRequestsOnlyTheSelectedPage() {
        var players = mock(PlayerRepository.class);
        when(players.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(player()), PageRequest.of(3, 20), 1000));
        var result = new DashboardService(mock(TeamRepository.class), players).getTopScorers(3, 20);
        assertThat(result.getTotalElements()).isEqualTo(1000);
        assertThat(result.getNumber()).isEqualTo(3);
        assertThat(result.getContent().get(0).pointsPerGame()).isZero();
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(players).findAll(any(Specification.class), page.capture());
        assertThat(page.getValue().getOffset()).isEqualTo(60);
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
        verify(players, never()).findAll();
    }
}

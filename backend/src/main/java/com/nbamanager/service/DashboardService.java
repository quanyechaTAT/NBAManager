package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.DashboardStatsDto.TeamWinRow;
import com.nbamanager.web.dto.DashboardStatsDto.TopScorerRow;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto buildStats() {
        List<Team> teams = teamRepository.findAll();
        List<Player> players = playerRepository.findAll();

        List<TeamWinRow> teamRows =
                teams.stream()
                        .sorted(Comparator.comparing(Team::getWins).reversed())
                        .map(t -> new TeamWinRow(t.getName(), t.getWins(), t.getLosses()))
                        .toList();

        List<TopScorerRow> top =
                players.stream()
                        .sorted(Comparator.comparing(Player::getPointsPerGame).reversed())
                        .limit(8)
                        .map(p -> new TopScorerRow(p.getName(), p.getPointsPerGame(), p.getTeam().getName()))
                        .toList();

        return new DashboardStatsDto(teamRows, top);
    }
}

package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.DashboardStatsDto.TeamWinRow;
import com.nbamanager.web.dto.DashboardStatsDto.TopScorerRow;
import com.nbamanager.web.dto.PlayerDto;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    @Cacheable(value = "dashboard", key = "'stats'")
    @Transactional(readOnly = true)
    public DashboardStatsDto buildStats() {
        List<Team> teams = teamRepository.findAll();
        List<Player> players = playerRepository.findAll();

        List<TeamWinRow> teamRows =
                teams.stream()
                        .sorted(Comparator.comparing(Team::getWins).reversed())
                        .map(t -> new TeamWinRow(t.getName(), t.getWins(), t.getLosses()))
                        .collect(Collectors.toList());

        List<TopScorerRow> top =
                players.stream()
                        .sorted(Comparator.comparing(Player::getPointsPerGame).reversed())
                        .limit(8)
                        .map(p -> new TopScorerRow(p.getId(), p.getName(), p.getPointsPerGame(), p.getTeam().getName(), p.getNbaPlayerId()))
                        .collect(Collectors.toList());

        return new DashboardStatsDto(teams.size(), players.size(), teamRows, top);
    }

    @Transactional(readOnly = true)
    public Page<PlayerDto> getTopScorers(int page, int size) {
        List<Player> all = playerRepository.findAllWithTeamSortedByPpg();
        int start = page * size;
        int end = Math.min(start + size, all.size());
        List<PlayerDto> content = start < all.size()
                ? all.subList(start, end).stream().map(this::toDto).collect(Collectors.toList())
                : List.of();
        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(page, size), all.size());
    }

    private PlayerDto toDto(Player p) {
        Team t = p.getTeam();
        return new PlayerDto(
                p.getId(), p.getNbaPlayerId(), p.getName(), p.getNameEn(), p.getTranslationStatus(),
                t.getId(), t.getName(), p.getPosition(),
                p.getPointsPerGame(), p.getReboundsPerGame(), p.getAssistsPerGame(), p.getStealsPerGame(),
                p.getGamesPlayed(), p.getMinutesPerGame(), p.getFieldGoalPct(), p.getThreePointPct(),
                p.getFreeThrowPct(), p.getBlocksPerGame(), p.getTurnoversPerGame(), p.getEfficiency(),
                p.getTrueShootingPct(), p.getUsagePct(), p.getJerseyNumber(), p.getHeight(), p.getWeight(),
                p.getCountry(), p.getPer(), p.getWinShares(), p.getVorp(), p.getBpm(),
                p.getOffensiveRating(), p.getDefensiveRating());
    }
}

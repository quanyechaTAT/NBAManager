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
import org.springframework.data.jpa.domain.Specification;
import com.nbamanager.web.support.PageRequestSanitizer;
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
        Page<Player> leaders = publishedScorers(PageRequest.of(0, 9));

        List<TeamWinRow> teamRows =
                teams.stream()
                        .filter(t -> t.canDisplay("wins") && t.canDisplay("losses")
                                && t.getWins() != null && t.getLosses() != null)
                        .sorted(Comparator.comparing(Team::getWins).reversed())
                        .map(t -> new TeamWinRow(t.getName(), t.getWins(), t.getLosses()))
                        .collect(Collectors.toList());

        List<TopScorerRow> top =
                leaders.getContent().stream()
                        .map(p -> new TopScorerRow(p.getId(), p.getName(), p.getPointsPerGame(), p.getTeam().getName(), p.getNbaPlayerId()))
                        .collect(Collectors.toList());

        return new DashboardStatsDto(teams.size(), Math.toIntExact(playerRepository.count()), teamRows, top);
    }

    @Transactional(readOnly = true)
    public Page<PlayerDto> getTopScorers(int page, int size) {
        int safePage = Math.max(0, Math.min(page, PageRequestSanitizer.MAX_PAGE_NUMBER));
        int safeSize = Math.max(1, Math.min(size, PageRequestSanitizer.MAX_PAGE_SIZE));
        return publishedScorers(PageRequest.of(safePage, safeSize)).map(this::toDto);
    }

    private Page<Player> publishedScorers(Pageable page) {
        // Apply the same publication rule as Player getters before LIMIT/count.
        // Filtering after pagination would create short pages and incorrect totals.
        Specification<Player> published = (root, query, builder) -> DataPublication.available(root, builder, "pointsPerGame");
        var ordered = PageRequest.of(page.getPageNumber(), page.getPageSize(),
                Sort.by(Sort.Order.desc("pointsPerGame"), Sort.Order.asc("id")));
        return playerRepository.findAll(published, ordered);
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
                p.getOffensiveRating(), p.getDefensiveRating(), p.getDataMeta());
    }
}

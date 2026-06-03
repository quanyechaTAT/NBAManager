package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.PlayerDto;
import com.nbamanager.web.dto.PlayerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public Page<PlayerDto> list(String keyword, Long teamId, String position, Pageable pageable) {
        Page<Player> page;

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasTeam = teamId != null;
        boolean hasPos = position != null && !position.isBlank();
        String kw = hasKeyword ? keyword.trim() : null;
        String ps = hasPos ? position.trim() : null;

        if (hasKeyword && hasTeam && hasPos) {
            page = playerRepository.findByNameContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(kw, teamId, ps, pageable);
        } else if (hasKeyword && hasTeam) {
            page = playerRepository.findByNameContainingIgnoreCaseAndTeamId(kw, teamId, pageable);
        } else if (hasKeyword && hasPos) {
            page = playerRepository.findByNameContainingIgnoreCaseAndPositionIgnoreCase(kw, ps, pageable);
        } else if (hasTeam && hasPos) {
            page = playerRepository.findByTeamIdAndPositionIgnoreCase(teamId, ps, pageable);
        } else if (hasKeyword) {
            page = playerRepository.findByNameContainingIgnoreCase(kw, pageable);
        } else if (hasTeam) {
            page = playerRepository.findByTeamId(teamId, pageable);
        } else if (hasPos) {
            page = playerRepository.findByPositionIgnoreCase(ps, pageable);
        } else {
            page = playerRepository.findAll(pageable);
        }

        return page.map(this::toDto);
    }

    @Cacheable(value = "players", key = "#id")
    @Transactional(readOnly = true)
    public PlayerDto get(Long id) {
        Player p = playerRepository.findById(id).orElseThrow(() -> notFound(id));
        return toDto(p);
    }

    @CacheEvict(value = {"players", "dashboard"}, allEntries = true)
    @Transactional
    public PlayerDto create(PlayerRequest req) {
        Team team = teamRepository.findById(req.teamId()).orElseThrow(() -> teamNotFound(req.teamId()));
        Player p = new Player();
        apply(p, req, team);
        return toDto(playerRepository.save(p));
    }

    @CacheEvict(value = {"players", "dashboard"}, allEntries = true)
    @Transactional
    public PlayerDto update(Long id, PlayerRequest req) {
        Player p = playerRepository.findById(id).orElseThrow(() -> notFound(id));
        Team team = teamRepository.findById(req.teamId()).orElseThrow(() -> teamNotFound(req.teamId()));
        apply(p, req, team);
        return toDto(playerRepository.save(p));
    }

    @CacheEvict(value = {"players", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!playerRepository.existsById(id)) {
            throw notFound(id);
        }
        playerRepository.deleteById(id);
    }

    private void apply(Player p, PlayerRequest req, Team team) {
        p.setName(req.name());
        p.setTeam(team);
        p.setPosition(req.position());
        p.setPointsPerGame(req.pointsPerGame());
        p.setReboundsPerGame(req.reboundsPerGame());
        p.setAssistsPerGame(req.assistsPerGame());
        p.setStealsPerGame(req.stealsPerGame());
        p.setGamesPlayed(req.gamesPlayed());
        p.setMinutesPerGame(req.minutesPerGame());
        p.setFieldGoalPct(req.fieldGoalPct());
        p.setThreePointPct(req.threePointPct());
        p.setFreeThrowPct(req.freeThrowPct());
        p.setBlocksPerGame(req.blocksPerGame());
        p.setTurnoversPerGame(req.turnoversPerGame());
        p.setEfficiency(req.efficiency());
        p.setTrueShootingPct(req.trueShootingPct());
        p.setUsagePct(req.usagePct());
        p.setJerseyNumber(req.jerseyNumber());
        p.setHeight(req.height());
        p.setWeight(req.weight());
        p.setCountry(req.country());
    }

    private PlayerDto toDto(Player p) {
        Team t = p.getTeam();
        return new PlayerDto(
                p.getId(),
                p.getNbaPlayerId(),
                p.getName(),
                t.getId(),
                t.getName(),
                p.getPosition(),
                p.getPointsPerGame(),
                p.getReboundsPerGame(),
                p.getAssistsPerGame(),
                p.getStealsPerGame(),
                p.getGamesPlayed(),
                p.getMinutesPerGame(),
                p.getFieldGoalPct(),
                p.getThreePointPct(),
                p.getFreeThrowPct(),
                p.getBlocksPerGame(),
                p.getTurnoversPerGame(),
                p.getEfficiency(),
                p.getTrueShootingPct(),
                p.getUsagePct(),
                p.getJerseyNumber(),
                p.getHeight(),
                p.getWeight(),
                p.getCountry());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球员不存在: " + id);
    }

    private static ApiException teamNotFound(Long id) {
        return new ApiException(HttpStatus.BAD_REQUEST, "球队不存在: " + id);
    }
}

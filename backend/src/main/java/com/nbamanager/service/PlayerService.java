package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.PlayerDto;
import com.nbamanager.web.dto.PlayerRequest;
import com.nbamanager.web.support.PageRequestSanitizer;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final PlayerListCache playerListCache;

    /**
     * 球员列表允许的排序字段。前端球员列表默认按得分排序，导出按 name 排序，
     * 其他字段一律拒绝（未知字段过去会经 PropertyReferenceException 变成 500）。
     */
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "name", "nameEn", "position", "jerseyNumber",
            "gamesPlayed", "minutesPerGame", "pointsPerGame", "reboundsPerGame",
            "assistsPerGame", "stealsPerGame", "blocksPerGame", "turnoversPerGame",
            "fieldGoalPct", "threePointPct", "freeThrowPct",
            "efficiency", "trueShootingPct", "usagePct");
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "ppg", "pointsPerGame",
            "rpg", "reboundsPerGame",
            "apg", "assistsPerGame");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    /**
     * 球员列表。
     *
     * 性能（压测报告 §十 10.3 建议 3）：该列表是 50 并发下最慢的读路径——原本没有缓存
     * （只有 {@link #get(Long)} 按 id 缓存），每次请求要 findAll + count 两条查询，
     * 在默认 10 连接的 Hikari 池下很快成为排队点；实测 50 并发 p99 250-800ms。
     * 现由 {@link PlayerListCache} 以短 TTL 承接；该类注释说明了为什么不直接缓存 Page。
     */
    @Transactional(readOnly = true)
    public Page<PlayerDto> list(String keyword, Long teamId, String position, Pageable pageable) {
        Pageable safePageable = PageRequestSanitizer.sanitize(
                pageable, ALLOWED_SORTS, SORT_ALIASES, DEFAULT_SORT);
        PlayerListCache.PagePayload payload =
                playerListCache.load(keyword, teamId, position, safePageable);
        return new org.springframework.data.domain.PageImpl<>(
                payload.content(), safePageable, payload.totalElements());
    }

    /** 实际执行列表查询（缓存未命中时由 {@link PlayerListCache} 调用）。 */
    public PlayerListCache.PagePayload queryPage(String keyword, Long teamId, String position,
                                                 Pageable safePageable) {
        Page<Player> page;

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasTeam = teamId != null;
        boolean hasPos = position != null && !position.isBlank();
        String kw = hasKeyword ? keyword.trim() : null;
        String ps = hasPos ? position.trim() : null;

        var numericOrder = safePageable.getSort().stream().filter(order -> !Set.of("id", "name", "nameEn", "position", "jerseyNumber").contains(order.getProperty())).findFirst();
        if (numericOrder.isPresent()) {
            var order = numericOrder.get();
            org.springframework.data.jpa.domain.Specification<Player> specification = (root, query, builder) -> {
                var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                if (hasKeyword) predicates.add(builder.or(builder.like(builder.lower(root.get("name")), "%" + kw.toLowerCase(java.util.Locale.ROOT) + "%"), builder.like(builder.lower(root.get("nameEn")), "%" + kw.toLowerCase(java.util.Locale.ROOT) + "%")));
                if (hasTeam) predicates.add(builder.equal(root.get("team").get("id"), teamId));
                if (hasPos) predicates.add(builder.equal(builder.lower(root.get("position")), ps.toLowerCase(java.util.Locale.ROOT)));
                if (query != null && query.getResultType() != Long.class) {
                    var unavailable = builder.not(DataPublication.available(root, builder, order.getProperty()));
                    var missing = builder.<Integer>selectCase().when(unavailable, 1).otherwise(0);
                    query.orderBy(builder.asc(missing), order.isAscending() ? builder.asc(root.get(order.getProperty())) : builder.desc(root.get(order.getProperty())), builder.asc(root.get("id")));
                }
                return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            };
            page = playerRepository.findAll(specification, org.springframework.data.domain.PageRequest.of(safePageable.getPageNumber(), safePageable.getPageSize()));
            return new PlayerListCache.PagePayload(page.getContent().stream().map(this::toDto).toList(), page.getTotalElements());
        }

        if (hasKeyword && hasTeam && hasPos) {
            page = playerRepository.findByNameOrNameEnContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(kw, teamId, ps, safePageable);
        } else if (hasKeyword && hasTeam) {
            page = playerRepository.findByNameOrNameEnContainingIgnoreCaseAndTeamId(kw, teamId, safePageable);
        } else if (hasKeyword && hasPos) {
            page = playerRepository.findByNameOrNameEnContainingIgnoreCaseAndPositionIgnoreCase(kw, ps, safePageable);
        } else if (hasTeam && hasPos) {
            page = playerRepository.findByTeamIdAndPositionIgnoreCase(teamId, ps, safePageable);
        } else if (hasKeyword) {
            page = playerRepository.findByNameOrNameEnContainingIgnoreCase(kw, safePageable);
        } else if (hasTeam) {
            page = playerRepository.findByTeamId(teamId, safePageable);
        } else if (hasPos) {
            page = playerRepository.findByPositionIgnoreCase(ps, safePageable);
        } else {
            page = playerRepository.findAll(safePageable);
        }

        return new PlayerListCache.PagePayload(
                page.getContent().stream().map(this::toDto).collect(java.util.stream.Collectors.toList()),
                page.getTotalElements());
    }

    @Cacheable(value = "players", key = "#id")
    @Transactional(readOnly = true)
    public PlayerDto get(Long id) {
        Player p = playerRepository.findById(id).orElseThrow(() -> notFound(id));
        return toDto(p);
    }

    @CacheEvict(value = {"players", "playerList", "dashboard"}, allEntries = true)
    @Transactional
    public PlayerDto create(PlayerRequest req) {
        Team team = teamRepository.findById(req.teamId()).orElseThrow(() -> teamNotFound(req.teamId()));
        Player p = new Player();
        apply(p, req, team);
        return toDto(playerRepository.save(p));
    }

    @CacheEvict(value = {"players", "playerList", "dashboard"}, allEntries = true)
    @Transactional
    public PlayerDto update(Long id, PlayerRequest req) {
        Player p = playerRepository.findById(id).orElseThrow(() -> notFound(id));
        Team team = teamRepository.findById(req.teamId()).orElseThrow(() -> teamNotFound(req.teamId()));
        apply(p, req, team);
        return toDto(playerRepository.save(p));
    }

    @CacheEvict(value = {"players", "playerList", "dashboard"}, allEntries = true)
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
        p.markData("MANUAL", p.getDataSeason(), "REGULAR_SEASON", PlayerSnapshotMapper.FIELDS.keySet().toArray(String[]::new));
    }

    private PlayerDto toDto(Player p) {
        Team t = p.getTeam();
        return new PlayerDto(
                p.getId(),
                p.getNbaPlayerId(),
                p.getName(),
                p.getNameEn(),
                p.getTranslationStatus(),
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
                p.getCountry(),
                p.getPer(),
                p.getWinShares(),
                p.getVorp(),
                p.getBpm(),
                p.getOffensiveRating(),
                p.getDefensiveRating(), p.getDataMeta());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球员不存在: " + id);
    }

    private static ApiException teamNotFound(Long id) {
        return new ApiException(HttpStatus.BAD_REQUEST, "球队不存在: " + id);
    }
}

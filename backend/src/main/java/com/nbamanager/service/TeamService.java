package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Team;
import com.nbamanager.domain.TeamSeasonStats;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import com.nbamanager.web.dto.DataMeta;
import com.nbamanager.web.dto.TeamDto;
import com.nbamanager.web.dto.TeamRankDto;
import com.nbamanager.web.dto.TeamRequest;
import com.nbamanager.web.support.PageRequestSanitizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
public class TeamService {

    private final TeamRepository teamRepository;
    private final MatchRecordRepository matchRecordRepository;
    private SeasonManagementService seasonService;
    private TeamSeasonStatsRepository teamStatsRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public TeamService(TeamRepository teamRepository,
                       MatchRecordRepository matchRecordRepository,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) SeasonManagementService seasonService,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) TeamSeasonStatsRepository teamStatsRepo) {
        this.teamRepository = teamRepository;
        this.matchRecordRepository = matchRecordRepository;
        this.seasonService = seasonService;
        this.teamStatsRepo = teamStatsRepo;
    }

    public TeamService(TeamRepository teamRepository, MatchRecordRepository matchRecordRepository) {
        this(teamRepository, matchRecordRepository, null, null);
    }

    /** 球队列表允许的排序字段（默认按胜场降序，与前端期望一致）。 */
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "name", "nameEn", "abbreviation", "city", "conference", "division",
            "wins", "losses", "logoUrl");
    private static final Map<String, String> SORT_ALIASES = Map.of();
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "wins");

    @Transactional(readOnly = true)
    public Page<TeamDto> list(String keyword, Pageable pageable) {
        Pageable safePageable = PageRequestSanitizer.sanitize(
                pageable, ALLOWED_SORTS, SORT_ALIASES, DEFAULT_SORT);
        Page<Team> page;
        if (keyword == null || keyword.isBlank()) {
            page = teamRepository.findAll(safePageable);
        } else {
            String q = keyword.trim();
            page = teamRepository.findByNameContainingIgnoreCaseOrCityContainingIgnoreCase(q, q, safePageable);
        }
        return page.map(this::toDto);
    }

    @Cacheable(value = "teams", key = "#id")
    @Transactional(readOnly = true)
    public TeamDto get(Long id) {
        return toDto(teamRepository.findById(id).orElseThrow(() -> notFound(id)));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public TeamDto create(TeamRequest req) {
        Team t = new Team();
        apply(t, req);
        return toDto(teamRepository.save(t));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public TeamDto update(Long id, TeamRequest req) {
        Team t = teamRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(t, req);
        return toDto(teamRepository.save(t));
    }

    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!teamRepository.existsById(id)) {
            throw notFound(id);
        }
        teamRepository.deleteById(id);
    }

    @Cacheable(value = "rankings", key = "'all'")
    @Transactional(readOnly = true)
    public Map<String, List<TeamRankDto>> getConferenceRankings() {
        return rankings(false);
    }

    private Map<String, List<TeamRankDto>> rankings(boolean divisions) {
        List<Team> all = teamRepository.findAll();
        List<MatchRecord> matches = matchRecordRepository.findByStatus("FINISHED");
        String season = seasonService != null ? seasonService.getCurrentSeason() : "2025-26";
        Map<String, TeamSeasonStats> seasonStatsMap = (teamStatsRepo != null && season != null)
                ? teamStatsRepo.findBySeasonOrderByWinsDesc(season).stream()
                        .collect(Collectors.toMap(TeamSeasonStats::getTeamName, s -> s, (a, b) -> a))
                : Map.of();
        Map<String, List<TeamRankDto>> result = new java.util.LinkedHashMap<>();
        List<String> groups = divisions ? List.of("大西洋", "中部", "东南", "西北", "太平洋", "西南") : List.of("东部", "西部");
        for (String group : groups) {
            var sorted = all.stream().filter(t -> group.equals(divisions ? t.getDivision() : t.getConference()))
                    .sorted(Comparator.comparing((Team t) -> effectiveWinRate(t, season, seasonStatsMap), Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(t -> effectiveDiff(t, season, seasonStatsMap),
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Team::getName)).toList();
            List<TeamRankDto> ranks = new ArrayList<>();
            Team first = sorted.isEmpty() ? null : sorted.get(0);
            Integer firstWins = first != null ? getEffectiveWins(first, season, seasonStatsMap) : null;
            Integer firstLosses = first != null ? getEffectiveLosses(first, season, seasonStatsMap) : null;
            for (Team team : sorted) {
                Integer wins = getEffectiveWins(team, season, seasonStatsMap);
                Integer losses = getEffectiveLosses(team, season, seasonStatsMap);
                Double gb = (firstWins != null && firstLosses != null && wins != null && losses != null)
                        ? ((firstWins - firstLosses) - (wins - losses)) / 2.0 : null;
                if (gb != null) {
                    gb = Math.round(gb * 10.0) / 10.0;
                }
                var sample = TeamMatchStatistics.calculate(team.getName(), season, matches);
                TeamSeasonStats tss = seasonStatsMap.get(team.getName());

                String homeRecord = sample.homeRecord() != null ? sample.homeRecord() : (tss != null ? tss.getHomeRecord() : null);
                String awayRecord = sample.awayRecord() != null ? sample.awayRecord() : (tss != null ? tss.getAwayRecord() : null);
                Double ppg = sample.pointsPerGame() != null ? sample.pointsPerGame() : (tss != null && tss.getPointsPerGame() != null ? tss.getPointsPerGame().doubleValue() : null);
                Double oppg = sample.oppPointsPerGame() != null ? sample.oppPointsPerGame() : (tss != null && tss.getOpponentsPointsPerGame() != null ? tss.getOpponentsPointsPerGame().doubleValue() : null);
                Double diff = sample.pointDifferential() != null ? sample.pointDifferential() : ((ppg != null && oppg != null) ? Math.round((ppg - oppg) * 10.0) / 10.0 : null);
                String streak = sample.streak() != null ? sample.streak() : (tss != null ? tss.getStreak() : null);
                DataMeta sampleMeta = (sample.homeRecord() != null || sample.pointsPerGame() != null)
                        ? sample.dataMeta()
                        : (tss != null && tss.getDataMeta() != null ? tss.getDataMeta() : sample.dataMeta());
                DataMeta teamMeta = team.getDataMeta();
                if (teamMeta == null && tss != null) {
                    teamMeta = tss.getDataMeta();
                }
                ranks.add(new TeamRankDto(team.getName(), wins, losses,
                        gb, team.getConference(), homeRecord, awayRecord, ppg,
                        oppg, diff, streak, diff,
                        sampleMeta, teamMeta));
            }
            result.put(group, ranks);
        }
        return result;
    }

    private Integer getEffectiveWins(Team team, String season, Map<String, TeamSeasonStats> seasonStatsMap) {
        if (hasDisplayableRecord(team, season)) return team.getWins();
        TeamSeasonStats tss = seasonStatsMap.get(team.getName());
        return tss != null ? tss.getWins() : null;
    }

    private Integer getEffectiveLosses(Team team, String season, Map<String, TeamSeasonStats> seasonStatsMap) {
        if (hasDisplayableRecord(team, season)) return team.getLosses();
        TeamSeasonStats tss = seasonStatsMap.get(team.getName());
        return tss != null ? tss.getLosses() : null;
    }

    private Double effectiveWinRate(Team team, String season, Map<String, TeamSeasonStats> seasonStatsMap) {
        Integer w = getEffectiveWins(team, season, seasonStatsMap);
        Integer l = getEffectiveLosses(team, season, seasonStatsMap);
        if (w == null || l == null) return null;
        int total = w + l;
        return total == 0 ? 0.0 : (double) w / total;
    }

    private Integer effectiveDiff(Team team, String season, Map<String, TeamSeasonStats> seasonStatsMap) {
        Integer w = getEffectiveWins(team, season, seasonStatsMap);
        Integer l = getEffectiveLosses(team, season, seasonStatsMap);
        return (w != null && l != null) ? (w - l) : null;
    }

    private static Double winRate(Team team, String season) {
        if (!hasDisplayableRecord(team, season)) return null;
        int total = team.getWins() + team.getLosses();
        return total == 0 ? 0.0 : (double) team.getWins() / total;
    }

    private static boolean hasDisplayableRecord(Team team, String season) {
        return (team.hasRetainedLegacyValues() || java.util.Objects.equals(season, team.getDataMeta().season()))
                && team.canDisplay("wins") && team.canDisplay("losses")
                && team.getWins() != null && team.getLosses() != null;
    }

    /** 初始化球队分区数据 */
    @CacheEvict(value = {"teams", "rankings", "dashboard"}, allEntries = true)
    @Transactional
    public int initDivisions() {
        Map<String, String> divisionMap = Map.ofEntries(
                // 东部 - 大西洋
                Map.entry("凯尔特人", "大西洋"), Map.entry("尼克斯", "大西洋"),
                Map.entry("76人", "大西洋"), Map.entry("篮网", "大西洋"), Map.entry("猛龙", "大西洋"),
                // 东部 - 中部
                Map.entry("骑士", "中部"), Map.entry("雄鹿", "中部"),
                Map.entry("步行者", "中部"), Map.entry("活塞", "中部"), Map.entry("公牛", "中部"),
                // 东部 - 东南
                Map.entry("魔术", "东南"), Map.entry("热火", "东南"),
                Map.entry("老鹰", "东南"), Map.entry("黄蜂", "东南"), Map.entry("奇才", "东南"),
                // 西部 - 西北
                Map.entry("雷霆", "西北"), Map.entry("掘金", "西北"),
                Map.entry("森林狼", "西北"), Map.entry("爵士", "西北"), Map.entry("开拓者", "西北"),
                // 西部 - 太平洋
                Map.entry("湖人", "太平洋"), Map.entry("快船", "太平洋"),
                Map.entry("太阳", "太平洋"), Map.entry("国王", "太平洋"), Map.entry("勇士", "太平洋"),
                // 西部 - 西南
                Map.entry("火箭", "西南"), Map.entry("马刺", "西南"),
                Map.entry("独行侠", "西南"), Map.entry("灰熊", "西南"), Map.entry("鹈鹕", "西南")
        );

        List<Team> all = teamRepository.findAll();
        int updated = 0;
        for (Team t : all) {
            String div = divisionMap.get(t.getName());
            if (div != null && !div.equals(t.getDivision())) {
                t.setDivision(div);
                teamRepository.save(t);
                updated++;
            }
        }
        return updated;
    }

    @Cacheable(value = "rankings", key = "'divisions'")
    @Transactional(readOnly = true)
    public Map<String, List<TeamRankDto>> getDivisionRankings() { return rankings(true); }

    private void apply(Team t, TeamRequest req) {
        t.setName(req.name());
        t.setCity(req.city());
        t.setConference(req.conference());
        if (req.division() != null) t.setDivision(req.division());
        t.setWins(req.wins());
        t.setLosses(req.losses());
        if (req.logoUrl() != null) t.setLogoUrl(req.logoUrl());
        t.markData("MANUAL", seasonService.getCurrentSeason(), "REGULAR_SEASON", "wins", "losses");
    }

    private TeamDto toDto(Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getNameEn(), t.getAbbreviation(), t.getCity(), t.getConference(), t.getDivision(), t.canDisplay("wins") ? t.getWins() : null, t.canDisplay("losses") ? t.getLosses() : null, t.getLogoUrl(), t.getDataMeta());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球队不存在: " + id);
    }
}

package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Team;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.TeamDto;
import com.nbamanager.web.dto.TeamRankDto;
import com.nbamanager.web.dto.TeamRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
public class TeamService {

    private final TeamRepository teamRepository;
    private final MatchRecordRepository matchRecordRepository;

    @Transactional(readOnly = true)
    public Page<TeamDto> list(String keyword, Pageable pageable) {
        Page<Team> page;
        if (keyword == null || keyword.isBlank()) {
            page = teamRepository.findAll(pageable);
        } else {
            String q = keyword.trim();
            page = teamRepository.findByNameContainingIgnoreCaseOrCityContainingIgnoreCase(q, q, pageable);
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

    /** 东西部分区排名（按胜率排序，含胜场差 + 主客场/得分/失分/连胜） */
    @Cacheable(value = "rankings", key = "'all'")
    @Transactional(readOnly = true)
    public Map<String, List<TeamRankDto>> getConferenceRankings() {
        List<Team> all = teamRepository.findAll();
        Map<String, List<Team>> grouped = all.stream()
                .collect(Collectors.groupingBy(Team::getConference));

        // 加载所有已完赛记录用于统计
        List<MatchRecord> finishedMatches = matchRecordRepository.findByStatus("FINISHED");

        Map<String, List<TeamRankDto>> result = new java.util.LinkedHashMap<>();
        for (String conf : List.of("东部", "西部")) {
            List<Team> confTeams = grouped.getOrDefault(conf, List.of());
            List<Team> sorted = confTeams.stream()
                    .sorted(Comparator
                            .comparingDouble((Team t) -> {
                                int total = t.getWins() + t.getLosses();
                                return total == 0 ? 0.0 : (double) t.getWins() / total;
                            }).reversed()
                            .thenComparing(Comparator.comparingInt((Team t) -> t.getWins() - t.getLosses()).reversed()))
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                result.put(conf, List.of());
                continue;
            }
            Team first = sorted.get(0);
            double firstGb = first.getWins() - first.getLosses();
            List<TeamRankDto> ranks = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                Team t = sorted.get(i);
                double gb = i == 0 ? 0.0 : (firstGb - (t.getWins() - t.getLosses())) / 2.0;

                // 从比赛记录计算详细数据
                String teamName = t.getName();
                int homeWins = 0, homeLosses = 0, awayWins = 0, awayLosses = 0;
                int totalScored = 0, totalAllowed = 0, gamesPlayed = 0;
                int currentStreak = 0; // 正数=连胜，负数=连败
                boolean streakSet = false;

                // 按日期排序比赛记录
                List<MatchRecord> teamMatches = finishedMatches.stream()
                        .filter(m -> teamName.equals(m.getHomeTeam()) || teamName.equals(m.getAwayTeam()))
                        .sorted(Comparator.comparing(MatchRecord::getMatchDate).reversed())
                        .collect(Collectors.toList());

                for (MatchRecord m : teamMatches) {
                    boolean isHome = teamName.equals(m.getHomeTeam());
                    int myScore = isHome ? m.getHomeScore() : m.getAwayScore();
                    int oppScore = isHome ? m.getAwayScore() : m.getHomeScore();
                    boolean won = myScore > oppScore;

                    totalScored += myScore;
                    totalAllowed += oppScore;
                    gamesPlayed++;

                    if (isHome) {
                        if (won) homeWins++; else homeLosses++;
                    } else {
                        if (won) awayWins++; else awayLosses++;
                    }

                    // 计算连胜/连败（从最近比赛开始）
                    if (!streakSet) {
                        currentStreak = won ? 1 : -1;
                        streakSet = true;
                    } else if (streakSet) {
                        boolean prevWon = currentStreak > 0;
                        if (won == prevWon) {
                            currentStreak = currentStreak > 0 ? currentStreak + 1 : currentStreak - 1;
                        } else {
                            break; // 连胜/连败中断
                        }
                    }
                }

                String homeRecord = homeWins + "-" + homeLosses;
                String awayRecord = awayWins + "-" + awayLosses;
                double ppg = gamesPlayed > 0 ? (double) totalScored / gamesPlayed : 0;
                double oppg = gamesPlayed > 0 ? (double) totalAllowed / gamesPlayed : 0;
                double netRating = ppg - oppg;
                String streak = currentStreak > 0 ? currentStreak + "连胜" : (currentStreak < 0 ? Math.abs(currentStreak) + "连败" : "—");

                ranks.add(new TeamRankDto(
                        teamName, t.getWins(), t.getLosses(), gb, conf,
                        homeRecord, awayRecord,
                        Math.round(ppg * 10.0) / 10.0,
                        Math.round(oppg * 10.0) / 10.0,
                        Math.round(netRating * 10.0) / 10.0,
                        streak
                ));
            }
            result.put(conf, ranks);
        }
        return result;
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

    /** 分区排名（按赛区分组） */
    @Cacheable(value = "rankings", key = "'divisions'")
    @Transactional(readOnly = true)
    public Map<String, List<TeamRankDto>> getDivisionRankings() {
        List<Team> all = teamRepository.findAll();

        // 按 division 分组
        Map<String, List<Team>> grouped = all.stream()
                .filter(t -> t.getDivision() != null && !t.getDivision().isBlank())
                .collect(Collectors.groupingBy(Team::getDivision));

        // 加载比赛记录
        List<MatchRecord> finishedMatches = matchRecordRepository.findByStatus("FINISHED");

        Map<String, List<TeamRankDto>> result = new java.util.LinkedHashMap<>();
        // 按固定顺序排列赛区
        List<String> divisionOrder = List.of("大西洋", "中部", "东南", "西北", "太平洋", "西南");
        for (String div : divisionOrder) {
            List<Team> divTeams = grouped.getOrDefault(div, List.of());
            if (divTeams.isEmpty()) continue;

            List<Team> sorted = divTeams.stream()
                    .sorted(Comparator
                            .comparingDouble((Team t) -> {
                                int total = t.getWins() + t.getLosses();
                                return total == 0 ? 0.0 : (double) t.getWins() / total;
                            }).reversed())
                    .collect(Collectors.toList());

            Team first = sorted.get(0);
            double firstGb = first.getWins() - first.getLosses();
            List<TeamRankDto> ranks = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                Team t = sorted.get(i);
                double gb = i == 0 ? 0.0 : (firstGb - (t.getWins() - t.getLosses())) / 2.0;
                String teamName = t.getName();

                // 复用比赛记录统计
                int homeWins = 0, homeLosses = 0, awayWins = 0, awayLosses = 0;
                int totalScored = 0, totalAllowed = 0, gamesPlayed = 0;
                int currentStreak = 0;
                boolean streakSet = false;

                List<MatchRecord> teamMatches = finishedMatches.stream()
                        .filter(m -> teamName.equals(m.getHomeTeam()) || teamName.equals(m.getAwayTeam()))
                        .sorted(Comparator.comparing(MatchRecord::getMatchDate).reversed())
                        .collect(Collectors.toList());

                for (MatchRecord m : teamMatches) {
                    boolean isHome = teamName.equals(m.getHomeTeam());
                    int myScore = isHome ? m.getHomeScore() : m.getAwayScore();
                    int oppScore = isHome ? m.getAwayScore() : m.getHomeScore();
                    boolean won = myScore > oppScore;

                    totalScored += myScore;
                    totalAllowed += oppScore;
                    gamesPlayed++;

                    if (isHome) { if (won) homeWins++; else homeLosses++; }
                    else { if (won) awayWins++; else awayLosses++; }

                    if (!streakSet) {
                        currentStreak = won ? 1 : -1;
                        streakSet = true;
                    } else {
                        boolean prevWon = currentStreak > 0;
                        if (won == prevWon) {
                            currentStreak = currentStreak > 0 ? currentStreak + 1 : currentStreak - 1;
                        } else {
                            break;
                        }
                    }
                }

                String homeRecord = homeWins + "-" + homeLosses;
                String awayRecord = awayWins + "-" + awayLosses;
                double ppg = gamesPlayed > 0 ? (double) totalScored / gamesPlayed : 0;
                double oppg = gamesPlayed > 0 ? (double) totalAllowed / gamesPlayed : 0;
                double netRating = ppg - oppg;
                String streak = currentStreak > 0 ? currentStreak + "连胜" : (currentStreak < 0 ? Math.abs(currentStreak) + "连败" : "—");

                ranks.add(new TeamRankDto(
                        teamName, t.getWins(), t.getLosses(), gb, t.getConference(),
                        homeRecord, awayRecord,
                        Math.round(ppg * 10.0) / 10.0,
                        Math.round(oppg * 10.0) / 10.0,
                        Math.round(netRating * 10.0) / 10.0,
                        streak
                ));
            }
            result.put(div, ranks);
        }
        return result;
    }

    private void apply(Team t, TeamRequest req) {
        t.setName(req.name());
        t.setCity(req.city());
        t.setConference(req.conference());
        if (req.division() != null) t.setDivision(req.division());
        t.setWins(req.wins());
        t.setLosses(req.losses());
        if (req.logoUrl() != null) t.setLogoUrl(req.logoUrl());
    }

    private TeamDto toDto(Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getNameEn(), t.getAbbreviation(), t.getCity(), t.getConference(), t.getDivision(), t.getWins(), t.getLosses(), t.getLogoUrl());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "球队不存在: " + id);
    }
}

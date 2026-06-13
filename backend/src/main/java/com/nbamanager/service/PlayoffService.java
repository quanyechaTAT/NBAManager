package com.nbamanager.service;

import com.nbamanager.domain.PlayoffGame;
import com.nbamanager.domain.PlayoffMatchup;
import com.nbamanager.repository.PlayoffGameRepository;
import com.nbamanager.repository.PlayoffMatchupRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayoffService {

    private final PlayoffMatchupRepository matchupRepository;
    private final PlayoffGameRepository gameRepository;

    /** 获取季后赛对阵图 */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlayoffBracket(String season) {
        List<PlayoffMatchup> matchups = matchupRepository.findBySeasonOrderByRoundAsc(season);

        Map<String, Object> result = new HashMap<>();
        result.put("season", season);

        // 按联盟和轮次分组
        List<Map<String, Object>> eastern = new ArrayList<>();
        List<Map<String, Object>> western = new ArrayList<>();
        Map<String, Object> finals = null;

        for (PlayoffMatchup m : matchups) {
            Map<String, Object> matchupMap = matchupToMap(m);

            if ("Finals".equals(m.getConference())) {
                finals = matchupMap;
            } else if ("East".equals(m.getConference())) {
                eastern.add(matchupMap);
            } else if ("West".equals(m.getConference())) {
                western.add(matchupMap);
            }
        }

        result.put("eastern", eastern);
        result.put("western", western);
        result.put("finals", finals);

        return result;
    }

    /** 获取可用赛季列表 */
    @Transactional(readOnly = true)
    public List<String> getAvailableSeasons() {
        return matchupRepository.findAll().stream()
                .map(PlayoffMatchup::getSeason)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** 获取系列赛详情（含比赛列表） */
    @Transactional(readOnly = true)
    public Map<String, Object> getMatchupDetail(Long matchupId) {
        PlayoffMatchup matchup = matchupRepository.findById(matchupId)
                .orElseThrow(() -> new RuntimeException("系列赛不存在"));

        List<PlayoffGame> games = gameRepository.findByMatchupIdOrderByGameNumberAsc(matchupId);

        Map<String, Object> result = matchupToMap(matchup);
        result.put("games", games.stream().map(this::gameToMap).collect(Collectors.toList()));

        return result;
    }

    /** 清除指定赛季的季后赛数据 */
    @Transactional
    public int clearPlayoffData(String season) {
        List<PlayoffMatchup> existing = matchupRepository.findBySeasonOrderByRoundAsc(season);
        int count = existing.size();
        matchupRepository.deleteAll(existing);
        log.info("已清除 {} 赛季的 {} 条季后赛数据", season, count);
        return count;
    }

    /**
     * 清除并重新导入季后赛数据（在同一事务中执行，确保原子性）
     * 如果导入失败，整个事务回滚，数据不会丢失
     */
    @Transactional
    public int clearAndImportPlayoffData(List<Map<String, Object>> matchups, String season) {
        // 1. 先清除旧数据
        List<PlayoffMatchup> existing = matchupRepository.findBySeasonOrderByRoundAsc(season);
        int cleared = existing.size();
        matchupRepository.deleteAll(existing);
        log.info("已清除 {} 赛季的 {} 条季后赛数据", season, cleared);

        // 2. 导入新数据
        int imported = 0;
        if (matchups != null) {
            for (Map<String, Object> m : matchups) {
                String team1 = (String) m.getOrDefault("team1Name", "");
                String team2 = (String) m.getOrDefault("team2Name", "");
                int round = (int) m.getOrDefault("round", 1);
                String conference = (String) m.getOrDefault("conference", "Unknown");

                PlayoffMatchup matchup = new PlayoffMatchup();
                matchup.setSeason(season);
                matchup.setConference(conference);
                matchup.setRound(round);
                matchup.setTeam1Name(team1);
                matchup.setTeam1NameEn((String) m.getOrDefault("team1NameEn", ""));
                matchup.setTeam2Name(team2);
                matchup.setTeam2NameEn((String) m.getOrDefault("team2NameEn", ""));
                matchup.setTeam1Wins((int) m.getOrDefault("team1Wins", 0));
                matchup.setTeam2Wins((int) m.getOrDefault("team2Wins", 0));
                matchup.setStatus((String) m.getOrDefault("status", "SCHEDULED"));
                matchup.setWinnerName((String) m.getOrDefault("winner", null));
                matchupRepository.save(matchup);
                imported++;
            }
        }

        log.info("季后赛数据导入完成: 清除{}组, 新增{}组", cleared, imported);
        return imported;
    }

    /** 导入季后赛数据（支持更新已有记录） */
    @Transactional
    public int importPlayoffData(List<Map<String, Object>> matchups, List<Map<String, Object>> games, String season) {
        int imported = 0;
        int updated = 0;

        if (matchups != null) {
            for (Map<String, Object> m : matchups) {
                String team1 = (String) m.getOrDefault("team1Name", "");
                String team2 = (String) m.getOrDefault("team2Name", "");
                int round = (int) m.getOrDefault("round", 1);
                String conference = (String) m.getOrDefault("conference", "Unknown");

                // 查找已有记录
                PlayoffMatchup existing = matchupRepository.findBySeasonAndRoundOrderByTeam1SeedAsc(season, round)
                        .stream()
                        .filter(pm -> pm.getTeam1Name().equals(team1) && pm.getTeam2Name().equals(team2))
                        .findFirst()
                        .orElse(null);

                if (existing == null) {
                    // 新增
                    PlayoffMatchup matchup = new PlayoffMatchup();
                    matchup.setSeason(season);
                    matchup.setConference(conference);
                    matchup.setRound(round);
                    matchup.setTeam1Name(team1);
                    matchup.setTeam1NameEn((String) m.getOrDefault("team1NameEn", ""));
                    matchup.setTeam2Name(team2);
                    matchup.setTeam2NameEn((String) m.getOrDefault("team2NameEn", ""));
                    matchup.setTeam1Wins((int) m.getOrDefault("team1Wins", 0));
                    matchup.setTeam2Wins((int) m.getOrDefault("team2Wins", 0));
                    matchup.setStatus((String) m.getOrDefault("status", "SCHEDULED"));
                    matchup.setWinnerName((String) m.getOrDefault("winner", null));
                    matchupRepository.save(matchup);
                    imported++;
                } else {
                    // 更新已有记录的比分和状态
                    int newW1 = (int) m.getOrDefault("team1Wins", 0);
                    int newW2 = (int) m.getOrDefault("team2Wins", 0);
                    String newStatus = (String) m.getOrDefault("status", "SCHEDULED");
                    String newWinner = (String) m.getOrDefault("winner", null);

                    boolean changed = !existing.getTeam1Wins().equals(newW1)
                            || !existing.getTeam2Wins().equals(newW2)
                            || !existing.getStatus().equals(newStatus)
                            || (existing.getWinnerName() == null ? newWinner != null : !existing.getWinnerName().equals(newWinner));

                    if (changed) {
                        existing.setTeam1Wins(newW1);
                        existing.setTeam2Wins(newW2);
                        existing.setStatus(newStatus);
                        existing.setWinnerName(newWinner);
                        matchupRepository.save(existing);
                        updated++;
                    }
                }
            }
        }

        log.info("季后赛数据导入完成: 新增{}组, 更新{}组", imported, updated);
        return imported + updated;
    }

    private Map<String, Object> matchupToMap(PlayoffMatchup m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("season", m.getSeason());
        map.put("conference", m.getConference());
        map.put("round", m.getRound());
        map.put("seriesId", m.getSeriesId());
        map.put("team1Name", m.getTeam1Name());
        map.put("team1NameEn", m.getTeam1NameEn());
        map.put("team1Seed", m.getTeam1Seed());
        map.put("team1Wins", m.getTeam1Wins());
        map.put("team2Name", m.getTeam2Name());
        map.put("team2NameEn", m.getTeam2NameEn());
        map.put("team2Seed", m.getTeam2Seed());
        map.put("team2Wins", m.getTeam2Wins());
        map.put("status", m.getStatus());
        map.put("winnerName", m.getWinnerName());
        return map;
    }

    private Map<String, Object> gameToMap(PlayoffGame g) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", g.getId());
        map.put("gameNumber", g.getGameNumber());
        map.put("homeTeam", g.getHomeTeam());
        map.put("awayTeam", g.getAwayTeam());
        map.put("homeScore", g.getHomeScore());
        map.put("awayScore", g.getAwayScore());
        map.put("gameDate", g.getGameDate() != null ? g.getGameDate().toString() : null);
        map.put("status", g.getStatus());
        map.put("nbaGameId", g.getNbaGameId());
        return map;
    }
}

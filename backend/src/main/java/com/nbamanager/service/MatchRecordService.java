package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.dto.MatchRecordDto;
import com.nbamanager.web.dto.MatchRecordRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchRecordService {

    private final MatchRecordRepository matchRecordRepository;

    /** 查询某支球队的最近比赛记录（最多25场，3个月内） */
    @Cacheable(value = "matchRecordsByTeam", key = "#teamName")
    public List<MatchRecordDto> getByTeam(String teamName) {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        return matchRecordRepository.findByTeamName(teamName).stream()
                .filter(m -> isValidMatch(m))
                .filter(m -> m.getMatchDate() != null && m.getMatchDate().isAfter(threeMonthsAgo))
                .sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
                .limit(25)
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** 查询两队交锋记录（最近3个月内） */
    @Cacheable(value = "headToHead", key = "#team1 + ':' + #team2")
    public List<MatchRecordDto> getHeadToHead(String team1, String team2) {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        return matchRecordRepository.findHeadToHead(team1, team2).stream()
                .filter(m -> isValidMatch(m))
                .filter(m -> m.getMatchDate() != null && m.getMatchDate().isAfter(threeMonthsAgo))
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** 判断比赛记录是否有效：已完成且双方比分都大于0 */
    private boolean isValidMatch(MatchRecord m) {
        return "FINISHED".equals(m.getStatus())
                && m.getHomeScore() != null && m.getHomeScore() > 0
                && m.getAwayScore() != null && m.getAwayScore() > 0;
    }

    /** 查询所有比赛记录 */
    @Cacheable(value = "matchRecords", key = "'all'")
    public List<MatchRecordDto> getAll() {
        return matchRecordRepository.findAllByOrderByMatchDateDesc().stream()
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** 新增比赛记录 */
    @CacheEvict(value = {"matchRecords", "matchRecordsByTeam", "headToHead"}, allEntries = true)
    public MatchRecordDto create(MatchRecordRequest req) {
        MatchRecord m = new MatchRecord();
        m.setHomeTeam(req.homeTeam());
        m.setAwayTeam(req.awayTeam());
        m.setHomeScore(req.homeScore());
        m.setAwayScore(req.awayScore());
        m.setMatchDate(req.matchDate());
        m.setSeason(req.season());
        m.setStatus(req.status());
        return MatchRecordDto.from(matchRecordRepository.save(m));
    }

    /** 删除比赛记录 */
    @CacheEvict(value = {"matchRecords", "matchRecordsByTeam", "headToHead"}, allEntries = true)
    public void delete(Long id) {
        matchRecordRepository.deleteById(id);
    }

    /** 清除3个月前的旧数据 */
    @CacheEvict(value = {"matchRecords", "matchRecordsByTeam", "headToHead"}, allEntries = true)
    public int cleanOldData() {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<MatchRecord> oldRecords = matchRecordRepository.findAll().stream()
                .filter(m -> m.getMatchDate() != null && m.getMatchDate().isBefore(threeMonthsAgo))
                .collect(Collectors.toList());
        matchRecordRepository.deleteAll(oldRecords);
        return oldRecords.size();
    }

    /** 清除无效数据（无比分或状态异常） */
    @CacheEvict(value = {"matchRecords", "matchRecordsByTeam", "headToHead"}, allEntries = true)
    public int cleanInvalidData() {
        List<MatchRecord> invalidRecords = matchRecordRepository.findAll().stream()
                .filter(m -> !isValidMatch(m))
                .collect(Collectors.toList());
        matchRecordRepository.deleteAll(invalidRecords);
        return invalidRecords.size();
    }
}

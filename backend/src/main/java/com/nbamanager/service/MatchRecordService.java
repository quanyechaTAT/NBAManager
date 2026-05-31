package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.dto.MatchRecordDto;
import com.nbamanager.web.dto.MatchRecordRequest;
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

    /** 查询某支球队的所有比赛记录 */
    @Cacheable(value = "matchRecordsByTeam", key = "#teamName")
    public List<MatchRecordDto> getByTeam(String teamName) {
        return matchRecordRepository.findByTeamName(teamName).stream()
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** 查询两队交锋记录 */
    @Cacheable(value = "headToHead", key = "#team1 + ':' + #team2")
    public List<MatchRecordDto> getHeadToHead(String team1, String team2) {
        return matchRecordRepository.findHeadToHead(team1, team2).stream()
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
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
}

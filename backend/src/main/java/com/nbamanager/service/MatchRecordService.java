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

    public MatchRecordDto getById(Long id) {
        return matchRecordRepository.findById(id).map(MatchRecordDto::from).orElse(null);
    }

    @Cacheable(value = "matchRecord", key = "@matchCacheGeneration.key(#gameId)", sync = true)
    public MatchRecordDto getByGameId(String gameId) {
        var match = matchRecordRepository.findByNbaGameId(gameId);
        // Older links can identify a manually recorded game by its database ID.
        if (match.isEmpty() && gameId.matches("[0-9]{1,16}")) {
            match = matchRecordRepository.findById(Long.parseLong(gameId));
        }
        return match.map(MatchRecordDto::from).orElse(null);
    }

    /** Return the recorded range; callers can apply season and competition filters without truncated samples. */
    @Cacheable(value = "matchRecordsByTeam", key = "@matchCacheGeneration.key(#teamName)", sync = true)
    public List<MatchRecordDto> getByTeam(String teamName) {
        return matchRecordRepository.findByTeamName(teamName).stream()
                .filter(m -> m.getMatchDate() != null)
                .sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** Completed, verified head-to-head records in the full recorded range. */
    @Cacheable(value = "headToHead", key = "@matchCacheGeneration.key(#team1 + ':' + #team2)", sync = true)
    public List<MatchRecordDto> getHeadToHead(String team1, String team2) {
        return matchRecordRepository.findHeadToHead(team1, team2).stream()
                .filter(m -> isValidMatch(m))
                .filter(m -> m.getMatchDate() != null)
                .map(MatchRecordDto::from)
                .collect(Collectors.toList());
    }

    /** Zero is observed data; unknown or unpublished scores cannot determine a result. */
    private boolean isValidMatch(MatchRecord m) {
        return "FINISHED".equals(m.getStatus())
                && m.getHomeScore() != null && m.getHomeScore() >= 0
                && m.getAwayScore() != null && m.getAwayScore() >= 0
                && m.canDisplay("homeScore") && m.canDisplay("awayScore");
    }

    /** 查询所有比赛记录 */
    @Cacheable(value = "matchRecords", key = "@matchCacheGeneration.key('all')", sync = true)
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
        m.setGameType(MatchGameTypeResolver.resolve(
                req.gameType(), null, req.matchDate()));
        m.markData("MANUAL", req.season(), m.getGameType(), "homeScore", "awayScore", "status");
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
                .filter(m -> m.getMatchDate() == null || m.getHomeTeam() == null || m.getAwayTeam() == null
                        || (m.getHomeScore() != null && m.getHomeScore() < 0) || (m.getAwayScore() != null && m.getAwayScore() < 0))
                .collect(Collectors.toList());
        matchRecordRepository.deleteAll(invalidRecords);
        return invalidRecords.size();
    }
}

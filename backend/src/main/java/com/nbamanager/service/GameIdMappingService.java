package com.nbamanager.service;

import com.nbamanager.domain.GameIdMapping;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.GameIdMappingRepository;
import com.nbamanager.repository.MatchRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameIdMappingService {

    private final GameIdMappingRepository gameIdMappingRepository;
    private final MatchRecordRepository matchRecordRepository;

    /**
     * 获取NBA格式的gameId
     * 如果输入的是ESPN格式，转换为NBA格式
     * 如果输入的已经是NBA格式，直接返回
     */
    public String getNbaGameId(String gameId) {
        if (gameId == null || gameId.isEmpty()) {
            return gameId;
        }

        // NBA API格式：0042500404（10位数字，以00开头）
        if (gameId.startsWith("00") && gameId.length() == 10) {
            return gameId;
        }

        // ESPN格式：401859967（9位数字，以401开头）
        if (gameId.startsWith("401")) {
            // 先查缓存
            Optional<GameIdMapping> mapping = gameIdMappingRepository.findByEspnGameId(gameId);
            if (mapping.isPresent()) {
                String nbaId = mapping.get().getNbaGameId();
                log.info("从缓存转换ESPN gameId {} 为NBA gameId {}", gameId, nbaId);
                return nbaId;
            }

            // 缓存中没有，尝试自动匹配
            String nbaId = tryAutoMatch(gameId);
            if (nbaId != null) {
                return nbaId;
            }

            log.warn("未找到ESPN gameId {} 的映射", gameId);
        }

        return gameId;
    }

    /**
     * 尝试自动匹配ESPN gameId和NBA gameId
     * 通过比赛日期和球队名称来匹配
     */
    private String tryAutoMatch(String espnGameId) {
        // 从新闻数据中获取比赛信息
        // 这里需要调用GameNewsRepository来获取新闻中的比赛信息
        // 然后在MatchRecord中查找匹配的比赛

        // 暂时返回null，需要后续实现
        return null;
    }

    /**
     * 根据球队和日期查找匹配的比赛记录
     */
    public MatchRecord findMatchByTeamsAndDate(String homeTeam, String awayTeam, LocalDate matchDate) {
        // 先尝试精确匹配
        Optional<MatchRecord> exactMatch = matchRecordRepository
                .findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, matchDate);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        // 精确匹配失败，查找两队之间的比赛，找日期最接近的
        List<MatchRecord> matches = matchRecordRepository.findHeadToHead(homeTeam, awayTeam);

        return matches.stream()
                .filter(m -> m.getMatchDate() != null)
                .min((a, b) -> {
                    long diffA = Math.abs(a.getMatchDate().toEpochDay() - matchDate.toEpochDay());
                    long diffB = Math.abs(b.getMatchDate().toEpochDay() - matchDate.toEpochDay());
                    return Long.compare(diffA, diffB);
                })
                .orElse(null);
    }

    /**
     * 获取ESPN格式的gameId
     */
    public String getEspnGameId(String nbaGameId) {
        if (nbaGameId == null || nbaGameId.isEmpty()) {
            return nbaGameId;
        }

        Optional<GameIdMapping> mapping = gameIdMappingRepository.findByNbaGameId(nbaGameId);
        if (mapping.isPresent()) {
            return mapping.get().getEspnGameId();
        }

        return nbaGameId;
    }

    /**
     * 添加gameId映射
     */
    public void addMapping(String espnGameId, String nbaGameId, String homeTeam, String awayTeam, LocalDate matchDate) {
        Optional<GameIdMapping> existing = gameIdMappingRepository.findByEspnGameId(espnGameId);
        if (existing.isEmpty()) {
            GameIdMapping mapping = new GameIdMapping();
            mapping.setEspnGameId(espnGameId);
            mapping.setNbaGameId(nbaGameId);
            mapping.setHomeTeam(homeTeam);
            mapping.setAwayTeam(awayTeam);
            mapping.setMatchDate(matchDate);
            gameIdMappingRepository.save(mapping);
            log.info("添加gameId映射: ESPN {} -> NBA {}, {} vs {}, {}",
                    espnGameId, nbaGameId, homeTeam, awayTeam, matchDate);
        }
    }

    /**
     * 批量同步gameId映射
     * 从新闻和比赛记录中自动建立映射关系
     */
    public int syncMappings() {
        // 这个方法可以定期调用，自动建立映射关系
        // 实现逻辑：
        // 1. 查询所有有ESPN gameId但没有NBA gameId的新闻
        // 2. 根据主队、客队、日期在比赛记录中查找匹配
        // 3. 如果找到，建立映射关系

        log.info("开始同步gameId映射...");
        // TODO: 实现自动同步逻辑
        return 0;
    }
}

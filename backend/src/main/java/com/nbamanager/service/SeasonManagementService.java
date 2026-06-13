package com.nbamanager.service;

import com.nbamanager.domain.SeasonConfig;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.SeasonConfigRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonManagementService {

    private final SeasonConfigRepository seasonConfigRepo;
    private final PlayerSeasonStatsRepository playerStatsRepo;
    private final TeamSeasonStatsRepository teamStatsRepo;

    private static final int MAX_SEASONS = 5;

    /**
     * 获取当前赛季
     */
    public String getCurrentSeason() {
        SeasonConfig current = seasonConfigRepo.findByIsCurrentTrue();
        return current != null ? current.getSeason() : calculateCurrentSeason();
    }

    /**
     * 获取所有已同步的赛季（按时间倒序）
     */
    public List<String> getSyncedSeasons() {
        return seasonConfigRepo.findAllByOrderBySeasonDesc()
                .stream()
                .filter(config -> "COMPLETED".equals(config.getSyncStatus()))
                .map(SeasonConfig::getSeason)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有赛季配置
     */
    public List<SeasonConfig> getAllSeasonConfigs() {
        return seasonConfigRepo.findAllByOrderBySeasonDesc();
    }

    /**
     * 检查是否需要轮转赛季
     */
    @Transactional
    public boolean checkAndRotateSeasons() {
        String newSeason = calculateCurrentSeason();
        String currentSeason = getCurrentSeason();

        if (!newSeason.equals(currentSeason)) {
            log.info("检测到新赛季: {} -> {}", currentSeason, newSeason);
            rotateToNewSeason(newSeason);
            return true;
        }
        return false;
    }

    /**
     * 轮转到新赛季
     */
    @Transactional
    protected void rotateToNewSeason(String newSeason) {
        // 1. 将当前赛季设为非当前
        SeasonConfig currentConfig = seasonConfigRepo.findByIsCurrentTrue();
        if (currentConfig != null) {
            currentConfig.setIsCurrent(false);
            seasonConfigRepo.save(currentConfig);
        }

        // 2. 创建或更新新赛季配置
        SeasonConfig newConfig = seasonConfigRepo.findBySeason(newSeason)
                .orElse(new SeasonConfig());
        newConfig.setSeason(newSeason);
        newConfig.setIsCurrent(true);
        newConfig.setSyncStatus("PENDING");
        newConfig.setLastSyncTime(null);
        newConfig.setPlayerCount(0);
        newConfig.setTeamCount(0);
        seasonConfigRepo.save(newConfig);

        // 3. 清理超出5个赛季的旧数据
        cleanupOldSeasons();

        log.info("赛季轮转完成，当前赛季: {}", newSeason);
    }

    /**
     * 清理超出保留数量的旧赛季数据
     */
    @Transactional
    public int cleanupOldSeasons() {
        List<String> allSeasons = seasonConfigRepo.findAllByOrderBySeasonDesc()
                .stream()
                .map(SeasonConfig::getSeason)
                .collect(Collectors.toList());

        int deletedCount = 0;

        if (allSeasons.size() > MAX_SEASONS) {
            List<String> seasonsToDelete = allSeasons.subList(MAX_SEASONS, allSeasons.size());

            for (String season : seasonsToDelete) {
                log.info("删除旧赛季数据: {}", season);

                // 删除球员统计
                int playersDeleted = playerStatsRepo.deleteBySeason(season);

                // 删除球队统计
                int teamsDeleted = teamStatsRepo.deleteBySeason(season);

                // 删除赛季配置
                seasonConfigRepo.deleteBySeason(season);

                deletedCount++;
                log.info("已删除 {} 赛季: {} 名球员, {} 支球队",
                        season, playersDeleted, teamsDeleted);
            }
        }

        return deletedCount;
    }

    /**
     * 更新赛季同步状态
     */
    @Transactional
    public void updateSyncStatus(String season, String status, int playerCount, int teamCount) {
        SeasonConfig config = seasonConfigRepo.findBySeason(season)
                .orElse(new SeasonConfig());
        config.setSeason(season);
        config.setSyncStatus(status);
        config.setLastSyncTime(LocalDateTime.now());
        config.setPlayerCount(playerCount);
        config.setTeamCount(teamCount);
        seasonConfigRepo.save(config);
    }

    /**
     * 计算当前赛季
     * 10月前为上赛季，10月后为本赛季
     */
    private String calculateCurrentSeason() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        if (month >= 10) {
            return year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        }
    }
}

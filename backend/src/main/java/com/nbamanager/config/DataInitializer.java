package com.nbamanager.config;

import com.nbamanager.service.HistoricalDataSyncService;
import com.nbamanager.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DataInitializer implements CommandLineRunner {

    private final TeamService teamService;
    private final HistoricalDataSyncService historicalSyncService;

    @Override
    public void run(String... args) {
        // 1. 初始化球队分区数据
        try {
            int updated = teamService.initDivisions();
            if (updated > 0) {
                log.info("已初始化 {} 支球队的分区数据", updated);
            }
        } catch (Exception e) {
            log.warn("初始化分区数据失败: {}", e.getMessage());
        }

        // 2. 异步同步缺失的历史赛季数据
        try {
            new Thread(() -> {
                try {
                    log.info("开始检查并同步缺失的历史赛季数据...");
                    historicalSyncService.syncMissingSeasons();
                    log.info("历史赛季数据同步检查完成");
                } catch (Exception e) {
                    log.warn("同步历史赛季数据失败: {}", e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            log.warn("启动历史赛季同步失败: {}", e.getMessage());
        }
    }
}

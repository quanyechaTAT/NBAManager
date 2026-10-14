package com.nbamanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncScheduler {

    private final SyncCoordinator coordinator;

    @Scheduled(cron = "${app.sync.cron.full-sync:0 0 3 * * ?}", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncFull() {
        submitQuietly(() -> coordinator.submitFullSync("SCHEDULED", "scheduler"), "FULL_SYNC");
    }

    @Scheduled(fixedRateString = "${app.sync.interval.today-games-ms:300000}")
    public void syncTodayGames() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("TODAY_GAMES")), "TODAY_GAMES");
    }

    @Scheduled(fixedRateString = "${app.sync.interval.news-ms:3600000}")
    public void syncNews() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("NEWS")), "NEWS");
    }

    @Scheduled(cron = "0 0 */2 * 4-6 ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncPlayoff() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("PLAYOFF")), "PLAYOFF");
    }

    @Scheduled(cron = "0 */30 * 15-30 6 ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftJune() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("DRAFT")), "DRAFT");
    }

    @Scheduled(cron = "0 */30 * 1-10 7 ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftJuly() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("DRAFT")), "DRAFT");
    }

    @Scheduled(cron = "0 20 4 * * ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncDraftDaily() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("DRAFT")), "DRAFT");
    }

    @Scheduled(cron = "0 0 4 * * ?", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncHistorical() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("HISTORICAL")), "HISTORICAL");
    }

    @Scheduled(cron = "0 10 * * * *", zone = "${app.sync.zone:Asia/Shanghai}")
    public void syncTranslation() {
        submitQuietly(() -> coordinator.submit(SyncCommand.scheduled("TRANSLATION")), "TRANSLATION");
    }

    private void submitQuietly(Runnable submitter, String module) {
        try {
            submitter.run();
        } catch (com.nbamanager.exception.SyncJobConflictException e) {
            // Deduplication or conflict - log and skip
            log.debug("调度提交被跳过: module={}, reason={}", module, e.getMessage());
        } catch (Exception e) {
            log.warn("调度提交异常: module={}", module, e);
        }
    }
}

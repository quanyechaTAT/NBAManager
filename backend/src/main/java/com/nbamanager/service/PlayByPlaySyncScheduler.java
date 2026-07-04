package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.repository.MatchRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlaySyncScheduler {

    private final PlayByPlayCollectorService collectorService;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayByPlaySyncLogService syncLogService;

    /**
     * Sync finished games: runs every 30 minutes
     * Queries MatchRecord with status="FINISHED" and matchDate after yesterday
     * For each game with nbaGameId that doesn't have successful sync, call collector
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void syncFinishedGames() {
        log.info("Starting scheduled sync for finished games");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<MatchRecord> recentGames = matchRecordRepository.findByStatusAndMatchDateAfter("FINISHED", yesterday);

        int syncedCount = 0;
        for (MatchRecord game : recentGames) {
            String gameId = game.getNbaGameId();
            if (gameId != null && !gameId.isEmpty() && !syncLogService.hasSuccessfulSync(gameId)) {
                try {
                    log.info("Syncing finished game: {}", gameId);
                    collectorService.collect(gameId);
                    syncedCount++;
                } catch (Exception e) {
                    log.error("Failed to sync finished game {}: {}", gameId, e.getMessage(), e);
                }
            }
        }
        log.info("Finished games sync completed. Synced {} games out of {} total", syncedCount, recentGames.size());
    }

    /**
     * Retry failed syncs: runs daily at 3:00 AM
     * Gets failed logs for retry from syncLogService
     * For each, call collector.collect(gameId)
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void retryFailedSyncs() {
        log.info("Starting scheduled retry for failed syncs");
        List<PlayByPlaySyncLog> failedLogs = syncLogService.getFailedLogsForRetry();

        int retriedCount = 0;
        for (PlayByPlaySyncLog failedLog : failedLogs) {
            String gameId = failedLog.getGameId();
            try {
                log.info("Retrying failed sync for game: {} (attempt {})", gameId, failedLog.getAttemptCount() + 1);
                collectorService.collect(gameId);
                retriedCount++;
            } catch (Exception e) {
                log.error("Failed to retry sync for game {}: {}", gameId, e.getMessage(), e);
            }
        }
        log.info("Failed syncs retry completed. Retried {} games out of {} total", retriedCount, failedLogs.size());
    }

    /**
     * Recalibrate recent games: runs every 2 hours
     * Gets recent successful logs from last 2 days
     * For each, call collector.collect(gameId) to recalibrate
     */
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void recalibrateRecentGames() {
        log.info("Starting scheduled recalibration for recent games");
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        List<PlayByPlaySyncLog> recentSuccessLogs = syncLogService.getRecentSuccessfulLogs(twoDaysAgo);

        int recalibratedCount = 0;
        for (PlayByPlaySyncLog successLog : recentSuccessLogs) {
            String gameId = successLog.getGameId();
            try {
                log.info("Recalibrating game: {}", gameId);
                collectorService.collect(gameId);
                recalibratedCount++;
            } catch (Exception e) {
                log.error("Failed to recalibrate game {}: {}", gameId, e.getMessage(), e);
            }
        }
        log.info("Recent games recalibration completed. Recalibrated {} games out of {} total", recalibratedCount, recentSuccessLogs.size());
    }
}

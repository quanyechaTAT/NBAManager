package com.nbamanager.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.repository.MatchRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayByPlaySyncSchedulerTest {

    private static final String GAME_ID = "0042500405";

    @Mock private ReplayPreparationService replayPreparationService;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private PlayByPlaySyncLogService syncLogService;

    @Test
    void finishedGameSyncDelegatesToReplayPreparationWithoutLegacySuccessCache() {
        MatchRecord match = new MatchRecord();
        match.setNbaGameId(GAME_ID);
        when(matchRecordRepository.findByStatusAndMatchDateAfter(
                org.mockito.ArgumentMatchers.eq("FINISHED"),
                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of(match));
        PlayByPlaySyncScheduler scheduler = scheduler();

        scheduler.syncFinishedGames();

        verify(replayPreparationService).prepare(GAME_ID);
        verifyNoInteractions(syncLogService);
    }

    @Test
    void failedSyncRetryDelegatesToReplayPreparation() {
        PlayByPlaySyncLog failed = log("FAILED");
        when(syncLogService.getFailedLogsForRetry()).thenReturn(List.of(failed));
        PlayByPlaySyncScheduler scheduler = scheduler();

        scheduler.retryFailedSyncs();

        verify(replayPreparationService).prepare(GAME_ID);
    }

    @Test
    void recentRecalibrationDelegatesToReplayPreparation() {
        PlayByPlaySyncLog success = log("SUCCESS");
        when(syncLogService.getRecentSuccessfulLogs(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(success));
        PlayByPlaySyncScheduler scheduler = scheduler();

        scheduler.recalibrateRecentGames();

        verify(replayPreparationService).prepare(GAME_ID);
    }

    private PlayByPlaySyncScheduler scheduler() {
        return new PlayByPlaySyncScheduler(
                replayPreparationService,
                matchRecordRepository,
                syncLogService);
    }

    private PlayByPlaySyncLog log(String status) {
        PlayByPlaySyncLog log = new PlayByPlaySyncLog();
        log.setGameId(GAME_ID);
        log.setSource("NBA_PLAY_BY_PLAY");
        log.setStatus(status);
        return log;
    }
}

package com.nbamanager.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSyncSchedulerTest {

    @Mock private SyncCoordinator coordinator;
    @Mock private com.nbamanager.service.NbaLiveSyncService liveSyncService;

    @InjectMocks
    private DataSyncScheduler scheduler;

    @Test
    void newsScheduleSubmitsScheduledCommand() {
        scheduler.syncNews();
        verify(coordinator).submit(SyncCommand.scheduled("NEWS"));
        verifyNoInteractions(liveSyncService);
    }

    @Test
    void todayGamesScheduleSubmitsScheduledCommand() {
        scheduler.syncTodayGames();
        verify(coordinator).submit(SyncCommand.scheduled("TODAY_GAMES"));
    }

    @Test
    void playoffScheduleSubmitsScheduledCommand() {
        scheduler.syncPlayoff();
        verify(coordinator).submit(SyncCommand.scheduled("PLAYOFF"));
    }

    @Test
    void draftScheduleSubmitsScheduledCommand() {
        scheduler.syncDraftJune();
        verify(coordinator).submit(SyncCommand.scheduled("DRAFT"));
    }

    @Test
    void historicalScheduleSubmitsScheduledCommand() {
        scheduler.syncHistorical();
        verify(coordinator).submit(SyncCommand.scheduled("HISTORICAL"));
    }

    @Test
    void translationScheduleSubmitsScheduledCommand() {
        scheduler.syncTranslation();
        verify(coordinator).submit(SyncCommand.scheduled("TRANSLATION"));
    }

    @Test
    void fullSyncScheduleSubmitsFullSyncCommand() {
        scheduler.syncFull();
        verify(coordinator).submitFullSync("SCHEDULED", "scheduler");
    }
}

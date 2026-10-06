package com.nbamanager.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.util.function.BiConsumer;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModuleSyncWorkerProgressTest {

    @Mock private SyncJobRepository syncJobRepository;
    @Mock private SyncJobStateService jobStateService;

    @Test
    void reportsLocalStepProgressSeparatelyAndClearsTheListenerBeforeAnotherJob() {
        ModuleSyncWorker worker = new ModuleSyncWorker(syncJobRepository, jobStateService) {
            @Override protected SyncResult doSync(Long jobId) {
                updateProgress(jobId, 50, "FETCHING");
                return SyncResult.success("TEST", 0, 0, 0, 0, null, "test");
            }
        };
        @SuppressWarnings("unchecked") BiConsumer<Integer, String> listener = mock(BiConsumer.class);
        worker.sync(7L, 20, 40, listener);
        verify(jobStateService).updateProgress(7L, 30, "FETCHING");
        verify(listener).accept(50, "FETCHING");
        worker.sync(8L);
        verify(jobStateService).updateProgress(8L, 50, "FETCHING");
        verifyNoMoreInteractions(listener);
    }

    @Test
    void mapsModuleProgressIntoFullSyncRange() {
        ModuleSyncWorker worker = new ModuleSyncWorker(syncJobRepository, jobStateService) {
            @Override
            protected SyncResult doSync(Long jobId) {
                updateProgress(jobId, 50);
                return SyncResult.success("TEST", 0, 0, 0, 0, LocalDateTime.now(), "test");
            }
        };

        worker.sync(7L, 20, 40);

        verify(jobStateService).updateProgress(7L, 30);
    }
}

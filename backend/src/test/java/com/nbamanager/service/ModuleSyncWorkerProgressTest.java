package com.nbamanager.service;

import static org.mockito.Mockito.verify;

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

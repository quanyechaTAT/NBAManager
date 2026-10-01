package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.web.dto.SyncResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncJobStateServiceCancellationTest {

    @Mock private SyncJobRepository syncJobRepository;
    @InjectMocks private SyncJobStateService stateService;

    @Test
    void completeCancelledJobPreservesActualProgress() {
        SyncJob job = runningJobAt(42);
        job.setCancelRequested(true);
        when(syncJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        stateService.complete(1L, SyncResult.failed("MATCHES", "CANCELLED", "任务已取消"));

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
        assertThat(job.getProgress()).isEqualTo(42);
    }

    @Test
    void markCancelledPreservesActualProgress() {
        SyncJob job = runningJobAt(37);
        when(syncJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        stateService.markCancelled(1L);

        assertThat(job.getStatus()).isEqualTo("CANCELLED");
        assertThat(job.getProgress()).isEqualTo(37);
    }

    @Test
    void markLockLostAtomicallyRequestsCancellationAndFailsJob() {
        SyncJob job = runningJobAt(31);
        when(syncJobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        stateService.markLockLost(1L, "lock expired");

        assertThat(job.isCancelRequested()).isTrue();
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorCode()).isEqualTo("LOCK_LOST");
        assertThat(job.getProgress()).isEqualTo(31);
    }

    private SyncJob runningJobAt(int progress) {
        SyncJob job = new SyncJob();
        job.setId(1L);
        job.setStatus("RUNNING");
        job.setProgress(progress);
        return job;
    }
}

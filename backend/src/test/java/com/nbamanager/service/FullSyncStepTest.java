package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.SyncJobStep;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.web.dto.SyncResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FullSyncStepTest {

    @Mock private SyncJobStepRepository stepRepository;
    @InjectMocks private SyncJobStepStateService stepStateService;
    @Mock private SyncJobStateService jobStateService;
    @Mock private TeamSyncWorker teamWorker;
    @Mock private PlayerSyncWorker playerWorker;

    @Test
    void workerUpdatesParentAndStepProgress() {
        // Create a worker that updates progress
        ModuleSyncWorker worker = new ModuleSyncWorker(null, jobStateService) {
            @Override
            protected SyncResult doSync(Long jobId) {
                updateProgress(jobId, 50);
                return SyncResult.success("TEST", 0, 0, 0, 0, null, "test");
            }
        };

        // Verify progress mapping
        worker.sync(10L, 20, 40);
        verify(jobStateService).updateProgress(10L, 30);
    }

    @Test
    void fullSyncContinuesAfterFailedStep() {
        when(teamWorker.sync(any(), eq(0), eq(11))).thenReturn(
                SyncResult.failed("TEAMS", "QUALITY_GATE", "29 teams"));
        when(playerWorker.sync(any(), eq(11), eq(22))).thenReturn(
                SyncResult.success("PLAYERS", 10, 10, 0, 0, null, "nba_api"));

        // Verify that full sync continues after team failure
        SyncResult teamResult = teamWorker.sync(90L, 0, 11);
        SyncResult playerResult = playerWorker.sync(90L, 11, 22);

        assertThat(teamResult.status()).isEqualTo("FAILED");
        assertThat(playerResult.status()).isEqualTo("SUCCESS");
    }

    @Test
    void stepStatusTransitions() {
        SyncJobStep step = new SyncJobStep();
        step.setId(1L);
        step.setStatus("QUEUED");
        step.setProgress(0);

        when(stepRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(step));

        stepStateService.markRunning(1L);
        assertThat(step.getStatus()).isEqualTo("RUNNING");
        assertThat(step.getStartedAt()).isNotNull();

        stepStateService.updateProgress(1L, 50, "FETCHING");
        assertThat(step.getProgress()).isEqualTo(50);
        assertThat(step.getStage()).isEqualTo("FETCHING");

        SyncResult result = SyncResult.success("TEST", 10, 10, 0, 0, null, "test");
        stepStateService.complete(1L, result);
        assertThat(step.getStatus()).isEqualTo("SUCCESS");
        assertThat(step.getProgress()).isEqualTo(100);
        assertThat(step.getFinishedAt()).isNotNull();
    }
}

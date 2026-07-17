package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.SyncJobStep;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncJobStepStateServiceTest {

    @Mock private SyncJobStepRepository repository;
    @InjectMocks private SyncJobStepStateService service;

    @Test
    void updateProgress_isMonotonicAndDoesNotChangeTerminalStep() {
        SyncJobStep running = step("RUNNING", 30);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(running));
        service.updateProgress(7L, 20, "FETCHING");
        assertThat(running.getProgress()).isEqualTo(30);

        running.setStatus("FAILED");
        service.updateProgress(7L, 80, "PERSISTING");
        assertThat(running.getProgress()).isEqualTo(30);
        verify(repository, never()).save(running);
    }

    @Test
    void complete_preservesModuleResult() {
        SyncJobStep running = step("RUNNING", 60);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(running));
        service.complete(7L, SyncResult.failed("NEWS", "PROVIDER_FAILED", "all providers failed"));
        assertThat(running.getStatus()).isEqualTo("FAILED");
        assertThat(running.getErrorCode()).isEqualTo("PROVIDER_FAILED");
    }

    private SyncJobStep step(String status, int progress) {
        SyncJobStep s = new SyncJobStep();
        s.setId(7L);
        s.setJobId(10L);
        s.setModule("NEWS");
        s.setStepOrder(4);
        s.setStatus(status);
        s.setProgress(progress);
        return s;
    }
}

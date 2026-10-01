package com.nbamanager.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.event.AdminLoginEvent;
import com.nbamanager.repository.SyncJobRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncCatchupServiceTest {

    @Mock private SyncCoordinator coordinator;
    @Mock private SyncDefinitionRegistry registry;
    @Mock private SyncJobRepository repository;
    @Mock private SyncLockService lockService;

    private SyncCatchupService catchupService;
    private final ZonedDateTime now = ZonedDateTime.of(2026, 7, 17, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        catchupService = new SyncCatchupService(coordinator, registry, repository, lockService);
        when(lockService.tryAcquire(any(), any(), any())).thenReturn(true);
    }

    @Test
    void missedMultipleNewsIntervalsSubmitOneCatchup() {
        SyncDefinition newsDef = new SyncDefinition("NEWS", null, 5,
                java.util.List.of(SyncSchedulePolicy.fixed(java.time.Duration.ofHours(1))));
        when(registry.scheduledDefinitions()).thenReturn(java.util.List.of(newsDef));

        SyncJob lastSuccess = successAt("NEWS", now.minusHours(4));
        when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("NEWS", "SUCCESS"))
                .thenReturn(Optional.of(lastSuccess));

        catchupService.onAdminLogin(new AdminLoginEvent("admin", now));

        verify(coordinator, times(1)).submit(SyncCommand.loginCatchup("NEWS", "admin"));
    }

    @Test
    void successfulCatchupDoesNotRemainPendingForRetry() {
        SyncDefinition newsDef = new SyncDefinition("NEWS", null, 5,
                java.util.List.of(SyncSchedulePolicy.fixed(java.time.Duration.ofHours(1))));
        when(registry.scheduledDefinitions()).thenReturn(java.util.List.of(newsDef));
        when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("NEWS", "SUCCESS"))
                .thenReturn(Optional.of(successAt("NEWS", now.minusHours(4))));
        SyncJob submitted = new SyncJob();
        submitted.setId(21L);
        when(coordinator.submit(any())).thenReturn(new SyncSubmission(submitted, false));

        catchupService.onAdminLogin(new AdminLoginEvent("admin", now));
        catchupService.retryPendingCatchup();

        verify(coordinator, times(1)).submit(SyncCommand.loginCatchup("NEWS", "admin"));
    }

    @Test
    void activeModuleIsDeduplicatedByCoordinator() {
        SyncDefinition todayDef = new SyncDefinition("TODAY_GAMES", null, 4,
                java.util.List.of(SyncSchedulePolicy.fixed(java.time.Duration.ofMinutes(5))));
        when(registry.scheduledDefinitions()).thenReturn(java.util.List.of(todayDef));

        when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("TODAY_GAMES", "SUCCESS"))
                .thenReturn(Optional.empty());

        SyncJob activeJob = new SyncJob();
        activeJob.setId(1L);
        activeJob.setStatus("RUNNING");
        when(coordinator.submit(any())).thenReturn(new SyncSubmission(activeJob, true));

        catchupService.onAdminLogin(new AdminLoginEvent("admin", now));

        verify(coordinator).submit(SyncCommand.loginCatchup("TODAY_GAMES", "admin"));
    }

    @Test
    void noCatchupWhenRecentlySucceeded() {
        SyncDefinition newsDef = new SyncDefinition("NEWS", null, 5,
                java.util.List.of(SyncSchedulePolicy.fixed(java.time.Duration.ofHours(1))));
        when(registry.scheduledDefinitions()).thenReturn(java.util.List.of(newsDef));

        SyncJob lastSuccess = successAt("NEWS", now.minusMinutes(30));
        when(repository.findTopByModuleAndStatusOrderByFinishedAtDesc("NEWS", "SUCCESS"))
                .thenReturn(Optional.of(lastSuccess));

        catchupService.onAdminLogin(new AdminLoginEvent("admin", now));

        verify(coordinator, never()).submit(any());
    }

    private SyncJob successAt(String module, ZonedDateTime time) {
        SyncJob job = new SyncJob();
        job.setModule(module);
        job.setStatus("SUCCESS");
        job.setFinishedAt(time.toLocalDateTime());
        return job;
    }
}

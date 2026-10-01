package com.nbamanager.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamSyncWorkerTest {

    @Mock private SyncJobRepository syncJobRepository;
    @Mock private SyncJobStateService jobStateService;
    @Mock private PythonScriptRunner runner;
    @Mock private TeamRepository teamRepository;
    @Mock private PythonTimeoutProperties timeoutProperties;

    @Test
    void sync_runsOnlyTeamsActionWithConfiguredBulkTimeout() {
        when(timeoutProperties.getBulkSeconds()).thenReturn(3600);
        when(runner.runForJob(
                        eq("nba_data_fetcher.py"),
                        eq(new String[]{"teams"}),
                        eq(3600),
                        eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(-1, "", "expected failure", false, 1));

        TeamSyncWorker worker = new TeamSyncWorker(
                syncJobRepository,
                jobStateService,
                runner,
                teamRepository,
                timeoutProperties);

        worker.sync(1L);

        verify(runner).runForJob(
                eq("nba_data_fetcher.py"),
                eq(new String[]{"teams"}),
                eq(3600),
                eq(1L));
    }
}

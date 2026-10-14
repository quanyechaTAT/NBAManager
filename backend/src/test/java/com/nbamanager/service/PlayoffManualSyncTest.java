package com.nbamanager.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nbamanager.config.PythonTimeoutProperties;
import org.junit.jupiter.api.Test;

class PlayoffManualSyncTest {
    @Test void manualRefreshMustFetchEvenWhenReferenceSnapshotExists() {
        var runner = mock(PythonScriptRunner.class);
        var reference = mock(PlayoffReferenceData.class);
        var playoffs = mock(PlayoffService.class);
        var timeouts = new PythonTimeoutProperties();
        when(reference.supports("2025-26")).thenReturn(true);
        when(runner.runForJob(eq("nba_data_fetcher.py"), eq(new String[]{"playoff", "2025-26"}), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(0,
                        "{\"playoff\":{\"sourceStatus\":\"ok\",\"matchups\":[{\"team1Name\":\"A\",\"team2Name\":\"B\"}]}}", "", false, 1));
        new NbaDataService(playoffs, reference, runner, timeouts).fetchAndImportPlayoffData("2025-26", 1L);
        verify(runner).runForJob(eq("nba_data_fetcher.py"), eq(new String[]{"playoff", "2025-26"}), anyInt(), eq(1L));
    }

    @Test void unavailableOrEmptyPlayoffsMustPreserveExistingData() {
        var runner = mock(PythonScriptRunner.class);
        var playoffs = mock(PlayoffService.class);
        var service = new NbaDataService(playoffs, mock(PlayoffReferenceData.class), runner, new PythonTimeoutProperties());
        when(runner.runForJob(anyString(), any(), anyInt(), eq(1L)))
                .thenReturn(new PythonScriptRunner.PythonResult(0,
                        "{\"playoff\":{\"sourceStatus\":\"failed\",\"matchups\":[]}}", "", false, 1));
        assertThatThrownBy(() -> service.fetchAndImportPlayoffData("2025-26", 1L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(playoffs);
    }
}

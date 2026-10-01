package com.nbamanager.web;

import static com.nbamanager.domain.replay.ReplayPageState.NOT_STARTED;
import static com.nbamanager.domain.replay.ReplayPageState.PREPARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.service.MatchDetailService;
import com.nbamanager.service.PlayByPlaySyncLogService;
import com.nbamanager.service.ReplayPreparationService;
import com.nbamanager.service.SmartDataService;
import com.nbamanager.web.dto.ReplayPreparationDto;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MatchDetailControllerTest {

    private static final String GAME_ID = "0042500405";

    @Mock private MatchDetailService matchDetailService;
    @Mock private SmartDataService smartDataService;
    @Mock private ReplayPreparationService replayPreparationService;
    @Mock private PlayByPlaySyncLogService syncLogService;

    @Test
    void syncDelegatesToReplayPreparationAndReturnsAcceptedForRunningPreparation() {
        LocalDateTime startedAt = LocalDateTime.parse("2026-07-21T10:15:00");
        ReplayPreparationDto preparation = new ReplayPreparationDto(
                GAME_ID,
                PREPARING,
                "operation-1",
                false,
                2,
                startedAt,
                startedAt.plusSeconds(90),
                null);
        when(replayPreparationService.prepare(GAME_ID)).thenReturn(preparation);
        MatchDetailController controller = controller();

        var result = controller.syncPlayByPlay(GAME_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(result.getBody()).isSameAs(preparation);
        verify(replayPreparationService).prepare(GAME_ID);
        verifyNoInteractions(matchDetailService, smartDataService, syncLogService);
    }

    @Test
    void syncReturnsOkForScheduledGamesWithoutPretendingACollectionStarted() {
        ReplayPreparationDto notStarted = new ReplayPreparationDto(
                GAME_ID,
                NOT_STARTED,
                null,
                false,
                null,
                null,
                null,
                null);
        when(replayPreparationService.prepare(GAME_ID)).thenReturn(notStarted);
        MatchDetailController controller = controller();

        var result = controller.syncPlayByPlay(GAME_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(notStarted);
        verify(replayPreparationService).prepare(GAME_ID);
        verifyNoInteractions(matchDetailService, smartDataService, syncLogService);
    }

    private MatchDetailController controller() {
        return new MatchDetailController(
                matchDetailService,
                smartDataService,
                replayPreparationService,
                syncLogService);
    }
}

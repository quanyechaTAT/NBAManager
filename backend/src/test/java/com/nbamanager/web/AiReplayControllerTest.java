package com.nbamanager.web;

import static com.nbamanager.domain.replay.ReplayPageState.PREPARING;
import static com.nbamanager.domain.replay.ReplayPageState.READY;
import static com.nbamanager.domain.replay.ReplayQualityLevel.FULL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.service.AiReplayService;
import com.nbamanager.service.ReplayPreparationService;
import com.nbamanager.service.ReplayReadService;
import com.nbamanager.web.dto.GameSummaryDto;
import com.nbamanager.web.dto.ReplayBootstrapDto;
import com.nbamanager.web.dto.ReplayPreparationDto;
import com.nbamanager.web.dto.ReplayQualityDto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiReplayControllerTest {

    private static final String GAME_ID = "0042500405";

    @Mock private AiReplayService aiReplayService;
    @Mock private ReplayReadService replayReadService;
    @Mock private ReplayPreparationService replayPreparationService;

    @Test
    void bootstrapIsPureReadThroughReplayReadService() {
        ReplayBootstrapDto bootstrap = bootstrap(null);
        when(replayReadService.bootstrap(GAME_ID)).thenReturn(bootstrap);
        AiReplayController controller = controller();

        ReplayBootstrapDto result = controller.bootstrap(GAME_ID);

        assertThat(result).isSameAs(bootstrap);
        verify(replayReadService).bootstrap(GAME_ID);
        verifyNoInteractions(aiReplayService, replayPreparationService);
    }

    @Test
    void prepareReturnsAcceptedAndRetryAfterWhenPreparationIsRunning() {
        LocalDateTime startedAt = LocalDateTime.parse("2026-07-21T10:15:00");
        ReplayPreparationDto preparation = new ReplayPreparationDto(
                GAME_ID,
                PREPARING,
                "operation-1",
                false,
                3,
                startedAt,
                startedAt.plusSeconds(90),
                null);
        when(replayPreparationService.prepare(GAME_ID)).thenReturn(preparation);
        AiReplayController controller = controller();

        var result = controller.prepare(GAME_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("3");
        assertThat(result.getBody()).isSameAs(preparation);
    }

    @Test
    void getSummaryOnlyReturnsCachedReadModelSummary() {
        GameSummaryDto cached = new GameSummaryDto(GAME_ID, "FULL", "cached", "[1]", null, "deepseek", "m1");
        when(replayReadService.bootstrap(GAME_ID)).thenReturn(bootstrap(cached));
        AiReplayController controller = controller();

        GameSummaryDto result = controller.getSummary(GAME_ID);

        assertThat(result).isSameAs(cached);
        verify(replayReadService).bootstrap(GAME_ID);
        verifyNoInteractions(aiReplayService, replayPreparationService);
    }

    @Test
    void postSummaryUsesExplicitGenerationPath() {
        GameSummaryDto generated = new GameSummaryDto(GAME_ID, "FULL", "generated", "[1]", null, "deepseek", "m1");
        when(aiReplayService.generateSummary(GAME_ID)).thenReturn(generated);
        AiReplayController controller = controller();

        GameSummaryDto result = controller.generateSummary(GAME_ID);

        assertThat(result).isSameAs(generated);
        verify(aiReplayService).generateSummary(GAME_ID);
        verifyNoInteractions(replayReadService, replayPreparationService);
    }

    private AiReplayController controller() {
        return new AiReplayController(aiReplayService, replayReadService, replayPreparationService);
    }

    private ReplayBootstrapDto bootstrap(GameSummaryDto cachedSummary) {
        return new ReplayBootstrapDto(
                GAME_ID,
                READY,
                new ReplayQualityDto(
                        FULL,
                        120,
                        40,
                        List.of(1, 2, 3, 4),
                        "NBA_PLAY_BY_PLAY",
                        "NBA Play-by-Play",
                        false,
                        true,
                        true,
                        LocalDateTime.parse("2026-07-21T10:00:00"),
                        List.of(),
                        "facts-1"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                cachedSummary,
                null);
    }
}

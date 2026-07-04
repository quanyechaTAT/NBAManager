package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MomentumComputeServiceTest {

    @Mock
    private GameMomentumSnapshotRepository repository;

    @InjectMocks
    private MomentumComputeService service;

    private PlayByPlayEvent createPlayByPlayEvent(
            int eventOrder, int period, String gameClock,
            int homeScore, int awayScore, int scoreMargin) {
        return new PlayByPlayEvent(
                "event_" + eventOrder,
                period,
                gameClock,
                "SHOT",
                "JUMPSHOT",
                "Description",
                "Player",
                1L,
                "Team",
                1L,
                homeScore,
                awayScore,
                scoreMargin,
                true,
                false,
                eventOrder,
                "{}"
        );
    }

    @Test
    void compute_createsSnapshotForEachEvent() {
        String gameId = "game_001";
        List<PlayByPlayEvent> events = List.of(
                createPlayByPlayEvent(0, 1, "12:00", 0, 0, 0),
                createPlayByPlayEvent(1, 1, "10:30", 5, 3, 2),
                createPlayByPlayEvent(2, 2, "08:15", 20, 25, -5),
                createPlayByPlayEvent(3, 2, "05:00", 30, 25, 5)
        );

        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GameMomentumSnapshot> result = service.compute(gameId, events);

        assertThat(result).hasSize(4);

        verify(repository).deleteByGameId(gameId);
        verify(repository).saveAll(result);

        assertThat(result.get(0).getGameId()).isEqualTo(gameId);
        assertThat(result.get(0).getEventOrder()).isEqualTo(0);
        assertThat(result.get(0).getPeriod()).isEqualTo(1);
        assertThat(result.get(0).getGameClock()).isEqualTo("12:00");
        assertThat(result.get(0).getHomeScore()).isEqualTo(0);
        assertThat(result.get(0).getAwayScore()).isEqualTo(0);
        assertThat(result.get(0).getScoreMargin()).isEqualTo(0);
        assertThat(result.get(0).getCreatedAt()).isNotNull();

        assertThat(result.get(1).getScoreMargin()).isEqualTo(2);
        assertThat(result.get(1).getEventOrder()).isEqualTo(1);
        assertThat(result.get(1).getPeriod()).isEqualTo(1);

        assertThat(result.get(2).getScoreMargin()).isEqualTo(-5);
        assertThat(result.get(2).getEventOrder()).isEqualTo(2);
        assertThat(result.get(2).getPeriod()).isEqualTo(2);

        assertThat(result.get(3).getScoreMargin()).isEqualTo(5);
        assertThat(result.get(3).getEventOrder()).isEqualTo(3);
        assertThat(result.get(3).getPeriod()).isEqualTo(2);
    }

    @Test
    void compute_handlesEmptyEvents() {
        String gameId = "game_002";
        List<PlayByPlayEvent> events = Collections.emptyList();

        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<GameMomentumSnapshot> result = service.compute(gameId, events);

        assertThat(result).isEmpty();
        verify(repository).deleteByGameId(gameId);
        verify(repository).saveAll(result);
    }
}

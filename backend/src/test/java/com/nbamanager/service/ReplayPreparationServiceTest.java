package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.ReplaySyncStateRepository;
import com.nbamanager.web.dto.ReplayPreparationDto;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayPreparationServiceTest {

    @Mock private PlayByPlayCollectorService collectorService;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private ReplaySyncStateRepository syncStateRepository;
    @Mock private ExecutorService executor;

    @Test
    void prepareReusesActiveLeaseAndOnlySubmitsOneTask() {
        String gameId = "0022500001";
        AtomicReference<ReplaySyncState> stored = new AtomicReference<>();
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.of(match()));
        when(syncStateRepository.findByGameId(gameId)).thenAnswer(inv -> Optional.ofNullable(stored.get()));
        doAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return stored.get();
        }).when(syncStateRepository).save(any(ReplaySyncState.class));

        ReplayPreparationService service = new ReplayPreparationService(
                collectorService, matchRecordRepository, syncStateRepository, executor);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        doAnswer(inv -> null).when(executor).execute(task.capture());

        ReplayPreparationDto first = service.prepare(gameId);
        ReplayPreparationDto second = service.prepare(gameId);

        assertThat(first.state()).isEqualTo(ReplayPageState.PREPARING);
        assertThat(first.operationId()).isNotBlank();
        assertThat(first.reused()).isFalse();
        assertThat(second.state()).isEqualTo(ReplayPageState.PREPARING);
        assertThat(second.operationId()).isEqualTo(first.operationId());
        assertThat(second.reused()).isTrue();
        verify(executor).execute(any(Runnable.class));
        verify(collectorService, never()).collect(any(), any());
    }

    @Test
    void prepareForScheduledGameReturnsNotStartedWithoutClaimingOrFetching() {
        String gameId = "0022500002";
        MatchRecord match = match();
        match.setStatus("SCHEDULED");
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.of(match));

        ReplayPreparationService service = new ReplayPreparationService(
                collectorService, matchRecordRepository, syncStateRepository, executor);

        ReplayPreparationDto result = service.prepare(gameId);

        assertThat(result.state()).isEqualTo(ReplayPageState.NOT_STARTED);
        verify(syncStateRepository, never()).save(any());
        verify(executor, never()).execute(any());
    }

    @Test
    void prepareTakesOverExpiredLeaseWithNewOperation() {
        String gameId = "0022500003";
        ReplaySyncState expired = new ReplaySyncState();
        expired.setGameId(gameId);
        expired.setStatus(ReplayPageState.PREPARING);
        expired.setQuality(ReplayQualityLevel.PARTIAL);
        expired.setOperationId("old-operation");
        expired.setLeaseExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.of(match()));
        when(syncStateRepository.findByGameId(gameId)).thenReturn(Optional.of(expired));
        when(syncStateRepository.save(any(ReplaySyncState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> null).when(executor).execute(any());

        ReplayPreparationService service = new ReplayPreparationService(
                collectorService, matchRecordRepository, syncStateRepository, executor);

        ReplayPreparationDto result = service.prepare(gameId);

        assertThat(result.state()).isEqualTo(ReplayPageState.PREPARING);
        assertThat(result.operationId()).isNotEqualTo("old-operation");
        assertThat(result.reused()).isFalse();
    }

    private MatchRecord match() {
        MatchRecord match = new MatchRecord();
        match.setNbaGameId("0022500001");
        match.setStatus("FINISHED");
        return match;
    }
}

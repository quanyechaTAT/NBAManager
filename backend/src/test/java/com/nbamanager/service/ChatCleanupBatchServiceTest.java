package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.nbamanager.repository.ChatMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCleanupBatchServiceTest {

    @InjectMocks
    private ChatCleanupBatchService batchService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Test
    void deleteOneBatchReturnsDeletedCount() {
        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        when(chatMessageRepository.deleteBatchBefore(any(Instant.class), anyInt())).thenReturn(500);

        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isEqualTo(500);
    }

    @Test
    void deleteOneBatchReturnsZeroWhenNothingToDelete() {
        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        when(chatMessageRepository.deleteBatchBefore(any(Instant.class), anyInt())).thenReturn(0);

        int deleted = batchService.deleteOneBatch(cutoff, 1000);

        assertThat(deleted).isZero();
    }
}

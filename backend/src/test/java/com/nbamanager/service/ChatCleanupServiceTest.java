package com.nbamanager.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatCleanupServiceTest {

    @InjectMocks
    private ChatCleanupService cleanupService;

    @Mock
    private ChatCleanupBatchService batchService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cleanupService, "enabled", true);
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 180);
        ReflectionTestUtils.setField(cleanupService, "batchSize", 1000);
        ReflectionTestUtils.setField(cleanupService, "maxBatches", 500);
    }

    @Test
    void skipsExecutionWhenDisabled() {
        ReflectionTestUtils.setField(cleanupService, "enabled", false);

        cleanupService.cleanupExpiredMessages();

        verifyNoInteractions(batchService);
    }

    @Test
    void skipsExecutionWhenLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        cleanupService.cleanupExpiredMessages();

        verifyNoInteractions(batchService);
    }

    @Test
    void deletesInBatchesUntilEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500)
                .thenReturn(200)
                .thenReturn(0);

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(3)).deleteOneBatch(any(Instant.class), eq(1000));
        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }

    @Test
    void stopsOnBatchFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500)
                .thenThrow(new RuntimeException("DB error"));

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(2)).deleteOneBatch(any(Instant.class), eq(1000));
        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }

    @Test
    void releasesLockOnException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        cleanupService.cleanupExpiredMessages();

        verify(redisTemplate).delete("nba:chat:cleanup:lock");
    }
}

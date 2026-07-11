package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
        ReflectionTestUtils.setField(cleanupService, "renewalFailed", new AtomicBoolean(false));
    }

    private void stubRedisLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        // Renewal and unlock are async/conditional — use lenient
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
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
        stubRedisLock();
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500).thenReturn(200).thenReturn(0);

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(3)).deleteOneBatch(any(Instant.class), eq(1000));
    }

    @Test
    void stopsOnBatchFailure() {
        stubRedisLock();
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenReturn(500).thenThrow(new RuntimeException("DB error"));

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(2)).deleteOneBatch(any(Instant.class), eq(1000));
    }

    @Test
    void releasesLockEvenOnException() {
        stubRedisLock();
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        cleanupService.cleanupExpiredMessages();

        // unlock Lua script must be called
        ArgumentCaptor<DefaultRedisScript> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(Collections.singletonList("nba:chat:cleanup:lock")), anyString());
    }

    @Test
    void unlockUsesSameUuidAsLock() {
        ArgumentCaptor<String> lockValueCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), lockValueCaptor.capture(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);

        cleanupService.cleanupExpiredMessages();

        String capturedLockValue = lockValueCaptor.getValue();
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("nba:chat:cleanup:lock")),
                eq(capturedLockValue));
    }

    @Test
    void skipsWhenRetentionDaysInvalid() {
        ReflectionTestUtils.setField(cleanupService, "retentionDays", 0);
        cleanupService.cleanupExpiredMessages();
        verifyNoInteractions(batchService);
    }

    @Test
    void skipsWhenBatchSizeInvalid() {
        ReflectionTestUtils.setField(cleanupService, "batchSize", -1);
        cleanupService.cleanupExpiredMessages();
        verifyNoInteractions(batchService);
    }

    @Test
    void skipsWhenMaxBatchesInvalid() {
        ReflectionTestUtils.setField(cleanupService, "maxBatches", 0);
        cleanupService.cleanupExpiredMessages();
        verifyNoInteractions(batchService);
    }

    @Test
    void skipsWhenRedisAcquireThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis down"));
        cleanupService.cleanupExpiredMessages();
        verifyNoInteractions(batchService);
    }

    @Test
    void unlockExceptionDoesNotPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);
        // Both renewal and unlock throw
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));

        // Should not throw
        cleanupService.cleanupExpiredMessages();
    }

    @Test
    void stopsAtMaxBatches() {
        ReflectionTestUtils.setField(cleanupService, "maxBatches", 3);
        stubRedisLock();
        // Always return non-zero to force hitting maxBatches
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(100);

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(3)).deleteOneBatch(any(Instant.class), eq(1000));
    }

    @Test
    void luaUnlockReturnsZeroDoesNotAffectCompletion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        // Unlock returns 0 (lock already expired)
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(0L);

        cleanupService.cleanupExpiredMessages();

        verify(batchService, times(1)).deleteOneBatch(any(Instant.class), eq(1000));
    }
}

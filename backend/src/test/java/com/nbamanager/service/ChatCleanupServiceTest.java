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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
        cleanupService.renewalIntervalSeconds = 60;
    }

    private void stubRedisLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
    }

    // ===== 开关和锁 =====

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
    void skipsWhenRedisAcquireThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis down"));
        cleanupService.cleanupExpiredMessages();
        verifyNoInteractions(batchService);
    }

    // ===== 参数校验 =====

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

    // ===== 批量删除 =====

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
    void stopsAtMaxBatches() {
        ReflectionTestUtils.setField(cleanupService, "maxBatches", 3);
        stubRedisLock();
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(100);
        cleanupService.cleanupExpiredMessages();
        verify(batchService, times(3)).deleteOneBatch(any(Instant.class), eq(1000));
    }

    // ===== 锁释放 =====

    @Test
    void releasesLockEvenOnException() {
        stubRedisLock();
        when(batchService.deleteOneBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));
        cleanupService.cleanupExpiredMessages();
        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(Collections.singletonList("nba:chat:cleanup:lock")), anyString());
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

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("nba:chat:cleanup:lock")),
                eq(lockValueCaptor.getValue()));
    }

    @Test
    void unlockExceptionDoesNotPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        cleanupService.cleanupExpiredMessages();
    }

    @Test
    void luaUnlockReturnsZeroDoesNotAffectCompletion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(0L);
        cleanupService.cleanupExpiredMessages();
        verify(batchService, times(1)).deleteOneBatch(any(Instant.class), eq(1000));
    }

    // ===== Watchdog 续期 =====

    @Test
    void renewalUsesSameUuidAsLock() throws Exception {
        ArgumentCaptor<String> lockValueCaptor = ArgumentCaptor.forClass(String.class);
        CountDownLatch renewalLatch = new CountDownLatch(1);
        ScheduledExecutorService testExecutor = Executors.newSingleThreadScheduledExecutor();

        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                return testExecutor;
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 1;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), lockValueCaptor.capture(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(eq(ChatCleanupService.RENEWAL_SCRIPT), any(), anyString(), anyString())).thenAnswer(inv -> {
            renewalLatch.countDown();
            return 1L;
        });
        when(redisTemplate.execute(eq(ChatCleanupService.UNLOCK_SCRIPT), any(), anyString())).thenReturn(1L);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenAnswer(inv -> {
            renewalLatch.await(3, TimeUnit.SECONDS);
            return 0;
        });

        spyService.cleanupExpiredMessages();

        verify(redisTemplate).execute(
                eq(ChatCleanupService.RENEWAL_SCRIPT),
                eq(Collections.singletonList("nba:chat:cleanup:lock")),
                eq(lockValueCaptor.getValue()),
                anyString());
        testExecutor.shutdownNow();
    }

    @Test
    void renewalFailureStopsCleanup() throws Exception {
        ScheduledExecutorService testExecutor = Executors.newSingleThreadScheduledExecutor();

        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                return testExecutor;
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 1;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        // Renewal returns 0 (lock lost)
        when(redisTemplate.execute(eq(ChatCleanupService.RENEWAL_SCRIPT), any(), anyString(), anyString())).thenReturn(0L);
        when(redisTemplate.execute(eq(ChatCleanupService.UNLOCK_SCRIPT), any(), anyString())).thenReturn(1L);
        // Batch returns non-zero; waits long enough for renewal to fire
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenAnswer(inv -> {
            Thread.sleep(1500);
            return 100;
        });

        spyService.cleanupExpiredMessages();

        // renewalFailed should stop after first batch — maxBatches=500 but only 1 batch executed
        verify(batchService, times(1)).deleteOneBatch(any(Instant.class), eq(1000));
        testExecutor.shutdownNow();
    }

    @Test
    void renewalExceptionStopsCleanup() throws Exception {
        ScheduledExecutorService testExecutor = Executors.newSingleThreadScheduledExecutor();

        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                return testExecutor;
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 1;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(eq(ChatCleanupService.RENEWAL_SCRIPT), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis down"));
        when(redisTemplate.execute(eq(ChatCleanupService.UNLOCK_SCRIPT), any(), anyString())).thenReturn(1L);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenAnswer(inv -> {
            Thread.sleep(1500);
            return 100;
        });

        spyService.cleanupExpiredMessages();

        // renewal exception should stop after first batch
        verify(batchService, times(1)).deleteOneBatch(any(Instant.class), eq(1000));
        testExecutor.shutdownNow();
    }

    @Test
    void watchdogCreationFailureStillReleasesLock() {
        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                throw new RuntimeException("Thread pool creation failed");
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 60;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);

        // Should not throw — exception is caught by finally
        spyService.cleanupExpiredMessages();

        // Lock must still be released via Lua
        verify(redisTemplate).execute(
                eq(ChatCleanupService.UNLOCK_SCRIPT),
                eq(Collections.singletonList("nba:chat:cleanup:lock")),
                anyString());
        // Fail-closed: no cleanup executed
        verifyNoInteractions(batchService);
    }

    @Test
    void watchdogIsShutdownAfterCompletion() {
        ScheduledExecutorService testExecutor = Executors.newSingleThreadScheduledExecutor();
        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                return testExecutor;
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 60;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);

        spyService.cleanupExpiredMessages();

        assertThat(testExecutor.isShutdown()).isTrue();
    }

    @Test
    void eachRunHasIndependentRenewalState() throws Exception {
        // Verify that two consecutive runs don't share renewalFailed state
        ChatCleanupService spyService = new ChatCleanupService(batchService, redisTemplate) {
            @Override
            ScheduledExecutorService createWatchdogExecutor() {
                return Executors.newSingleThreadScheduledExecutor();
            }
        };
        ReflectionTestUtils.setField(spyService, "enabled", true);
        ReflectionTestUtils.setField(spyService, "retentionDays", 180);
        ReflectionTestUtils.setField(spyService, "batchSize", 1000);
        ReflectionTestUtils.setField(spyService, "maxBatches", 500);
        spyService.renewalIntervalSeconds = 60;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(), anyString())).thenReturn(1L);
        when(batchService.deleteOneBatch(any(Instant.class), anyInt())).thenReturn(0);

        // First run
        spyService.cleanupExpiredMessages();
        // Second run should work independently
        spyService.cleanupExpiredMessages();

        verify(batchService, times(2)).deleteOneBatch(any(Instant.class), anyInt());
    }
}

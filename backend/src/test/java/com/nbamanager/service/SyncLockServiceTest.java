package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SyncLockServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private SyncLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new SyncLockService(redisTemplate);
    }

    @Test
    void acquireStoresPlainStringToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("sync:key", "token", Duration.ofMinutes(30))).thenReturn(true);

        assertThat(lockService.tryAcquire("sync:key", "token", Duration.ofMinutes(30))).isTrue();
    }

    @Test
    void renewPassesUnquotedTokenAndTtlToLua() {
        when(redisTemplate.execute(any(), eq(List.of("sync:key")), eq("token"), eq("1800000")))
                .thenReturn(1L);

        assertThat(lockService.renew("sync:key", "token", Duration.ofMinutes(30))).isTrue();
    }

    @Test
    void releaseUsesOwnershipToken() {
        lockService.release("sync:key", "token");

        verify(redisTemplate).execute(any(), eq(List.of("sync:key")), eq("token"));
    }
}

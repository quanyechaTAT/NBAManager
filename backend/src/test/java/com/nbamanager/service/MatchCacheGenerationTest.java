package com.nbamanager.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MatchCacheGenerationTest {
    @Test @SuppressWarnings("unchecked")
    void instancesShareGenerationAndACommittedWriteMakesOldKeysUnreachable() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var shared = new AtomicReference<String>();
        when(values.get(anyString())).thenAnswer(call -> shared.get());
        when(values.setIfAbsent(anyString(), anyString())).thenAnswer(call -> shared.compareAndSet(null, call.getArgument(1)));
        doAnswer(call -> { shared.set(call.getArgument(1)); return null; }).when(values).set(anyString(), anyString());
        var first = new MatchCacheGeneration(redis); var second = new MatchCacheGeneration(redis);
        String old = first.key("game");
        assertThat(second.key("game")).isEqualTo(old);
        first.advance();
        assertThat(second.key("game")).isEqualTo(first.key("game")).isNotEqualTo(old);
    }
    @Test void redisFailureUsesIsolatedLocalKeysAndBacksOff() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("down"));
        var first = new MatchCacheGeneration(redis); var second = new MatchCacheGeneration(redis);
        String old = first.key("game");
        assertThat(second.key("game")).isNotEqualTo(old);
        assertThat(first.key("game")).isEqualTo(old);
        first.advance();
        assertThat(first.key("game")).isNotEqualTo(old);
        verify(redis, times(2)).opsForValue();
    }
    @Test @SuppressWarnings("unchecked") void recoveryPublishesWritesThatHappenedDuringTheOutage() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var shared = new AtomicReference<>("before-outage");
        var unavailable = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            if (unavailable.get()) throw new IllegalStateException("down");
            shared.set(call.getArgument(1)); return null;
        }).when(values).set(anyString(), anyString());
        when(values.get(anyString())).thenAnswer(call -> shared.get());
        var time = new java.util.concurrent.atomic.AtomicLong(1);
        var writer = new MatchCacheGeneration(redis, time::get);
        var reader = new MatchCacheGeneration(redis, time::get);
        String old = reader.key("game");
        writer.advance();
        assertThat(writer.key("game")).doesNotStartWith("shared:");
        unavailable.set(false); time.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
        assertThat(writer.key("game")).isNotEqualTo(old);
        assertThat(reader.key("game")).isEqualTo(writer.key("game"));
    }
}

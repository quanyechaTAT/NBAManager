package com.nbamanager.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.nbamanager.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;

/**
 * P1-1/P1-4 共用的 Redis 原子窗口限流器。
 * 三态锁定：窗口内放行 / 超限 429 / Redis 不可用 fail-open。
 */
@ExtendWith(MockitoExtension.class)
class RedisWindowRateLimiterTest {

    @Mock private StringRedisTemplate stringRedisTemplate;

    private RedisWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisWindowRateLimiter(stringRedisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void counter(long value) {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenReturn(value);
    }

    @Test
    void allowsRequestsAtOrUnderLimit() {
        counter(10L);

        assertThatCode(() -> limiter.check("nba:test:rl:1", 10, 60_000, "too many"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsOverLimitWith429() {
        counter(11L);

        assertThatThrownBy(() -> limiter.check("nba:test:rl:1", 10, 60_000, "too many"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> limiter.check("nba:test:rl:1", 10, 60_000, "too many"))
                .as("Redis 不可用时必须放行：限流不能成为可用性故障点")
                .doesNotThrowAnyException();
    }

    @Test
    void windowKeyAndTtlArePassedToRedis() {
        counter(1L);

        limiter.check("nba:upload:rl:42", 10, 60_000, "too many");

        org.mockito.Mockito.verify(stringRedisTemplate)
                .execute(any(DefaultRedisScript.class),
                        org.mockito.ArgumentMatchers.eq(List.of("nba:upload:rl:42")),
                        org.mockito.ArgumentMatchers.eq("60000"));
    }
}

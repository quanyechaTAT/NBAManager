package com.nbamanager.service;

import java.time.Duration;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncLockService {

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(String key, String token, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, ttl));
    }

    public boolean renew(String key, String token, Duration ttl) {
        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                Collections.singletonList(key),
                token,
                String.valueOf(ttl.toMillis()));
        return result != null && result == 1L;
    }

    public void release(String key, String token) {
        redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key), token);
    }
}

package com.nbamanager.service.support;

import com.nbamanager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Redis 原子窗口限流（登录/注册/上传共用同型实现，安全报告 §十二 V3/V5）。
 *
 * 语义：key 在 windowMs 窗口内的计数 > limit 即 429。INCR 与首次 PEXPIRE 在
 * 同一 Lua 脚本里原子执行，并发下不会出现"计数了但没设过期"的泄漏键。
 *
 * fail-open：Redis 不可用时放行并告警——限流是可用性保护，绝不能因为它
 * 把正常用户挡在门外（与登录限流 checkLoginRateLimit 的既有取舍一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWindowRateLimiter {

    private static final String LUA =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @param key      完整限流键（含维度前缀，如 nba:upload:rl:<userId>）
     * @param limit    窗口内允许的最大次数（超过即拒绝）
     * @param windowMs 窗口时长（毫秒）
     * @param message  拒绝时返回给客户端的提示
     */
    public void check(String key, int limit, long windowMs, String message) {
        try {
            Long count = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(LUA, Long.class),
                    java.util.List.of(key),
                    String.valueOf(windowMs));
            if (count != null && count > limit) {
                log.warn("限流触发: key={} count={} limit={}", key, count, limit);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("限流 Redis 不可用，放行: key={} error={}", key, e.getMessage());
        }
    }
}

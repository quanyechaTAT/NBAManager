package com.nbamanager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.lang.NonNull;

/**
 * 缓存故障 fail-open（安全报告 §十四 Y5 / 14.3）。
 *
 * 现象：Redis 硬故障（CLIENT PAUSE 6s > Lettuce 3s 命令超时）时，
 * {@code @Cacheable} 读路径的 Redis 超时异常从 CacheInterceptor 直接抛出、
 * 被全局兜底成 500——Redis 成为全部公开读面（posts/players 等）的单点，
 * 读可用性从"降级变慢"退化为"不可用"。
 *
 * 语义：与限流器的既定取舍（登录/注册/聊天/上传 fail-open）对齐——
 * <ul>
 *   <li><b>get 失败</b>：记 WARN 后吞掉——Spring 视为 cache miss，自动回落 DB 执行方法体
 *       （读变慢但可用）；</li>
 *   <li><b>put/evict/clear 失败</b>：同样 WARN + 吞掉——业务写路径不能被缓存故障连带；
 *       失效丢失由缓存 TTL（60s）兜底，属既有取舍（最坏 60s 陈旧读）。</li>
 * </ul>
 *
 * 使用约束：全工程只允许一个 {@code CachingConfigurer}——本错误处理器由
 * {@link RedisConfig}{@code #errorHandler()} 提供（RedisConfig 已持有 cacheManager，二者同属缓存域）。
 * 日志不打印完整 key（截断 40 字符，防日志放大；故障窗口内 WARN 量可控，不做限速）。
 */
@Slf4j
public class CacheFailOpenErrorHandler implements CacheErrorHandler {

    private static final int MAX_KEY_LOG_LENGTH = 40;

    @Override
    public void handleCacheGetError(@NonNull RuntimeException exception,
                                    @NonNull Cache cache, Object key) {
        log.warn("缓存读取失败，降级回落 DB: cache={}, key={}, error={}",
                cache.getName(), summarize(key), exception.getMessage());
    }

    @Override
    public void handleCachePutError(@NonNull RuntimeException exception,
                                    @NonNull Cache cache, Object key, Object value) {
        log.warn("缓存写入失败（业务不受影响，TTL 兜底陈旧窗口）: cache={}, key={}, error={}",
                cache.getName(), summarize(key), exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(@NonNull RuntimeException exception,
                                      @NonNull Cache cache, Object key) {
        log.warn("缓存失效失败（业务不受影响，TTL 兜底陈旧窗口）: cache={}, key={}, error={}",
                cache.getName(), summarize(key), exception.getMessage());
    }

    @Override
    public void handleCacheClearError(@NonNull RuntimeException exception, @NonNull Cache cache) {
        log.warn("缓存清空失败（业务不受影响）: cache={}, error={}",
                cache.getName(), exception.getMessage());
    }

    /** key 摘要：只取 toString 前 40 字符，防日志放大（不抛出）。 */
    static String summarize(Object key) {
        if (key == null) {
            return "null";
        }
        String s = String.valueOf(key);
        return s.length() <= MAX_KEY_LOG_LENGTH ? s : s.substring(0, MAX_KEY_LOG_LENGTH) + "…";
    }
}

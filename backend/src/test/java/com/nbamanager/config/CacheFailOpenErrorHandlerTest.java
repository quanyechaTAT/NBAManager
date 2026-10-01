package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

/**
 * P1 回归（安全报告 §十四 Y5 / 14.3）：Redis 硬故障时缓存 fail-open。
 *
 * 此前 @Cacheable 读路径没有 CacheErrorHandler——CLIENT PAUSE 6s（> Lettuce 3s 超时）
 * 注入下，GET /api/posts、/api/players 全部 500，Redis 成为公开读面单点。
 * 修复后：get 失败视为 miss 回落 DB；put/evict/clear 失败吞掉（TTL 兜底），
 * 与限流器的 fail-open 语义对齐。
 *
 * 这里用"抛异常的 CacheManager"伪造 Redis 故障（不依赖真实 Redis），
 * 在迷你 Spring 上下文里验证 CacheInterceptor 的完整语义链路。
 */
class CacheFailOpenErrorHandlerTest {

    // ==================== 直接错误处理器行为 ====================

    @Test
    void allFourHandlersSwallowRedisFailures() {
        CacheFailOpenErrorHandler handler = new CacheFailOpenErrorHandler();
        Cache cache = new ThrowingCache();

        assertThatCode(() -> handler.handleCacheGetError(
                new RuntimeException("redis timeout"), cache, "posts::k1"))
                .as("get 失败必须吞掉（视为 miss 回落 DB），不得向读路径抛 500")
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(
                new RuntimeException("redis timeout"), cache, "k1", "v"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(
                new RuntimeException("redis timeout"), cache, "k1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(
                new RuntimeException("redis timeout"), cache))
                .doesNotThrowAnyException();
    }

    @Test
    void keySummaryTruncatesLongKeysAndToleratesNull() {
        assertThat(CacheFailOpenErrorHandler.summarize(null)).isEqualTo("null");
        String longKey = "x".repeat(100);
        String summarized = CacheFailOpenErrorHandler.summarize(longKey);
        assertThat(summarized.length()).isLessThanOrEqualTo(41);
        assertThat(summarized).endsWith("…");
    }

    // ==================== 迷你上下文端到端（CacheInterceptor 语义） ====================

    @Configuration
    @EnableCaching
    static class TestCacheConfig implements CachingConfigurer {
        @Override
        public CacheErrorHandler errorHandler() {
            return new CacheFailOpenErrorHandler();
        }

        @Bean
        CacheManager throwingCacheManager() {
            return new ThrowingCacheManager();
        }

        @Bean
        CountingService countingService() {
            return new CountingService();
        }
    }

    /** 模拟 Redis 故障：所有缓存操作一律抛 RuntimeException（等价 CLIENT PAUSE 硬故障）。 */
    static class ThrowingCacheManager implements CacheManager {
        @Override
        public Cache getCache(String name) {
            return new ThrowingCache();
        }

        @Override
        public Collection<String> getCacheNames() {
            return List.of("posts", "playerList");
        }
    }

    static class ThrowingCache implements Cache {
        @Override @NonNull
        public String getName() {
            return "posts";
        }

        @Override @NonNull
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            throw new RuntimeException("simulated redis hard failure");
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            throw new RuntimeException("simulated redis hard failure");
        }

        @Override
        public <T> T get(Object key, @NonNull java.util.concurrent.Callable<T> valueLoader) {
            throw new RuntimeException("simulated redis hard failure");
        }

        @Override
        public void put(Object key, Object value) {
            throw new RuntimeException("simulated redis hard failure");
        }

        @Override
        public void evict(Object key) {
            throw new RuntimeException("simulated redis hard failure");
        }

        @Override
        public void clear() {
            throw new RuntimeException("simulated redis hard failure");
        }
    }

    @Service
    static class CountingService {
        final AtomicInteger dbCalls = new AtomicInteger();

        /** 经代理取计数必须走方法：CGLIB 代理的实例字段永远是 null */
        public int dbCallCount() {
            return dbCalls.get();
        }

        @Cacheable("posts")
        public String load(String key) {
            return "db-value-" + dbCalls.incrementAndGet();
        }

        @CachePut("posts")
        public String put(String key) {
            return "put-ok";
        }

        @CacheEvict("posts")
        public String evict(String key) {
            return "evict-ok";
        }
    }

    @Test
    void cacheGetFailureFallsBackToDbInsteadOfThrowing() {
        var ctx = new AnnotationConfigApplicationContext(TestCacheConfig.class);
        try {
            CountingService service = ctx.getBean(CountingService.class);

            String first = service.load("k1");
            String second = service.load("k1");

            assertThat(first).isEqualTo("db-value-1");
            assertThat(second).isEqualTo("db-value-2");
            assertThat(service.dbCallCount())
                    .as("get 失败视为 miss：两次调用都应回落 DB（而不是抛 500 或返回错误数据）")
                    .isEqualTo(2);
        } finally {
            ctx.close();
        }
    }

    @Test
    void cachePutAndEvictFailuresDoNotAffectBusinessReturnValue() {
        var ctx = new AnnotationConfigApplicationContext(TestCacheConfig.class);
        try {
            CountingService service = ctx.getBean(CountingService.class);

            assertThat(service.put("k1"))
                    .as("put 失败不影响业务方法返回值")
                    .isEqualTo("put-ok");
            assertThat(service.evict("k1"))
                    .as("evict 失败不影响业务方法返回值（写路径不被缓存故障连带）")
                    .isEqualTo("evict-ok");
        } finally {
            ctx.close();
        }
    }
}

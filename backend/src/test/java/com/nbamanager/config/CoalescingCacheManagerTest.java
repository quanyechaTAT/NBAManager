package com.nbamanager.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.web.server.ResponseStatusException;

class CoalescingCacheManagerTest {
    private Cache cache(int capacity, Duration wait) {
        return new CoalescingCacheManager(new ConcurrentMapCacheManager(), Set.of("public"), capacity,
                wait, mock(CacheErrorHandler.class)).getCache("public");
    }
    @Test void thirtyTwoColdReadsShareOneLoad() throws Exception {
        Cache cache = cache(128, Duration.ofSeconds(5));
        AtomicInteger calls = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(32), start = new CountDownLatch(1);
        try {
            var reads = new ArrayList<Future<String>>();
            for (int i = 0; i < 32; i++) reads.add(pool.submit(() -> {
                ready.countDown(); start.await();
                return cache.get("same", () -> { calls.incrementAndGet(); Thread.sleep(100); return "verified"; });
            }));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var read : reads) assertThat(read.get(5, TimeUnit.SECONDS)).isEqualTo("verified");
            assertThat(calls).hasValue(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void missesAndFailuresAreRetryable() {
        Cache cache = cache(3, Duration.ofSeconds(1));
        assertThat(cache.<String>get("absent", () -> null)).isNull();
        assertThat(cache.get("absent", () -> "created")).isEqualTo("created");
        assertThatThrownBy(() -> cache.get("broken", () -> { throw new IllegalStateException("failed"); }))
                .isInstanceOf(Cache.ValueRetrievalException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(cache.get("broken", () -> "recovered")).isEqualTo("recovered");
    }
    @Test void redisFaultStillCoalescesAndReturnsDatabaseValue() throws Exception {
        var backend = mock(Cache.class);
        when(backend.getName()).thenReturn("public");
        when(backend.get(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable")).when(backend).put(any(), any());
        var manager = mock(org.springframework.cache.CacheManager.class);
        when(manager.getCache("public")).thenReturn(backend);
        Cache cache = new CoalescingCacheManager(manager, Set.of("public"), 2, Duration.ofSeconds(1),
                mock(CacheErrorHandler.class)).getCache("public");
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(2);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            Future<String> owner = pool.submit(() -> cache.get("same", () -> { started.countDown(); release.await(); return "DB"; }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            pool.schedule(release::countDown, 100, TimeUnit.MILLISECONDS);
            assertThat(cache.<String>get("same", () -> { throw new AssertionError("must share DB load"); })).isEqualTo("DB");
            assertThat(owner.get(2, TimeUnit.SECONDS)).isEqualTo("DB");
            verify(backend, times(1)).get(any());
            verify(backend, never()).put(any(), any());
        } finally { pool.shutdownNow(); }
    }
    @Test void clearDoesNotAllowAnOlderLoadToRepopulateOrCaptureNewReaders() throws Exception {
        Cache cache = cache(2, Duration.ofSeconds(1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            Future<String> old = pool.submit(() -> cache.get("same", () -> { started.countDown(); release.await(); return "old"; }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue(); cache.clear();
            assertThat(cache.get("same", () -> "new")).isEqualTo("new");
            release.countDown(); assertThat(old.get(2, TimeUnit.SECONDS)).isEqualTo("old");
            assertThat(cache.get("same").get()).isEqualTo("new");
        } finally { pool.shutdownNow(); }
    }
    @Test void distinctLoadCapacityAndFollowerWaitAreBounded() throws Exception {
        Cache cache = cache(1, Duration.ofMillis(30));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var owner = pool.submit(() -> cache.get("busy", () -> { started.countDown(); release.await(); return "ok"; }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> cache.get("other", () -> "unused"))
                    .isInstanceOf(Cache.ValueRetrievalException.class).hasCauseInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> cache.get("busy", () -> "duplicate"))
                    .isInstanceOf(Cache.ValueRetrievalException.class).hasCauseInstanceOf(ResponseStatusException.class);
            cache.clear();
            assertThatThrownBy(() -> cache.get("new-generation", () -> "unused"))
                    .isInstanceOf(Cache.ValueRetrievalException.class).hasCauseInstanceOf(ResponseStatusException.class);
            release.countDown(); assertThat(owner.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(cache.get("other", () -> "available")).isEqualTo("available");
        } finally { pool.shutdownNow(); }
    }
}

package com.nbamanager.config;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Coalesce public DTO loads in this JVM, including during Redis outages. */
public final class CoalescingCacheManager implements CacheManager {
    private final CacheManager delegate;
    private final Set<String> names;
    private final int capacity;
    private final long waitMillis;
    private final CacheErrorHandler errors;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final AtomicLong retryAfterNanos = new AtomicLong();

    public CoalescingCacheManager(CacheManager delegate, Set<String> names, int capacity,
                                 Duration wait, CacheErrorHandler errors) {
        this.delegate = delegate;
        this.names = Set.copyOf(names);
        this.capacity = Math.max(1, capacity);
        this.waitMillis = Math.max(1, wait.toMillis());
        this.errors = errors;
    }

    @Override public Cache getCache(String name) {
        if (!names.contains(name)) return delegate.getCache(name);
        return caches.computeIfAbsent(name, key -> {
            Cache cache = delegate.getCache(key);
            return cache == null ? null : new CoalescingCache(cache);
        });
    }
    @Override public Collection<String> getCacheNames() { return delegate.getCacheNames(); }

    private final class CoalescingCache implements Cache {
        private final Cache cache;
        private final Map<Object, CompletableFuture<Object>> pending = new HashMap<>();
        private long generation;
        private int runningLoads;
        CoalescingCache(Cache cache) { this.cache = cache; }
        @Override public String getName() { return cache.getName(); }
        @Override public Object getNativeCache() { return cache.getNativeCache(); }
        @Override public ValueWrapper get(Object key) { return cache.get(key); }
        @Override public <T> T get(Object key, Class<T> type) { return cache.get(key, type); }

        private ValueWrapper read(Object key) {
            if (System.nanoTime() < retryAfterNanos.get()) return null;
            try { return cache.get(key); }
            catch (RuntimeException ex) {
                retryAfterNanos.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
                errors.handleCacheGetError(ex, cache, key); return null;
            }
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> loader) {
            ValueWrapper hit = read(key);
            if (hit != null) return (T) hit.get();
            CompletableFuture<Object> future;
            boolean owner;
            long epoch;
            synchronized (pending) {
                future = pending.get(key);
                owner = future == null;
                if (owner) {
                    if (runningLoads >= capacity) throw failure(key, loader,
                            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "缓存加载繁忙，请稍后重试"));
                    future = new CompletableFuture<>();
                    pending.put(key, future);
                    runningLoads++;
                }
                epoch = generation;
            }
            if (!owner) {
                try { return (T) future.get(waitMillis, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw failure(key, loader, ex); }
                catch (TimeoutException ex) { throw failure(key, loader,
                        new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "等待缓存加载超时", ex)); }
                catch (ExecutionException ex) { throw failure(key, loader, ex.getCause()); }
            }
            try {
                // A previous owner may have filled Redis between our read and reservation.
                hit = read(key);
                T value = hit == null ? loader.call() : (T) hit.get();
                synchronized (pending) {
                    // Serialize publication with evictions; an older load cannot repopulate a cleared cache.
                    if (value != null && hit == null && generation == epoch && System.nanoTime() >= retryAfterNanos.get()) {
                        try { cache.put(key, value); }
                        catch (RuntimeException ex) {
                            retryAfterNanos.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
                            errors.handleCachePutError(ex, cache, key, value);
                        }
                    }
                }
                future.complete(value);
                return value;
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                if (ex instanceof Error error) throw error;
                throw failure(key, loader, ex);
            } finally {
                synchronized (pending) { pending.remove(key, future); runningLoads--; }
            }
        }
        private ValueRetrievalException failure(Object key, Callable<?> loader, Throwable ex) {
            return new ValueRetrievalException(key, loader, ex);
        }
        @Override public void put(Object key, Object value) { cache.put(key, value); }
        @Override public ValueWrapper putIfAbsent(Object key, Object value) { return cache.putIfAbsent(key, value); }
        @Override public void evict(Object key) {
            synchronized (pending) { generation++; pending.remove(key); cache.evict(key); }
        }
        @Override public boolean evictIfPresent(Object key) {
            synchronized (pending) { generation++; pending.remove(key); return cache.evictIfPresent(key); }
        }
        @Override public void clear() {
            synchronized (pending) { generation++; pending.clear(); cache.clear(); }
        }
        @Override public boolean invalidate() {
            synchronized (pending) { generation++; pending.clear(); return cache.invalidate(); }
        }
    }
}

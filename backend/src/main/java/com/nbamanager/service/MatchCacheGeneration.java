package com.nbamanager.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Shared Redis generation; old in-flight fills remain unreachable after a committed write. */
@Component
public class MatchCacheGeneration {
    private final String instance = UUID.randomUUID().toString();
    private final AtomicLong generation = new AtomicLong();
    private final StringRedisTemplate redis;
    private static final String SHARED_KEY = "nba:cache:match-generation:v1";
    private long retryAfterNanos;
    private boolean unpublishedWrite;
    private final java.util.function.LongSupplier clock;

    public MatchCacheGeneration() { this(null, System::nanoTime); }
    @Autowired public MatchCacheGeneration(StringRedisTemplate redis) { this(redis, System::nanoTime); }
    MatchCacheGeneration(StringRedisTemplate redis, java.util.function.LongSupplier clock) { this.redis = redis; this.clock = clock; }

    public synchronized String key(Object lookup) {
        if (redis != null && clock.getAsLong() >= retryAfterNanos) {
            try {
                var values = redis.opsForValue();
                if (unpublishedWrite) {
                    values.set(SHARED_KEY, UUID.randomUUID().toString());
                    unpublishedWrite = false;
                }
                String shared = values.get(SHARED_KEY);
                if (shared == null) {
                    values.setIfAbsent(SHARED_KEY, UUID.randomUUID().toString());
                    shared = values.get(SHARED_KEY);
                }
                if (shared != null) return "shared:" + shared + ':' + lookup;
            } catch (RuntimeException ex) {
                retryAfterNanos = clock.getAsLong() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            }
        }
        return instance + ':' + generation.get() + ':' + lookup;
    }

    public synchronized void advance() {
        generation.incrementAndGet();
        if (redis == null) return;
        unpublishedWrite = true;
        // During an outage, keep writes fast and publish a fresh generation when Redis recovers.
        if (clock.getAsLong() < retryAfterNanos) return;
        try {
            redis.opsForValue().set(SHARED_KEY, UUID.randomUUID().toString());
            unpublishedWrite = false;
        } catch (RuntimeException ex) {
            retryAfterNanos = clock.getAsLong() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        }
    }
}

package com.nbamanager.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Covers match writes from CRUD, live sync and archive import, once per committed transaction. */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchCacheEvictionAspect {
    private final CacheManager cacheManager;
    private final MatchCacheGeneration generation;
    private static final List<String> CACHES = List.of("matchRecord", "matchRecords", "matchRecordsByTeam",
            "headToHead", "boxScore", "quarterScores", "todayGames", "dashboard");

    @AfterReturning("execution(* com.nbamanager.repository.MatchRecordRepository+.save*(..))"
            + " || execution(* com.nbamanager.repository.MatchRecordRepository+.delete*(..))")
    public void matchesChanged() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict();
            return;
        }
        // Inspect this transaction's callbacks: thread-bound markers leak across REQUIRES_NEW.
        boolean registered = TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(callback -> callback instanceof InvalidateAfterCommit pending && pending.owner() == this);
        if (!registered) TransactionSynchronizationManager.registerSynchronization(new InvalidateAfterCommit(this));
    }

    private record InvalidateAfterCommit(MatchCacheEvictionAspect owner) implements TransactionSynchronization {
        @Override public void afterCommit() { owner.evict(); }
    }

    private void evict() {
        generation.advance();
        for (String name : CACHES) {
            try {
                var cache = cacheManager.getCache(name);
                // invalidate() is immediate even for a transaction-aware cache after commit.
                if (cache != null) cache.invalidate();
            } catch (RuntimeException ex) {
                log.warn("比赛缓存失效失败: cache={}, error={}", name, ex.getMessage());
            }
        }
    }
}

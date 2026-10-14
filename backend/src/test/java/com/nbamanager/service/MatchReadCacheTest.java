package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.MatchRecordController;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MatchReadCacheTest {
    @Configuration
    @EnableCaching
    @EnableAspectJAutoProxy
    static class Wiring {
        @Bean MatchRecordRepository repository() { return mock(MatchRecordRepository.class); }
        @Bean CacheManager cacheManager() { return new com.nbamanager.config.CoalescingCacheManager(
                new ConcurrentMapCacheManager(), java.util.Set.of("matchRecord", "matchRecords", "matchRecordsByTeam", "headToHead"),
                128, java.time.Duration.ofSeconds(10), new com.nbamanager.config.CacheFailOpenErrorHandler()); }
        @Bean MatchCacheGeneration matchCacheGeneration() { return new MatchCacheGeneration(); }
        @Bean MatchRecordService matches(MatchRecordRepository repository) { return new MatchRecordService(repository); }
        @Bean MatchCacheEvictionAspect invalidator(CacheManager manager, MatchCacheGeneration generation) { return new MatchCacheEvictionAspect(manager, generation); }
    }

    private MatchRecord game(String status) {
        var row = new MatchRecord(); row.setId(9L); row.setNbaGameId("0012600009");
        row.setHomeTeam("猛龙"); row.setAwayTeam("热火"); row.setStatus(status);
        row.setHomeScore(114); row.setAwayScore(144); row.setMatchDate(LocalDate.of(2026, 10, 6));
        row.markData("NBA_API", "2026-27", "PRESEASON", "homeScore", "awayScore", "status");
        return row;
    }

    @Test void singleGameEndpointDoesNotScanTheArchiveAndPreservesScheduledScoreRules() throws Exception {
        var repo = mock(MatchRecordRepository.class);
        when(repo.findByNbaGameId("0012600009")).thenReturn(Optional.of(game("SCHEDULED")));
        when(repo.findByNbaGameId("999")).thenReturn(Optional.empty());
        when(repo.findById(999L)).thenReturn(Optional.empty());
        var mvc = MockMvcBuilders.standaloneSetup(new MatchRecordController(new MatchRecordService(repo))).build();
        mvc.perform(get("/api/match-records/game/0012600009"))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("SCHEDULED"))
                .andExpect(jsonPath("homeScore").doesNotExist()).andExpect(jsonPath("awayScore").doesNotExist());
        mvc.perform(get("/api/match-records/game/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/match-records/game/invalid")).andExpect(status().isBadRequest());
        verify(repo, never()).findAllByOrderByMatchDateDesc();
        verify(repo, never()).findAll();
    }

    @Test void repositoryWritesInvalidateAnActuallyCachedRead() {
        try (var context = new AnnotationConfigApplicationContext(Wiring.class)) {
            var repo = context.getBean(MatchRecordRepository.class);
            var service = context.getBean(MatchRecordService.class);
            when(repo.findByNbaGameId("0012600009")).thenReturn(Optional.of(game("SCHEDULED")));
            assertThat(service.getByGameId("0012600009").homeScore()).isNull();
            service.getByGameId("0012600009");
            verify(repo, times(1)).findByNbaGameId("0012600009");
            when(repo.findByNbaGameId("0012600009")).thenReturn(Optional.of(game("LIVE")));
            repo.save(game("LIVE"));
            assertThat(service.getByGameId("0012600009").homeScore()).isEqualTo(114);
            verify(repo, times(2)).findByNbaGameId("0012600009");
        }
    }

    @Test void transactionChangesInvalidateOnceAfterCommitAndNeverAfterRollback() {
        var manager = mock(CacheManager.class);
        var cache = mock(org.springframework.cache.Cache.class);
        when(manager.getCache(anyString())).thenReturn(cache);
        var invalidator = new MatchCacheEvictionAspect(manager, new MatchCacheGeneration());
        for (boolean commit : new boolean[] { false, true }) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                invalidator.matchesChanged(); invalidator.matchesChanged();
                verify(cache, never()).invalidate();
                var callbacks = TransactionSynchronizationManager.getSynchronizations();
                assertThat(callbacks).hasSize(1);
                if (commit) callbacks.forEach(TransactionSynchronization::afterCommit);
                callbacks.forEach(callback -> callback.afterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
                assertThat(TransactionSynchronizationManager.hasResource(invalidator)).isFalse();
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
        verify(cache, times(8)).invalidate();
    }

    @Test void anOldInflightReadCannotReplaceTheNewGeneration() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Wiring.class)) {
            var repo = context.getBean(MatchRecordRepository.class);
            var service = context.getBean(MatchRecordService.class);
            var started = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            when(repo.findByNbaGameId("0012600009")).thenAnswer(call -> {
                if (reads.incrementAndGet() == 1) {
                    var old = game("SCHEDULED");
                    started.countDown();
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("read timed out");
                    return Optional.of(old);
                }
                return Optional.of(game("LIVE"));
            });
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var oldRead = executor.submit(() -> service.getByGameId("0012600009"));
                assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                repo.save(game("LIVE"));
                assertThat(service.getByGameId("0012600009").status()).isEqualTo("LIVE");
                release.countDown();
                assertThat(oldRead.get(5, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo("SCHEDULED");
                assertThat(service.getByGameId("0012600009").status()).isEqualTo("LIVE");
                assertThat(reads.get()).isEqualTo(2);
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test void aCommittedInnerTransactionInvalidatesEvenIfTheOuterOneRollsBack() {
        var manager = mock(CacheManager.class);
        var cache = mock(org.springframework.cache.Cache.class);
        when(manager.getCache(anyString())).thenReturn(cache);
        var invalidator = new MatchCacheEvictionAspect(manager, new MatchCacheGeneration());
        TransactionSynchronizationManager.initSynchronization();
        invalidator.matchesChanged();
        var outerCallbacks = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        try {
            invalidator.matchesChanged();
            var innerCallbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(innerCallbacks).hasSize(1);
            innerCallbacks.forEach(TransactionSynchronization::afterCommit);
            innerCallbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        outerCallbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(cache, times(8)).invalidate();
    }

    @Test void aSingleMatchDtoRoundTripsThroughTheProductionRedisSerializer() {
        var serializer = new com.nbamanager.config.RedisConfig(1800, 60, 60).jacksonRedisSerializer();
        var value = com.nbamanager.web.dto.MatchRecordDto.from(game("SCHEDULED"));
        assertThat(serializer.deserialize(serializer.serialize(value))).isEqualTo(value);
    }
}

package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * 第 9 轮 P0-2 回归：历史榜单缓存的行为契约。
 *
 * 该缓存此前是**死代码**（零调用点），本轮才接入读路径。接线后它第一次真正承受
 * 读写并发，因此这里把四件事钉死：
 * 1. 命中不查库、过期才查库（缓存确实生效）；
 * 2. evictAll 后立刻回源（同步完成即可见新数据）；
 * 3. 各排序 / 各 limit / 各赛季的键互不串（缓存键 = season|sortBy|limit）；
 * 4. **回填竞态**：查库期间发生 evictAll，本次结果不得写回缓存——
 *    否则同步刚完成就会被一个在途读请求用旧数据覆盖，且要等 TTL(30min) 才自愈。
 */
@ExtendWith(MockitoExtension.class)
class HistoricalStatsCacheTest {

    @Mock private PlayerSeasonStatsRepository playerStatsRepo;

    private HistoricalStatsCache cache;

    @BeforeEach
    void setUp() {
        cache = new HistoricalStatsCache(playerStatsRepo);
    }

    private static PlayerSeasonStats player(String name) {
        PlayerSeasonStats p = new PlayerSeasonStats();
        p.setSeason("2023-24");
        p.setPlayerName(name);
        return p;
    }

    @Test
    void secondReadIsServedFromCacheWithoutHittingRepository() {
        when(playerStatsRepo.findTopScorers(eq("2023-24"), any(Pageable.class)))
                .thenReturn(List.of(player("A")));

        List<PlayerSeasonStats> first = cache.topPlayers("2023-24", "pointsPerGame", 500);
        List<PlayerSeasonStats> second = cache.topPlayers("2023-24", "pointsPerGame", 500);

        assertThat(first).isSameAs(second);
        // 两次读只应产生一次查询——这正是"缓存生效"的直接证据
        verify(playerStatsRepo, times(1)).findTopScorers(eq("2023-24"), any(Pageable.class));
    }

    @Test
    void evictAllMakesNextReadQueryAgain() {
        when(playerStatsRepo.findTopScorers(eq("2023-24"), any(Pageable.class)))
                .thenReturn(List.of(player("old")))
                .thenReturn(List.of(player("new")));

        assertThat(cache.topPlayers("2023-24", "pointsPerGame", 500))
                .extracting(PlayerSeasonStats::getPlayerName).containsExactly("old");

        cache.evictAll();

        assertThat(cache.topPlayers("2023-24", "pointsPerGame", 500))
                .as("同步完成后必须立即看到新数据")
                .extracting(PlayerSeasonStats::getPlayerName).containsExactly("new");
    }

    @Test
    void eachSortAndLimitAndSeasonHasItsOwnKey() {
        when(playerStatsRepo.findTopScorers(eq("2023-24"), any(Pageable.class))).thenReturn(List.of(player("S")));
        when(playerStatsRepo.findTopRebounders(eq("2023-24"), any(Pageable.class))).thenReturn(List.of(player("R")));
        when(playerStatsRepo.findTopScorers(eq("2022-23"), any(Pageable.class))).thenReturn(List.of(player("P")));

        cache.topPlayers("2023-24", "pointsPerGame", 500);
        cache.topPlayers("2023-24", "reboundsPerGame", 500);
        cache.topPlayers("2023-24", "pointsPerGame", 100);
        cache.topPlayers("2022-23", "pointsPerGame", 500);

        // 同排序不同 limit 也算不同键（limit 影响结果集大小）
        verify(playerStatsRepo, times(2)).findTopScorers(eq("2023-24"), any(Pageable.class));
        verify(playerStatsRepo, times(1)).findTopRebounders(eq("2023-24"), any(Pageable.class));
        verify(playerStatsRepo, times(1)).findTopScorers(eq("2022-23"), any(Pageable.class));
        assertThat(cache.cacheSize()).isEqualTo(4);
    }

    /**
     * 回填竞态：查库期间 evictAll，旧快照不得写回。
     *
     * 时序：读者未命中 → 进入查库（被 latch 挂住）→ 同步完成 evictAll → 读者放行并尝试回填。
     * 期望：读者拿到的是它查到的数据（对本次请求正确），但**不写入缓存**，
     * 因此下一次读必须重新查库（而不是命中那份旧快照）。
     */
    @Test
    void staleSnapshotIsNotCachedWhenEvictedDuringQuery() throws Exception {
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);
        AtomicInteger queryCount = new AtomicInteger();

        when(playerStatsRepo.findTopScorers(eq("2023-24"), any(Pageable.class))).thenAnswer(inv -> {
            int n = queryCount.incrementAndGet();
            if (n == 1) {
                queryEntered.countDown();
                // 挂住第一次查库，让主线程在此期间完成 evictAll
                releaseQuery.await(5, TimeUnit.SECONDS);
                return List.of(player("stale"));
            }
            return List.of(player("fresh"));
        });

        AtomicReference<List<PlayerSeasonStats>> readerResult = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> readerResult.set(cache.topPlayers("2023-24", "pointsPerGame", 500)));

            assertThat(queryEntered.await(5, TimeUnit.SECONDS)).as("读者已进入查库").isTrue();
            cache.evictAll();          // 同步完成
            releaseQuery.countDown();

            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseQuery.countDown();
            pool.shutdownNow();
        }

        // 该读者自身拿到旧数据可以接受（它就是那一刻读的），但绝不能污染缓存
        assertThat(readerResult.get()).extracting(PlayerSeasonStats::getPlayerName)
                .containsExactly("stale");

        assertThat(cache.cacheSize())
                .as("查库期间被失效，旧快照不得回填")
                .isZero();

        // 下一次读必须重新查库并拿到新数据
        assertThat(cache.topPlayers("2023-24", "pointsPerGame", 500))
                .extracting(PlayerSeasonStats::getPlayerName).containsExactly("fresh");
        assertThat(queryCount.get()).isEqualTo(2);
    }
}

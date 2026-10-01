package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import com.nbamanager.service.HistoricalDataSyncService;
import com.nbamanager.service.HistoricalStatsCache;
import com.nbamanager.service.SeasonManagementService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

/**
 * 第 9 轮 P0-2 回归：历史榜单缓存**必须真的被读路径使用**。
 *
 * <h2>缺陷</h2>
 * {@code HistoricalDataController} 注入了 {@code historicalStatsCache}，但
 * {@code getPlayers()} 直接调 {@code playerStatsRepo.findTop*}，
 * {@code HistoricalStatsCache.topPlayers} 全仓库零调用点——缓存从未生效（死代码）。
 * 该端点因此相对基线劣化（599.9 RPS/p95 49.2ms → 363.4 RPS/p95 100.7ms），
 * 且报告把这归因为"传输下限"，掩盖了真实原因。
 *
 * <h2>本测试锁定的三件事</h2>
 * 1. 请求确实经由 {@code historicalStatsCache.topPlayers}（不再直连仓库）；
 * 2. 传参正确（season / sortBy / safeLimit），缓存键三要素齐全；
 * 3. 仓库的 5 个 findTop* 在该端点上**一次都不被调用**——
 *    这一条是关键：只要有人再把 controller 改回直连仓库，测试立刻失败。
 */
@ExtendWith(MockitoExtension.class)
class HistoricalDataControllerCacheWiringTest {

    @Mock private HistoricalDataSyncService syncService;
    @Mock private SeasonManagementService seasonService;
    @Mock private PlayerSeasonStatsRepository playerStatsRepo;
    @Mock private TeamSeasonStatsRepository teamStatsRepo;
    @Mock private HistoricalStatsCache historicalStatsCache;

    private HistoricalDataController controller;

    @BeforeEach
    void setUp() {
        controller = new HistoricalDataController(syncService, seasonService,
                playerStatsRepo, teamStatsRepo, historicalStatsCache);
    }

    @Test
    void getPlayersGoesThroughCache() {
        when(historicalStatsCache.topPlayers("2023-24", "pointsPerGame", 500))
                .thenReturn(List.of());

        controller.getPlayers("2023-24", "pointsPerGame", 500);

        verify(historicalStatsCache).topPlayers("2023-24", "pointsPerGame", 500);
    }

    /**
     * 关键断言：读路径不得直连仓库。
     *
     * 这正是死代码缺陷的形态——仓库方法被调用、缓存方法不被调用。
     */
    @Test
    void getPlayersNeverBypassesCacheToHitRepository() {
        when(historicalStatsCache.topPlayers("2023-24", "reboundsPerGame", 100))
                .thenReturn(List.of());

        controller.getPlayers("2023-24", "reboundsPerGame", 100);

        verify(playerStatsRepo, never()).findTopScorers(anyString(), any(Pageable.class));
        verify(playerStatsRepo, never()).findTopRebounders(anyString(), any(Pageable.class));
        verify(playerStatsRepo, never()).findTopAssisters(anyString(), any(Pageable.class));
        verify(playerStatsRepo, never()).findTopStealers(anyString(), any(Pageable.class));
        verify(playerStatsRepo, never()).findTopBlockers(anyString(), any(Pageable.class));
    }

    /** 每种排序都必须原样透传给缓存（缓存键含 sortBy，透传错会让不同榜单互相命中）。 */
    @Test
    void everySortOptionIsForwardedToCache() {
        for (String sortBy : List.of("pointsPerGame", "reboundsPerGame", "assistsPerGame",
                "stealsPerGame", "blocksPerGame")) {
            when(historicalStatsCache.topPlayers("2023-24", sortBy, 50)).thenReturn(List.of());

            controller.getPlayers("2023-24", sortBy, 50);

            verify(historicalStatsCache).topPlayers("2023-24", sortBy, 50);
        }
    }

    /** 未知 sortBy 走默认分支（pointsPerGame 语义）交给缓存处理，不在 controller 里散落分支。 */
    @Test
    void unknownSortByIsForwardedVerbatim() {
        when(historicalStatsCache.topPlayers("2023-24", "bogus", 50)).thenReturn(List.of());

        controller.getPlayers("2023-24", "bogus", 50);

        verify(historicalStatsCache).topPlayers("2023-24", "bogus", 50);
    }

    /**
     * limit 校验必须发生在进缓存之前（缓存键含 limit，且超大 limit 会把全表拉进内存）。
     */
    @Test
    void limitValidationHappensBeforeCacheLookup() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> controller.getPlayers("2023-24", "pointsPerGame", Integer.MAX_VALUE)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(historicalStatsCache, never()).topPlayers(anyString(), anyString(), anyInt());
    }

    /** 返回值原样透出（缓存不该改变响应形状）。 */
    @Test
    void cachedListIsReturnedAsIs() {
        PlayerSeasonStats row = new PlayerSeasonStats();
        row.setSeason("2023-24");
        row.setPlayerName("测试球员");
        when(historicalStatsCache.topPlayers("2023-24", "pointsPerGame", 10))
                .thenReturn(List.of(row));

        var response = controller.getPlayers("2023-24", "pointsPerGame", 10);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getPlayerName()).isEqualTo("测试球员");
    }
}

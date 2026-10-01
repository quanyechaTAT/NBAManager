package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.TeamSeasonStatsRepository;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 第 9 轮 P0-2 验收项：**同步成功路径必须失效历史榜单缓存**。
 *
 * 任务书验收标准原文："同步完成后 evict 生效：`HistoricalDataSyncService` 成功路径
 * 调 `evictAll()` 后新数据可见（补 1 条测试锁定）"。
 *
 * 该缓存是 30min TTL 的进程内缓存，若同步不主动失效，用户最长 30 分钟看到旧榜单——
 * 而榜单正是"赛季数据同步"的产物，这是最需要立即可见的场景。
 *
 * 另锁定：**失败/部分失败路径不得"假装成功"地失效后继续**，
 * 且同步中的互斥（syncing 标志）在异常时也会释放（finally）。
 */
@ExtendWith(MockitoExtension.class)
class HistoricalDataSyncCacheEvictionTest {

    @Mock private SeasonManagementService seasonService;
    @Mock private PlayerSeasonStatsRepository playerStatsRepo;
    @Mock private TeamSeasonStatsRepository teamStatsRepo;
    @Mock private PythonScriptRunner scriptRunner;
    @Mock private HistoricalStatsCache historicalStatsCache;
    @Mock private PlatformTransactionManager transactionManager;

    private HistoricalDataSyncService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalDataSyncService(seasonService, playerStatsRepo, teamStatsRepo,
                scriptRunner, historicalStatsCache);
    }

    private static JSONObject playersPayload(int count) {
        JSONArray players = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject p = new JSONObject();
            p.put("nbaPlayerId", 1000 + i);
            p.put("name", "球员" + i);
            p.put("teamName", "球队" + i);
            p.put("ppg", 20.5);
            players.put(p);
        }
        JSONObject root = new JSONObject();
        root.put("players", players);
        return root;
    }

    private static JSONObject teamsPayload(int count) {
        JSONArray teams = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject t = new JSONObject();
            t.put("teamName", "球队" + i);
            t.put("wins", 50);
            t.put("losses", 32);
            teams.put(t);
        }
        JSONObject root = new JSONObject();
        root.put("teams", teams);
        return root;
    }

    /**
     * 同步模式成功路径：球员+球队都拿到数据 → evictAll 必须被调用一次。
     */
    @Test
    void successfulSyncEvictsHistoricalStatsCache() {
        when(scriptRunner.execute(eq("historical_players"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(playersPayload(3));
        when(scriptRunner.execute(eq("historical_teams"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(teamsPayload(2));
        when(playerStatsRepo.findByNbaPlayerIdAndSeason(anyLong(), eq("2023-24")))
                .thenReturn(java.util.Optional.empty());
        when(teamStatsRepo.findByTeamNameAndSeason(anyString(), eq("2023-24")))
                .thenReturn(java.util.Optional.empty());

        service.syncSeasonDataSync("2023-24", null);

        verify(historicalStatsCache, times(1)).evictAll();
        verify(seasonService).updateSyncStatus("2023-24", "COMPLETED", 3, 2);
    }

    /**
     * 失败路径：脚本返回空 → 抛异常，且**不得**失效缓存（没有新数据就没有失效的理由，
     * 更重要的是不能掩盖失败）。
     *
     * 注：FAILED 状态实际会被写两次（114 行"数据为空"分支 + 133 行 catch 兜底），
     * 属幂等的重复写入、行为正确；这里不断言精确次数，只断言确实是 FAILED
     * 且没有误报 COMPLETED。
     */
    @Test
    void failedSyncDoesNotEvictCache() {
        when(scriptRunner.execute(eq("historical_players"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(new JSONObject());
        when(scriptRunner.execute(eq("historical_teams"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(new JSONObject());

        assertThatThrownBy(() -> service.syncSeasonDataSync("2023-24", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("历史赛季数据为空");

        verify(historicalStatsCache, never()).evictAll();
        verify(seasonService, atLeastOnce()).updateSyncStatus("2023-24", "FAILED", 0, 0);
        verify(seasonService, never()).updateSyncStatus(eq("2023-24"), eq("COMPLETED"), anyInt(), anyInt());
    }

    /** 部分失败（只有球员、没有球队）→ 不失效；状态记 PARTIAL。 */
    @Test
    void partialSyncDoesNotEvictCache() {
        when(scriptRunner.execute(eq("historical_players"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(playersPayload(2));
        when(scriptRunner.execute(eq("historical_teams"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(new JSONObject());
        when(playerStatsRepo.findByNbaPlayerIdAndSeason(anyLong(), eq("2023-24")))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.syncSeasonDataSync("2023-24", null))
                .isInstanceOf(PartialSyncException.class);

        verify(historicalStatsCache, never()).evictAll();
        verify(seasonService).updateSyncStatus("2023-24", "PARTIAL", 2, 0);
    }

    /** 异常后 syncing 必须释放，否则后续所有同步都会被"正在进行中"挡掉。 */
    @Test
    void syncingFlagIsReleasedAfterFailure() {
        when(scriptRunner.execute(eq("historical_players"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(new JSONObject());
        when(scriptRunner.execute(eq("historical_teams"), aryEq(new String[]{"2023-24"}), anyInt()))
                .thenReturn(new JSONObject());

        assertThatThrownBy(() -> service.syncSeasonDataSync("2023-24", null))
                .isInstanceOf(RuntimeException.class);

        // 第一次失败后标志应已释放：再次调用会重新执行（拿到同样的空数据 → 再抛一次），
        // 而不是抛 IllegalStateException("正在进行中")
        assertThatThrownBy(() -> service.syncSeasonDataSync("2023-24", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("历史赛季数据为空");
    }
}

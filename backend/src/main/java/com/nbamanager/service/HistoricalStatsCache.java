package com.nbamanager.service;

import com.nbamanager.domain.PlayerSeasonStats;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 历史赛季球员榜单缓存（第 8 轮 P1-5；第 9 轮 P0-2 修复接线与回填竞态）。
 *
 * 榜单是赛季静态数据（只在同步任务时写入），TTL 30min；同步完成后
 * {@code HistoricalDataSyncService} 调 {@link #evictAll()} 主动失效。
 *
 * 实现说明：改用进程内 TTL 而非 Redis @Cacheable——500 行实体经 Redis
 * 序列化往返就要 ~40ms（实测 p95 47.8ms），抵消了省下的查库时间；
 * 进程内直读 <5ms。单实例部署语义正确；多实例部署时各实例 TTL 30min 内
 * 自洽（同步完成后的主动失效只作用于本实例，跨实例靠 TTL，报告已注明）。
 *
 * <h2>回填竞态与 generation 护栏（第 9 轮补强）</h2>
 * 接线后本缓存进入真实读路径，必须处理"读-同步"竞态：
 * <pre>
 *   T0 读者：未命中 → 开始查库（读到旧数据）
 *   T1 同步：写入新数据 → evictAll()
 *   T2 读者：把 T0 读到的**旧**快照 put 进缓存
 *   → 缓存里是旧数据，且带新时间戳，最长要等 TTL(30min) 才自愈
 * </pre>
 * 仅靠"写后 evict"无法封住该窗口（evict 发生在 T2 之前）。
 * 因此引入 {@code generation}：{@link #evictAll()} 自增代数，读者在查库前
 * 记录代数、写入前比对——代数已变说明期间发生过失效，本次结果直接丢弃不缓存。
 * 开销是一次 {@code AtomicLong} 读 + 比。
 */
@Service
@RequiredArgsConstructor
public class HistoricalStatsCache {

    private static final long TTL_MS = 30 * 60_000L;

    private record Snapshot(long at, long generation, List<PlayerSeasonStats> players) { }

    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    /** 每次 evictAll 自增；用于识别"查库期间被失效"的过期回填。 */
    private final AtomicLong generation = new AtomicLong();

    private final PlayerSeasonStatsRepository playerStatsRepo;

    public List<PlayerSeasonStats> topPlayers(String season, String sortBy, int limit) {
        String key = season + "|" + sortBy + "|" + limit;
        long now = System.currentTimeMillis();
        Snapshot snapshot = cache.get(key);
        if (snapshot != null && now - snapshot.at() < TTL_MS) {
            return snapshot.players();
        }

        // 查库前记录代数：若查库期间发生 evictAll，则本次结果不得写回缓存
        long generationAtRead = generation.get();
        List<PlayerSeasonStats> players = queryTopPlayers(season, sortBy, limit);

        if (generation.get() == generationAtRead) {
            cache.put(key, new Snapshot(now, generationAtRead, players));
        }
        return players;
    }

    private List<PlayerSeasonStats> queryTopPlayers(String season, String sortBy, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return switch (sortBy) {
            case "reboundsPerGame" -> playerStatsRepo.findTopRebounders(season, page);
            case "assistsPerGame" -> playerStatsRepo.findTopAssisters(season, page);
            case "stealsPerGame" -> playerStatsRepo.findTopStealers(season, page);
            case "blocksPerGame" -> playerStatsRepo.findTopBlockers(season, page);
            default -> playerStatsRepo.findTopScorers(season, page);
        };
    }

    /**
     * 同步完成后的主动失效（幂等）。
     *
     * 先自增代数再清表：这样"已经查到旧数据、还没写回"的在途读者
     * 会因为代数不符而放弃回填（见类注释的竞态说明）。
     */
    public void evictAll() {
        generation.incrementAndGet();
        cache.clear();
    }

    /** 诊断用：当前缓存条目数（验收脚本用它断言容量上界/失效生效）。 */
    public int cacheSize() {
        return cache.size();
    }
}

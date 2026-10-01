package com.nbamanager.service;

import com.nbamanager.web.dto.GameNewsDto;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 新闻列表短 TTL 缓存（第 8 轮 P1-4；第 9 轮 P1-3 补上容量上界与页号护栏）。
 *
 * 失效网：NewsCacheEvictionAspect 拦截 GameNewsRepository 的全部写方法
 * （save 系、delete 系、bump 系，覆盖 GameNewsService CRUD、NbaLiveSyncService、
 * NewsPersistenceService、SmartDataService 等所有写方——见该切面注释），写后主动失效。
 *
 * 结构与 PlayerListCache 同型：
 * - 缓存入口独立 bean（对象间经 ObjectProvider 解耦，避免构造循环）；
 * - 缓存 PagePayload 而非 Page（PageImpl 无默认构造器，缓存命中即 500——
 *   CacheableReturnTypeGuardTest 的护栏语义）；
 * - 缓存的是"用户无关基座"（favoritedByMe=false + favoriteCount 全局值），
 *   每用户的收藏标记由 GameNewsService.list 读后装饰。
 *
 * 实现说明：进程内 TTL（60s）而非 Redis——85KB 载荷的 Redis 序列化往返 ~5ms
 * 会吃掉大半收益；进程内直读个位数 ms。写后失效由切面直调 {@link #evictAll()}，
 * 并在事务提交后再失效一次（封住回填竞态，见切面）。多实例部署时失效只作用
 * 本实例、其余实例靠 TTL 60s 自洽。
 *
 * <h2>为什么必须同时有"页号护栏"和"容量上界"（第 9 轮 P1-3）</h2>
 * 此前本类注释声称"深翻页（page &gt;= 20）不缓存，防 key 无界增长"，
 * 但实现是无条件 {@code cache.put(...)}——既无页号判断、也无容量上界、也无过期清理。
 * 测试方用 {@code jcmd GC.class_histogram} 直读存活对象证实：
 * 每发一个唯一 {@code ?q=} 就恰好 +1 个 Snapshot 实例（300 个 → +300），1:1 线性增长。
 * {@code q} 是用户可控的任意字符串，爬虫/攻击者可持续写入条目（每条目持有整页 DTO），
 * 构成内存增长面；而"注释声称已有护栏"会误导后续安全审计。
 *
 * 因此两道护栏&lt;b&gt;都要&lt;/b&gt;（页号护栏管不住 {@code q} 维度）：
 * <ol>
 *   <li><b>页号护栏</b>：{@code pageNumber >= MAX_CACHED_PAGE} 直接回源、不写缓存
 *       （与 PostListCache/PlayerListCache 的 {@code condition = "#pageable.pageNumber < 20"} 对齐）；</li>
 *   <li><b>容量上界</b>：{@link #MAX_ENTRIES} 条封顶，超限时先清过期、仍超限则整表收缩
 *       （与 {@code UserIdentityCache.purge()} 同一范式）。</li>
 * </ol>
 */
@Service
public class NewsListCache {

    /** 缓存值：只含可被 Jackson 直接往返的纯数据。 */
    public record PagePayload(List<GameNewsDto> content, long totalElements) {}

    private record Snapshot(long at, PagePayload payload) { }

    private static final long TTL_MS = 60_000;

    /**
     * 深翻页不缓存的上界（与 PostListCache/PlayerListCache 的 condition 语义一致）：
     * 命中率极低，只会让 key 无界增长。
     */
    static final int MAX_CACHED_PAGE = 20;

    /**
     * 条目数上界。
     *
     * 取值依据：正常业务下被缓存的键 = "搜索词 × 分页排序"组合。真实用户在 60s TTL 内
     * 产生的不同搜索词是数十量级；1000 条足以覆盖正常流量并留足余量，
     * 同时把"恶意唯一搜索词"造成的上限内存钉死在 1000 × 单页载荷
     * （实测单页 ~3.4 项 DTO，量级为百 KB，可接受）。
     */
    static final int MAX_ENTRIES = 1000;

    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    private final ObjectProvider<GameNewsService> gameNewsService;

    /** 测试可注入时钟，便于验证过期清理而不必真的等待 TTL。 */
    private final LongSupplier clock;

    /**
     * 生产构造器。
     *
     * 必须显式标注 {@code @Autowired}：本类另有一个包级可见的测试构造器（用于注入时钟），
     * 存在两个构造器时 Spring 不会再推断注入哪一个，会转而寻找默认构造器并启动失败
     * （实测：`No default constructor found`，应用起不来）。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public NewsListCache(ObjectProvider<GameNewsService> gameNewsService) {
        this(gameNewsService, System::currentTimeMillis);
    }

    NewsListCache(ObjectProvider<GameNewsService> gameNewsService, LongSupplier clock) {
        this.gameNewsService = gameNewsService;
        this.clock = clock;
    }

    /**
     * 取新闻列表（用户无关基座）。
     *
     * 键含 keyword 与完整 pageable（分页+排序），因此不同搜索词/排序/页码互不命中。
     * 页号 {@code >= MAX_CACHED_PAGE} 时不读也不写缓存，直接回源。
     */
    public PagePayload load(String keyword, Pageable pageable) {
        long now = clock.getAsLong();
        String key = (keyword == null ? "" : keyword) + "|" + pageable;

        // 深翻页：不缓存（命中率极低，只会撑大 key 空间）
        boolean cacheable = pageable == null || pageable.getPageNumber() < MAX_CACHED_PAGE;
        if (!cacheable) {
            return gameNewsService.getObject().queryNewsBasePage(keyword, pageable);
        }

        Snapshot snapshot = cache.get(key);
        if (snapshot != null && now - snapshot.at() < TTL_MS) {
            return snapshot.payload();
        }

        PagePayload payload = gameNewsService.getObject().queryNewsBasePage(keyword, pageable);
        putBounded(key, new Snapshot(now, payload));
        return payload;
    }

    /** 写入前执行容量控制：超限先清过期，仍超限则整表收缩（正确性优先于命中率）。 */
    private void putBounded(String key, Snapshot snapshot) {
        if (cache.size() >= MAX_ENTRIES && !cache.containsKey(key)) {
            purge();
        }
        cache.put(key, snapshot);
    }

    /** 超限时先清过期；仍超限则整表收缩（下次请求重新加载，正确性优先于命中率）。 */
    private void purge() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<String, Snapshot>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().at() >= TTL_MS) {
                it.remove();
            }
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
    }

    /** 失效网入口（NewsCacheEvictionAspect 在仓库写方法后调用，幂等）。 */
    public void evictAll() {
        cache.clear();
    }

    /** 诊断用：当前缓存条目数（验收脚本/测试用它断言容量上界）。 */
    public int cacheSize() {
        return cache.size();
    }

    /** 诊断用：条目数上界。 */
    public int maxEntries() {
        return MAX_ENTRIES;
    }
}

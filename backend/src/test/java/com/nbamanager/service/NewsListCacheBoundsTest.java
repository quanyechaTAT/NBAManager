package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.web.dto.GameNewsDto;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 第 9 轮 P1-3 回归：{@code NewsListCache} 的页号护栏与容量上界。
 *
 * <h2>缺陷</h2>
 * 类注释声称"深翻页（page &gt;= 20）不缓存，防 key 无界增长"，
 * 但实现是无条件 {@code cache.put(...)}：既无页号判断、也无容量上界、也无过期清理。
 * 测试方用 {@code jcmd GC.class_histogram} 直读存活对象证实"每发一个唯一 {@code ?q=}
 * 就恰好 +1 个 Snapshot 实例"（200→226、再 400→634、再 300→942），1:1 线性增长。
 * {@code q} 用户可控 ⇒ 可持续写入条目构成内存增长面；注释与实现不符还会误导审计。
 *
 * <h2>本测试锁定</h2>
 * 1. 深翻页不写缓存（页号护栏）；
 * 2. 条目数有明确上界，且"大量唯一搜索词"不会线性撑爆（容量上界）——
 *    直接复现测试方的增长场景，断言最终条目数被钉在 {@code MAX_ENTRIES}；
 * 3. 过期条目在超限清理时会被真正淘汰；
 * 4. 正常命中语义（同键第二次不查库）与写后失效语义不回归。
 */
@ExtendWith(MockitoExtension.class)
class NewsListCacheBoundsTest {

    @Mock private GameNewsService gameNewsService;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private NewsListCache cache;

    @BeforeEach
    void setUp() {
        ObjectProvider<GameNewsService> provider = new ObjectProvider<>() {
            @Override
            public GameNewsService getObject() {
                return gameNewsService;
            }

            @Override
            public GameNewsService getObject(Object... args) {
                return gameNewsService;
            }

            @Override
            public GameNewsService getIfAvailable() {
                return gameNewsService;
            }

            @Override
            public GameNewsService getIfUnique() {
                return gameNewsService;
            }
        };
        cache = new NewsListCache(provider, now::get);
        when(gameNewsService.queryNewsBasePage(any(), any(Pageable.class)))
                .thenReturn(new NewsListCache.PagePayload(List.<GameNewsDto>of(), 0L));
    }

    private static Pageable page(int number) {
        return PageRequest.of(number, 20, Sort.by(Sort.Direction.DESC, "createTime"));
    }

    // ------------------------------------------------------------ 页号护栏
    @Test
    void deepPagesAreNotCached() {
        cache.load("q", page(NewsListCache.MAX_CACHED_PAGE));

        assertThat(cache.cacheSize())
                .as("page >= MAX_CACHED_PAGE 不得写入缓存")
                .isZero();
    }

    @Test
    void lastCacheablePageIsStillCached() {
        cache.load("q", page(NewsListCache.MAX_CACHED_PAGE - 1));

        assertThat(cache.cacheSize()).isEqualTo(1);
    }

    @Test
    void deepPageAlwaysGoesToDatabaseEvenWhenRepeated() {
        // 深翻页既不读缓存也不写缓存：两次请求都应回源
        cache.load("q", page(100));
        cache.load("q", page(100));

        verify(gameNewsService, times(2)).queryNewsBasePage(any(), any(Pageable.class));
        assertThat(cache.cacheSize()).isZero();
    }

    // ------------------------------------------------------------ 容量上界
    /**
     * 直接复现测试方的攻击场景：大量**唯一搜索词**（用户可控）逐条写入。
     *
     * 修复前：条目数与请求数 1:1 增长（300 → +300）；
     * 修复后：条目数被钉在 MAX_ENTRIES 上界内，不随请求数线性增长。
     */
    @Test
    void uniqueKeywordsCannotGrowCacheWithoutBound() {
        int attempts = NewsListCache.MAX_ENTRIES + 500;

        for (int i = 0; i < attempts; i++) {
            cache.load("unique-keyword-" + i, page(0));
        }

        assertThat(cache.cacheSize())
                .as("唯一搜索词不得让缓存无界增长（修复前为 %d 条）", attempts)
                .isLessThanOrEqualTo(NewsListCache.MAX_ENTRIES);
        assertThat(cache.maxEntries()).isEqualTo(NewsListCache.MAX_ENTRIES);
    }

    /** 上界是"软"的：不是在恰好 MAX_ENTRIES 时硬拒写，而是整表收缩后继续服务。 */
    @Test
    void cacheStillServesAfterHittingTheBound() {
        for (int i = 0; i < NewsListCache.MAX_ENTRIES + 10; i++) {
            cache.load("k" + i, page(0));
        }

        int sizeAfterPurge = cache.cacheSize();
        assertThat(sizeAfterPurge).isLessThanOrEqualTo(NewsListCache.MAX_ENTRIES);

        // 继续写入不能抛异常，且仍会缓存
        cache.load("after-bound", page(0));
        assertThat(cache.cacheSize()).isPositive();
    }

    /** 超限清理优先淘汰**已过期**条目（而不是无差别整表清空）。 */
    @Test
    void purgeRemovesExpiredEntriesFirst() {
        // 先塞满并让它们"过期"
        for (int i = 0; i < NewsListCache.MAX_ENTRIES; i++) {
            cache.load("old" + i, page(0));
        }
        assertThat(cache.cacheSize()).isEqualTo(NewsListCache.MAX_ENTRIES);

        // 时间推过 TTL
        now.addAndGet(61_000);

        // 触发一次超限写入 → 过期条目应被清掉，而不是整表清空后只剩 1 条
        cache.load("trigger", page(0));

        assertThat(cache.cacheSize())
                .as("过期条目应被清理，新条目正常写入")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------ 语义不回归
    @Test
    void repeatedReadOfSameKeyHitsCache() {
        cache.load("湖人", page(0));
        cache.load("湖人", page(0));

        verify(gameNewsService, times(1)).queryNewsBasePage(any(), any(Pageable.class));
    }

    @Test
    void differentKeywordsSortsAndPagesDoNotCollide() {
        cache.load("湖人", page(0));
        cache.load("勇士", page(0));
        cache.load("湖人", page(1));
        cache.load("湖人", PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createTime")));

        assertThat(cache.cacheSize()).as("四组不同键应各自成条").isEqualTo(4);
    }

    @Test
    void nullKeywordIsHandledAndDistinctFromEmptyKeyword() {
        cache.load(null, page(0));

        assertThat(cache.cacheSize()).isEqualTo(1);
        verify(gameNewsService).queryNewsBasePage(any(), any(Pageable.class));
    }

    /** 写后失效语义不回归（建/改/删后必须立即回源）。 */
    @Test
    void evictAllForcesFreshQuery() {
        cache.load("湖人", page(0));
        verify(gameNewsService, times(1)).queryNewsBasePage(any(), any(Pageable.class));

        cache.evictAll();
        assertThat(cache.cacheSize()).isZero();

        cache.load("湖人", page(0));
        verify(gameNewsService, times(2)).queryNewsBasePage(any(), any(Pageable.class));
    }

    /** 失效后旧键不应残留（整表清空语义，防"改完还是旧新闻"）。 */
    @Test
    void evictAllClearsEveryKey() {
        for (int i = 0; i < 50; i++) {
            cache.load("k" + i, page(0));
        }
        assertThat(cache.cacheSize()).isEqualTo(50);

        cache.evictAll();

        assertThat(cache.cacheSize()).isZero();
    }

    /** 同键在 TTL 内不重复写入（避免每条请求都覆盖、无谓开销）。 */
    @Test
    void ttlExpiryTriggersReload() {
        cache.load("湖人", page(0));
        now.addAndGet(61_000);   // 超过 TTL
        cache.load("湖人", page(0));

        verify(gameNewsService, times(2)).queryNewsBasePage(anyString(), any(Pageable.class));
    }
}

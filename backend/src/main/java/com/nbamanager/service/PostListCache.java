package com.nbamanager.service;

import com.nbamanager.web.dto.PostDto;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 帖子列表缓存（压测报告 §十 10.3 建议 3 的最后一个未落地项）。
 *
 * <h2>为什么缓存的是"用户无关基座"</h2>
 * 列表返回里唯一随当前用户变化的字段是 {@code likedByMe}（{@code PostDto} 里没有
 * favoritedByMe；收藏只体现为全局的 favoriteCount）。因此这里缓存<b>不含任何用户状态</b>
 * 的基座数据（likedByMe 恒为 false），由 {@link PostService#list} 在返回前按当前用户
 * 覆盖一次。这样：
 * <ul>
 *   <li>缓存键里<b>不能</b>出现 userId —— 否则每个用户一份缓存，命中率归零；</li>
 *   <li>也不可能把 A 用户的点赞状态泄漏给 B 用户 —— 缓存值里根本没有这个字段为 true 的可能，
 *       由 {@code PostListCacheTest} 锁定该不变量。</li>
 * </ul>
 *
 * <h2>为什么单独一个 bean</h2>
 * {@code @Cacheable} 靠代理生效：写在 PostService 内部自调用不会走缓存。与
 * {@link PlayerListCache} 同一模式（含用 {@link ObjectProvider} 断开
 * PostService ↔ PostListCache 的循环依赖）。
 *
 * <h2>为什么缓存 {@link PagePayload} 而不是 Page</h2>
 * 与 PlayerListCache 同一个已实测的坑：Redis 用 GenericJackson2JsonRedisSerializer，
 * {@code PageImpl} 写进去没问题，读回来时 Jackson 没有可用的构造器
 * （"Cannot construct instance of PageImpl (no Creators...)"），表现为"缓存命中即 500"。
 * 所以只缓存纯数据容器，PageImpl 由调用方重建。
 * {@code CacheableReturnTypeGuardTest} 会阻止这个错误再次出现。
 *
 * <h2>失效</h2>
 * 写入路径都已显式失效该缓存：发帖/改帖/删帖（PostService）、评论（CommentService）、
 * 点赞（PostService.toggleLike）、收藏（FavoriteService.toggle）、
 * 改用户名/头像（UserIdentityEventListener，AFTER_COMMIT）。TTL 只是兜底
 * （例如运维直接改库、或将来新增写路径漏了失效）。
 */
@Service
public class PostListCache {

    /** 缓存值：只含可被 Jackson 直接往返的纯数据（likedByMe 一律 false）。 */
    public record PagePayload(List<PostDto> content, long totalElements) {}

    private final ObjectProvider<PostService> postService;

    public PostListCache(ObjectProvider<PostService> postService) {
        this.postService = postService;
    }

    /**
     * 键覆盖全部影响结果的入参：分类、搜索词、标签、分页与排序。
     * 任一缺失都会让不同筛选共用同一条缓存（例如换排序仍是旧顺序）。
     *
     * 深翻页（pageNumber >= 20）不缓存：命中率极低，避免 key 无界增长。
     */
    @Cacheable(value = "posts",
            key = "(#category == null ? '' : #category) + '|' + (#keyword == null ? '' : #keyword)"
                    + " + '|' + (#tag == null ? '' : #tag) + '|' + #pageable",
            condition = "#pageable.pageNumber < 20")
    public PagePayload load(String category, String keyword, String tag, Pageable pageable) {
        return postService.getObject().queryBaseListPage(category, keyword, tag, pageable);
    }
}

package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.config.RedisConfig;
import com.nbamanager.event.UserIdentityEventListener;
import com.nbamanager.web.dto.PostDto;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 帖子列表缓存回归（压测报告 §十 10.3 建议 3 的最后一个未落地项）。
 *
 * 锁定四件事：
 * 1. 列表确实带缓存，且键覆盖全部影响结果的入参（分类/关键字/标签/分页排序）；
 * 2. 键里<b>不能</b>有 userId —— 缓存值本身与用户无关，是"跨用户共享"的前提；
 * 3. 所有会改变列表内容的写路径都失效该缓存，否则写后读到旧数据；
 * 4. 缓存值必须能被真实 Redis 序列化器往返（这是本项目已经踩过的坑：
 *    缓存 PageImpl 时"写入成功、读取失败"，表现为首次 200、之后全 500）。
 */
class PostListCacheTest {

    private static Method loadMethod() throws Exception {
        return PostListCache.class.getMethod("load", String.class, String.class, String.class, Pageable.class);
    }

    @Test
    void listIsCacheable() throws Exception {
        Cacheable cacheable = loadMethod().getAnnotation(Cacheable.class);

        assertThat(cacheable).as("帖子列表必须带缓存").isNotNull();
        assertThat(cacheable.value()).containsExactly("posts");
    }

    @Test
    void cacheKeyCoversAllFilterParametersAndNoUserState() throws Exception {
        String key = loadMethod().getAnnotation(Cacheable.class).key();

        // 缺任何一个入参都会让不同筛选条件共用同一条缓存
        assertThat(key).contains("#category").contains("#keyword").contains("#tag").contains("#pageable");
        // 键里一旦出现 userId，就变成"每用户一份"，命中率归零（用户状态是靠返回前覆盖的）
        assertThat(key).doesNotContain("userId");
    }

    @Test
    void cacheKeyEvaluatesForNullFiltersAndDistinguishesRequests() throws Exception {
        Method method = loadMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        SpelExpressionParser parser = new SpelExpressionParser();

        Pageable firstPage = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        String baseline = evaluateKey(parser, method, cacheable, null, null, null, firstPage);

        assertThat(baseline).as("null 筛选条件也要能求值").isNotNull();

        // 每个筛选维度都必须产生不同的键
        assertThat(evaluateKey(parser, method, cacheable, "NEWS", null, null, firstPage))
                .isNotEqualTo(baseline);
        assertThat(evaluateKey(parser, method, cacheable, null, "湖人", null, firstPage))
                .isNotEqualTo(baseline);
        assertThat(evaluateKey(parser, method, cacheable, null, null, "季后赛", firstPage))
                .isNotEqualTo(baseline);

        // 排序 / 页码不同也必须不同（否则换排序会拿到旧顺序、翻页拿到上一页）
        Pageable otherSort = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "viewCount"));
        assertThat(evaluateKey(parser, method, cacheable, null, null, null, otherSort))
                .isNotEqualTo(baseline);
        assertThat(evaluateKey(parser, method, cacheable, null, null, null, PageRequest.of(1, 10)))
                .isNotEqualTo(baseline);

        // 空白筛选与不传筛选语义相同，共用一条缓存是正确的
        assertThat(evaluateKey(parser, method, cacheable, "", "", "", firstPage)).isEqualTo(baseline);
    }

    /** 深翻页不缓存：命中率极低，避免 key 无界增长。 */
    @Test
    void deepPagesAreNotCached() throws Exception {
        Method method = loadMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        SpelExpressionParser parser = new SpelExpressionParser();

        assertThat(evaluateCondition(parser, method, cacheable, PageRequest.of(0, 10)))
                .as("首页应进缓存").isTrue();
        assertThat(evaluateCondition(parser, method, cacheable, PageRequest.of(19, 10)))
                .as("第 20 页仍应进缓存").isTrue();
        assertThat(evaluateCondition(parser, method, cacheable, PageRequest.of(20, 10)))
                .as("第 21 页起不缓存").isFalse();
    }

    /**
     * 失效审计：任何会改变列表返回内容的写路径都必须失效 "posts"。
     *
     * 列表基座里含 title/content/category/tags、likeCount/commentCount/favoriteCount、
     * lastReplyTime、username/avatarUrl，因此发帖/改帖/删帖/评论/点赞/收藏/改身份都要覆盖。
     */
    @Test
    void everyWritePathThatChangesListContentEvictsPostListCache() {
        assertMethodEvicts(PostService.class, "create");
        assertMethodEvicts(PostService.class, "update");
        assertMethodEvicts(PostService.class, "delete");
        assertMethodEvicts(PostService.class, "cleanupOldPosts");
        // 点赞改变 likeCount 与当前用户的 likedByMe（此前漏了失效，会让前端点完赞仍显示旧计数）
        assertMethodEvicts(PostService.class, "toggleLike");
        // 评论改变 commentCount / lastReplyTime
        assertMethodEvicts(CommentService.class, "create");
        assertMethodEvicts(CommentService.class, "delete");
        // 收藏改变 favoriteCount
        assertMethodEvicts(FavoriteService.class, "toggle");
        // 改用户名/头像改变列表里的 username/avatarUrl（AFTER_COMMIT 统一出口）
        assertMethodEvicts(UserIdentityEventListener.class, "onUserIdentityUpdated");
    }

    /**
     * 用应用真实的 Redis 序列化器做一次往返。
     *
     * 缓存值类型一旦不能往返，就会表现为"缓存命中即 500"（比不缓存更糟），
     * 所以这里用与生产同一个 ObjectMapper 验证。
     */
    @Test
    void cachePayloadSurvivesRedisSerializerRoundTrip() {
        RedisConfig config = new RedisConfig(1800, 60, 60);
        var serializer = config.jacksonRedisSerializer();

        PostDto dto = new PostDto(1L, 3L, "user1", "/uploads/avatars/a.png",
                "预测马刺夺冠", "文班亚马太强了", "PREDICTION", "马刺",
                63, 5, 2, 1, false, false, false,
                false, LocalDateTime.of(2026, 1, 2, 3, 4), LocalDateTime.of(2026, 1, 1, 0, 0));

        PostListCache.PagePayload original = new PostListCache.PagePayload(List.of(dto), 1L);

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored)
                .as("缓存值必须能被真实序列化器还原，否则缓存命中即 500")
                .isInstanceOf(PostListCache.PagePayload.class);
        PostListCache.PagePayload payload = (PostListCache.PagePayload) restored;
        assertThat(payload.totalElements()).isEqualTo(1L);
        assertThat(payload.content()).hasSize(1);
        PostDto restoredDto = payload.content().get(0);
        assertThat(restoredDto.id()).isEqualTo(1L);
        assertThat(restoredDto.username()).isEqualTo("user1");
        assertThat(restoredDto.title()).isEqualTo("预测马刺夺冠");
        assertThat(restoredDto.commentCount()).isEqualTo(2);
        assertThat(restoredDto.lastReplyTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(restoredDto.likedByMe()).isFalse();
    }

    private static String evaluateKey(SpelExpressionParser parser, Method method, Cacheable cacheable,
                                      String category, String keyword, String tag, Pageable pageable) {
        var context = new MethodBasedEvaluationContext(
                null, method, new Object[]{category, keyword, tag, pageable}, new DefaultParameterNameDiscoverer());
        return parser.parseExpression(cacheable.key()).getValue(context, String.class);
    }

    private static Boolean evaluateCondition(SpelExpressionParser parser, Method method, Cacheable cacheable,
                                             Pageable pageable) {
        var context = new MethodBasedEvaluationContext(
                null, method, new Object[]{null, null, null, pageable}, new DefaultParameterNameDiscoverer());
        return parser.parseExpression(cacheable.condition()).getValue(context, Boolean.class);
    }

    private static void assertMethodEvicts(Class<?> type, String methodName) {
        List<Method> methods = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .toList();

        assertThat(methods).as("%s.%s 必须存在", type.getSimpleName(), methodName).isNotEmpty();
        for (Method method : methods) {
            CacheEvict evict = method.getAnnotation(CacheEvict.class);
            assertThat(evict)
                    .as("%s.%s 必须失效 posts，否则写后读到旧列表", type.getSimpleName(), methodName)
                    .isNotNull();
            assertThat(evict.value())
                    .as("%s.%s 的 @CacheEvict 必须包含 posts", type.getSimpleName(), methodName)
                    .contains("posts");
        }
    }
}

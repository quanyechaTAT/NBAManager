package com.nbamanager.service;

import com.nbamanager.domain.Poll;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.PostRequest;
import com.nbamanager.web.dto.TopicDto;
import com.nbamanager.web.support.PageRequestSanitizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PostService {

    private static final Map<String, String> SORT_ALIASES = Map.of(
            "createdAt", "createTime",
            "updatedAt", "updateTime"
    );
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "title", "category", "viewCount", "likeCount", "commentCount", "favoriteCount",
            "isTop", "isFeatured", "lastReplyTime", "createTime", "updateTime"
    );

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserBrowseHistoryRepository userBrowseHistoryRepository;
    private final NotificationService notificationService;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;
    private final UserAccountRepository userAccountRepository;
    private final PlatformTransactionManager transactionManager;
    private final PostListCache postListCache;
    private final com.nbamanager.service.support.ViewCountBuffer viewCountBuffer;

    /** 同帖并发切换的最大尝试次数（首次 + 重试）。翻转语义下重试安全。 */
    private static final int TOGGLE_MAX_ATTEMPTS = 3;

    /**
     * 帖子列表。
     *
     * 读路径（压测报告 §十 10.3 建议 3 的最后一个未落地项）：
     * <ol>
     *   <li>用户无关的基座数据由 {@link PostListCache} 承接，键 = 分类|关键字|标签|分页排序，
     *       因此缓存条目可以<b>跨用户共享</b>；</li>
     *   <li>唯一随用户变化的 {@code likedByMe} 在返回前按当前用户覆盖一次
     *       （只查本页 id 范围，不再拉取该用户的全部点赞）；</li>
     *   <li>基座构建时把"每行一次 COUNT / 每行一次取最新评论"收敛成两条批量查询。</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public Page<PostDto> list(String category, String keyword, String tag, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        PostListCache.PagePayload payload = postListCache.load(category, keyword, tag, safePageable);
        List<PostDto> content = decorateLikedByMe(payload.content(), getCurrentUserId());
        return new PageImpl<>(content, safePageable, payload.totalElements());
    }

    /**
     * 实际执行列表查询（缓存未命中时由 {@link PostListCache} 调用）。
     *
     * 约定：返回的 DTO 一律 {@code likedByMe=false}——本方法<b>不允许</b>读取当前用户身份，
     * 否则用户状态会进入跨用户共享的缓存条目（缓存键里没有 userId，这是设计前提）。
     */
    public PostListCache.PagePayload queryBaseListPage(String category, String keyword, String tag,
                                                       Pageable safePageable) {
        Page<Post> page = queryBasePage(category, keyword, tag, safePageable);
        return new PostListCache.PagePayload(toBaseDtos(page.getContent()), page.getTotalElements());
    }

    private Page<Post> queryBasePage(String category, String keyword, String tag, Pageable safePageable) {
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasTag = tag != null && !tag.isBlank();

        Page<Post> page;
        if (hasTag) {
            // Tag filtering done in memory with normalized comparison, then paged
            String normalizedTag = normalizeTag(tag);
            List<Post> candidates;
            if (hasCategory) {
                candidates = postRepository.findByCategoryAll(category.trim());
            } else {
                candidates = postRepository.findAllPosts();
            }
            List<Post> filtered = candidates.stream()
                    .filter(p -> containsNormalizedTag(p.getTags(), normalizedTag))
                    .collect(Collectors.toList());
            // Apply sort from pageable
            Sort sort = safePageable.getSort();
            java.util.Comparator<Post> comparator = null;
            for (Sort.Order order : sort) {
                java.util.Comparator<Post> c = switch (order.getProperty()) {
                    case "id" -> java.util.Comparator.comparing(Post::getId, java.util.Comparator.nullsLast(Long::compareTo));
                    case "title" -> java.util.Comparator.comparing(Post::getTitle, java.util.Comparator.nullsLast(String::compareToIgnoreCase));
                    case "category" -> java.util.Comparator.comparing(Post::getCategory, java.util.Comparator.nullsLast(String::compareToIgnoreCase));
                    case "viewCount" -> java.util.Comparator.comparing(Post::getViewCount, java.util.Comparator.nullsLast(Integer::compareTo));
                    case "commentCount" -> java.util.Comparator.comparing(Post::getCommentCount, java.util.Comparator.nullsLast(Integer::compareTo));
                    case "likeCount" -> java.util.Comparator.comparing(Post::getLikeCount, java.util.Comparator.nullsLast(Integer::compareTo));
                    case "favoriteCount" -> java.util.Comparator.comparing(Post::getFavoriteCount, java.util.Comparator.nullsLast(Integer::compareTo));
                    case "isTop" -> java.util.Comparator.comparing(Post::getIsTop, java.util.Comparator.nullsLast(Boolean::compareTo));
                    case "isFeatured" -> java.util.Comparator.comparing(Post::getIsFeatured, java.util.Comparator.nullsLast(Boolean::compareTo));
                    case "lastReplyTime" -> java.util.Comparator.comparing(Post::getLastReplyTime, java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo));
                    case "updateTime" -> java.util.Comparator.comparing(Post::getUpdateTime, java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo));
                    default -> java.util.Comparator.comparing(Post::getCreateTime, java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo));
                };
                if (order.isDescending()) c = c.reversed();
                comparator = comparator == null ? c : comparator.thenComparing(c);
            }
            if (comparator != null) filtered.sort(comparator);
            int start = (int) safePageable.getOffset();
            int end = Math.min(start + safePageable.getPageSize(), filtered.size());
            List<Post> pageContent = start < filtered.size() ? filtered.subList(start, end) : List.of();
            page = new org.springframework.data.domain.PageImpl<>(pageContent, safePageable, filtered.size());
        } else if (hasCategory && hasKeyword) {
            page = postRepository.findByCategoryAndTitleContainingIgnoreCase(category.trim(), keyword.trim(), safePageable);
        } else if (hasCategory) {
            page = postRepository.findByCategory(category.trim(), safePageable);
        } else if (hasKeyword) {
            page = postRepository.findByTitleContainingIgnoreCase(keyword.trim(), safePageable);
        } else {
            page = postRepository.findAll(safePageable);
        }
        return page;
    }

    @Transactional(readOnly = true)
    /**
     * 话题聚合（第 8 轮 P1-2）：60s 内存缓存。
     * 话题变化不敏感；聚合是全表内存拆分计数，万帖级会崩——短期以缓存解耦
     * 请求频率与扫描行数（长期方案为 SQL 聚合/计数表，见 PERF_REPORT）。
     */
    public List<TopicDto> getTopics(String category, int limit) {
        String key = (category == null ? "" : category) + "|" + limit;
        long now = System.currentTimeMillis();
        var cached = topicsCache.get(key);
        if (cached != null && now - cached.at < TOPICS_TTL_MS) {
            return cached.topics;
        }
        List<TopicDto> topics = computeTopics(category, limit);
        topicsCache.put(key, new TopicsSnapshot(now, topics));
        return topics;
    }

    private record TopicsSnapshot(long at, List<TopicDto> topics) { }

    private static final long TOPICS_TTL_MS = 60_000;
    private final java.util.concurrent.ConcurrentHashMap<String, TopicsSnapshot> topicsCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private List<TopicDto> computeTopics(String category, int limit) {
        boolean hasCategory = category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category);
        List<Post> posts = hasCategory
                ? postRepository.findByCategoryWithTags(category.trim())
                : postRepository.findAllWithTags();

        // Normalize and count tags, each post contributes at most once per normalized tag
        Map<String, Set<Long>> tagPostIds = new HashMap<>();
        for (Post post : posts) {
            if (post.getTags() == null || post.getTags().isBlank()) continue;
            Set<String> seen = new HashSet<>();
            for (String raw : post.getTags().split("[,，]")) {
                String normalized = raw.strip();
                if (normalized.isEmpty() || !seen.add(normalized)) continue;
                tagPostIds.computeIfAbsent(normalized, k -> new HashSet<>()).add(post.getId());
            }
        }

        return tagPostIds.entrySet().stream()
                .map(e -> new TopicDto(e.getKey(), e.getValue().size()))
                .sorted((a, b) -> Long.compare(b.postCount(), a.postCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    static String normalizeTag(String tag) {
        return tag == null ? "" : tag.strip();
    }

    static boolean containsNormalizedTag(String tags, String target) {
        if (tags == null || tags.isBlank() || target.isBlank()) return false;
        for (String raw : tags.split("[,，]")) {
            if (raw.strip().equals(target)) return true;
        }
        return false;
    }

    static Pageable sanitizePageable(Pageable pageable) {
        // 统一走共享消毒器：page/size 上限与 400 语义在压测/安全测试后收敛到一处
        return PageRequestSanitizer.sanitize(
                pageable, ALLOWED_SORTS, SORT_ALIASES, Sort.by(Sort.Direction.DESC, "createTime"));
    }

    static Sort sanitizeSort(Sort sort) {
        return PageRequestSanitizer.sanitizeSort(
                sort, ALLOWED_SORTS, SORT_ALIASES, Sort.by(Sort.Direction.DESC, "createTime"));
    }

    /** 注册浏览量缓冲的批量落库函数（第 8 轮 P0-2，见 ViewCountBuffer）。 */
    @jakarta.annotation.PostConstruct
    void registerViewCountPersister() {
        // flush 跑在缓冲自己的调度线程上，@Modifying UPDATE 必须有事务——
        // 用 TransactionTemplate 显式开启（失败时缓冲已还回，下窗重试）
        var template = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        viewCountBuffer.setPersister(batch -> template.executeWithoutResult(status -> {
            for (Map.Entry<Long, Long> e : batch) {
                postRepository.bumpViewCountBy(e.getKey(), e.getValue());
            }
        }));
    }

    /**
     * 帖子详情（公开端点）。
     *
     * 安全报告 §十二 V6 / 压测 P3：此前本方法带着
     * {@code @CacheEvict(value="posts", allEntries=true)} 且 viewCount 走
     * "读实体-改-存回"——匿名 50 并发刷详情就能让列表缓存永远为空
     * （实测列表吞吐 3670→806 RPS，-78%）。第 8 轮 P0-2 进一步把每读一写的
     * 原子 UPDATE 改为内存写合并（ViewCountBuffer，5s 批量落库）——
     * 单行热点写消除后详情吞吐与"无写的球员详情"同量级。
     * 取舍：viewCount 接受 ≤一个落库窗口（5s）的陈旧；崩溃丢失窗口内计数。
     * 帖子内容变更的失效仍由 create/update/delete 上的 @CacheEvict 承担。
     */
    public PostDto get(Long id) {
        viewCountBuffer.increment(id);
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        Long currentUserId = getCurrentUserId();
        return toDto(post, currentUserId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto create(Long userId, PostRequest req) {
        return create(userId, req, null);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto create(Long userId, PostRequest req, com.nbamanager.web.dto.PollRequest pollReq) {
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setCategory(req.category());
        post.setTags(req.tags());
        if (pollReq != null) {
            post.setHasPoll(true);
        }
        Post saved = postRepository.save(post);
        if (pollReq != null) {
            Poll poll = new Poll();
            poll.setPostId(saved.getId());
            poll.setQuestion(pollReq.question());
            poll.setOption1(pollReq.option1());
            poll.setOption2(pollReq.option2());
            poll.setOption3(pollReq.option3());
            poll.setOption4(pollReq.option4());
            pollRepository.save(poll);
        }
        return toDto(saved, userId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto update(Long id, Long userId, PostRequest req) {
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!post.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权修改此帖子");
        }
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setCategory(req.category());
        post.setTags(req.tags());
        return toDto(postRepository.save(post), userId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id, Long userId) {
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!post.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权删除此帖子");
        }
        // 如果是管理员删除他人帖子，通知帖子作者
        if (!post.getUserId().equals(userId) && isAdmin()) {
            notificationService.notifyPostDeleted(post.getUserId(), post.getTitle());
        }
        // 先清从属数据再删主记录，避免留下孤儿点赞/评论/收藏（S9-3 压测后残留 4560 行）
        deletePostDependents(List.of(id));
        postRepository.deleteById(id);
    }

    /**
     * 点赞 / 取消点赞（翻转语义）。
     *
     * 并发正确性依赖两层保护：
     *
     * 1) 按【帖子】加锁（不是按 (帖子,用户)）：同帖的多个用户并发切换时，
     *    各自事务都会 DELETE post_likes(post_id,?) 再 INSERT IGNORE，同时
     *    UPDATE posts.like_count 更新同一行；若只串行同一用户，不同用户的
     *    锁顺序仍会交错，InnoDB 判定死锁并回滚其中一个
     *    （压测 §九 P3：20/50/100 并发各 1 个 500 + "Deadlock found" 15 条）。
     *    改成同帖串行后，同帖的所有切换天然排队，跨帖仍可并发。
     *
     * 2) 有限重试兜底：即使仍有死锁（例如锁等待超时），翻转语义下重试安全
     *    （结果只取决于最终落库状态，不累加副作用），重试即可成功。
     *
     * 重试必须放在【新事务】里执行：死锁发生时当前事务已被标记回滚，
     * 在同一个事务内重试只会一直失败，因此这里用 TransactionTemplate 逐次开事务。
     *
     * 缓存：点赞改变 likeCount（列表基座字段）与当前用户的 likedByMe，必须失效帖子列表缓存，
     * 否则前端点完赞看到的仍是旧计数（缓存里没有 userId，无法只失效某个用户）。
     * 这里不失效 dashboard——看板统计只有球队/球员聚合，不含点赞数据。
     */
    @CacheEvict(value = "posts", allEntries = true)
    public boolean toggleLike(Long postId, Long userId) {
        PessimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= TOGGLE_MAX_ATTEMPTS; attempt++) {
            try {
                return new TransactionTemplate(transactionManager)
                        .execute(status -> doToggleLike(postId, userId));
            } catch (PessimisticLockingFailureException e) {
                // 死锁 / 锁等待超时：整个事务已回滚，可安全重开事务重试
                lastFailure = e;
                log.warn("点赞切换遇到锁冲突，第 {}/{} 次尝试: postId={}, userId={}, error={}",
                        attempt, TOGGLE_MAX_ATTEMPTS, postId, userId, e.getMessage());
            }
        }
        log.error("点赞切换重试 {} 次仍失败: postId={}, userId={}", TOGGLE_MAX_ATTEMPTS, postId, userId, lastFailure);
        throw likeStateConflict();
    }

    /** 单次点赞切换（在独立事务中执行）。 */
    private boolean doToggleLike(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> notFound(postId));
        // 同帖串行化：锁持有到事务提交后释放（KeyedMutex 内部注册事务同步回调）
        return com.nbamanager.service.support.KeyedMutex.execute(
                "post-like:" + postId,
                () -> {
                    try {
                        int deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId);
                        if (deleted > 0) {
                            postRepository.bumpLikeCount(postId, -1);
                            return false;
                        }

                        int inserted = postLikeRepository.insertIgnore(postId, userId);
                        if (inserted > 0) {
                            postRepository.bumpLikeCount(postId, +1);
                            // 通知帖子作者
                            String senderName = getSenderName(userId);
                            notificationService.notifyPostAuthor(
                                    post.getUserId(), userId, senderName, post.getTitle(), postId);
                            return true;
                        }

                        throw likeStateConflict();
                    } catch (ApiException ex) {
                        throw ex;
                    } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
                        throw likeStateConflict();
                    }
                });
    }

    /**
     * 级联清理帖子的从属数据。
     *
     * 此前 delete/cleanupOldPosts 只处理了部分关联：压测 S9-3（80 并发 create/delete
     * 清理 9014 帖）后发现 post_likes 残留 897 行、comments 残留 2779 行、
     * user_favorites 残留 884 行（孤儿数据既占空间，又会让"按帖子聚合"的统计虚高）。
     * 现在统一走这里，并顺带清理评论点赞、投票与浏览历史。
     */
    private void deletePostDependents(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(postIds);

        // 评论点赞依赖评论 id，需先取出评论 id 再删
        List<Long> commentIds = commentRepository.findIdsByPostIdIn(ids);
        if (!commentIds.isEmpty()) {
            commentLikeRepository.deleteByCommentIdIn(commentIds);
        }

        userBrowseHistoryRepository.deleteByTargetTypeAndTargetIdIn("POST", ids);
        userFavoriteRepository.deleteByTargetTypeAndTargetIdIn("POST", ids);
        commentRepository.deleteByPostIdIn(ids);
        postLikeRepository.deleteByPostIds(ids);

        List<Long> pollIds = pollRepository.findIdsByPostIdIn(ids);
        if (!pollIds.isEmpty()) {
            pollVoteRepository.deleteByPollIdIn(pollIds);
            pollRepository.deleteByPostIdIn(ids);
        }
    }

    @Transactional(readOnly = true)
    public Page<PostDto> hot(Pageable pageable) {
        // 排序语义由仓储方法名固定为 viewCount DESC：
        // 这里只保留分页并校验边界，用户传入的 sort 一律忽略（此前非法 sort 会打成 500）
        Pageable safePageable = PageRequestSanitizer.pageOnly(pageable);
        Page<Post> page = postRepository.findByOrderByViewCountDesc(safePageable);
        // 与列表同一条路径：基座数据（批量取收藏数/最后回复/身份）+ 按当前用户覆盖 likedByMe。
        // 此前这里传的是空集合，导致热门列表的 likedByMe 恒为 false——前端类型里声明了该字段，
        // 已登录用户在热门列表点过赞也永远显示未点赞。
        List<PostDto> content = decorateLikedByMe(toBaseDtos(page.getContent()), getCurrentUserId());
        return new PageImpl<>(content, safePageable, page.getTotalElements());
    }

    /** 清理超出100条的旧帖子，同步删除浏览历史和收藏 */
    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public int cleanupOldPosts(int keepCount) {
        List<Post> allPosts = postRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        if (allPosts.size() <= keepCount) return 0;

        List<Post> toDelete = allPosts.subList(keepCount, allPosts.size());
        List<Long> ids = toDelete.stream().map(Post::getId).collect(Collectors.toList());
        deletePostDependents(ids);
        postRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    private PostDto toDto(Post post, Long currentUserId) {
        boolean likedByMe = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId);
        long favoriteCount = userFavoriteRepository.countByTargetTypeAndTargetId("POST", post.getId());
        UserAccount account = userAccountRepository.findById(post.getUserId()).orElse(null);
        String username = account != null ? account.getUsername() : null;
        String avatarUrl = account != null ? account.getAvatarUrl() : null;
        java.time.LocalDateTime lastReply = post.getLastReplyTime();
        if (lastReply == null) {
            lastReply = commentRepository.findTopByPostIdOrderByCreateTimeDesc(post.getId())
                    .map(c -> c.getCreateTime()).orElse(null);
        }
        return new PostDto(
                post.getId(), post.getUserId(), username, avatarUrl,
                post.getTitle(), post.getContent(), post.getCategory(), post.getTags(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                (int) favoriteCount, post.getIsTop(), post.getIsFeatured(), post.getHasPoll(),
                likedByMe, lastReply, post.getCreateTime());
    }

    /**
     * 列表基座映射：一次性把本页帖子需要的附加数据取齐，再逐行组装。
     *
     * 原先 {@code toDtoOptimized} 每行都要发一条收藏计数 SELECT、以及（last_reply_time 为空时）
     * 一条"取最新评论" SELECT——每页 10 行就是最多 20 条额外查询，这正是 50 并发下列表读
     * 排队的来源之一。现在收敛为两条批量查询 + 一条身份批量查询。
     */
    private List<PostDto> toBaseDtos(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).collect(Collectors.toList());

        Map<Long, Long> favoriteCounts = new HashMap<>();
        for (com.nbamanager.repository.projection.TargetCount row
                : userFavoriteRepository.countGroupedByTargetIds("POST", ids)) {
            if (row.getTargetId() != null) {
                favoriteCounts.put(row.getTargetId(), row.getCnt() == null ? 0L : row.getCnt());
            }
        }

        Map<Long, java.time.LocalDateTime> lastReplies = new HashMap<>();
        for (com.nbamanager.repository.projection.PostLastReply row
                : commentRepository.findLastReplyTimesByPostIds(ids)) {
            // createTime 允许为空：MAX 全空时该行也是 null，跳过即可（与原先 orElse(null) 语义一致）
            if (row.getPostId() != null && row.getLastReplyTime() != null) {
                lastReplies.put(row.getPostId(), row.getLastReplyTime());
            }
        }

        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, UserAccount> identityMap = userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));

        return posts.stream()
                .map(post -> toBaseDto(post, favoriteCounts, lastReplies, identityMap))
                .collect(Collectors.toList());
    }

    /** 用户无关的基座 DTO：likedByMe 恒为 false，用户状态由 {@link #decorateLikedByMe} 覆盖。 */
    private PostDto toBaseDto(Post post, Map<Long, Long> favoriteCounts,
                              Map<Long, java.time.LocalDateTime> lastReplies,
                              Map<Long, UserAccount> identityMap) {
        UserAccount account = identityMap.get(post.getUserId());
        java.time.LocalDateTime lastReply = post.getLastReplyTime();
        if (lastReply == null) {
            lastReply = lastReplies.get(post.getId());
        }
        return new PostDto(
                post.getId(), post.getUserId(),
                account != null ? account.getUsername() : null,
                account != null ? account.getAvatarUrl() : null,
                post.getTitle(), post.getContent(), post.getCategory(), post.getTags(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                favoriteCounts.getOrDefault(post.getId(), 0L).intValue(),
                post.getIsTop(), post.getIsFeatured(), post.getHasPoll(),
                false, lastReply, post.getCreateTime());
    }

    /**
     * 按当前用户覆盖 {@code likedByMe}。
     *
     * 匿名访问直接返回基座数据（不查库）；已登录时只查"本页 id"范围内的点赞。
     */
    private List<PostDto> decorateLikedByMe(List<PostDto> content, Long currentUserId) {
        if (content.isEmpty() || currentUserId == null) {
            return content;
        }
        List<Long> ids = content.stream().map(PostDto::id).collect(Collectors.toList());
        Set<Long> liked = new HashSet<>(postLikeRepository.findLikedPostIds(currentUserId, ids));
        if (liked.isEmpty()) {
            return content;
        }
        return content.stream()
                .map(dto -> liked.contains(dto.id()) ? withLikedByMe(dto) : dto)
                .collect(Collectors.toList());
    }

    private static PostDto withLikedByMe(PostDto dto) {
        return new PostDto(
                dto.id(), dto.userId(), dto.username(), dto.avatarUrl(), dto.title(), dto.content(),
                dto.category(), dto.tags(), dto.viewCount(), dto.likeCount(), dto.commentCount(),
                dto.favoriteCount(), dto.isTop(), dto.isFeatured(), dto.hasPoll(),
                true, dto.lastReplyTime(), dto.createTime());
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ((com.nbamanager.security.UserPrincipal) ud).getId();
        }
        return null;
    }

    private String getSenderName(Long userId) {
        return userAccountRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse("用户");
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "帖子不存在: " + id);
    }

    private static ApiException likeStateConflict() {
        return new ApiException(HttpStatus.CONFLICT, "点赞状态正在更新，请稍后重试");
    }
}

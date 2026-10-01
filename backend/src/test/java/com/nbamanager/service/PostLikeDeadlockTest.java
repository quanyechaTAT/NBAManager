package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.service.support.FakeTransactionManager;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * F4 回归：同帖跨用户并发点赞不再死锁 500。
 *
 * 压测报告 §九 P3：
 * - KeyedMutex 原先按 (postId, userId) 分片，只串行"同一用户对同一帖"；
 * - 不同用户并发切换同一帖时，各自 DELETE post_likes + INSERT IGNORE，
 *   同时 UPDATE posts.like_count 更新同一行，锁顺序交错触发 InnoDB 死锁，
 *   20/50/100 并发各出现 1 个 500，日志 15 条 "Deadlock found"。
 *
 * 修复：锁 key 改为 "post-like:" + postId（按帖串行，跨用户互斥）+ 新事务有限重试。
 *
 * 说明：Mockito 造不出真实的 InnoDB 死锁，因此这里用
 * 1) 有状态的内存假仓储 + 多线程并发，验证"按帖串行"下计数与行数始终一致、
 *    且不会出现 500/409；
 * 2) 显式注入 PessimisticLockingFailureException，验证重试确实生效（这是死锁兜底路径）。
 */
@ExtendWith(MockitoExtension.class)
class PostLikeDeadlockTest {

    private static final Long POST_ID = 9001L;
    private static final int USERS = 8;
    private static final int ROUNDS = 25;

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private UserBrowseHistoryRepository userBrowseHistoryRepository;
    @Mock private PollRepository pollRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;
    @Mock private PostListCache postListCache;
    @Mock private com.nbamanager.service.support.ViewCountBuffer viewCountBuffer;

    /** postId -> 已点赞的 userId 集合（模拟 post_likes 表的唯一键）。 */
    private final Map<Long, Set<Long>> likeRows = new ConcurrentHashMap<>();
    /** postId -> like_count（模拟 posts.like_count）。 */
    private final Map<Long, AtomicInteger> likeCounts = new ConcurrentHashMap<>();
    /** 模拟数据库对同一行的互斥，保证"读改写计数"原子。 */
    private final Object dbRowLock = new Object();

    private PostService postService;
    private FakeTransactionManager txManager;

    @BeforeEach
    void setUp() {
        txManager = new FakeTransactionManager();
        postService = new PostService(postRepository, postLikeRepository, commentRepository,
                commentLikeRepository, userFavoriteRepository, userBrowseHistoryRepository,
                notificationService, pollRepository, pollVoteRepository, userAccountRepository,
                txManager, postListCache, viewCountBuffer);
        likeRows.put(POST_ID, new CopyOnWriteArraySet<>());
        likeCounts.put(POST_ID, new AtomicInteger(0));

        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(1L);
        post.setTitle("deadlock-regression");
        post.setCategory("讨论");
        post.setContent("x");
        lenient().when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        UserAccount liker = new UserAccount();
        liker.setId(9L);
        liker.setUsername("liker");
        lenient().when(userAccountRepository.findById(anyLong())).thenReturn(Optional.of(liker));

        // 有状态的假仓储：DELETE / INSERT IGNORE / 原子计数
        lenient().when(postLikeRepository.deleteByPostIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long postId = inv.getArgument(0);
                    Long userId = inv.getArgument(1);
                    synchronized (dbRowLock) {
                        boolean removed = likeRows
                                .computeIfAbsent(postId, k -> new CopyOnWriteArraySet<>())
                                .remove(userId);
                        return removed ? 1 : 0;
                    }
                });
        lenient().when(postLikeRepository.insertIgnore(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long postId = inv.getArgument(0);
                    Long userId = inv.getArgument(1);
                    synchronized (dbRowLock) {
                        return likeRows
                                .computeIfAbsent(postId, k -> new CopyOnWriteArraySet<>())
                                .add(userId) ? 1 : 0;
                    }
                });
        lenient().when(postRepository.bumpLikeCount(anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    Long postId = inv.getArgument(0);
                    Integer delta = inv.getArgument(1);
                    synchronized (dbRowLock) {
                        likeCounts
                                .computeIfAbsent(postId, k -> new AtomicInteger(0))
                                .addAndGet(delta);
                        return 1;
                    }
                });
    }

    @Test
    void concurrentTogglesOnSamePostAcrossUsersNeverFailAndKeepCountConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        for (long userId = 1; userId <= USERS; userId++) {
            final long uid = userId;
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    for (int round = 0; round < ROUNDS; round++) {
                        try {
                            postService.toggleLike(POST_ID, uid);
                        } catch (Throwable t) {
                            failures.incrementAndGet();
                            firstFailure.compareAndSet(null, t);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures.get())
                .as("并发 toggle 不应有失败（原缺陷每档都会出现 500），首个异常: %s", firstFailure.get())
                .isZero();

        // 计数一致性：SUM(posts.like_count) == COUNT(post_likes)
        int declared = likeCounts.get(POST_ID).get();
        int actualRows = likeRows.get(POST_ID).size();
        assertThat(declared)
                .as("likeCount 必须等于实际点赞行数")
                .isEqualTo(actualRows);
        // 每个用户被翻转 25 次（奇数）-> 最终每个用户都是"已点赞"
        assertThat(actualRows).isEqualTo(USERS);
    }

    @Test
    void retriesWhenDatabaseReportsDeadlockThenSucceeds() {
        // 第一次 DELETE 抛死锁（CannotAcquireLockException 继承 PessimisticLockingFailureException），
        // 第二次正常 -> 应重试成功，且最终恰好提交 1 次、回滚 1 次
        AtomicInteger deleteCalls = new AtomicInteger();
        when(postLikeRepository.deleteByPostIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    int call = deleteCalls.incrementAndGet();
                    if (call == 1) {
                        throw new CannotAcquireLockException("Deadlock found when trying to get lock");
                    }
                    Long postId = inv.getArgument(0);
                    Long userId = inv.getArgument(1);
                    synchronized (dbRowLock) {
                        return likeRows
                                .computeIfAbsent(postId, k -> new CopyOnWriteArraySet<>())
                                .remove(userId) ? 1 : 0;
                    }
                });

        boolean liked = postService.toggleLike(POST_ID, 42L);

        assertThat(liked).as("重试后应成功执行（首次已点赞）").isTrue();
        assertThat(deleteCalls.get()).as("应发生一次重试").isEqualTo(2);
        assertThat(likeCounts.get(POST_ID).get()).isEqualTo(1);
        assertThat(likeRows.get(POST_ID)).containsExactly(42L);
    }

    @Test
    void givesUpAfterBoundedRetriesAndReturnsConflictNotServerError() {
        // 始终死锁 -> 有限次重试后返回 409（ApiException），而不是 500
        AtomicInteger attempts = new AtomicInteger();
        when(postLikeRepository.deleteByPostIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    attempts.incrementAndGet();
                    throw new CannotAcquireLockException("Deadlock found when trying to get lock");
                });

        assertThatThrownBy(() -> postService.toggleLike(POST_ID, 43L))
                .isInstanceOfSatisfying(com.nbamanager.exception.ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));

        assertThat(attempts.get())
                .as("重试次数应有上界（不会无限重试）")
                .isLessThanOrEqualTo(3);
    }

    @Test
    void optimisticLockingFailureStillBecomesConflictWithoutRetryLoop() {
        // 非死锁类冲突（乐观锁）仍按原语义立即转 409
        when(postLikeRepository.deleteByPostIdAndUserId(anyLong(), anyLong()))
                .thenThrow(new OptimisticLockingFailureException("stale like row"));

        assertThatThrownBy(() -> postService.toggleLike(POST_ID, 44L))
                .isInstanceOf(com.nbamanager.exception.ApiException.class);
    }

    @Test
    void dataIntegrityViolationStillBecomesConflict() {
        when(postLikeRepository.deleteByPostIdAndUserId(anyLong(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> postService.toggleLike(POST_ID, 45L))
                .isInstanceOf(com.nbamanager.exception.ApiException.class);
    }
}
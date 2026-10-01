package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Comment;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.service.support.FakeTransactionManager;
import com.nbamanager.web.dto.CommentRequest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * F1 回归：新增评论必须让 posts.comment_count 自增。
 *
 * 安全报告 §十 10.7 的 VULN：CommentService.create() 先用 findById 加载 Post（commentCount=0），
 * 再 bumpCommentCount 原子自增，最后 setLastReplyTime + save(post) —— save 对已存在实体是
 * merge，会把内存里过期的 commentCount 整行写回，抹掉自增。
 * 实测发 3 条评论后 comment_count 仍为 0，而 last_reply_time 却是新的。
 *
 * 修复：lastReplyTime 也改为原子 UPDATE（touchLastReplyTime），并删除 save(post)。
 */
@ExtendWith(MockitoExtension.class)
class CommentCountConsistencyTest {

    private static final Long POST_ID = 4242L;

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;

    private CommentService service;

    /** 模拟数据库里的 posts.comment_count（只被原子 UPDATE 修改）。 */
    private final AtomicInteger declaredCount = new AtomicInteger(0);
    /** 模拟 comments 表里该帖的评论行数。 */
    private final AtomicInteger commentRows = new AtomicInteger(0);
    private final AtomicLong idSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, commentLikeRepository, postRepository,
                userAccountRepository, notificationService, new FakeTransactionManager());

        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(1L);
        post.setTitle("count-regression");
        post.setCategory("讨论");
        post.setContent("x");
        // 关键：模拟"进入 create() 时读到的陈旧实体"，其 commentCount 永远是 0
        lenient().when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        UserAccount author = new UserAccount();
        author.setId(1L);
        author.setUsername("author");
        lenient().when(userAccountRepository.findById(anyLong())).thenReturn(Optional.of(author));

        lenient().when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(idSeq.incrementAndGet());
            commentRows.incrementAndGet();
            return c;
        });

        // 原子自增：只改计数，不触碰实体
        lenient().when(postRepository.bumpCommentCount(eq(POST_ID), any(Integer.class)))
                .thenAnswer(inv -> {
                    declaredCount.addAndGet(inv.getArgument(1));
                    return 1;
                });
        lenient().when(postRepository.touchLastReplyTime(eq(POST_ID))).thenReturn(1);
    }

    @Test
    void commentCountIncrementsWithEachComment() {
        for (int i = 0; i < 3; i++) {
            service.create(1L, POST_ID, new CommentRequest(null, null, "c" + i, null));
        }

        assertThat(declaredCount.get())
                .as("发 3 条评论后 comment_count 应为 3（修复前恒为 0）")
                .isEqualTo(commentRows.get())
                .isEqualTo(3);
    }

    @Test
    void createNeverSavesWholePostEntityWhichWouldEraseTheIncrement() {
        service.create(1L, POST_ID, new CommentRequest(null, null, "hello", null));

        // save(post) 是缺陷本身：它会用陈旧实体 merge 掉 comment_count
        verify(postRepository, never()).save(any(Post.class));
        // 两个字段各自原子更新
        verify(postRepository).bumpCommentCount(POST_ID, 1);
        verify(postRepository).touchLastReplyTime(POST_ID);
    }

    @Test
    void concurrentCommentsKeepDeclaredCountEqualToActualRows() throws Exception {
        int threads = 16;
        int perThread = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < perThread; i++) {
                        service.create(1L, POST_ID,
                                new CommentRequest(null, null, "t" + seed + "-" + i, null));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        int expected = threads * perThread;
        assertThat(commentRows.get()).isEqualTo(expected);
        assertThat(declaredCount.get())
                .as("并发 %d 条后 comment_count 必须等于实际评论行数（修复前 delta=%d）",
                        expected, -expected)
                .isEqualTo(commentRows.get());
    }

    @Test
    void replyCommentAlsoIncrementsCount() {
        // 回复（parentId != null）走的也是同一路径，同样不能漏计数
        Comment parent = new Comment();
        parent.setId(555L);
        parent.setUserId(2L);
        parent.setPostId(POST_ID);
        parent.setContent("parent");
        when(commentRepository.findById(555L)).thenReturn(Optional.of(parent));

        service.create(1L, POST_ID, new CommentRequest(null, null, "reply", 555L));

        assertThat(declaredCount.get()).isEqualTo(1);
        verify(postRepository).touchLastReplyTime(POST_ID);
    }
}
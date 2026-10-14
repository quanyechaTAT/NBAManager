package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * P1-2 回归（安全报告 §十二 V6 / 压测 P3）：详情 GET 不得失效帖子列表缓存。
 *
 * 此前 {@code PostService.get} 挂着 {@code @CacheEvict(value="posts", allEntries=true)}
 * 且 viewCount 走实体读改写——匿名 50 并发刷详情就能让列表缓存永远为空
 * （实测列表吞吐 3670→806 RPS，-78%）。本测试把修复钉死：
 * 1. get() 上不再有 @CacheEvict；
 * 2. viewCount 走原子 UPDATE（bumpViewCount），不再 save 实体；
 * 3. bumpViewCount 带 @Modifying（JPQL UPDATE 需要），保证不落实体缓存。
 */
@ExtendWith(MockitoExtension.class)
class PostDetailCacheGuardTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private com.nbamanager.repository.CommentRepository commentRepository;
    @Mock private com.nbamanager.repository.CommentLikeRepository commentLikeRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private com.nbamanager.repository.UserBrowseHistoryRepository userBrowseHistoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private com.nbamanager.repository.PollRepository pollRepository;
    @Mock private com.nbamanager.repository.PollVoteRepository pollVoteRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private PostListCache postListCache;
    @Mock private com.nbamanager.service.support.ViewCountBuffer viewCountBuffer;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(postRepository, postLikeRepository, commentRepository,
                commentLikeRepository, userFavoriteRepository, userBrowseHistoryRepository,
                notificationService, pollRepository, pollVoteRepository, userAccountRepository,
                transactionManager, postListCache, viewCountBuffer, org.mockito.Mockito.mock(com.nbamanager.discovery.DiscoveryStore.class));
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDoesNotEvictListCache() throws Exception {
        CacheEvict evict = PostService.class
                .getMethod("get", Long.class)
                .getAnnotation(CacheEvict.class);

        assertThat(evict)
                .as("详情 GET 绝不能再带 @CacheEvict(allEntries=true)——否则匿名流量可清零列表缓存")
                .isNull();
    }

    @Test
    void detailReadBuffersViewCountInsteadOfPerReadUpdate() {
        Post post = new Post();
        post.setId(7L);
        post.setUserId(1L);
        post.setTitle("t");
        post.setCategory("讨论");
        post.setViewCount(41);
        when(postRepository.findById(7L)).thenReturn(Optional.of(post));

        service.get(7L);

        // 第 8 轮 P0-2：每读一写的原子 UPDATE 改为内存写合并——
        // 读路径不再触碰 UPDATE，计数进入缓冲（定时批量落库）
        verify(viewCountBuffer).increment(7L);
        verify(postRepository, never()).bumpViewCount(any());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void batchViewCountUpdateIsModifying() throws Exception {
        var method = PostRepository.class.getMethod("bumpViewCountBy", Long.class, long.class);

        assertThat(method.getAnnotation(Modifying.class))
                .as("缓冲落库的批量 UPDATE 必须是 @Modifying")
                .isNotNull();
    }
}

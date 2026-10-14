package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Post;
import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.repository.projection.PostLastReply;
import com.nbamanager.repository.projection.TargetCount;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.web.dto.PostDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 帖子列表"用户无关基座 + 按用户装饰"的核心不变量。
 *
 * 这是把列表做成可跨用户共享缓存的前提，也是它唯一的风险点：
 * 一旦基座里带上了某个用户的点赞状态，别的用户就会看到它。这里把不变量钉死。
 */
@ExtendWith(MockitoExtension.class)
class PostListDecorationTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private com.nbamanager.repository.CommentLikeRepository commentLikeRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private UserBrowseHistoryRepository userBrowseHistoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private PollRepository pollRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PostListCache postListCache;
    @Mock private com.nbamanager.service.support.ViewCountBuffer viewCountBuffer;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(postRepository, postLikeRepository, commentRepository,
                commentLikeRepository, userFavoriteRepository, userBrowseHistoryRepository,
                notificationService, pollRepository, pollVoteRepository, userAccountRepository,
                new com.nbamanager.service.support.FakeTransactionManager(), postListCache, viewCountBuffer, org.mockito.Mockito.mock(com.nbamanager.discovery.DiscoveryStore.class));
        // 共享 stub：hot() 走的是 findByOrderByViewCountDesc，用不到 findAll
        lenient().when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(pageOf(post(10L, 1L), post(11L, 2L)));
        when(userFavoriteRepository.countGroupedByTargetIds(anyString(), any())).thenReturn(List.of());
        when(commentRepository.findLastReplyTimesByPostIds(any())).thenReturn(List.of());
        when(userAccountRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 基座构建阶段绝不能读取当前用户身份——即使请求确实带着登录用户。
     * 这是"缓存条目可跨用户共享"的根据。
     */
    @Test
    void basePayloadNeverCarriesUserStateEvenWhenRequestIsAuthenticated() {
        authenticateAs(7L);

        PostListCache.PagePayload payload =
                service.queryBaseListPage(null, null, null, PageRequest.of(0, 10));

        assertThat(payload.content()).hasSize(2);
        assertThat(payload.content()).allSatisfy(dto ->
                assertThat(dto.likedByMe())
                        .as("基座里出现 likedByMe=true 就会把该用户的点赞状态泄漏给所有人")
                        .isFalse());
        // 基座阶段不应查询任何用户态点赞
        verify(postLikeRepository, never()).findLikedPostIds(anyLong(), any());
    }

    /** 装饰阶段：只有当前用户点过赞的帖子被置为 true，其余保持 false。 */
    @Test
    void listDecoratesLikedByMeForCurrentUserOnly() {
        authenticateAs(7L);
        when(postListCache.load(any(), any(), any(), any(Pageable.class))).thenAnswer(inv ->
                service.queryBaseListPage(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3)));
        when(postLikeRepository.findLikedPostIds(7L, List.of(10L, 11L))).thenReturn(List.of(10L));

        Page<PostDto> result = service.list(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).id()).isEqualTo(10L);
        assertThat(result.getContent().get(0).likedByMe()).isTrue();
        assertThat(result.getContent().get(1).likedByMe()).isFalse();
    }

    /** 另一个用户看同一批数据，得到的是自己的点赞状态（互不串数据）。 */
    @Test
    void anotherUserSeesTheirOwnLikeState() {
        authenticateAs(8L);
        when(postListCache.load(any(), any(), any(), any(Pageable.class))).thenAnswer(inv ->
                service.queryBaseListPage(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3)));
        when(postLikeRepository.findLikedPostIds(8L, List.of(10L, 11L))).thenReturn(List.of(11L));

        Page<PostDto> result = service.list(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).likedByMe()).isFalse();
        assertThat(result.getContent().get(1).likedByMe()).isTrue();
    }

    /** 匿名访问不查库，全部为未点赞。 */
    @Test
    void anonymousRequestSkipsLikeLookupEntirely() {
        when(postListCache.load(any(), any(), any(), any(Pageable.class))).thenAnswer(inv ->
                service.queryBaseListPage(inv.getArgument(0), inv.getArgument(1),
                        inv.getArgument(2), inv.getArgument(3)));

        Page<PostDto> result = service.list(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).allSatisfy(dto -> assertThat(dto.likedByMe()).isFalse());
        verify(postLikeRepository, never()).findLikedPostIds(anyLong(), any());
    }

    /**
     * 热门列表同样要带当前用户的点赞状态。
     *
     * 回归：此前 hot() 传的是空集合，likedByMe 恒为 false——前端类型里声明了该字段，
     * 已登录用户在热门列表点过赞也永远显示未点赞。
     */
    @Test
    void hotListAlsoDecoratesLikedByMe() {
        authenticateAs(7L);
        when(postRepository.findByOrderByViewCountDesc(any(Pageable.class)))
                .thenReturn(pageOf(post(10L, 1L)));
        when(postLikeRepository.findLikedPostIds(7L, List.of(10L))).thenReturn(List.of(10L));

        Page<PostDto> result = service.hot(PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).likedByMe()).isTrue();
    }

    /**
     * N+1 回归：一页 N 行只允许常数条查询。
     *
     * 原先每行一次收藏 COUNT、以及 last_reply_time 为空时每行一次"取最新评论"，
     * 每页 10 行最多 20 条额外查询；现在收敛为批量查询。
     */
    @Test
    void baseMappingUsesBatchedQueriesInsteadOfPerRowLookups() {
        when(userFavoriteRepository.countGroupedByTargetIds("POST", List.of(10L, 11L)))
                .thenReturn(List.of(targetCount(10L, 3L), targetCount(11L, 0L)));
        when(commentRepository.findLastReplyTimesByPostIds(List.of(10L, 11L)))
                .thenReturn(List.of(postLastReply(10L, LocalDateTime.of(2026, 2, 3, 4, 5))));

        PostListCache.PagePayload payload =
                service.queryBaseListPage(null, null, null, PageRequest.of(0, 10));

        // 一次批量查询覆盖整页
        verify(userFavoriteRepository).countGroupedByTargetIds("POST", List.of(10L, 11L));
        verify(commentRepository).findLastReplyTimesByPostIds(List.of(10L, 11L));
        verify(userAccountRepository).findAllById(any());
        // 逐行查询彻底消失
        verify(userFavoriteRepository, never()).countByTargetTypeAndTargetId(anyString(), anyLong());
        verify(commentRepository, never()).findTopByPostIdOrderByCreateTimeDesc(anyLong());

        assertThat(payload.content().get(0).favoriteCount()).isEqualTo(3);
        assertThat(payload.content().get(1).favoriteCount()).isZero();
        // last_reply_time 为空的帖子由批量结果补齐
        assertThat(payload.content().get(0).lastReplyTime())
                .isEqualTo(LocalDateTime.of(2026, 2, 3, 4, 5));
        assertThat(payload.content().get(1).lastReplyTime()).isNull();
    }

    private void authenticateAs(Long userId) {
        UserAccount account = new UserAccount();
        account.setId(userId);
        account.setUsername("user" + userId);
        account.setPassword("x");
        account.setRole(Role.USER);
        UserPrincipal principal = new UserPrincipal(account);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static Post post(Long id, Long userId) {
        Post p = new Post();
        p.setId(id);
        p.setUserId(userId);
        p.setTitle("t" + id);
        p.setContent("c");
        p.setCategory("DISCUSSION");
        p.setViewCount(0);
        p.setLikeCount(0);
        p.setCommentCount(0);
        return p;
    }

    private static Page<Post> pageOf(Post... posts) {
        return new PageImpl<>(List.of(posts), PageRequest.of(0, 10), posts.length);
    }

    private static TargetCount targetCount(Long targetId, Long cnt) {
        return new TargetCount() {
            @Override
            public Long getTargetId() {
                return targetId;
            }

            @Override
            public Long getCnt() {
                return cnt;
            }
        };
    }

    private static PostLastReply postLastReply(Long postId, LocalDateTime lastReplyTime) {
        return new PostLastReply() {
            @Override
            public Long getPostId() {
                return postId;
            }

            @Override
            public LocalDateTime getLastReplyTime() {
                return lastReplyTime;
            }
        };
    }
}

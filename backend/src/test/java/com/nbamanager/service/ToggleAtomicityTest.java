package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Comment;
import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.GameNewsRepository;
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
import org.springframework.http.HttpStatus;

/**
 * 点赞/收藏切换的原子性回归测试。
 *
 * 背景：压测 R4 的点赞风暴里 84% 请求失败，其中 578 个 500 来自
 * {@code StaleObjectStateException: [PostLike#N]} —— 派生 deleteBy 会把实体加载进
 * 持久化上下文，并发删除同一行时提交阶段报"行已被其他事务更新/删除"。
 * 现在统一改为显式批量 DELETE + INSERT IGNORE + 原子计数 UPDATE，
 * 并发冲突应体现为 409，而不是 500。
 */
@ExtendWith(MockitoExtension.class)
class ToggleAtomicityTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private com.nbamanager.repository.PollRepository pollRepository;
    @Mock private com.nbamanager.repository.PollVoteRepository pollVoteRepository;
    @Mock private GameNewsRepository gameNewsRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;
    @Mock private PostListCache postListCache;
    @Mock private com.nbamanager.service.support.ViewCountBuffer viewCountBuffer;

    private PostService postService;
    private CommentService commentService;
    private GameNewsService gameNewsService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, postLikeRepository, commentRepository,
                commentLikeRepository, userFavoriteRepository, null, notificationService,
                pollRepository, pollVoteRepository, userAccountRepository,
                new com.nbamanager.service.support.FakeTransactionManager(), postListCache, viewCountBuffer, org.mockito.Mockito.mock(com.nbamanager.discovery.DiscoveryStore.class));
        commentService = new CommentService(commentRepository, commentLikeRepository, postRepository,
                userAccountRepository, notificationService,
                new com.nbamanager.service.support.FakeTransactionManager());
        var newsListCacheProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        gameNewsService = new GameNewsService(gameNewsRepository, userFavoriteRepository,
                newsListCacheProvider, null, null, org.mockito.Mockito.mock(com.nbamanager.discovery.DiscoveryStore.class));
    }

    // ---------- 帖子点赞 ----------

    @Test
    void postLikeDeleteWinsTogglesOffAndDecrementsAtomically() {
        Post post = post(10L);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(1);

        assertThat(postService.toggleLike(10L, 7L)).isFalse();

        verify(postRepository).bumpLikeCount(10L, -1);
        verify(postLikeRepository, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    void postLikeInsertWinsTogglesOnAndIncrementsAtomically() {
        Post post = post(10L);
        UserAccount liker = new UserAccount();
        liker.setId(7L);
        liker.setUsername("tester");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(0);
        when(postLikeRepository.insertIgnore(10L, 7L)).thenReturn(1);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(liker));

        assertThat(postService.toggleLike(10L, 7L)).isTrue();

        verify(postRepository).bumpLikeCount(10L, 1);
        verify(notificationService).notifyPostAuthor(1L, 7L, "tester", "test", 10L);
    }

    @Test
    void concurrentInsertLoserGetsConflictNotFiveHundred() {
        Post post = post(10L);
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(0);
        when(postLikeRepository.insertIgnore(10L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> postService.toggleLike(10L, 7L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("点赞状态正在更新");
                });
        verify(postRepository, never()).bumpLikeCount(anyLong(), anyInt());
    }

    // ---------- 评论点赞 ----------

    @Test
    void commentLikeDeleteWinsTogglesOffAndDecrementsAtomically() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(5L)));
        when(commentLikeRepository.deleteByCommentIdAndUserId(5L, 7L)).thenReturn(1);

        assertThat(commentService.toggleLike(5L, 7L)).isFalse();

        verify(commentRepository).bumpLikeCount(5L, -1);
        verify(commentLikeRepository, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    void commentLikeInsertWinsTogglesOnAndIncrementsAtomically() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(5L)));
        when(commentLikeRepository.deleteByCommentIdAndUserId(5L, 7L)).thenReturn(0);
        when(commentLikeRepository.insertIgnore(5L, 7L)).thenReturn(1);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(commentService.toggleLike(5L, 7L)).isTrue();

        verify(commentRepository).bumpLikeCount(5L, 1);
        verify(notificationService).notifyCommentAuthor(eq(1L), eq(7L), anyString(), anyString(), any(), any());
    }

    @Test
    void concurrentCommentLikeInsertLoserGetsConflict() {
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment(5L)));
        when(commentLikeRepository.deleteByCommentIdAndUserId(5L, 7L)).thenReturn(0);
        when(commentLikeRepository.insertIgnore(5L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.toggleLike(5L, 7L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(commentRepository, never()).bumpLikeCount(anyLong(), anyInt());
    }

    // ---------- 资讯收藏 ----------

    @Test
    void newsFavoriteDeleteWinsTogglesOffAndDecrementsAtomically() {
        when(gameNewsRepository.findForUpdateById(3L)).thenReturn(Optional.of(new GameNews()));
        when(userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(7L, "NEWS", 3L))
                .thenReturn(1);

        assertThat(gameNewsService.toggleFavorite(7L, 3L)).isFalse();

        verify(gameNewsRepository).bumpFavoriteCount(3L, -1);
        verify(userFavoriteRepository, never()).insertIgnore(anyLong(), anyString(), anyLong());
    }

    @Test
    void newsFavoriteInsertWinsTogglesOnAndIncrementsAtomically() {
        when(gameNewsRepository.findForUpdateById(3L)).thenReturn(Optional.of(new GameNews()));
        when(userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(7L, "NEWS", 3L))
                .thenReturn(0);
        when(userFavoriteRepository.insertIgnore(7L, "NEWS", 3L)).thenReturn(1);

        assertThat(gameNewsService.toggleFavorite(7L, 3L)).isTrue();

        verify(gameNewsRepository).bumpFavoriteCount(3L, 1);
    }

    @Test
    void concurrentNewsFavoriteInsertLoserGetsConflict() {
        when(gameNewsRepository.findForUpdateById(3L)).thenReturn(Optional.of(new GameNews()));
        when(userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(7L, "NEWS", 3L))
                .thenReturn(0);
        when(userFavoriteRepository.insertIgnore(7L, "NEWS", 3L)).thenReturn(0);

        assertThatThrownBy(() -> gameNewsService.toggleFavorite(7L, 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(gameNewsRepository, never()).bumpFavoriteCount(anyLong(), anyInt());
    }

    private static Post post(Long id) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(1L);
        post.setTitle("test");
        post.setCategory("讨论");
        post.setContent("content");
        return post;
    }

    private static Comment comment(Long id) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setUserId(1L);
        comment.setContent("comment");
        return comment;
    }
}

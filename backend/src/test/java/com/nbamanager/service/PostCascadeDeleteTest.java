package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Post;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PollVoteRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 帖子删除的级联清理回归测试。
 *
 * 背景：S9-3 压测（80 并发 create/delete 共 9014 帖）后库中残留
 * post_likes 897 行、comments 2779 行、user_favorites 884 行 —— 删除帖子时
 * 只删了主记录。孤儿行会让按帖子聚合的统计虚高，也持续占用空间。
 */
@ExtendWith(MockitoExtension.class)
class PostCascadeDeleteTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
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
    }

    @Test
    void deletingPostRemovesLikesCommentsFavoritesHistoryAndPoll() {
        Post post = post(42L, 7L);
        when(postRepository.findById(42L)).thenReturn(Optional.of(post));
        when(commentRepository.findIdsByPostIdIn(List.of(42L))).thenReturn(List.of(100L, 101L));
        when(pollRepository.findIdsByPostIdIn(List.of(42L))).thenReturn(List.of(9L));

        service.delete(42L, 7L);

        verify(commentLikeRepository).deleteByCommentIdIn(List.of(100L, 101L));
        verify(commentRepository).deleteByPostIdIn(List.of(42L));
        verify(postLikeRepository).deleteByPostIds(List.of(42L));
        verify(userFavoriteRepository).deleteByTargetTypeAndTargetIdIn("POST", List.of(42L));
        verify(userBrowseHistoryRepository).deleteByTargetTypeAndTargetIdIn("POST", List.of(42L));
        verify(pollVoteRepository).deleteByPollIdIn(List.of(9L));
        verify(pollRepository).deleteByPostIdIn(List.of(42L));
        verify(postRepository).deleteById(42L);
    }

    @Test
    void deletingPostWithoutPollSkipsPollCleanup() {
        Post post = post(43L, 7L);
        when(postRepository.findById(43L)).thenReturn(Optional.of(post));
        when(commentRepository.findIdsByPostIdIn(List.of(43L))).thenReturn(List.of());
        when(pollRepository.findIdsByPostIdIn(List.of(43L))).thenReturn(List.of());

        service.delete(43L, 7L);

        verify(pollVoteRepository, never()).deleteByPollIdIn(any());
        verify(pollRepository, never()).deleteByPostIdIn(any());
        verify(commentLikeRepository, never()).deleteByCommentIdIn(any());
    }

    @Test
    void cleanupOldPostsCascadesDependentsForEveryRemovedPost() {
        Post keep = post(1L, 7L);
        Post old1 = post(2L, 7L);
        Post old2 = post(3L, 7L);
        when(postRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(keep, old1, old2));
        when(commentRepository.findIdsByPostIdIn(List.of(2L, 3L))).thenReturn(List.of(20L));
        when(pollRepository.findIdsByPostIdIn(List.of(2L, 3L))).thenReturn(List.of());

        int deleted = service.cleanupOldPosts(1);

        assertThat(deleted).isEqualTo(2);
        verify(postLikeRepository).deleteByPostIds(List.of(2L, 3L));
        verify(commentRepository).deleteByPostIdIn(List.of(2L, 3L));
        verify(commentLikeRepository).deleteByCommentIdIn(List.of(20L));
        verify(userFavoriteRepository).deleteByTargetTypeAndTargetIdIn("POST", List.of(2L, 3L));
        verify(userBrowseHistoryRepository).deleteByTargetTypeAndTargetIdIn("POST", List.of(2L, 3L));
        verify(postRepository).deleteAll(List.of(old1, old2));
        verify(postRepository, never()).deleteById(anyLong());
    }

    @Test
    void cleanupOldPostsIsNoopWhenWithinLimit() {
        when(postRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(post(1L, 7L)));

        assertThat(service.cleanupOldPosts(10)).isZero();

        verify(postLikeRepository, never()).deleteByPostIds(any());
        verify(postRepository, never()).deleteAll(any());
    }

    private static Post post(Long id, Long userId) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setTitle("t");
        post.setContent("c");
        post.setCategory("讨论");
        return post;
    }
}

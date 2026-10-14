package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nbamanager.exception.ApiException;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.TopicDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTopicsTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private com.nbamanager.repository.CommentLikeRepository commentLikeRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private UserBrowseHistoryRepository userBrowseHistoryRepository;
    @Mock private NotificationService notificationService;
    @Mock private PollRepository pollRepository;
    @Mock private com.nbamanager.repository.PollVoteRepository pollVoteRepository;
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
        // 单元测试里没有 Spring 缓存代理：把缓存入口直接接到真实查询实现，
        // 这样筛选/排序/分页逻辑仍然是被真实执行的（缓存语义由 PostListCacheTest 覆盖）。
        lenient().when(postListCache.load(any(), any(), any(), any(Pageable.class)))
                .thenAnswer(inv -> service.queryBaseListPage(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    @Test
    void topicsNormalizeCommasAndDedup() {
        Post p1 = makePost(1L, "湖人, 湖人, 季后赛");
        Post p2 = makePost(2L, "湖人，交易");
        Post p3 = makePost(3L, "季后赛");

        when(postRepository.findAllWithTags()).thenReturn(List.of(p1, p2, p3));

        List<TopicDto> topics = service.getTopics("ALL", 8);

        assertThat(topics).hasSize(3);
        assertThat(topics).extracting(TopicDto::name).contains("湖人", "季后赛", "交易");
        // 湖人 and 季后赛 both have count 2
        assertThat(topics).anySatisfy(t -> {
            assertThat(t.name()).isEqualTo("湖人");
            assertThat(t.postCount()).isEqualTo(2);
        });
        assertThat(topics).anySatisfy(t -> {
            assertThat(t.name()).isEqualTo("季后赛");
            assertThat(t.postCount()).isEqualTo(2);
        });
        assertThat(topics).anySatisfy(t -> {
            assertThat(t.name()).isEqualTo("交易");
            assertThat(t.postCount()).isEqualTo(1);
        });
    }

    @Test
    void topicsEachPostContributesOncePerTag() {
        Post p1 = makePost(1L, "湖人, 湖人, 湖人");
        when(postRepository.findAllWithTags()).thenReturn(List.of(p1));

        List<TopicDto> topics = service.getTopics("ALL", 8);

        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).postCount()).isEqualTo(1);
    }

    @Test
    void topicsFilterByCategory() {
        Post p1 = makePost(1L, "湖人");
        p1.setCategory("NEWS");
        Post p2 = makePost(2L, "湖人");
        p2.setCategory("DEBATE");

        when(postRepository.findByCategoryWithTags("NEWS")).thenReturn(List.of(p1));

        List<TopicDto> topics = service.getTopics("NEWS", 8);

        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).postCount()).isEqualTo(1);
    }

    @Test
    void normalizeTagStripsWhitespace() {
        assertThat(PostService.normalizeTag("  湖人  ")).isEqualTo("湖人");
        assertThat(PostService.normalizeTag("")).isEqualTo("");
        assertThat(PostService.normalizeTag(null)).isEqualTo("");
    }

    @Test
    void containsNormalizedTagMatchesExactly() {
        assertThat(PostService.containsNormalizedTag("湖人, 季后赛", "湖人")).isTrue();
        assertThat(PostService.containsNormalizedTag("湖人, 季后赛", "湖")).isFalse();
        assertThat(PostService.containsNormalizedTag("湖人人, 季后赛", "湖人")).isFalse();
        assertThat(PostService.containsNormalizedTag(null, "湖人")).isFalse();
        assertThat(PostService.containsNormalizedTag("湖人", "")).isFalse();
    }

    @Test
    void listMapsCreatedAtSortAliasBeforeCallingRepository() {
        when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        // 空页不会触发身份/收藏批量查询，因此这里不 stub userAccountRepository（严格模式会判定多余）

        PageRequest request = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("id")
        ));

        service.list(null, null, null, request);

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAll(captor.capture());
        Sort safeSort = captor.getValue().getSort();
        assertThat(safeSort.getOrderFor("createdAt")).isNull();
        assertThat(safeSort.getOrderFor("createTime")).isNotNull();
        assertThat(safeSort.getOrderFor("createTime").isDescending()).isTrue();
        assertThat(safeSort.getOrderFor("id")).isNotNull();
        assertThat(safeSort.getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void listRejectsUnknownSortBeforeJpaCanThrowFiveHundred() {
        PageRequest request = PageRequest.of(0, 20, Sort.by("createdAtBogus"));

        assertThatThrownBy(() -> service.list(null, null, null, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("排序字段")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(postRepository);
    }

    @Test
    void tagFilterRespectsPageableSort() {
        Post p1 = makePost(1L, "湖人");
        p1.setViewCount(100);
        p1.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        Post p2 = makePost(2L, "湖人");
        p2.setViewCount(500);
        p2.setCreateTime(LocalDateTime.of(2025, 1, 2, 0, 0));
        Post p3 = makePost(3L, "湖人");
        p3.setViewCount(50);
        p3.setCreateTime(LocalDateTime.of(2025, 1, 3, 0, 0));

        when(postRepository.findAllPosts()).thenReturn(List.of(p1, p2, p3));
        when(userAccountRepository.findAllById(any())).thenReturn(List.of());

        // Sort by viewCount DESC
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "viewCount"));
        Page<PostDto> result = service.list(null, null, "湖人", pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent().get(0).viewCount()).isEqualTo(500);
        assertThat(result.getContent().get(1).viewCount()).isEqualTo(100);
        assertThat(result.getContent().get(2).viewCount()).isEqualTo(50);
    }

    @Test
    void tagFilterPaginationReturnsCorrectPage() {
        // Create 15 posts with tag "湖人"
        java.util.ArrayList<Post> posts = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            Post p = makePost((long) i, "湖人");
            p.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0).plusDays(i));
            posts.add(p);
        }

        when(postRepository.findAllPosts()).thenReturn(posts);
        when(userAccountRepository.findAllById(any())).thenReturn(List.of());

        // Page 1 (second page), size 10
        PageRequest pageRequest = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<PostDto> result = service.list(null, null, "湖人", pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(5); // 15 - 10 = 5 on second page
    }

    @Test
    void toggleLikeDeletesExistingLikeAndOnlyDecrementsWhenRowDeleted() {
        Post post = makePost(10L, "湖人");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(1);

        boolean liked = service.toggleLike(10L, 7L);

        assertThat(liked).isFalse();
        verify(postRepository).bumpLikeCount(10L, -1);
        verify(postLikeRepository, never()).insertIgnore(any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void toggleLikeInsertsMissingLikeAndOnlyIncrementsWhenRowInserted() {
        Post post = makePost(10L, "湖人");
        UserAccount liker = new UserAccount();
        liker.setId(7L);
        liker.setUsername("tester");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(0);
        when(postLikeRepository.insertIgnore(10L, 7L)).thenReturn(1);
        when(userAccountRepository.findById(7L)).thenReturn(Optional.of(liker));

        boolean liked = service.toggleLike(10L, 7L);

        assertThat(liked).isTrue();
        verify(postRepository).bumpLikeCount(10L, 1);
        verify(notificationService).notifyPostAuthor(1L, 7L, "tester", "test", 10L);
    }

    @Test
    void toggleLikeConcurrentDuplicateInsertBecomesConflictWithoutCounterBump() {
        Post post = makePost(10L, "湖人");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L)).thenReturn(0);
        when(postLikeRepository.insertIgnore(10L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.toggleLike(10L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("点赞状态正在更新")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(postRepository, never()).bumpLikeCount(anyLong(), anyInt());
        verifyNoInteractions(notificationService);
    }

    @Test
    void toggleLikeOptimisticLockingFailureBecomesConflictWithoutCounterBump() {
        Post post = makePost(10L, "湖人");
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(postLikeRepository.deleteByPostIdAndUserId(10L, 7L))
                .thenThrow(new OptimisticLockingFailureException("stale like row"));

        assertThatThrownBy(() -> service.toggleLike(10L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("点赞状态正在更新")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(postRepository, never()).bumpLikeCount(anyLong(), anyInt());
        verifyNoInteractions(notificationService);
    }

    private Post makePost(Long id, String tags) {
        Post p = new Post();
        p.setId(id);
        p.setUserId(1L);
        p.setTitle("test");
        p.setContent("content");
        p.setCategory("DISCUSSION");
        p.setTags(tags);
        p.setViewCount(0);
        p.setLikeCount(0);
        p.setCommentCount(0);
        return p;
    }
}

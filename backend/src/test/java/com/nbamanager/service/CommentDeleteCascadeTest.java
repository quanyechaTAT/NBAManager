package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nbamanager.domain.Comment;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.service.support.FakeTransactionManager;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * F5 回归：删除评论时必须级联清理 comment_likes。
 *
 * 压测报告 §九 P4：给评论点赞后删除该评论，comment 行删掉了但 comment_likes
 * 仍残留 1 行孤儿（comments 维度 SUM(like_count)=3 vs COUNT(comment_likes)=5）。
 * 对比 PostService.deletePostDependents() 会先取评论 id 再删评论点赞，
 * CommentService.delete() 缺了这一步。
 *
 * 注意：本类【刻意不设置 SecurityContextHolder】。它是全局 ThreadLocal，
 * 单测里设置认证会把身份泄漏给同 JVM 的后续测试（实测让 AiReplaySecurityTest
 * 的匿名请求被当成已认证，期望 4xx 却得到 200）。断言所需行为无需认证：
 * userId == 评论作者 -> 放行；userId != 作者且非管理员 -> 403。
 */
@ExtendWith(MockitoExtension.class)
class CommentDeleteCascadeTest {

    private static final Long COMMENT_ID = 777L;
    private static final Long POST_ID = 55L;
    private static final Long AUTHOR_ID = 7L;

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private NotificationService notificationService;

    private CommentService service;
    private Comment comment;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, commentLikeRepository, postRepository,
                userAccountRepository, notificationService, new FakeTransactionManager());
        comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setUserId(AUTHOR_ID);
        comment.setPostId(POST_ID);
        comment.setContent("to be deleted");
        lenient().when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
    }

    @Test
    void deletingCommentAlsoRemovesItsLikes() {
        service.delete(COMMENT_ID, AUTHOR_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(commentLikeRepository).deleteByCommentIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactly(COMMENT_ID);

        verify(commentRepository).deleteById(COMMENT_ID);
    }

    @Test
    void likesAreRemovedBeforeTheCommentRowItself() {
        // 先清从属数据再删主记录，中途失败也不会留下孤儿点赞
        service.delete(COMMENT_ID, AUTHOR_ID);

        InOrder inOrder = inOrder(commentLikeRepository, commentRepository);
        inOrder.verify(commentLikeRepository).deleteByCommentIdIn(any());
        inOrder.verify(commentRepository).deleteById(COMMENT_ID);
    }

    @Test
    void deletingCommentDecrementsPostCommentCount() {
        service.delete(COMMENT_ID, AUTHOR_ID);

        verify(postRepository).bumpCommentCount(POST_ID, -1);
    }

    @Test
    void deletingCommentWithoutPostDoesNotTouchCommentCount() {
        comment.setPostId(null);

        service.delete(COMMENT_ID, AUTHOR_ID);

        verify(commentLikeRepository).deleteByCommentIdIn(any());
        verify(commentRepository).deleteById(COMMENT_ID);
        verify(postRepository, never()).bumpCommentCount(anyLong(), anyInt());
    }

    @Test
    void forbiddenDeletionRemovesNothing() {
        // 既不是作者也不是管理员 -> 403，且不得产生任何删除动作
        assertThatThrownBy(() -> service.delete(COMMENT_ID, 999L))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(commentLikeRepository, never()).deleteByCommentIdIn(any());
        verify(commentRepository, never()).deleteById(anyLong());
    }
}
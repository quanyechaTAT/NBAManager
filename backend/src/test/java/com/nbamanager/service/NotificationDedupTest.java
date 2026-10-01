package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Notification;
import com.nbamanager.repository.NotificationRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * P1-3 回归（安全报告 §十二 V4）：通知轰炸两向量的去重。
 *
 * POST_LIKE 在第 3 轮已有"未读去重"，本轮补齐：
 * - COMMENT_LIKE：同一评论未读只留一条（去重键=commentId，不含 senderName/content）；
 * - NEW_COMMENT：同帖未读合并一条。
 */
@ExtendWith(MockitoExtension.class)
class NotificationDedupTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserFavoriteRepository userFavoriteRepository;
    @Mock private TeamRepository teamRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userFavoriteRepository, teamRepository);
    }

    @Test
    void commentLikeDedupesOnUnreadForSameComment() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "COMMENT_LIKE", 55L)).thenReturn(true);

        service.notifyCommentAuthor(7L, 2L, "alice", "content", 55L, 300L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void commentLikeFromDifferentUserStillCreatesOnePerComment() {
        // 第一条：无未读 → 落库，relatedId 必须是 commentId（而非 postId）
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "COMMENT_LIKE", 55L)).thenReturn(false);

        service.notifyCommentAuthor(7L, 2L, "alice", "content", 55L, 300L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedId()).as("去重键与 relatedId 都是 commentId").isEqualTo(55L);
        assertThat(captor.getValue().getType()).isEqualTo("COMMENT_LIKE");
    }

    // ==================== P2：postId 跳转列（第 5 轮任务书） ====================

    @Test
    void commentLikeStoresCommentIdAsDedupKeyAndPostIdForNavigation() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "COMMENT_LIKE", 55L)).thenReturn(false);

        service.notifyCommentAuthor(7L, 2L, "alice", "content", 55L, 300L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedId())
                .as("relatedId（去重键）保持 commentId")
                .isEqualTo(55L);
        assertThat(captor.getValue().getPostId())
                .as("post_id 冗余列必须携带帖子定位，前端据此跳转")
                .isEqualTo(300L);
    }

    @Test
    void commentLikeWithNullPostIdStillDedupesOnCommentId() {
        // 比赛评论（无 postId）不破坏去重语义
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "COMMENT_LIKE", 55L)).thenReturn(false);

        service.notifyCommentAuthor(7L, 2L, "alice", "content", 55L, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedId()).isEqualTo(55L);
        assertThat(captor.getValue().getPostId()).isNull();
    }

    @Test
    void dedupeStillWorksAfterPostIdColumnAdded() {
        // postId 列加入后，去重键仍是 (userId, type, relatedId=commentId)
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "COMMENT_LIKE", 55L)).thenReturn(true);

        service.notifyCommentAuthor(7L, 2L, "alice", "content", 55L, 300L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void postLikeNotificationCarriesSamePostIdAsRelatedId() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "POST_LIKE", 300L)).thenReturn(false);

        service.notifyPostAuthor(7L, 2L, "alice", "title", 300L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedId()).isEqualTo(300L);
        assertThat(captor.getValue().getPostId()).isEqualTo(300L);
    }

    @Test
    void dtoRoundTripExposesPostIdAndLegacyNullFallsBackToRelatedId() {
        Notification legacy = new Notification();   // 存量行：post_id 为 NULL
        legacy.setId(1L);
        legacy.setType("COMMENT_LIKE");
        legacy.setRelatedId(300L);
        legacy.setIsRead(false);
        Notification fresh = new Notification();    // 新行：relatedId=commentId + postId
        fresh.setId(2L);
        fresh.setType("COMMENT_LIKE");
        fresh.setRelatedId(55L);
        fresh.setPostId(300L);
        fresh.setIsRead(false);

        var legacyDto = service.toDtoForTest(legacy);
        var freshDto = service.toDtoForTest(fresh);

        assertThat(freshDto.postId())
                .as("新通知必须携带可跳转的帖子定位")
                .isEqualTo(300L);
        assertThat(legacyDto.postId()).isNull();
        assertThat(legacyDto.relatedId())
                .as("存量通知前端回退 relatedId（其值恰为 postId），跳转不回归")
                .isEqualTo(300L);
    }

    @Test
    void newCommentMergesUnreadPerPost() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "NEW_COMMENT", 100L)).thenReturn(true);

        service.notifyPostAuthorNewComment(7L, 2L, "alice", "title", 100L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void newCommentCreatesWhenNoUnreadExists() {
        when(notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                7L, "NEW_COMMENT", 100L)).thenReturn(false);

        service.notifyPostAuthorNewComment(7L, 2L, "alice", "title", 100L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("NEW_COMMENT");
        assertThat(captor.getValue().getRelatedId()).isEqualTo(100L);
    }
}

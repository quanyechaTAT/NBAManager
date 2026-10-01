package com.nbamanager.service;

import com.nbamanager.domain.Notification;
import com.nbamanager.domain.Team;
import com.nbamanager.domain.UserFavorite;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.NotificationRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.NotificationDto;
import com.nbamanager.web.support.PageRequestSanitizer;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final TeamRepository teamRepository;

    /** 通知列表允许的排序字段（默认按创建时间倒序）。 */
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "type", "isRead", "createTime");
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "createdAt", "createTime");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createTime");

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(Long userId, Pageable pageable) {
        // 仓储方法名已固定 createTime 倒序，排序白名单用于拦截非法 sort 而非改变排序
        Pageable safePageable = PageRequestSanitizer.sanitize(
                pageable, ALLOWED_SORTS, SORT_ALIASES, null);
        return notificationRepository.findByUserIdOrderByCreateTimeDesc(userId, safePageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return (int) notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id, Long userId, boolean admin) {
        Notification notification = getOwnedNotification(id, userId, admin);
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        java.util.List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        for (Notification n : unread) {
            n.setIsRead(true);
        }
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void create(Long userId, String type, String title, String content, Long relatedId) {
        create(userId, type, title, content, relatedId, null);
    }

    /**
     * @param relatedId 业务关联键（去重与既有前端语义，如 COMMENT_LIKE=commentId）
     * @param postId    跳转用帖子定位（无帖场景传 null，见 Notification.postId 注释）
     */
    @Transactional
    public void create(Long userId, String type, String title, String content,
                       Long relatedId, Long postId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setPostId(postId);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyPostAuthor(Long postAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (postAuthorId.equals(senderId)) return;
        // 同一帖子只保留一条未读点赞通知（不同用户点赞合并；已读后再赞会重新通知）
        if (notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                postAuthorId, "POST_LIKE", postId)) {
            return;
        }
        create(postAuthorId, "POST_LIKE", "收到点赞",
                senderName + " 赞了你的帖子「" + postTitle + "」", postId, postId);
    }

    /**
     * 评论被点赞（COMMENT_LIKE）。
     *
     * 安全报告 §十二 V4：与 POST_LIKE 对齐"未读去重"——同一评论未读只留一条，
     * toggle 赞 10 次不再刷出 5 条通知。去重键是 <b>commentId</b>（relatedId 同步存
     * commentId）：POST_LIKE 的教训是不能把 senderName/content 放进键里（不同用户
     * 点赞会被误合并），这里同样只按"哪条评论"去重，不区分点赞者。
     * 注意：relatedId 语义从 postId 变为 commentId（此前存的是 postId）。
     */
    @Transactional
    public void notifyCommentAuthor(Long commentAuthorId, Long senderId, String senderName,
                                    String commentContent, Long commentId, Long postId) {
        if (commentAuthorId.equals(senderId)) return;
        if (commentId == null) return;
        if (notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                commentAuthorId, "COMMENT_LIKE", commentId)) {
            return;
        }
        create(commentAuthorId, "COMMENT_LIKE", "评论被点赞",
                senderName + " 赞了你的评论「" + truncate(commentContent, 30) + "」",
                commentId, postId);
    }

    /**
     * 新评论（NEW_COMMENT）：同帖未读合并一条（安全报告 §十二 V4）。
     * 20 连发评论只产生 1 条未读通知；已读后新评论会再次通知。
     */
    @Transactional
    public void notifyPostAuthorNewComment(Long postAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (postAuthorId.equals(senderId)) return;
        if (notificationRepository.existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(
                postAuthorId, "NEW_COMMENT", postId)) {
            return;
        }
        create(postAuthorId, "NEW_COMMENT", "新评论",
                senderName + " 评论了你的帖子「" + postTitle + "」", postId, postId);
    }

    @Transactional
    public void notifyCommentAuthorReply(Long commentAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (commentAuthorId.equals(senderId)) return;
        create(commentAuthorId, "POST_REPLY", "收到回复",
                senderName + " 回复了你在「" + postTitle + "」中的评论", postId, postId);
    }

    @Transactional
    public void notifyPostDeleted(Long postAuthorId, String postTitle) {
        create(postAuthorId, "POST_DELETED", "帖子被删除",
                "你的帖子「" + postTitle + "」已被管理员删除", null);
    }

    @Transactional
    public void notifyFavorited(Long postAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (postAuthorId.equals(senderId)) return;
        create(postAuthorId, "POST_FAVORITE", "帖子被收藏",
                senderName + " 收藏了你的帖子「" + postTitle + "」", postId, postId);
    }

    /**
     * 通知关注某球队的所有用户：球队有最新比赛记录
     */
    @Transactional
    public void notifyTeamFollowersNewMatch(String teamName, String opponentTeam, int teamScore, int opponentScore, Long matchId) {
        // 查找所有关注该球队的用户
        List<UserFavorite> favorites = userFavoriteRepository.findByTargetTypeAndTargetId("TEAM", getTeamIdByName(teamName));
        String result = teamScore > opponentScore ? "胜" : "负";
        String content = String.format("您关注的%s有最新比赛记录：%s %d:%d %s",
                teamName, teamName, teamScore, opponentScore, opponentTeam);

        for (UserFavorite fav : favorites) {
            create(fav.getUserId(), "TEAM_MATCH", "球队比赛更新", content, matchId);
        }
    }

    /**
     * 根据球队名获取球队ID
     */
    private Long getTeamIdByName(String teamName) {
        Team team = teamRepository.findByName(teamName);
        return team != null ? team.getId() : 0L;
    }

    /** 归属校验：通知属于本人或管理员才能操作（IDOR 防护） */
    private Notification getOwnedNotification(Long id, Long userId, boolean admin) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new com.nbamanager.exception.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "通知不存在"));
        if (!admin && !notification.getUserId().equals(userId)) {
            // 404 而非 403：不向请求者泄露他人通知的存在性
            throw new com.nbamanager.exception.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "通知不存在");
        }
        return notification;
    }

    @Transactional
    public void delete(Long id, Long userId, boolean admin) {
        notificationRepository.delete(getOwnedNotification(id, userId, admin));
    }

    @Transactional
    public void clearRead(Long userId) {
        java.util.List<Notification> readNotifications = notificationRepository.findByUserIdAndIsReadTrue(userId);
        notificationRepository.deleteAll(readNotifications);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /** 包级可见供测试锁定 DTO 往返（存量 NULL postId / 新行 postId）。 */
    NotificationDto toDtoForTest(Notification notification) {
        return toDto(notification);
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedId(),
                notification.getPostId(),
                notification.getIsRead(),
                notification.getCreateTime());
    }
}

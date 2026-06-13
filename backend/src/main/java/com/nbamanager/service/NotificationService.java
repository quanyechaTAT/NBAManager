package com.nbamanager.service;

import com.nbamanager.domain.Notification;
import com.nbamanager.domain.Team;
import com.nbamanager.domain.UserFavorite;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.NotificationRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return (int) notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "通知不存在: " + id));
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
        // 不给自己发通知
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyPostAuthor(Long postAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (postAuthorId.equals(senderId)) return;
        create(postAuthorId, "POST_LIKE", "收到点赞",
                senderName + " 赞了你的帖子「" + postTitle + "」", postId);
    }

    @Transactional
    public void notifyCommentAuthor(Long commentAuthorId, Long senderId, String senderName, String commentContent, Long postId) {
        if (commentAuthorId.equals(senderId)) return;
        create(commentAuthorId, "COMMENT_LIKE", "评论被点赞",
                senderName + " 赞了你的评论「" + truncate(commentContent, 30) + "」", postId);
    }

    @Transactional
    public void notifyPostAuthorNewComment(Long postAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (postAuthorId.equals(senderId)) return;
        create(postAuthorId, "NEW_COMMENT", "新评论",
                senderName + " 评论了你的帖子「" + postTitle + "」", postId);
    }

    @Transactional
    public void notifyCommentAuthorReply(Long commentAuthorId, Long senderId, String senderName, String postTitle, Long postId) {
        if (commentAuthorId.equals(senderId)) return;
        create(commentAuthorId, "POST_REPLY", "收到回复",
                senderName + " 回复了你在「" + postTitle + "」中的评论", postId);
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
                senderName + " 收藏了你的帖子「" + postTitle + "」", postId);
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

    @Transactional
    public void delete(Long id) {
        notificationRepository.deleteById(id);
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

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedId(),
                notification.getIsRead(),
                notification.getCreateTime());
    }
}

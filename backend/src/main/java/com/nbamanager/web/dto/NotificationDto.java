package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String content,
        Long relatedId,
        /** 跳转用帖子定位（COMMENT_LIKE 存评论所在帖；历史行为 NULL，前端回退 relatedId） */
        Long postId,
        Boolean isRead,
        LocalDateTime createTime) {}

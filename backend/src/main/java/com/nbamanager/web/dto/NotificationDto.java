package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String content,
        Long relatedId,
        Boolean isRead,
        LocalDateTime createTime) {}

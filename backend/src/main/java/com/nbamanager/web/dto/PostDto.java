package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record PostDto(
        Long id,
        Long userId,
        String title,
        String content,
        String category,
        String tags,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Integer favoriteCount,
        Boolean isTop,
        Boolean hasPoll,
        boolean likedByMe,
        LocalDateTime createTime) {}

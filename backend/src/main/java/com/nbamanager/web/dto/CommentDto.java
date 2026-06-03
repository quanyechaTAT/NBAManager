package com.nbamanager.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommentDto(
        Long id,
        Long postId,
        Long userId,
        String username,
        Long parentId,
        String content,
        Integer likeCount,
        boolean likedByMe,
        boolean likedByAuthor,
        boolean repliedByAuthor,
        boolean pinned,
        LocalDateTime createTime,
        List<CommentDto> replies) {}

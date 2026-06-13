package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        Long postId,
        String gameId,
        @NotBlank @Size(max = 2000) String content,
        Long parentId) {}

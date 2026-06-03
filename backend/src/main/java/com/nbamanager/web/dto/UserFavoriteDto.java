package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record UserFavoriteDto(
        Long id,
        String targetType,
        Long targetId,
        LocalDateTime createTime) {}

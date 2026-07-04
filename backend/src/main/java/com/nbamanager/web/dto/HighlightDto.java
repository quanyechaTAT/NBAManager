package com.nbamanager.web.dto;

public record HighlightDto(
        Long id,
        Integer period,
        String gameClock,
        String highlightType,
        String title,
        String description,
        Long playerId,
        Long teamId,
        String scoreAfter
) {}

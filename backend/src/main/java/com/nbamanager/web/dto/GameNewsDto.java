package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record GameNewsDto(
        Long id,
        String title,
        String summary,
        String content,
        String homeTeam,
        String awayTeam,
        Integer homeScore,
        Integer awayScore,
        LocalDateTime gameStartTime,
        LocalDateTime gameEndTime,
        String status,
        LocalDateTime createTime,
        String nbaGameId,
        String category,
        String sourceUrl,
        String imageUrl) {}

package com.nbamanager.web.dto;

import java.time.LocalDateTime;

public record DraftPickDto(
        Long id,
        Integer year,
        Integer round,
        Integer pickNumber,
        String teamName,
        String teamNameEn,
        String playerName,
        String playerNameEn,
        String notes,
        LocalDateTime createTime) {}

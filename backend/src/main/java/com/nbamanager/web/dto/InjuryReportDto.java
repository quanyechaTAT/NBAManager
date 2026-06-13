package com.nbamanager.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record InjuryReportDto(
        Long id,
        Long playerId,
        String playerName,
        String teamName,
        String injuryType,
        String status,
        String description,
        LocalDate startDate,
        LocalDate expectedReturn,
        LocalDateTime createTime) {}

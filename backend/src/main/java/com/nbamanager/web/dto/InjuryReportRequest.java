package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InjuryReportRequest(
        @NotNull Long playerId,
        @NotBlank String playerName,
        @NotBlank String teamName,
        @NotBlank String injuryType,
        @NotBlank String status,
        String description,
        LocalDate startDate,
        LocalDate expectedReturn) {}

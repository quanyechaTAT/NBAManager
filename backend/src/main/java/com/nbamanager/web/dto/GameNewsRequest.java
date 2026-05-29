package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record GameNewsRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 300) String summary,
        @NotBlank String content,
        @NotBlank @Size(max = 80) String homeTeam,
        @NotBlank @Size(max = 80) String awayTeam,
        Integer homeScore,
        Integer awayScore,
        @NotNull LocalDateTime gameStartTime,
        @NotNull LocalDateTime gameEndTime) {}

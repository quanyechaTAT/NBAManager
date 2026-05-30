package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MatchRecordRequest(
        @NotBlank String homeTeam,
        @NotBlank String awayTeam,
        @NotNull Integer homeScore,
        @NotNull Integer awayScore,
        @NotNull LocalDate matchDate,
        @NotBlank String season,
        @NotBlank String status
) {}

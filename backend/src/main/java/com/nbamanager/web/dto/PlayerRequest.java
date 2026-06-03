package com.nbamanager.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerRequest(
        @NotBlank String name,
        @NotNull Long teamId,
        @NotBlank String position,
        @NotNull @DecimalMin("0.0") Double pointsPerGame,
        @NotNull @DecimalMin("0.0") Double reboundsPerGame,
        @NotNull @DecimalMin("0.0") Double assistsPerGame,
        @NotNull @DecimalMin("0.0") Double stealsPerGame,
        @NotNull @Min(0) Integer gamesPlayed,
        @NotNull @DecimalMin("0.0") Double minutesPerGame,
        @NotNull @DecimalMin("0.0") Double fieldGoalPct,
        @NotNull @DecimalMin("0.0") Double threePointPct,
        @NotNull @DecimalMin("0.0") Double freeThrowPct,
        @NotNull @DecimalMin("0.0") Double blocksPerGame,
        @NotNull @DecimalMin("0.0") Double turnoversPerGame,
        @NotNull @DecimalMin("0.0") Double efficiency,
        @NotNull @DecimalMin("0.0") Double trueShootingPct,
        @NotNull @DecimalMin("0.0") Double usagePct,
        @Size(max = 3) String jerseyNumber,
        @NotBlank @Size(max = 32) String height,
        @NotNull @Min(0) Integer weight,
        @NotBlank @Size(max = 64) String country) {}

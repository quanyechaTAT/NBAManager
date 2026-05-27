package com.nbamanager.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlayerRequest(
        @NotBlank String name,
        @NotNull Long teamId,
        @NotBlank String position,
        @NotNull @DecimalMin("0.0") Double pointsPerGame,
        @NotNull @DecimalMin("0.0") Double reboundsPerGame,
        @NotNull @DecimalMin("0.0") Double assistsPerGame,
        @NotNull @DecimalMin("0.0") Double stealsPerGame) {}

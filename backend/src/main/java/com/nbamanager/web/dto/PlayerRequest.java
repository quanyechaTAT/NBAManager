package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String name,
        @NotNull Long teamId,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String position,
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
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 3) String jerseyNumber,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank @Size(max = 32) String height,
        @NotNull @Min(0) Integer weight,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank @Size(max = 64) String country) {}

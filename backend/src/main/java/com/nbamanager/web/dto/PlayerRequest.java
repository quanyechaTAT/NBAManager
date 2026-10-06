package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String name,
        @NotNull Long teamId,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String position,
        @DecimalMin("0.0") Double pointsPerGame,
        @DecimalMin("0.0") Double reboundsPerGame,
        @DecimalMin("0.0") Double assistsPerGame,
        @DecimalMin("0.0") Double stealsPerGame,
        @Min(0) Integer gamesPlayed,
        @DecimalMin("0.0") Double minutesPerGame,
        @DecimalMin("0.0") @DecimalMax("1.0") Double fieldGoalPct,
        @DecimalMin("0.0") @DecimalMax("1.0") Double threePointPct,
        @DecimalMin("0.0") @DecimalMax("1.0") Double freeThrowPct,
        @DecimalMin("0.0") Double blocksPerGame,
        @DecimalMin("0.0") Double turnoversPerGame,
        @DecimalMin("0.0") Double efficiency,
        @DecimalMin("0.0") @DecimalMax("1.0") Double trueShootingPct,
        @DecimalMin("0.0") @DecimalMax("100.0") Double usagePct,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 3) String jerseyNumber,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 32) String height,
        @Min(0) Integer weight,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 64) String country) {}

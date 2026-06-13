package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DraftPickRequest(
        @NotNull Integer year,
        @NotNull Integer round,
        @NotNull Integer pickNumber,
        @NotBlank String teamName,
        String playerName,
        String notes) {}

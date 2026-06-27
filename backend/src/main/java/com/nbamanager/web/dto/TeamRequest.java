package com.nbamanager.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TeamRequest(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String conference,
        String division,
        @NotNull @Min(0) Integer wins,
        @NotNull @Min(0) Integer losses,
        String logoUrl) {}

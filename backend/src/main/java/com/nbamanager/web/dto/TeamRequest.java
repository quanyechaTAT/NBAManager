package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TeamRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String name,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String city,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String conference,
        @JsonDeserialize(using = StrictStringDeserializer.class) String division,
        @NotNull @Min(0) Integer wins,
        @NotNull @Min(0) Integer losses,
        @JsonDeserialize(using = StrictStringDeserializer.class) String logoUrl) {}

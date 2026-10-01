package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InjuryReportRequest(
        @NotNull Long playerId,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String playerName,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String teamName,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String injuryType,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String status,
        @JsonDeserialize(using = StrictStringDeserializer.class) String description,
        LocalDate startDate,
        LocalDate expectedReturn) {}

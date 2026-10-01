package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record GameNewsRequest(
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 120) String title,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 300) String summary,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank String content,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 80) String homeTeam,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 80) String awayTeam,
        Integer homeScore,
        Integer awayScore,
        @NotNull LocalDateTime gameStartTime,
        @NotNull LocalDateTime gameEndTime,
        @JsonDeserialize(using = StrictStringDeserializer.class) String status,
        @JsonDeserialize(using = StrictStringDeserializer.class) String nbaGameId,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 20) String category,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 500) String sourceUrl,
        @JsonDeserialize(using = StrictStringDeserializer.class) @Size(max = 500) String imageUrl) {}

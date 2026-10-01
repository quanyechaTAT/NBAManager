package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;

public record PollRequest(
        Long postId,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String question,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String option1,
        @JsonDeserialize(using = StrictStringDeserializer.class) @NotBlank String option2,
        @JsonDeserialize(using = StrictStringDeserializer.class) String option3,
        @JsonDeserialize(using = StrictStringDeserializer.class) String option4) {}

package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PollRequest(
        Long postId,
        @NotBlank String question,
        @NotBlank String option1,
        @NotBlank String option2,
        String option3,
        String option4) {}

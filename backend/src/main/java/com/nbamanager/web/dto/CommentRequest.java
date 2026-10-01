package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        Long postId,
        @JsonDeserialize(using = StrictStringDeserializer.class) String gameId,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 2000) String content,
        Long parentId) {}

package com.nbamanager.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(
        // 字符串字段严格类型：拒绝 JSON number/boolean 强转（第 9 轮 I9-1，与 RAG question 同型）
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 200) String title,
        // posts.content 列是 TEXT(64KB)：超限给 400 校验错误，而不是落库时 409（安全 12.3 I3）
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 60000) String content,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @NotBlank @Size(max = 20) String category,
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @Size(max = 500) String tags) {}

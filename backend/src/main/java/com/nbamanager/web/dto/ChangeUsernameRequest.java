package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank(message = "新用户名不能为空")
        @Size(min = 2, max = 30, message = "用户名长度应为2-30个字符")
        String newUsername) {}

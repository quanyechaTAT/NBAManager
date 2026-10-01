package com.nbamanager.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度 3-64 个字符")
        @Pattern(regexp = "^[\\w\u4e00-\u9fa5.-]+$", message = "用户名仅支持中英文、数字、下划线、点和连字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度 6-128 个字符")
        String password
) {}

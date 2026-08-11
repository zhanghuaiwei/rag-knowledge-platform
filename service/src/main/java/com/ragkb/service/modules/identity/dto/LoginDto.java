package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录入参（form 模式）。
 */
public record LoginDto(@NotBlank String username, @NotBlank String password) {
}

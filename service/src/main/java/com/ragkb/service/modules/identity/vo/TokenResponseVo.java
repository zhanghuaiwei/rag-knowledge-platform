package com.ragkb.service.modules.identity.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 登录/刷新响应视图：access token 进响应体（前端仅内存持有）；refresh token 只写进
 * HttpOnly cookie {@code ragkb_refresh}，不回传给前端（见 OpenAPI TokenResponse）。
 */
public record TokenResponseVo(
        @NotBlank String accessToken,
        String tokenType,
        long expiresIn,
        @NotNull AuthSessionVo session) {
}

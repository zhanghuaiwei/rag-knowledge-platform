package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自助修改密码入参（对齐 OpenAPI {@code ChangePasswordRequest}）。
 *
 * <p>服务端必须核验 {@code currentPassword}（PasswordEncoder.matches），
 * 成功后将 {@code mustChangePassword} 置 false。
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 72) String newPassword) {
}

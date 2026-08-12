package com.ragkb.service.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码入参（对齐 OpenAPI {@code ResetPasswordRequest}）。
 *
 * <p>重置后置 {@code mustChangePassword=true}，用户首登强制改密（BCrypt 72 字节上限）。
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 6, max = 72) String newPassword) {
}

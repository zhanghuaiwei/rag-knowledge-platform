package com.ragkb.service.modules.identity.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;

/**
 * 本地账号密码强度策略（自助改密 / 管理员建号 / 管理员重置三个密码入口统一适用）。
 *
 * <p>策略：长度 &ge; 8 且同时包含字母与数字。契约 DTO 的 {@code @Size(min=6)} 仅是
 * Bean Validation 的宽松下限，本策略在 Service 层施加更严格的业务下限 ——
 * 所有写入口一致，避免"自助改密要 8 位、管理员建号 6 位就行"的策略漂移。
 *
 * <p>红线：只校验、不落日志 —— 任何日志/审计不得记录密码明文。
 */
final class PasswordPolicy {

    /** 密码最小长度（业务策略下限，严于 DTO 的 min=6）。 */
    static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    /** 校验明文密码强度，不满足抛 {@code BAD_REQUEST}（信息不含密码内容本身）。 */
    static void requireStrong(String rawPassword) {
        // 空值/长度不足直接拒绝（密码内容不回显，错误信息只描述规则）
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "密码至少 " + MIN_LENGTH + " 位，且需同时包含字母和数字");
        }
        // 必须至少含一个字母（大小写均可）
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        // 必须至少含一个数字
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "密码至少 " + MIN_LENGTH + " 位，且需同时包含字母和数字");
        }
    }
}

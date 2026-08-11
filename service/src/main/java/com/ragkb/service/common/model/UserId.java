package com.ragkb.service.common.model;

/**
 * 全局用户标识值对象（04-数据库设计 §3 sys_user）。
 */
public record UserId(long value) {

    public UserId {
        if (value <= 0) {
            throw new IllegalArgumentException("userId 必须为正整数");
        }
    }
}

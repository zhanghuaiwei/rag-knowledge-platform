package com.ragkb.service.common.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ragkb.service.common.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 审计字段自动填充（MyBatis-Plus {@link MetaObjectHandler}）。
 *
 * <p>insert 填 {@code create_by / create_time / update_by / update_time}，
 * update 填 {@code update_by / update_time}。用户 id 取自安全上下文（{@link SecurityUtils}），
 * 系统任务 / API Key 发起时为空；strict 填充不会覆盖调用方显式设置的值。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        Long userId = SecurityUtils.currentUserId();
        this.strictInsertFill(metaObject, "createTime", Instant.class, now);
        this.strictInsertFill(metaObject, "updateTime", Instant.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", Instant.class, Instant.now());
        this.strictUpdateFill(metaObject, "updateBy", Long.class, SecurityUtils.currentUserId());
    }
}

package com.ragkb.service.common.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;

import java.io.Serializable;
import java.time.Instant;

/**
 * 全表统一审计列基类（对齐 {@code deploy/ddl/migrations/V0.3__unified_audit_columns.sql}）。
 *
 * <p>约定：
 * <ul>
 *   <li>{@code del_flag} 逻辑删除（0 未删 / 1 已删），由 MyBatis-Plus {@code @TableLogic}
 *       全局生效：删除走 {@code UPDATE ... SET del_flag=1}，查询/更新自动过滤 del_flag=0；</li>
 *   <li>{@code create_by / create_time} 插入自动填充，{@code update_by / update_time}
 *       插入+更新自动填充（{@link AuditMetaObjectHandler} 从安全上下文取 userId）；</li>
 *   <li>数据库 {@code DEFAULT now()} 与 {@code set_update_time()} 触发器作为兜底，直接 SQL 也维护。</li>
 * </ul>
 *
 * <p>所有实体继承本类后无需再声明这 5 个字段；{@code row_version} 乐观锁等非通用列留在各实体。
 */
public abstract class BaseAuditEntity implements Serializable {

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private Instant createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updateTime;

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}

package com.ragkb.service.modules.governance.vo;

/**
 * 删除进度视图（存储/索引/缓存/备份各阶段是否已完成）。
 */
public record DeletionProgressVo(boolean storage, boolean index, boolean cache, boolean backup) {
}

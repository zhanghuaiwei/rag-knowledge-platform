package com.ragkb.service.common.storage;

import com.ragkb.service.common.model.TenantId;

import java.io.InputStream;
import java.util.Optional;

/**
 * 对象存储端口：保存不可变原文与派生预览（ADR-6 原文不可变）。
 * 领域层只依赖本接口，不依赖具体 S3/MinIO SDK；返回原始字节的接口
 * 必须要求下载权限（00-README §4 统一约定）。
 *
 * @deprecated 已部分迁移：实现类 {@link com.ragkb.service.modules.infrastructure.objectstore.LocalObjectStore}
 *             与 {@link com.ragkb.service.modules.infrastructure.objectstore.MinioObjectStore}
 *             已迁至 {@code com.ragkb.service.modules.infrastructure.objectstore}；接口定义暂留此处以
 *             避免破坏 Spring 注入（DocumentServiceImpl 仍按本接口类型注入，下一轮迭代再迁接口并更新所有引用）。
 */
@Deprecated
public interface ObjectStorePort {

    String put(TenantId tenantId, String objectKey,
               InputStream content, long size, String contentType);

    Optional<InputStream> get(TenantId tenantId, String objectKey);

    boolean exists(TenantId tenantId, String objectKey);

    void delete(TenantId tenantId, String objectKey);
}

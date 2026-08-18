package com.ragkb.service.modules.infrastructure.port;

import com.ragkb.service.common.model.TenantId;

import java.io.InputStream;
import java.util.Optional;

/**
 * 对象存储端口：保存不可变原文与派生预览（ADR-6 原文不可变）。
 * 领域层只依赖本接口，不依赖具体 S3/MinIO SDK；返回原始字节的接口
 * 必须要求下载权限（00-README §4 统一约定）。
 *
 * 迁移说明：本接口原位于 {@code com.ragkb.service.common.storage}（基础设施适配器不得留在
 * common 的模块化红线），已整体迁至 infrastructure 模块——接口在本包（port/），实现在同模块
 * {@code adapter/}（LocalObjectStore / MinioObjectStore）。目录为 PackageStructureTest
 * 约定的 modules/&lt;feature&gt;/&lt;layer&gt; 两级结构。
 */
public interface ObjectStorePort {

    String put(TenantId tenantId, String objectKey,
               InputStream content, long size, String contentType);

    Optional<InputStream> get(TenantId tenantId, String objectKey);

    boolean exists(TenantId tenantId, String objectKey);

    void delete(TenantId tenantId, String objectKey);
}

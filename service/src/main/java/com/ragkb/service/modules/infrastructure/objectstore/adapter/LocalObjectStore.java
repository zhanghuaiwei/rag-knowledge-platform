package com.ragkb.service.modules.infrastructure.objectstore.adapter;

import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.infrastructure.objectstore.port.ObjectStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * {@link ObjectStorePort} 的本地文件系统实现（开发/单机部署兜底）。
 *
 * <p>对象按 {@code object_key}（形如 {@code 1/2026/08/9f3c...-report.pdf}）落盘到
 * {@code ragkb.storage.local-dir}（默认 {@code ./data/objects}）下，key 与文件路径一一对应。
 *
 * <p>装配开关：{@code ragkb.storage.type=local} 时生效（缺省默认），与
 * {@link MinioObjectStore}（{@code ragkb.storage.type=minio}）互斥。切换存储后端时
 * 领域层与 {@code DocumentService} 无需任何改动（仅依赖 {@link ObjectStorePort}）。
 *
 * <p>⚠️ 边界：
 * <ul>
 *   <li>本实现仅用于本地开发/演示，让「上传 → 落库 → 摄取」链路先跑通；</li>
 *   <li>原文不可变（ADR-6）：{@code put} 到已存在 key 时直接报错，防止误改历史版本；</li>
 *   <li>删除即物理删除（生产由 deletion_task 审计后处置）。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ragkb.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStore implements ObjectStorePort {

    private static final Logger log = LoggerFactory.getLogger(LocalObjectStore.class);

    /** 本地对象存储根目录（key 的分段即目录层级，禁止绝对路径/越级路径）。 */
    private final Path root;

    public LocalObjectStore(@Value("${ragkb.storage.local-dir:./data/objects}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
    }

    @Override
    public String put(TenantId tenantId, String objectKey, InputStream content, long size, String contentType) {
        // key 由调用方生成并已含 tenant 前缀（如 1/2026/08/xxx.pdf），此处按 key 落盘。
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                // 原文不可变：同一 key 重复写入视为冲突，防止静默覆盖历史版本（ADR-6）。
                throw new IllegalStateException("object key already exists: " + objectKey);
            }
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("object stored: tenantId={} key={} size={} contentType={}", tenantId.value(), objectKey, size, contentType);
            return objectKey;
        } catch (IOException e) {
            throw new UncheckedIOException("fail to put object: " + objectKey, e);
        }
    }

    @Override
    public Optional<InputStream> get(TenantId tenantId, String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target));
        } catch (IOException e) {
            throw new UncheckedIOException("fail to open object: " + objectKey, e);
        }
    }

    @Override
    public boolean exists(TenantId tenantId, String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public void delete(TenantId tenantId, String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new UncheckedIOException("fail to delete object: " + objectKey, e);
        }
    }

    /**
     * key → 文件路径。安全约束：key 必须是相对路径且不含 {@code ..}，防止路径穿越；
     * 空段直接忽略（避免双斜杠导致的目录漂移）。
     */
    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("illegal object key: " + objectKey);
        }
        Path relative = root.getFileSystem().getPath(objectKey);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("illegal object key (escape root): " + objectKey);
        }
        return target;
    }
}

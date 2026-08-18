package com.ragkb.service.modules.infrastructure.adapter;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.infrastructure.port.ObjectStorePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

/**
 * {@link ObjectStorePort} 的 MinIO/S3 实现（生产/容器化部署默认）。
 *
 * <p>对象按 {@code object_key}（形如 {@code 1/2026/08/9f3c...-report.pdf}）存入
 * 配置的 bucket（{@code ragkb.storage.minio.bucket}，默认 {@code ragkb}）。
 * key 即对象名，天然按租户/时间分桶，与 {@link LocalObjectStore} 的 key 规则一致，
 * 切换后端无需迁移 key。
 *
 * <p>装配开关：{@code ragkb.storage.type=minio} 时生效，与 {@link LocalObjectStore}
 * 互斥（后者 {@code matchIfMissing=true} 为兜底）。
 *
 * <p>启动行为：MinioClient 懒加载（首次读写/建桶时才构建），配置缺失或 MinIO 未就绪时
 * 应用上下文仍可启动；由 {@link #ensureBucket()} 创建 bucket（若不存在）。
 * 原文不可变（ADR-6）：{@code put} 到已存在 key 时直接报错，防止误改历史版本；
 * 删除即物理删除（生产由 deletion_task 审计后处置）。
 *
 * <p>连接参数：
 * <ul>
 *   <li>{@code ragkb.storage.minio.endpoint}：S3 端点，如 {@code http://localhost:9000}；</li>
 *   <li>{@code ragkb.storage.minio.access-key} / secret-key：凭证（取自 .env 的
 *       {@code MINIO_ROOT_USER} / {@code MINIO_ROOT_PASSWORD}）；</li>
 *   <li>{@code ragkb.storage.minio.bucket}：bucket 名（默认 {@code ragkb}）。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ragkb.storage.type", havingValue = "minio")
public class MinioObjectStore implements ObjectStorePort {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStore.class);

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private volatile MinioClient client;

    public MinioObjectStore(
            @Value("${ragkb.storage.minio.endpoint}") String endpoint,
            @Value("${ragkb.storage.minio.access-key}") String accessKey,
            @Value("${ragkb.storage.minio.secret-key}") String secretKey,
            @Value("${ragkb.storage.minio.bucket:ragkb}") String bucket) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    /** 懒加载 MinioClient：凭据缺失或 MinIO 未就绪时上下文仍可启动，首次真实读写/建桶时才构建并报错。 */
    private MinioClient client() {
        MinioClient local = this.client;
        if (local == null) {
            synchronized (this) {
                local = this.client;
                if (local == null) {
                    local = MinioClient.builder()
                            .endpoint(endpoint)
                            .credentials(accessKey, secretKey)
                            .build();
                    this.client = local;
                }
            }
        }
        return local;
    }

    /** 启动时确保 bucket 存在（不存在则创建），避免首次上传因 bucket 缺失失败。 */
    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("minio bucket created: {}", bucket);
            }
        } catch (Exception e) {
            // 启动期连接失败不阻塞上下文（允许 MinIO 晚于应用启动）；上传时再报错更明确。
            log.warn("minio bucket check/create failed (will retry on first put): bucket={}, cause={}",
                    bucket, e.getMessage());
        }
    }

    @Override
    public String put(TenantId tenantId, String objectKey, InputStream content, long size, String contentType) {
        try {
            if (exists(tenantId, objectKey)) {
                // 原文不可变：同一 key 重复写入视为冲突，防止静默覆盖历史版本（ADR-6）。
                throw new ApiException(ErrorCode.CONFLICT, "对象已存在: " + objectKey);
            }
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            log.info("object stored: tenantId={} key={} size={} contentType={}",
                    tenantId.value(), objectKey, size, contentType);
            return objectKey;
        } catch (ApiException e) {
            throw e; // 原样向上抛业务异常
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象写入 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<InputStream> get(TenantId tenantId, String objectKey) {
        try {
            InputStream stream = client().getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return Optional.of(stream);
        } catch (ErrorResponseException e) {
            // NoSuchKey 等 S3 错误码 → 视为不存在（返回空，由调用方判定权限/404）。
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象读取 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象读取 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(TenantId tenantId, String objectKey) {
        try {
            client().statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象校验 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象校验 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(TenantId tenantId, String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "对象删除 MinIO 失败: " + objectKey + " — " + e.getMessage(), e);
        }
    }
}

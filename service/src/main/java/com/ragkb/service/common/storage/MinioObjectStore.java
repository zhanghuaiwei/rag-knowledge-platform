package com.ragkb.service.common.storage;

/**
 * @deprecated 实现已迁至
 *             {@link com.ragkb.service.modules.infrastructure.objectstore.MinioObjectStore}。
 *             本类仅作类型兼容保留，不再注册为 Bean（{@code @Component} 已移除，避免与新位置重复装配）；
 *             运行时由新位置类装配 Bean。请勿直接引用本类。
 */
@Deprecated
public class MinioObjectStore
        extends com.ragkb.service.modules.infrastructure.objectstore.MinioObjectStore {

    /**
     * 转发至新位置类的构造函数（仅保证类型层级可编译；本类不被 Spring 装配）。
     *
     * @param endpoint  MinIO/S3 端点
     * @param accessKey 访问密钥
     * @param secretKey 秘密密钥
     * @param bucket    bucket 名
     */
    public MinioObjectStore(String endpoint, String accessKey, String secretKey, String bucket) {
        super(endpoint, accessKey, secretKey, bucket);
    }
}

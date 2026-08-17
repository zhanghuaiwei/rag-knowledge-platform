package com.ragkb.service.common.storage;

/**
 * @deprecated 实现已迁至
 *             {@link com.ragkb.service.modules.infrastructure.objectstore.LocalObjectStore}。
 *             本类仅作类型兼容保留，不再注册为 Bean（{@code @Component} 已移除，避免与新位置重复装配）；
 *             运行时由新位置类装配 Bean。请勿直接引用本类。
 */
@Deprecated
public class LocalObjectStore
        extends com.ragkb.service.modules.infrastructure.objectstore.LocalObjectStore {

    /**
     * 转发至新位置类的构造函数（仅保证类型层级可编译；本类不被 Spring 装配）。
     *
     * @param localDir 本地对象存储根目录
     */
    public LocalObjectStore(String localDir) {
        super(localDir);
    }
}

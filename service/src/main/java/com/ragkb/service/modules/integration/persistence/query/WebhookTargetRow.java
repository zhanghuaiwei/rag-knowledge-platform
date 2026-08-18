package com.ragkb.service.modules.integration.persistence.query;

/**
 * Webhook 订阅目标行（投递引擎视角的最小字段集）。
 *
 * <p>背景：{@code webhook_subscription} 表实体属 admin 模块持久化，integration 模块
 * 经自有 Mapper 手写 SQL 直连读取（对齐 identity 模块 UserAccountMapper 直连
 * {@code sys_org/audit_log} 的先例：跨模块不建 Java 依赖，只共享表结构）。
 */
public class WebhookTargetRow {

    /** 订阅 id（webhook_delivery.subscription_id 外键）。 */
    private Long id;

    /** 订阅归属租户（投递记录落同租户）。 */
    private Long tenantId;

    /** 回调地址（https）。 */
    private String targetUrl;

    /** 签名密钥（secret_ref 列原文；HMAC 计算需要恢复明文，故不在日志透出）。 */
    private String secretRef;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }
}

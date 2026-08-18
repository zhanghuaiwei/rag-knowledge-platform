package com.ragkb.service.modules.integration.persistence.query;

import java.time.Instant;

/**
 * 到期投递富行：{@code webhook_delivery} JOIN 订阅（取回调地址与签名密钥）与
 * {@code outbox_event}（取事件 UUID / 类型 / 载荷）的一次性读取结果，
 * 供投递引擎单条 SQL 取齐执行一次投递所需的全部字段。
 */
public class WebhookDeliveryTargetRow {

    /** 投递记录 id（结果回写的主键）。 */
    private Long id;

    /** 投递记录归属租户。 */
    private Long tenantId;

    /** 订阅 id。 */
    private Long subscriptionId;

    /** 关联的 outbox_event 主键 id（webhook_delivery.event_id 外键）。 */
    private Long eventId;

    /** 当前投递状态（PENDING / RETRY）。 */
    private String status;

    /** 已尝试次数（本次执行前）。 */
    private Integer attemptCount;

    /** 回调地址（来自订阅）。 */
    private String targetUrl;

    /** 签名密钥（来自订阅 secret_ref；不入日志）。 */
    private String secretRef;

    /** 事件 UUID（outbox_event.event_id 列，全局唯一，作为 X-RagKB-Event-Id 头）。 */
    private String outboxEventUuid;

    /** 事件类型（outbox_event.event_type，如 document.uploaded）。 */
    private String eventType;

    /** 事件载荷 JSON（outbox_event.payload 原文，即 POST body）。 */
    private String payload;

    /** 本行创建时间（重放守卫与展示用）。 */
    private Instant createTime;

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

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
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

    public String getOutboxEventUuid() {
        return outboxEventUuid;
    }

    public void setOutboxEventUuid(String outboxEventUuid) {
        this.outboxEventUuid = outboxEventUuid;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }
}

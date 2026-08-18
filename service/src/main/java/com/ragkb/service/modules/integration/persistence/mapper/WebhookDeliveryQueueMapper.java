package com.ragkb.service.modules.integration.persistence.mapper;

import com.ragkb.service.modules.integration.persistence.entity.OutboxEvent;
import com.ragkb.service.modules.integration.persistence.query.WebhookDeliveryTargetRow;
import com.ragkb.service.modules.integration.persistence.query.WebhookTargetRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * Webhook 投递队列 Mapper：投递引擎（{@code WebhookDeliveryServiceImpl}）的专属数据访问。
 *
 * <p>跨模块边界说明：{@code webhook_subscription / webhook_delivery} 表实体属 admin 模块
 * 持久化，本 Mapper 经手写 SQL 直连这两张表（对齐 identity 模块 UserAccountMapper 直连
 * {@code sys_org/audit_log} 的先例——跨模块不建 Java 依赖，只共享表结构）；
 * {@code outbox_event} 为本模块自有表。全部 SQL 手写 {@code tenant_id} + {@code del_flag = 0}
 * 过滤（{@code @TableLogic} 不作用于手写 XML）。
 *
 * <p>实现见 {@code resources/mapper/WebhookDeliveryQueueMapper.xml}。
 */
@Mapper
public interface WebhookDeliveryQueueMapper {

    /**
     * 认领到期的 outbox 事件：{@code status IN ('NEW','FAILED')} 且
     * {@code available_at <= now()}，按 id 升序限量返回（走 idx_outbox_dispatch 部分索引）。
     */
    List<OutboxEvent> listDueOutboxEvents(@Param("limit") int limit);

    /**
     * 查询某事件在指定租户内的全部启用订阅（{@code event_types @>} 包含匹配）。
     *
     * @param eventTypeJson 形如 {@code '["document.uploaded"]'} 的 JSON 数组字符串
     *                      （服务层序列化；避免在 SQL 里直写 jsonb {@code ?} 操作符
     *                      与 JDBC 占位符的转义歧义）
     */
    List<WebhookTargetRow> findActiveSubscribers(@Param("tenantId") long tenantId,
                                                 @Param("eventTypeJson") String eventTypeJson);

    /**
     * 幂等登记一条投递记录：撞 {@code uq_webhook_delivery_event}
     * {@code (tenant_id, subscription_id, event_id)} 唯一约束时忽略（at-least-once 语义下
     * 重复 fan-out 不产生重复投递行）。
     *
     * @return 受影响行数（0 = 已存在，跳过；1 = 新登记）
     */
    int insertDeliveryIgnoreConflict(@Param("tenantId") long tenantId,
                                     @Param("subscriptionId") long subscriptionId,
                                     @Param("eventId") long eventId);

    /**
     * 取到期投递富行：{@code status IN ('PENDING','RETRY')} 且 {@code next_attempt_at <= now()}，
     * JOIN 订阅（target_url / secret_ref）与 outbox 事件（event_id UUID / event_type / payload）。
     */
    List<WebhookDeliveryTargetRow> findDueDeliveries(@Param("limit") int limit);

    /** 按主键取单条投递富行（deliverWebhook / replayWebhookDelivery 手动触发场景）。 */
    WebhookDeliveryTargetRow findDeliveryDetail(@Param("deliveryId") long deliveryId);

    /** 按唯一键 (tenant, subscription, event) 反查投递记录主键（手动投递场景定位刚登记的行）。 */
    Long findDeliveryIdByUniqueKey(@Param("tenantId") long tenantId,
                                   @Param("subscriptionId") long subscriptionId,
                                   @Param("eventId") long eventId);

    /**
     * 回写投递结果（终态流转：SUCCEEDED / RETRY / DEAD）。
     *
     * @param status         新状态
     * @param httpStatus     HTTP 状态码（网络层失败为 null）
     * @param responseSha256 响应体 SHA-256 摘要（成功时记录，供对账）
     * @param lastErrorCode  失败错误码（成功为 null）
     * @param attemptCount   累计尝试次数（含本次）
     * @param nextAttemptAt  下次可投递时间（终态为 null）
     * @param deliveredAt    成功送达时间（失败为 null）
     */
    int updateDeliveryOutcome(@Param("id") long id,
                              @Param("status") String status,
                              @Param("httpStatus") Integer httpStatus,
                              @Param("responseSha256") String responseSha256,
                              @Param("lastErrorCode") String lastErrorCode,
                              @Param("attemptCount") int attemptCount,
                              @Param("nextAttemptAt") Instant nextAttemptAt,
                              @Param("deliveredAt") Instant deliveredAt);

    /** 重置投递记录为待投递（死信重放：status=PENDING、attempt_count=0、next_attempt_at=now）。 */
    int resetDeliveryForReplay(@Param("id") long deliveryId);

    /** 标记 outbox 事件发布完成（PUBLISHED + published_at；fan-out 结束即算发布）。 */
    int markOutboxPublished(@Param("id") long id, @Param("publishedAt") Instant publishedAt);

    /**
     * outbox 事件处理失败退避：attempt_count+1，失败达到上限时置 DEAD + dead_letter_at，
     * 否则保持可重试语义并推迟 available_at。
     */
    int bumpOutboxAttempt(@Param("id") long id,
                          @Param("errorCode") String errorCode,
                          @Param("availableAt") Instant availableAt,
                          @Param("dead") boolean dead);
}

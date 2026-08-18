package com.ragkb.service.modules.integration.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.integration.persistence.entity.OutboxEvent;
import com.ragkb.service.modules.integration.persistence.mapper.OutboxEventMapper;
import com.ragkb.service.modules.integration.persistence.mapper.WebhookDeliveryQueueMapper;
import com.ragkb.service.modules.integration.persistence.query.WebhookDeliveryTargetRow;
import com.ragkb.service.modules.integration.persistence.query.WebhookTargetRow;
import com.ragkb.service.modules.integration.port.WebhookSenderPort;
import com.ragkb.service.modules.integration.service.IntegrationUseCase;
import com.ragkb.service.modules.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Webhook 投递引擎（{@link IntegrationUseCase} 实现）：消费 outbox 事件，
 * 按订阅匹配结果登记投递记录并执行签名投递，失败重试、超限死信。
 *
 * <p><b>投递语义（at-least-once）</b>：业务事件与 outbox 写入同事务（{@link OutboxAppender}），
 * 投递器只轮询 {@code status IN ('NEW','FAILED')} 的事件；每轮处理分两段：
 * <ol>
 *   <li><b>fan-out</b>：按 {@code event_type} 匹配启用订阅，幂等登记
 *       {@code webhook_delivery}（撞 {@code uq_webhook_delivery_event} 唯一约束忽略），
 *       登记完成即置 outbox 为 PUBLISHED（投递进度由 delivery 行承载）；</li>
 *   <li><b>deliver</b>：逐条执行到期的 PENDING/RETRY 投递（HTTP POST + HMAC 签名），
 *       成功置 SUCCEEDED，失败按指数退避置 RETRY，尝试次数达到上限置 DEAD。</li>
 * </ol>
 *
 * <p><b>签名方案</b>（GitHub 风格，防篡改 + 时间戳防重放）：
 * 请求头 {@code X-RagKB-Signature: t=<unix秒>,v1=<hex>}，
 * {@code v1 = HMAC-SHA256(secret, "<t>." + body)}；接收方用相同 secret 重算比对，
 * 并校验 {@code t} 与当前时间差（建议阈值 5 分钟）拒绝重放。
 * secret 即订阅创建时下发的 {@code whsec_} 密钥，只存 {@code secret_ref} 列，绝不落日志。
 *
 * <p><b>重试与死信</b>：单条投递最多 {@code ragkb.webhook.max-attempts}（默认 3）次尝试，
 * 退避 {@code 30s << (attempt-1)}（30s / 60s，第 3 次失败直接 DEAD）；DEAD 后不再自动重试，
 * 经 {@link #replayWebhookDelivery} 人工重放。outbox 事件级处理失败（如登记时 DB 故障）
 * 独立退避，达到 5 次置 outbox DEAD。
 *
 * <p><b>事务边界</b>：投递含外部 HTTP 调用（超时 10s），绝不放进数据库事务；
 * 每条事件 / 每条投递独立短事务提交，崩溃后由下一轮自然续跑（at-least-once）。
 *
 * <p><b>并发防护</b>：对齐 {@code IngestionDispatchScheduler} 的模式——
 * {@code @ConditionalOnProperty(ragkb.db.enabled)} 条件装配 + fixedDelay 轮询 +
 * {@link AtomicBoolean} 防重入（@Scheduled 单线程下双保险）。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class WebhookDeliveryServiceImpl implements IntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryServiceImpl.class);

    // ---------- 状态常量（与 DDL CHECK 约束一一对应） ----------

    /** webhook_delivery.status：待投递（fan-out 登记的初始态）。 */
    private static final String DELIVERY_PENDING = "PENDING";
    /** webhook_delivery.status：投递成功（终态）。 */
    private static final String DELIVERY_SUCCEEDED = "SUCCEEDED";
    /** webhook_delivery.status：失败待重试（next_attempt_at 到期后重新入队）。 */
    private static final String DELIVERY_RETRY = "RETRY";
    /** webhook_delivery.status：死信（达到尝试上限，终态；仅人工重放可恢复）。 */
    private static final String DELIVERY_DEAD = "DEAD";

    /** HMAC 算法名（JDK 标准名，签名与验签双方约定一致）。 */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 签名头前缀：时间戳部分（接收方按 t 取值参与重算）。 */
    private static final String SIGNATURE_HEADER_PREFIX = "t=";
    /** 签名头前缀：HMAC 摘要部分（v1 版本化，未来换算法可加 v2 并存）。 */
    private static final String SIGNATURE_VALUE_PREFIX = ",v1=";

    /** 重试退避基数：第 n 次失败后等待 30s << (n-1)（第 1 次失败等 30s，第 2 次等 60s）。 */
    private static final Duration RETRY_BASE = Duration.ofSeconds(30);

    /** outbox 事件级处理失败上限（达到后置 DEAD，不再认领；区别于单条投递的 maxAttempts）。 */
    private static final int OUTBOX_MAX_ATTEMPTS = 5;

    /** outbox 事件级失败的退避时长（登记类故障多为瞬时 DB 抖动，固定 1 分钟）。 */
    private static final Duration OUTBOX_RETRY_BACKOFF = Duration.ofMinutes(1);

    // ---------- 依赖 ----------

    /** 投递队列读写（outbox 认领 / 订阅匹配 / 投递登记与回写）。 */
    private final WebhookDeliveryQueueMapper queueMapper;

    /** outbox 事件按主键读取（手动投递时校验事件存在）。 */
    private final OutboxEventMapper outboxEventMapper;

    /** HTTP 发送抽象（真实实现 WebhookHttpClient；单测注入内存桩，不真正调用外部 URL）。 */
    private final WebhookSenderPort senderPort;

    /** 任务中心登记（手动投递 / 死信重放返回 Task 供前端轮询）。 */
    private final TaskService taskService;

    /** 事件类型单元素数组的 JSON 序列化（订阅匹配入参）。 */
    private final ObjectMapper objectMapper;

    /** 单条投递的最大尝试次数（含首次；默认 3，超限即死信）。 */
    private final int maxAttempts;

    /** 防止上一轮未完成时下一轮重入（对齐 IngestionDispatchScheduler 的双保险模式）。 */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public WebhookDeliveryServiceImpl(WebhookDeliveryQueueMapper queueMapper,
                                      OutboxEventMapper outboxEventMapper,
                                      WebhookSenderPort senderPort,
                                      TaskService taskService,
                                      ObjectMapper objectMapper,
                                      @Value("${ragkb.webhook.max-attempts:3}") int maxAttempts) {
        this.queueMapper = queueMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.senderPort = senderPort;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    // =====================================================================
    // 轮询入口（@Scheduled 驱动）
    // =====================================================================

    /**
     * 投递轮询主循环：每 {@code ragkb.webhook.dispatch-interval-ms}（默认 5s）一轮，
     * 依次执行 fan-out（outbox → 投递登记）与 deliver（到期投递执行）。
     */
    @Scheduled(fixedDelayString = "${ragkb.webhook.dispatch-interval-ms:5000}")
    public void dispatchPendingWebhooks() {
        // 重入防护：上一轮仍在执行时直接跳过本轮（fixedDelay 本身不重叠，双保险）。
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            fanOutDueOutboxEvents();
            deliverDueWebhookDeliveries();
        } finally {
            busy.set(false);
        }
    }

    // ------------------------------------------------------------------
    // ① fan-out：outbox 事件 → 匹配订阅 → 幂等登记投递记录
    // ------------------------------------------------------------------

    /** 认领到期 outbox 事件并扇出到匹配订阅（登记即完成发布，投递由下一段执行）。 */
    private void fanOutDueOutboxEvents() {
        List<OutboxEvent> events = queueMapper.listDueOutboxEvents(20);
        for (OutboxEvent event : events) {
            try {
                fanOutOne(event);
                // fan-out 完成（含无订阅匹配）即视为发布完成：投递进度由 delivery 行承载。
                queueMapper.markOutboxPublished(event.getId(), Instant.now());
            } catch (Exception e) {
                // 事件级处理失败（登记时 DB 故障等）：退避重试，超限置 outbox DEAD。
                log.warn("webhook fan-out 失败 outboxId={} attempt={}",
                        event.getId(), event.getAttemptCount(), e);
                bumpOutboxAttempt(event);
            }
        }
    }

    /** 单事件扇出：按 event_type 匹配启用订阅并逐个幂等登记投递记录。 */
    private void fanOutOne(OutboxEvent event) {
        List<WebhookTargetRow> subscribers =
                queueMapper.findActiveSubscribers(event.getTenantId(), toJsonArray(event.getEventType()));
        for (WebhookTargetRow subscriber : subscribers) {
            // 幂等登记：撞 (tenant, subscription, event) 唯一约束时忽略，返回 0 表示已存在（跳过）。
            int inserted = queueMapper.insertDeliveryIgnoreConflict(
                    event.getTenantId(), subscriber.getId(), event.getId());
            if (inserted > 0) {
                log.info("webhook 投递已登记 outboxId={} subscriptionId={} tenantId={}",
                        event.getId(), subscriber.getId(), event.getTenantId());
            }
        }
        if (subscribers.isEmpty()) {
            // 无订阅匹配属正常（未配置订阅的事件直接发布完成），降为 debug 避免日志噪音。
            log.debug("webhook 事件无匹配订阅 outboxId={} eventType={}", event.getId(), event.getEventType());
        }
    }

    /** outbox 事件级失败退避：尝试次数达到上限置 DEAD（死信，不再认领），否则推迟 1 分钟重试。 */
    private void bumpOutboxAttempt(OutboxEvent event) {
        int attempt = (event.getAttemptCount() == null ? 0 : event.getAttemptCount()) + 1;
        boolean dead = attempt >= OUTBOX_MAX_ATTEMPTS;
        queueMapper.bumpOutboxAttempt(event.getId(), "FANOUT_FAILED",
                Instant.now().plus(OUTBOX_RETRY_BACKOFF), dead);
        if (dead) {
            log.error("webhook fan-out 连续失败进入死信 outboxId={} attempts={}", event.getId(), attempt);
        }
    }

    // ------------------------------------------------------------------
    // ② deliver：到期投递执行（签名 → HTTP → 结果回写）
    // ------------------------------------------------------------------

    /** 逐条执行到期的 PENDING/RETRY 投递（单条失败不影响其余投递）。 */
    private void deliverDueWebhookDeliveries() {
        List<WebhookDeliveryTargetRow> deliveries = queueMapper.findDueDeliveries(20);
        for (WebhookDeliveryTargetRow delivery : deliveries) {
            try {
                deliverOne(delivery);
            } catch (Exception e) {
                // 意外异常（DB 回写失败等）：保持当前状态，到期后下一轮自然重试（at-least-once）。
                log.warn("webhook 投递执行异常 deliveryId={}", delivery.getId(), e);
            }
        }
    }

    /** 执行单条投递：组装签名头 → POST → 按结果回写终态（SUCCEEDED / RETRY / DEAD）。 */
    private void deliverOne(WebhookDeliveryTargetRow delivery) {
        String body = delivery.getPayload();
        long timestamp = Instant.now().getEpochSecond();
        // 签名：v1 = HMAC-SHA256(secret, "<t>." + body)，secret 取订阅 secret_ref（不落日志）。
        String signature = buildSignature(delivery.getSecretRef(), timestamp, body);
        Map<String, String> headers = Map.of(
                "Content-Type", MediaType.APPLICATION_JSON_VALUE,
                "X-RagKB-Event-Id", delivery.getOutboxEventUuid() != null ? delivery.getOutboxEventUuid() : "",
                "X-RagKB-Event-Type", delivery.getEventType() != null ? delivery.getEventType() : "",
                "X-RagKB-Delivery", String.valueOf(delivery.getId()),
                "X-RagKB-Signature", signature);
        // HTTP 发送（端口抽象：真实实现带 10s 超时，失败以结果对象汇报不抛异常）。
        WebhookSenderPort.SendResult result = senderPort.send(
                new WebhookSenderPort.SendRequest(delivery.getTargetUrl(), headers, body));
        // 本次为第 attempt 次尝试（attemptCount 为执行前计数）。
        int attempt = (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1;
        Instant now = Instant.now();
        if (result.success()) {
            // 成功终态：记录 HTTP 状态与响应体摘要（SHA-256，不存原文防膨胀），清空下次重试时间。
            queueMapper.updateDeliveryOutcome(delivery.getId(), DELIVERY_SUCCEEDED,
                    result.httpStatus(), sha256(result.responseBody()), null,
                    attempt, null, now);
            log.info("webhook 投递成功 deliveryId={} subscriptionId={} httpStatus={} attempts={}",
                    delivery.getId(), delivery.getSubscriptionId(), result.httpStatus(), attempt);
            return;
        }
        if (attempt >= maxAttempts) {
            // 死信终态：达到尝试上限，不再自动重试，等待人工重放。
            queueMapper.updateDeliveryOutcome(delivery.getId(), DELIVERY_DEAD,
                    result.httpStatus(), null, result.errorCode(),
                    attempt, null, null);
            log.warn("webhook 投递进入死信 deliveryId={} subscriptionId={} attempts={} errorCode={}",
                    delivery.getId(), delivery.getSubscriptionId(), attempt, result.errorCode());
            return;
        }
        // 指数退避：第 n 次失败后等待 RETRY_BASE << (n-1)（30s / 60s / ...）。
        Instant nextAttemptAt = now.plus(RETRY_BASE.multipliedBy(1L << (attempt - 1)));
        queueMapper.updateDeliveryOutcome(delivery.getId(), DELIVERY_RETRY,
                result.httpStatus(), null, result.errorCode(),
                attempt, nextAttemptAt, null);
        log.info("webhook 投递失败待重试 deliveryId={} attempt={} next={} errorCode={}",
                delivery.getId(), attempt, nextAttemptAt, result.errorCode());
    }

    // =====================================================================
    // IntegrationUseCase 契约方法（手动投递 / 死信重放）
    // =====================================================================

    @Override
    public Task deliverWebhook(TenantId tenantId, long subscriptionId, String eventId) {
        // eventId 契约为字符串（与 WebhookDeliveryVo.eventId 一致），语义是 outbox_event 主键 id。
        long outboxId;
        try {
            outboxId = Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "eventId 必须为 outbox 事件主键数字");
        }
        // 事件必须存在且归属当前租户（deny-by-default，跨租户按不存在处理）。
        OutboxEvent event = outboxEventMapper.selectById(outboxId);
        if (event == null || !Long.valueOf(tenantId.value()).equals(event.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "outbox 事件不存在");
        }
        // 幂等登记投递记录（已存在则复用），随后同步执行一次投递（手动触发不等轮询周期）。
        queueMapper.insertDeliveryIgnoreConflict(tenantId.value(), subscriptionId, outboxId);
        // 按唯一键定位投递行（登记刚完成，行必然存在）。
        Long deliveryId = queueMapper.findDeliveryIdByUniqueKey(tenantId.value(), subscriptionId, outboxId);
        if (deliveryId == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "投递记录登记后未找到");
        }
        WebhookDeliveryTargetRow delivery = queueMapper.findDeliveryDetail(deliveryId);
        if (delivery == null) {
            // 订阅或事件行在登记后被清理（JOIN 不到富行）：按冲突拒绝并说明。
            throw new ApiException(ErrorCode.CONFLICT, "订阅或事件已不存在，无法投递");
        }
        deliverOne(delivery);
        // 同步完成后返回终态任务（对齐 cloneKb 的即时完成模式，前端无需轮询）。
        return taskService.submit("WEBHOOK_DELIVER", DELIVERY_SUCCEEDED,
                "Webhook 手动投递已执行", 100,
                "WEBHOOK_DELIVERY", String.valueOf(deliveryId),
                "投递已执行，可到投递记录查看最终状态");
    }

    @Override
    public Task replayWebhookDelivery(TenantId tenantId, long deliveryId, String idempotencyKey) {
        // 加载投递记录并校验租户归属（跨租户按不存在处理）。
        WebhookDeliveryTargetRow delivery = queueMapper.findDeliveryDetail(deliveryId);
        if (delivery == null || !Long.valueOf(tenantId.value()).equals(delivery.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "投递记录不存在");
        }
        // 重置为 PENDING 并清零尝试次数，交由下一轮投递循环执行（重放是人工决策，不再退避）。
        if (queueMapper.resetDeliveryForReplay(deliveryId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "投递记录不存在");
        }
        // idempotencyKey：项目幂等设施（idempotency_record）尚未接线到用例层，
        // 与 KbServiceImpl 的处理一致仅接受参数不实现防重（文档记录该遗留点）。
        return taskService.submit("WEBHOOK_REPLAY", DELIVERY_PENDING,
                "Webhook 死信重放已受理", 0,
                "WEBHOOK_DELIVERY", String.valueOf(deliveryId),
                "投递记录已重置为待投递，下一轮投递周期（默认 5s）内执行");
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    /**
     * 组装签名头：{@code t=<unix秒>,v1=<HMAC-SHA256(secret, "<t>." + body) 的 hex>}。
     * 包级私有 + 静态：签名是纯函数，供单测直接断言（不依赖 Spring 上下文）。
     */
    static String buildSignature(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            // 签名原文 = 时间戳 + "." + 请求体（时间戳参与运算，接收方可校验新鲜度防重放）。
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_HEADER_PREFIX + timestamp + SIGNATURE_VALUE_PREFIX
                    + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // HmacSHA256 为 JDK 必备算法，init 异常属环境故障，直接失败（fail-closed）。
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    /** 响应体 SHA-256 摘要（hex，64 位；成功投递的对账证据，不存原文防行膨胀）。 */
    private static String sha256(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /** 事件类型序列化为单元素 JSON 数组（订阅匹配 @> 的右操作数）。 */
    private String toJsonArray(String eventType) {
        try {
            return objectMapper.writeValueAsString(List.of(eventType));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "事件类型序列化失败");
        }
    }
}

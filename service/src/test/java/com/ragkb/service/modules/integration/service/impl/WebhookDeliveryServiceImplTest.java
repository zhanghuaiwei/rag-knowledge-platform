package com.ragkb.service.modules.integration.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.integration.persistence.entity.OutboxEvent;
import com.ragkb.service.modules.integration.persistence.mapper.OutboxEventMapper;
import com.ragkb.service.modules.integration.persistence.mapper.WebhookDeliveryQueueMapper;
import com.ragkb.service.modules.integration.persistence.query.WebhookDeliveryTargetRow;
import com.ragkb.service.modules.integration.persistence.query.WebhookTargetRow;
import com.ragkb.service.modules.integration.port.WebhookSenderPort;
import com.ragkb.service.modules.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WebhookDeliveryServiceImpl} 单测 —— 不真正调用外部 URL：
 * HTTP 发送经 {@link WebhookSenderPort} mock 注入（红线要求），
 * 覆盖签名计算 / fan-out 扇出 / 成功回写 / 指数退避重试 / 死信终态 / 手动投递 / 死信重放。
 */
@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceImplTest {

    /** 测试用签名密钥（非真实凭证，仅断言 HMAC 计算的正确性）。 */
    private static final String SECRET = "whsec_test_secret_0123456789abcdef";

    /** 测试用事件载荷（outbox payload 原文）。 */
    private static final String BODY = "{\"documentId\":42,\"versionId\":100}";

    /** 测试租户。 */
    private static final TenantId TENANT = new TenantId(1L);

    @Mock private WebhookDeliveryQueueMapper queueMapper;
    @Mock private OutboxEventMapper outboxEventMapper;
    @Mock private WebhookSenderPort senderPort;
    @Mock private TaskService taskService;

    /** 被测对象（maxAttempts=3：3 次失败进死信）。 */
    private WebhookDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        // 直接构造被测对象：依赖全部 mock / 真实 ObjectMapper，不启动 Spring 上下文。
        service = new WebhookDeliveryServiceImpl(
                queueMapper, outboxEventMapper, senderPort, taskService, new ObjectMapper(), 3);
    }

    // ---------- 签名计算（接收方视角重算比对） ----------

    @Test
    void buildSignatureMatchesIndependentHmacRecomputation() throws Exception {
        long timestamp = 1700000000L;
        // 被测方法产出签名头。
        String header = WebhookDeliveryServiceImpl.buildSignature(SECRET, timestamp, BODY);
        // 断言格式：t=<unix秒>,v1=<hex(64)>。
        assertTrue(header.startsWith("t=" + timestamp + ",v1="), "签名头应携带时间戳前缀: " + header);
        String v1 = header.substring(header.indexOf(",v1=") + 4);
        assertEquals(64, v1.length(), "v1 应为 SHA-256 的 64 位 hex");
        // 接收方视角独立重算：HMAC-SHA256(secret, "<t>." + body)。
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + BODY).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, v1, "签名应与独立重算的 HMAC 一致（时间戳参与运算防重放）");
    }

    @Test
    void buildSignatureChangesWithBodyOrTimestamp() {
        // 同密钥不同载荷/时间戳 → 签名不同（防篡改：body 改动必须导致签名失效）。
        String base = WebhookDeliveryServiceImpl.buildSignature(SECRET, 1700000000L, BODY);
        String otherBody = WebhookDeliveryServiceImpl.buildSignature(SECRET, 1700000000L, BODY + " ");
        String otherTs = WebhookDeliveryServiceImpl.buildSignature(SECRET, 1700000001L, BODY);
        assertTrue(!base.equals(otherBody) && !base.equals(otherTs));
    }

    // ---------- fan-out：outbox → 匹配订阅 → 幂等登记 ----------

    @Test
    void fanOutRegistersDeliveryForEachActiveSubscriberAndMarksPublished() {
        // 事件：租户 1 的 document.uploaded。
        OutboxEvent event = outboxEvent(7L, 1L, "document.uploaded");
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of(event));
        // 两个启用订阅（返回顺序即登记顺序）。
        WebhookTargetRow first = subscriber(11L);
        WebhookTargetRow second = subscriber(12L);
        when(queueMapper.findActiveSubscribers(eq(1L), eq("[\"document.uploaded\"]")))
                .thenReturn(List.of(first, second));

        service.dispatchPendingWebhooks();

        // 断言：两个订阅各登记一条投递记录（tenant + subscription + outbox id）。
        verify(queueMapper).insertDeliveryIgnoreConflict(1L, 11L, 7L);
        verify(queueMapper).insertDeliveryIgnoreConflict(1L, 12L, 7L);
        // fan-out 完成（无论订阅数）即置 outbox PUBLISHED。
        verify(queueMapper).markOutboxPublished(eq(7L), any(Instant.class));
    }

    @Test
    void fanOutWithNoSubscriberStillMarksPublished() {
        // 无订阅匹配属正常路径：事件直接发布完成，不视为失败。
        OutboxEvent event = outboxEvent(8L, 1L, "kb.archived");
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of(event));
        when(queueMapper.findActiveSubscribers(eq(1L), anyString())).thenReturn(List.of());

        service.dispatchPendingWebhooks();

        verify(queueMapper, never()).insertDeliveryIgnoreConflict(anyLong(), anyLong(), anyLong());
        verify(queueMapper).markOutboxPublished(eq(8L), any(Instant.class));
    }

    @Test
    void fanOutFailureBumpsOutboxAttemptWithoutDeadBelowThreshold() {
        // 登记时 DB 故障：退避重试（不置死信），available_at 推迟 1 分钟。
        OutboxEvent event = outboxEvent(9L, 1L, "document.uploaded");
        event.setAttemptCount(0);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of(event));
        when(queueMapper.findActiveSubscribers(anyLong(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        service.dispatchPendingWebhooks();

        // attempt=1 < 5：dead=false，事件保持可重试。
        verify(queueMapper).bumpOutboxAttempt(eq(9L), eq("FANOUT_FAILED"), any(Instant.class), eq(false));
        verify(queueMapper, never()).markOutboxPublished(anyLong(), any(Instant.class));
    }

    @Test
    void fanOutFailureReachesOutboxDeadLetterAfterFiveAttempts() {
        // 事件级失败达到 5 次上限：置 DEAD，不再认领。
        OutboxEvent event = outboxEvent(10L, 1L, "document.uploaded");
        event.setAttemptCount(4);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of(event));
        when(queueMapper.findActiveSubscribers(anyLong(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        service.dispatchPendingWebhooks();

        // attempt=5 >= 5：dead=true（outbox 死信）。
        verify(queueMapper).bumpOutboxAttempt(eq(10L), eq("FANOUT_FAILED"), any(Instant.class), eq(true));
    }

    // ---------- deliver：成功 / 指数退避重试 / 死信 ----------

    @Test
    void successfulDeliveryWritesSucceededWithResponseDigest() throws Exception {
        // 到期投递行：attempt_count=0（首次投递）。
        WebhookDeliveryTargetRow row = delivery(101L, 0);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of());
        when(queueMapper.findDueDeliveries(20)).thenReturn(List.of(row));
        when(senderPort.send(any())).thenReturn(new WebhookSenderPort.SendResult(
                true, 200, "ok", null));

        service.dispatchPendingWebhooks();

        // 断言：签名头进入发送请求（防篡改契约的核心证据）。
        ArgumentCaptor<WebhookSenderPort.SendRequest> requestCaptor =
                ArgumentCaptor.forClass(WebhookSenderPort.SendRequest.class);
        verify(senderPort).send(requestCaptor.capture());
        Map<String, String> headers = requestCaptor.getValue().headers();
        assertEquals(BODY, requestCaptor.getValue().body(), "POST body 应为 outbox payload 原文");
        assertTrue(headers.get("X-RagKB-Signature").startsWith("t="), "应携带签名头");
        assertEquals("3f8a2c4e-0000-0000-0000-000000000007", headers.get("X-RagKB-Event-Id"));
        assertEquals("document.uploaded", headers.get("X-RagKB-Event-Type"));
        // 断言：成功终态 + attempt=1 + 响应体 SHA-256 摘要（对账证据）。
        String expectedSha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("ok".getBytes(StandardCharsets.UTF_8)));
        verify(queueMapper).updateDeliveryOutcome(eq(101L), eq("SUCCEEDED"), eq(200), eq(expectedSha),
                isNull(), eq(1), isNull(), any(Instant.class));
    }

    @Test
    void failedFirstAttemptWritesRetryWithExponentialBackoff() {
        // 首次失败（attempt 0→1）：置 RETRY，下次投递时间 ≈ now + 30s。
        WebhookDeliveryTargetRow row = delivery(102L, 0);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of());
        when(queueMapper.findDueDeliveries(20)).thenReturn(List.of(row));
        when(senderPort.send(any())).thenReturn(new WebhookSenderPort.SendResult(
                false, 500, "err", "NON_2XX"));
        Instant before = Instant.now();

        service.dispatchPendingWebhooks();

        ArgumentCaptor<Instant> nextCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(queueMapper).updateDeliveryOutcome(eq(102L), eq("RETRY"), eq(500), isNull(),
                eq("NON_2XX"), eq(1), nextCaptor.capture(), isNull());
        // 退避窗口断言：[before+30s, now+40s]（留 10s 余量防时钟边界抖动）。
        assertTrue(nextCaptor.getValue().isAfter(before.plusSeconds(29)), "退避应 >= 30s");
        assertTrue(nextCaptor.getValue().isBefore(Instant.now().plusSeconds(40)), "退避应在 40s 内");
    }

    @Test
    void failedSecondAttemptStillRetriesWithDoubledBackoff() {
        // 第二次失败（attempt 1→2 < maxAttempts=3）：仍 RETRY，退避翻倍为 60s。
        WebhookDeliveryTargetRow row = delivery(103L, 1);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of());
        when(queueMapper.findDueDeliveries(20)).thenReturn(List.of(row));
        when(senderPort.send(any())).thenReturn(new WebhookSenderPort.SendResult(
                false, null, null, "TIMEOUT"));
        Instant before = Instant.now();

        service.dispatchPendingWebhooks();

        ArgumentCaptor<Instant> nextCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(queueMapper).updateDeliveryOutcome(eq(103L), eq("RETRY"), isNull(), isNull(),
                eq("TIMEOUT"), eq(2), nextCaptor.capture(), isNull());
        // 指数退避：第 2 次失败等待 30s << 1 = 60s。
        assertTrue(nextCaptor.getValue().isAfter(before.plusSeconds(59)), "第二次退避应 >= 60s");
    }

    @Test
    void failedThirdAttemptMarksDeadLetter() {
        // 第三次失败（attempt 2→3 >= maxAttempts=3）：置 DEAD 终态，不再安排重试。
        WebhookDeliveryTargetRow row = delivery(104L, 2);
        when(queueMapper.listDueOutboxEvents(20)).thenReturn(List.of());
        when(queueMapper.findDueDeliveries(20)).thenReturn(List.of(row));
        when(senderPort.send(any())).thenReturn(new WebhookSenderPort.SendResult(
                false, null, null, "NETWORK_ERROR"));

        service.dispatchPendingWebhooks();

        verify(queueMapper).updateDeliveryOutcome(104L, "DEAD", null, null,
                "NETWORK_ERROR", 3, null, null);
    }

    // ---------- IntegrationUseCase：手动投递 / 死信重放 ----------

    @Test
    void deliverWebhookExecutesImmediatelyAndReturnsCompletedTask() {
        // 手动投递：outbox 事件存在且租户匹配 → 登记后同步执行一次成功投递。
        OutboxEvent event = outboxEvent(7L, 1L, "document.uploaded");
        when(outboxEventMapper.selectById(7L)).thenReturn(event);
        when(queueMapper.insertDeliveryIgnoreConflict(1L, 11L, 7L)).thenReturn(1);
        when(queueMapper.findDeliveryIdByUniqueKey(1L, 11L, 7L)).thenReturn(101L);
        when(queueMapper.findDeliveryDetail(101L)).thenReturn(delivery(101L, 0));
        when(senderPort.send(any())).thenReturn(new WebhookSenderPort.SendResult(
                true, 200, "ok", null));
        when(taskService.submit(anyString(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), anyString()))
                .thenReturn(Task.of("t1", "WEBHOOK_DELIVER", "SUCCEEDED", "手动投递", 100));

        Task task = service.deliverWebhook(TENANT, 11L, "7");

        assertNotNull(task);
        verify(senderPort).send(any());
        verify(queueMapper).updateDeliveryOutcome(eq(101L), eq("SUCCEEDED"), eq(200),
                any(), isNull(), eq(1), isNull(), any(Instant.class));
    }

    @Test
    void deliverWebhookRejectsCrossTenantEvent() {
        // 跨租户事件按不存在处理（deny-by-default，不泄露存在性）。
        when(outboxEventMapper.selectById(7L)).thenReturn(outboxEvent(7L, 2L, "document.uploaded"));
        ApiException error = assertThrows(ApiException.class,
                () -> service.deliverWebhook(TENANT, 11L, "7"));
        assertEquals("E-1003", error.getErrorCode().getCode(), "跨租户应返回 NOT_FOUND");
    }

    @Test
    void deliverWebhookRejectsNonNumericEventId() {
        // eventId 契约为 outbox 主键数字串，非数字按参数错误拒绝。
        ApiException error = assertThrows(ApiException.class,
                () -> service.deliverWebhook(TENANT, 11L, "not-a-number"));
        assertEquals("E-1000", error.getErrorCode().getCode());
        verify(senderPort, never()).send(any());
    }

    @Test
    void replayWebhookResetsDeliveryAndReturnsQueuedTask() {
        // 死信重放：重置 PENDING + 清零尝试次数，返回排队任务。
        when(queueMapper.findDeliveryDetail(104L)).thenReturn(delivery(104L, 3));
        when(queueMapper.resetDeliveryForReplay(104L)).thenReturn(1);
        when(taskService.submit(anyString(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), anyString()))
                .thenReturn(Task.of("t2", "WEBHOOK_REPLAY", "PENDING", "死信重放", 0));

        Task task = service.replayWebhookDelivery(TENANT, 104L, "idem-key");

        assertNotNull(task);
        verify(queueMapper).resetDeliveryForReplay(104L);
    }

    @Test
    void replayWebhookRejectsUnknownDelivery() {
        // 记录不存在（含跨租户）：按 404 拒绝。
        when(queueMapper.findDeliveryDetail(999L)).thenReturn(null);
        ApiException error = assertThrows(ApiException.class,
                () -> service.replayWebhookDelivery(TENANT, 999L, null));
        assertEquals("E-1003", error.getErrorCode().getCode());
    }

    // ---------- 测试数据构造 ----------

    /** 构造一条 outbox 事件（ingestion 域 document.uploaded）。 */
    private static OutboxEvent outboxEvent(long id, long tenantId, String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setTopic("ingestion");
        event.setPayload(BODY);
        event.setAttemptCount(0);
        return event;
    }

    /** 构造一个启用中的订阅目标行。 */
    private static WebhookTargetRow subscriber(long id) {
        WebhookTargetRow row = new WebhookTargetRow();
        row.setId(id);
        row.setTenantId(1L);
        row.setTargetUrl("https://example.com/hook");
        row.setSecretRef(SECRET);
        return row;
    }

    /** 构造一条到期投递富行（JOIN 订阅与 outbox 后的完整投递上下文）。 */
    private static WebhookDeliveryTargetRow delivery(long id, int attemptCount) {
        WebhookDeliveryTargetRow row = new WebhookDeliveryTargetRow();
        row.setId(id);
        row.setTenantId(1L);
        row.setSubscriptionId(11L);
        row.setEventId(7L);
        row.setStatus(attemptCount == 0 ? "PENDING" : "RETRY");
        row.setAttemptCount(attemptCount);
        row.setTargetUrl("https://example.com/hook");
        row.setSecretRef(SECRET);
        row.setOutboxEventUuid("3f8a2c4e-0000-0000-0000-000000000007");
        row.setEventType("document.uploaded");
        row.setPayload(BODY);
        return row;
    }
}

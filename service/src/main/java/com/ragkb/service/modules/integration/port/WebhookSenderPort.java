package com.ragkb.service.modules.integration.port;

import java.util.Map;

/**
 * Webhook HTTP 发送端口：投递引擎只依赖本抽象，真实 HTTP 客户端实现见
 * {@code integration.adapter.WebhookHttpClient}；单测注入内存桩即可覆盖
 * 签名 / 重试 / 死信逻辑，不真正调用外部 URL。
 *
 * <p>边界约定：签名头由投递引擎组装后经 {@code headers} 传入（签名是可测的业务规则），
 * 本端口只负责「把给定请求 POST 到目标地址并汇报结果」。
 */
public interface WebhookSenderPort {

    /**
     * 一次投递请求（值全部由投递引擎准备完毕）。
     *
     * @param targetUrl 回调地址（https，创建订阅时已校验）
     * @param headers   签名与事件元数据头（X-RagKB-* 系列）
     * @param body      事件载荷 JSON 字符串（outbox_event.payload 原文）
     */
    record SendRequest(String targetUrl, Map<String, String> headers, String body) {
    }

    /**
     * 一次投递结果。
     *
     * @param success      是否成功（HTTP 2xx）
     * @param httpStatus   HTTP 状态码（网络层失败时为 null）
     * @param responseBody 响应体文本（截断后存摘要用；可能为 null）
     * @param errorCode    失败错误码（NON_2XX / TIMEOUT / NETWORK_ERROR；成功为 null）
     */
    record SendResult(boolean success, Integer httpStatus, String responseBody, String errorCode) {
    }

    /**
     * 执行一次 POST 投递（实现须自带超时，不抛出异常——所有失败以
     * {@link SendResult#success()}{@code = false} 汇报，供重试状态机消费）。
     */
    SendResult send(SendRequest request);
}

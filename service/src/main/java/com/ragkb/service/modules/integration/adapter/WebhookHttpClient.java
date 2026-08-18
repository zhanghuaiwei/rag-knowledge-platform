package com.ragkb.service.modules.integration.adapter;

import com.ragkb.service.modules.integration.port.WebhookSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * {@link WebhookSenderPort} 的 RestClient 实现：把签名完毕的请求 POST 到订阅方回调地址。
 *
 * <p>超时：连接与读取均为 {@code ragkb.webhook.timeout-ms}（默认 10s，
 * 回调方响应慢不能拖垮投递线程）。
 *
 * <p>异常语义（本实现不向上抛异常，全部转为失败结果）：
 * <ul>
 *   <li>非 2xx 响应 → {@code NON_2xx}（记录状态码供排障）；</li>
 *   <li>读写超时 → {@code TIMEOUT}；</li>
 *   <li>DNS/连接等其他 IO 故障 → {@code NETWORK_ERROR}。</li>
 * </ul>
 *
 * <p>日志红线：绝不记录请求头（含签名）与 secret，只记目标 host 与结果状态。
 */
@Component
public class WebhookHttpClient implements WebhookSenderPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpClient.class);

    /** 失败错误码：回调方返回了非 2xx 状态（重试可能恢复，如 5xx）。 */
    private static final String ERROR_NON_2XX = "NON_2XX";
    /** 失败错误码：连接或读取超时（回调方无响应）。 */
    private static final String ERROR_TIMEOUT = "TIMEOUT";
    /** 失败错误码：DNS 解析失败 / 连接拒绝等网络层故障。 */
    private static final String ERROR_NETWORK = "NETWORK_ERROR";

    /** 发送用 RestClient（独立实例：webhook 目标地址逐次指定，不设 baseUrl）。 */
    private final RestClient restClient;

    /** 读取超时（毫秒），与连接超时一致（默认 10s）。 */
    private final long timeoutMs;

    public WebhookHttpClient(@Value("${ragkb.webhook.timeout-ms:10000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
        // SimpleClientHttpRequestFactory 支持 connect/read 双超时（JDK HttpURLConnection）。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public SendResult send(SendRequest request) {
        try {
            // POST 事件载荷到订阅方回调地址（headers 已含签名与事件元数据，由引擎组装）。
            String response = restClient.post()
                    .uri(request.targetUrl())
                    .headers(headers -> request.headers().forEach(headers::set))
                    .body(request.body())
                    .retrieve()
                    .body(String.class);
            // retrieve() 对 2xx 返回 body；走到这里即成功。
            return new SendResult(true, 200, response, null);
        } catch (RestClientResponseException e) {
            // 非 2xx：回调方拒绝（4xx）或自身故障（5xx），状态码入库供排障。
            HttpStatusCode status = e.getStatusCode();
            return new SendResult(false, status.value(), truncate(e.getResponseBodyAsString()), ERROR_NON_2XX);
        } catch (Exception e) {
            // 网络层异常：按根因归类错误码（超时与一般网络故障区分，排障与统计需要）。
            String errorCode = hasTimeoutCause(e) ? ERROR_TIMEOUT : ERROR_NETWORK;
            // 日志只记目标 host 与错误码，不落请求头/载荷/secret。
            log.warn("webhook 投递网络失败 target={} code={}", hostOf(request.targetUrl()), errorCode);
            return new SendResult(false, null, null, errorCode);
        }
    }

    /** 判断异常链中是否含超时根因（SocketTimeoutException：连接或读取超时）。 */
    private static boolean hasTimeoutCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 从 URL 提取 host（日志脱敏：不暴露完整 path 与 query）。 */
    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return "invalid-url";
        }
    }

    /** 响应体截断（超长响应不入库摘要，防拖垮 webhook_delivery 行）。 */
    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > 512 ? body.substring(0, 512) : body;
    }
}

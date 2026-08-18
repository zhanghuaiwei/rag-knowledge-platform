package com.ragkb.service.modules.connector.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.connector.persistence.entity.SourceConnection;
import com.ragkb.service.modules.connector.persistence.mapper.SourceConnectionMapper;
import com.ragkb.service.modules.connector.port.ContentConnectorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code http_index} 适配器：GET 一个 JSON 索引 URL 形成内容清单
 * （企业内网已有「文件清单服务」场景的最小接入）。
 *
 * <p>索引格式兼容两种：顶层数组，或 {@code {"items":[...]}} / {@code {"objects":[...]}}
 * / {@code {"data":[...]}} 包装对象。元素字段兼容多命名
 * （id/externalId、uri/sourceUri/url、version/sourceVersion/etag、sha256/contentSha256、
 * deleted/tombstoned）。
 *
 * <p>SSRF 最小防护（06-架构方案 §2.3）：仅允许 http/https；默认拒绝环回/链路本地/
 * 私网目标，需要访问内网索引服务时在 config 显式声明 {@code allowPrivateNetwork=true}
 * （fail-closed：宁拒勿放过）。生产级防护（DNS rebinding、重定向审计）留待迭代。
 */
@Component
// 依赖 SourceConnectionMapper（仅 ragkb.db.enabled=true 时注册），与 Service 按同一开关装配。
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class HttpIndexConnector extends AbstractContentConnector {

    private static final Logger log = LoggerFactory.getLogger(HttpIndexConnector.class);

    /** 私网/环回地址判定（IPv4 十进制点分写法 + 常见本地主机名）。 */
    private static final Pattern PRIVATE_HOST = Pattern.compile(
            "^(localhost|127\\.[0-9.]+|10\\.[0-9.]+|192\\.168\\.[0-9.]+|172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9.]+)$");

    /** 单轮发现的对象数上限（防超大索引拖垮同步线程）。 */
    private static final int MAX_OBJECTS = 1000;

    /** 拉取索引用 RestClient（独立实例：目标地址逐次指定，不设 baseUrl）。 */
    private final RestClient restClient;

    /** 索引 JSON 解析器（复用全局 ObjectMapper）。 */
    private final ObjectMapper objectMapper;

    public HttpIndexConnector(ObjectMapper objectMapper,
                              SourceConnectionMapper sourceConnectionMapper) {
        // providerKey 固定为 http_index（provider_key 路由键）
        super(objectMapper, sourceConnectionMapper, "http_index");
        this.objectMapper = objectMapper;
        // 连接与读取双超时（索引服务慢响应不得拖垮同步调度线程）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void validate(Map<String, Object> config) {
        // ① URL 合法性 + SSRF 防护（未放行私网时拒绝内网目标）
        String indexUrl = requireIndexUrl(config);
        // ② 连通性：GET 一次索引，非 2xx / 网络故障 / 非 JSON 清单均判不可达（fail-closed）
        fetchIndex(indexUrl);
    }

    @Override
    public List<SourceObject> discover(TenantId tenantId, long connectionId, String cursor) {
        // 加载连接配置并拉取索引清单（cursor 对快照式索引无意义，忽略）
        SourceConnection connection = requireConnection(connectionId);
        String indexUrl = requireIndexUrl(parseConfig(connection));
        return parseIndex(fetchIndex(indexUrl));
    }

    @Override
    public void health(TenantId tenantId, long connectionId) {
        // 存活探测 = 索引 URL 当前可达且返回合法清单（与 validate 同一判定）
        SourceConnection connection = requireConnection(connectionId);
        requireIndexUrl(parseConfig(connection));
    }

    /** 从 config 提取并校验 indexUrl（必填 + scheme/host/私网防护）。 */
    private static String requireIndexUrl(Map<String, Object> config) {
        Object indexUrl = config == null ? null : config.get("indexUrl");
        if (!(indexUrl instanceof String text) || text.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "连接器「http_index」缺少必填配置字段: indexUrl");
        }
        URI uri = URI.create(text.trim());
        String scheme = uri.getScheme();
        // scheme 白名单：仅 http/https（file/ftp 等一律拒绝）
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "indexUrl 仅支持 http/https 协议");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "indexUrl 缺少合法主机名");
        }
        // 私网防护：默认拒绝内网/环回目标；显式 allowPrivateNetwork=true 才放行（内网索引场景）
        boolean allowPrivate = Boolean.TRUE.equals(config.get("allowPrivateNetwork"));
        if (!allowPrivate && PRIVATE_HOST.matcher(uri.getHost().toLowerCase()).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "indexUrl 指向私网/本机地址，默认拒绝；内网索引服务请在配置中显式声明 allowPrivateNetwork");
        }
        return text.trim();
    }

    /** GET 索引 URL 并返回原始 JSON 文本；非 2xx 或网络故障抛业务异常（只记 host，不落完整 URL）。 */
    private String fetchIndex(String indexUrl) {
        try {
            return restClient.get().uri(indexUrl).retrieve().body(String.class);
        } catch (Exception e) {
            // 日志脱敏：只记目标 host 与异常类型，不落 query/凭证参数
            log.warn("http_index 索引拉取失败 host={} error={}", URI.create(indexUrl).getHost(),
                    e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.BAD_REQUEST, "索引服务不可达或响应异常");
        }
    }

    /** 索引 JSON → 清单对象列表（兼容顶层数组与 items/objects/data 包装）。 */
    private List<SourceObject> parseIndex(String indexJson) {
        List<?> elements;
        try {
            Object parsed = objectMapper.readValue(indexJson, Object.class);
            if (parsed instanceof List<?> list) {
                // 形态一：顶层数组
                elements = list;
            } else if (parsed instanceof Map<?, ?> map) {
                // 形态二：包装对象（items/objects/data 任一键）
                Object wrapped = map.get("items") != null ? map.get("items")
                        : map.get("objects") != null ? map.get("objects")
                        : map.get("data");
                if (!(wrapped instanceof List<?> list)) {
                    throw new IllegalArgumentException("missing list field");
                }
                elements = list;
            } else {
                throw new IllegalArgumentException("unsupported json shape");
            }
        } catch (Exception e) {
            // 非法清单：明确报错（fail-closed，不把空清单当成功）
            throw new ApiException(ErrorCode.BAD_REQUEST, "索引响应不是合法的 JSON 清单");
        }
        List<SourceObject> objects = new ArrayList<>();
        int skipped = 0;
        for (Object element : elements) {
            if (!(element instanceof Map<?, ?> item)) {
                // 非对象元素跳过并计数（告警不中断整轮）
                skipped++;
                continue;
            }
            SourceObject converted = toItem(item);
            if (converted == null) {
                // 缺外部 id 的条目跳过并计数（无稳定身份无法 upsert）
                skipped++;
                continue;
            }
            if (objects.size() >= MAX_OBJECTS) {
                // 超上限截断（剩余条目保持原 last_seen，下轮可见）
                log.warn("http_index 清单条目超过单轮上限，本轮截断 max={}", MAX_OBJECTS);
                break;
            }
            objects.add(converted);
        }
        if (skipped > 0) {
            // 脏条目只告警（数量级提示），合法条目正常同步
            log.warn("http_index 清单存在无法识别的条目 skipped={}", skipped);
        }
        return objects;
    }

    /** 单条索引元素 → 清单对象（字段多命名兼容；缺 id 返回 null 由调用方跳过）。 */
    private static SourceObject toItem(Map<?, ?> item) {
        Object externalId = firstNonNull(item, "id", "externalId");
        if (!(externalId instanceof String idText) || idText.isBlank()) {
            return null;
        }
        // 源地址与版本：多命名兼容（uri/sourceUri/url、version/sourceVersion/etag）
        String sourceUri = textOrNull(firstNonNull(item, "uri", "sourceUri", "url"));
        String sourceVersion = textOrNull(firstNonNull(item, "version", "sourceVersion", "etag"));
        // 内容摘要：仅接受 64 位十六进制（对齐 ck_source_object_sha），其余置空不强写
        String sha256 = textOrNull(firstNonNull(item, "sha256", "contentSha256"));
        if (sha256 != null && !sha256.matches("^[0-9a-fA-F]{64}$")) {
            sha256 = null;
        }
        // 删除墓碑：索引方可声明对象已在源侧删除
        boolean tombstoned = Boolean.TRUE.equals(firstNonNull(item, "deleted", "tombstoned"));
        return new SourceObject(idText, sourceUri, sourceVersion, sha256, tombstoned);
    }

    /** 依序取第一个非 null 值（字段多命名兼容用）。 */
    private static Object firstNonNull(Map<?, ?> item, String... keys) {
        for (String key : keys) {
            if (item.get(key) != null) {
                return item.get(key);
            }
        }
        return null;
    }

    /** 值转字符串（仅接受字符串类型，数字等不隐式转换以免污染外部 id 语义）。 */
    private static String textOrNull(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}

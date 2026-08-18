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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code local_directory} 适配器：扫描本地目录形成内容清单（开发/内网文件共享的最小闭环）。
 *
 * <p>发现语义：常规文件 → {@code SourceObject}；{@code external_id} 为相对根目录的
 * 路径（正斜杠规范化，跨平台稳定）；{@code source_version} 取「大小+修改时间」摘要
 * （本轮只建清单不读内容，内容摄取留 source_object → document 转换实现）。
 * 游标不适用（快照式全量清单），增量对账由同步执行器按 tombstone/last_seen 收敛。
 *
 * <p>⚠️ 安全边界：rootPath 由租户自选，生产建议以白名单根目录约束（当前为最小实现）。
 */
@Component
// 依赖 SourceConnectionMapper（仅 ragkb.db.enabled=true 时注册），与 Service 按同一开关装配。
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class LocalDirectoryConnector extends AbstractContentConnector {

    private static final Logger log = LoggerFactory.getLogger(LocalDirectoryConnector.class);

    /** 目录扫描深度上限（防符号链接环与超深目录拖垮同步线程）。 */
    private static final int MAX_DEPTH = 4;

    /** 单轮发现的对象数上限（超大目录截断，剩余对象下轮同步继续可见）。 */
    private static final int MAX_OBJECTS = 500;

    public LocalDirectoryConnector(ObjectMapper objectMapper,
                                   SourceConnectionMapper sourceConnectionMapper) {
        // providerKey 固定为 local_directory（provider_key 路由键）
        super(objectMapper, sourceConnectionMapper, "local_directory");
    }

    @Override
    public void validate(Map<String, Object> config) {
        // 必填字段校验（rootPath）+ 目标必须是已存在的目录（连通性语义）
        Path root = requireRootPath(config);
        if (!Files.isDirectory(root)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "本地目录不存在或不是目录");
        }
    }

    @Override
    public List<SourceObject> discover(TenantId tenantId, long connectionId, String cursor) {
        // 加载连接配置并定位扫描根目录（cursor 对快照式源无意义，忽略）
        SourceConnection connection = requireConnection(connectionId);
        Path root = requireRootPath(parseConfig(connection));
        List<SourceObject> objects = new ArrayList<>();
        // walk 惰性流：深度与数量双重上限，异常（权限/消失）转业务错误由同步执行器置 FAILED
        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            // takeWhile：达到数量上限即短路停止遍历（截断告警只打一次，不逐文件刷屏）
            paths.filter(Files::isRegularFile)
                    .takeWhile(path -> objects.size() < MAX_OBJECTS)
                    .forEach(path -> objects.add(toSourceObject(root, path)));
            if (objects.size() >= MAX_OBJECTS) {
                // 超上限截断：剩余对象保持原 last_seen（下轮同步继续可见）
                log.warn("本地目录对象数达到单轮上限，本轮截断 root={} max={}", root, MAX_OBJECTS);
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "扫描本地目录失败: " + e.getMessage());
        }
        return objects;
    }

    @Override
    public void health(TenantId tenantId, long connectionId) {
        // 存活探测 = 配置目录当前可访问（与 validate 同一判定）
        SourceConnection connection = requireConnection(connectionId);
        requireRootPath(parseConfig(connection));
    }

    /** 从 config 提取并规范化 rootPath（必填，缺失抛业务异常）。 */
    private static Path requireRootPath(Map<String, Object> config) {
        Object rootPath = config == null ? null : config.get("rootPath");
        if (rootPath instanceof String text && !text.isBlank()) {
            return Path.of(text.trim()).toAbsolutePath().normalize();
        }
        throw new ApiException(ErrorCode.BAD_REQUEST, "连接器「local_directory」缺少必填配置字段: rootPath");
    }

    /** 单个文件 → 清单对象（externalId=相对路径；sourceVersion=大小+修改时间摘要）。 */
    private static SourceObject toSourceObject(Path root, Path file) {
        try {
            // 相对路径统一正斜杠：Windows 下也得到稳定的外部 id（uq 键）
            String externalId = root.relativize(file).toString().replace('\\', '/');
            long size = Files.size(file);
            long modifiedAt = Files.getLastModifiedTime(file).toInstant().getEpochSecond();
            // 版本摘要只含元数据（不读文件内容），变更即触发 source_object 更新
            String sourceVersion = "size=" + size + ";mtime=" + modifiedAt;
            return new SourceObject(externalId, file.toUri().toString(), sourceVersion, null, false);
        } catch (IOException e) {
            // 单文件元数据读取失败：抛出由同步执行器计为失败对象（不中断整轮）
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "读取文件元数据失败: " + file);
        }
    }
}

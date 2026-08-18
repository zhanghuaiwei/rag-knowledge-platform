package com.ragkb.service.modules.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.connector.persistence.entity.SourceConnection;
import com.ragkb.service.modules.connector.persistence.entity.SourceObject;
import com.ragkb.service.modules.connector.persistence.entity.SyncJob;
import com.ragkb.service.modules.connector.persistence.mapper.SourceConnectionMapper;
import com.ragkb.service.modules.connector.persistence.mapper.SourceObjectMapper;
import com.ragkb.service.modules.connector.persistence.mapper.SyncJobMapper;
import com.ragkb.service.modules.connector.port.ContentConnectorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 同步任务执行器：轮询 {@code sync_job} 队列（QUEUED），调用对应
 * {@link ContentConnectorPort} 适配器发现外部对象清单，并按
 * {@code (tenant_id, connection_id, external_id)} 增量 upsert 到 {@code source_object}，
 * 完成后把 discovered/created/updated/deleted/failed 计数写回任务行。
 *
 * <p>驱动模式对齐 {@code IngestionDispatchScheduler}：轮询 + {@code AtomicBoolean}
 * 防重入 + 条件更新做状态机推进（QUEUED→RUNNING→SUCCEEDED/PARTIAL/FAILED）；
 * RUNNING 中被取消的任务最终写回前再校验状态，不覆盖 CANCELLED。
 *
 * <p>⚠️ fail-closed 边界（本轮不实现，留待人工迭代）：
 * <ul>
 *   <li>source_object → document 的转换（自动摄取入库）未实现：document 模块未提供
 *       「按 source_object 建档」端口，本轮只维护源侧清单（tombstone/版本/摘要），
 *       不假报入库进度；</li>
 *   <li>未覆盖的 provider_key（sharepoint/confluence/s3）在此兜底置 FAILED
 *       （ADAPTER_NOT_IMPLEMENTED）——Service 入口已拒绝，双保险防配置漂移；</li>
 *   <li>失败对象不自动重试（attempt 语义留待 RECONCILE 任务驱动）；skipped 计数
 *       无对应列，仅汇总日志（表结构限制，见模块文档说明）。</li>
 * </ul>
 */
@Component
// 依赖三个 Mapper（仅 ragkb.db.enabled=true 时注册），与适配器/Service 按同一开关装配。
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class SyncJobDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncJobDispatchScheduler.class);

    /** 任务终态：全部对象处理成功。 */
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    /** 任务终态：部分对象失败（其余已处理，可按 RECONCILE 重放）。 */
    private static final String STATUS_PARTIAL = "PARTIAL";
    /** 任务终态：整体失败（发现阶段失败或连接器缺失）。 */
    private static final String STATUS_FAILED = "FAILED";
    /** 任务执行态。 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 任务排队态。 */
    private static final String STATUS_QUEUED = "QUEUED";

    /** 单轮最多执行的任务数（串行逐个跑，防单轮占用调度线程过久）。 */
    private static final int MAX_JOBS_PER_ROUND = 3;

    /** 失败对象明细上限（error_detail JSONB 防超长）。 */
    private static final int MAX_FAILED_OBJECTS = 20;

    /** 同步任务表。 */
    private final SyncJobMapper syncJobMapper;

    /** 连接器配置表（发现前加载 config 与 provider_key）。 */
    private final SourceConnectionMapper sourceConnectionMapper;

    /** 内容对象清单表（upsert 目标）。 */
    private final SourceObjectMapper sourceObjectMapper;

    /** 已注册适配器按 provider_key 索引（与 ConnectorServiceImpl 同构路由）。 */
    private final Map<String, ContentConnectorPort> adaptersByProviderKey;

    /** error_detail JSON 序列化（复用全局 ObjectMapper）。 */
    private final ObjectMapper objectMapper;

    /** 防止上一轮未完成时下一轮重入（对齐 IngestionDispatchScheduler 双保险）。 */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public SyncJobDispatchScheduler(SyncJobMapper syncJobMapper,
                                    SourceConnectionMapper sourceConnectionMapper,
                                    SourceObjectMapper sourceObjectMapper,
                                    ObjectMapper objectMapper,
                                    List<ContentConnectorPort> adapters) {
        this.syncJobMapper = syncJobMapper;
        this.sourceConnectionMapper = sourceConnectionMapper;
        this.sourceObjectMapper = sourceObjectMapper;
        this.objectMapper = objectMapper;
        this.adaptersByProviderKey = adapters.stream()
                .collect(Collectors.toMap(ContentConnectorPort::providerKey, Function.identity(),
                        (first, duplicate) -> {
                            throw new IllegalStateException("重复的连接器适配器: " + first.providerKey());
                        }));
    }

    /** 轮询入口：每 ragkb.connector.sync-interval-ms 执行一轮（默认 5s）。 */
    @Scheduled(fixedDelayString = "${ragkb.connector.sync-interval-ms:5000}")
    public void dispatchQueued() {
        if (!busy.compareAndSet(false, true)) {
            // 上一轮未跑完：跳过本轮（不排队堆积）
            return;
        }
        try {
            // 按入队时间先进先出，单轮限量
            List<SyncJob> queued = syncJobMapper.selectList(new LambdaQueryWrapper<SyncJob>()
                    .eq(SyncJob::getStatus, STATUS_QUEUED)
                    .orderByAsc(SyncJob::getQueuedAt)
                    .last("LIMIT " + MAX_JOBS_PER_ROUND));
            for (SyncJob job : queued) {
                try {
                    runOne(job);
                } catch (Exception e) {
                    // 单任务异常不拖垮整轮：兜底置 FAILED（runOne 内部已兜底，此处双保险）
                    log.warn("同步任务执行异常 jobId={}", job.getId(), e);
                    fail(job, "SYNC_CRASHED", e.getMessage());
                }
            }
        } finally {
            busy.set(false);
        }
    }

    // ------------------------------------------------------------------
    // 单任务执行：QUEUED → RUNNING → 发现 → 逐对象 upsert → 终态写回
    // ------------------------------------------------------------------

    private void runOne(SyncJob job) {
        // ① 认领：条件更新 QUEUED→RUNNING（0 行 = 已被取消/并发认领，直接跳过）
        int claimed = syncJobMapper.update(null, new LambdaUpdateWrapper<SyncJob>()
                .eq(SyncJob::getId, job.getId())
                .eq(SyncJob::getStatus, STATUS_QUEUED)
                .set(SyncJob::getStatus, STATUS_RUNNING)
                .set(SyncJob::getStartedAt, Instant.now()));
        if (claimed == 0) {
            return;
        }
        // ② 加载连接器：已删除（软删后 selectById 不可见）或租户错位均判任务失败
        SourceConnection connection = sourceConnectionMapper.selectById(job.getConnectionId());
        if (connection == null) {
            fail(job, "CONNECTION_NOT_FOUND", "连接器不存在或已删除");
            return;
        }
        // ③ 适配器路由兜底（Service 入口已拒绝未覆盖类型；此处防配置漂移，fail-closed）
        ContentConnectorPort adapter = adaptersByProviderKey.get(connection.getProviderKey());
        if (adapter == null) {
            fail(job, "ADAPTER_NOT_IMPLEMENTED",
                    "连接器类型「" + connection.getProviderKey() + "」暂未接入同步适配器");
            return;
        }
        // ④ 发现外部对象清单（目录扫描 / 索引拉取；失败整体置 FAILED）
        List<ContentConnectorPort.SourceObject> objects;
        try {
            objects = adapter.discover(new TenantId(job.getTenantId()), job.getConnectionId(),
                    job.getCursorBefore());
        } catch (ApiException e) {
            // 业务错误（目录不存在/索引不可达等）：消息已脱敏，可直接入库
            fail(job, "DISCOVER_FAILED", e.getMessage());
            return;
        } catch (Exception e) {
            // 非预期异常：只记异常类型与截断消息（可能含源路径，绝无凭证——config 不外泄）
            fail(job, "DISCOVER_FAILED", e.getClass().getSimpleName() + ": " + abbreviate(e.getMessage()));
            return;
        }
        // ⑤ 逐对象增量 upsert：按 (tenant, connection, external_id) 定位，版本变化才更新
        int created = 0;
        int updated = 0;
        int deleted = 0;
        int failed = 0;
        int skipped = 0;
        List<String> failedObjects = new ArrayList<>();
        for (ContentConnectorPort.SourceObject source : objects) {
            try {
                String outcome = upsertSourceObject(job, source);
                // 分支计数：created / updated / deleted / skipped（skipped 无表列，仅日志）
                switch (outcome) {
                    case "created" -> created++;
                    case "updated" -> updated++;
                    case "deleted" -> deleted++;
                    default -> skipped++;
                }
            } catch (Exception e) {
                // 单对象失败不中断整轮（终态转 PARTIAL，明细进 error_detail）
                failed++;
                if (failedObjects.size() < MAX_FAILED_OBJECTS) {
                    failedObjects.add(source.externalId());
                }
                log.warn("同步单对象失败 jobId={} externalId={}", job.getId(), source.externalId(), e);
            }
        }
        // ⑥ 全量对账（仅 FULL）：本轮未 seen 且未墓碑的存量行 → 源侧已删除，置墓碑
        if ("FULL".equals(job.getJobType())) {
            deleted += tombstoneVanished(job);
        }
        // ⑦ 终态写回：条件 status=RUNNING（RUNNING 中被取消则不覆盖 CANCELLED）；
        //    有失败对象转 PARTIAL（部分成功），否则 SUCCEEDED。
        String finalStatus = failed > 0 ? STATUS_PARTIAL : STATUS_SUCCEEDED;
        int finished = syncJobMapper.update(null, new LambdaUpdateWrapper<SyncJob>()
                .eq(SyncJob::getId, job.getId())
                .eq(SyncJob::getStatus, STATUS_RUNNING)
                .set(SyncJob::getStatus, finalStatus)
                .set(SyncJob::getDiscoveredCount, objects.size())
                .set(SyncJob::getCreatedCount, created)
                .set(SyncJob::getUpdatedCount, updated)
                .set(SyncJob::getDeletedCount, deleted)
                .set(SyncJob::getFailedCount, failed)
                .set(SyncJob::getCursorAfter, job.getCursorBefore())
                .set(SyncJob::getFinishedAt, Instant.now())
                .set(failedObjects.isEmpty(), SyncJob::getErrorDetail, writeErrorDetail(failedObjects)));
        if (finished == 0) {
            // 已被取消：保留 CANCELLED 终态，仅记日志
            log.info("同步任务在执行中被取消，放弃终态写回 jobId={}", job.getId());
            return;
        }
        // ⑧ 连接器健康回写：跑完一轮即视为「最近成功」（PARTIAL 也算，错误码留空），
        //    前端卡片据此显示游标新鲜度与本轮计数。
        sourceConnectionMapper.update(null, new LambdaUpdateWrapper<SourceConnection>()
                .eq(SourceConnection::getId, connection.getId())
                .set(SourceConnection::getLastSuccessAt, Instant.now())
                .set(SourceConnection::getLastErrorCode, null));
        // skipped 无表列（DDL 仅 discovered/created/updated/deleted/failed）：日志汇总对账
        log.info("同步任务完成 jobId={} status={} discovered={} created={} updated={} deleted={} failed={} skipped={}",
                job.getId(), finalStatus, objects.size(), created, updated, deleted, failed, skipped);
    }

    /**
     * 单对象 upsert：返回结果分支（created/updated/deleted/skipped）。
     * 判定依据：外部 id 不存在 → 新建；源侧墓碑 → 落墓碑（deleted）；
     * 版本/地址/摘要变化 → 更新（updated）；均未变 → 仅续 last_seen（skipped）。
     */
    private String upsertSourceObject(SyncJob job, ContentConnectorPort.SourceObject source) {
        Instant now = Instant.now();
        SourceObject existing = sourceObjectMapper.selectOne(new LambdaQueryWrapper<SourceObject>()
                .eq(SourceObject::getTenantId, job.getTenantId())
                .eq(SourceObject::getConnectionId, job.getConnectionId())
                .eq(SourceObject::getExternalId, source.externalId())
                .last("LIMIT 1"));
        if (existing == null) {
            // 新对象：登记清单行（tombstoned 源侧声明直接按墓碑落库）
            SourceObject insert = new SourceObject();
            insert.setTenantId(job.getTenantId());
            insert.setConnectionId(job.getConnectionId());
            insert.setExternalId(source.externalId());
            insert.setSourceUri(source.sourceUri());
            insert.setSourceVersion(source.sourceVersion());
            insert.setSourceEtag(source.sourceVersion());
            insert.setContentSha256(source.contentSha256());
            insert.setTombstoned(source.tombstoned());
            insert.setLastSyncJobId(job.getId());
            insert.setLastSeenAt(now);
            sourceObjectMapper.insert(insert);
            return "created";
        }
        if (source.tombstoned() && !Boolean.TRUE.equals(existing.getTombstoned())) {
            // 源侧已删除：本地落墓碑（保留行做审计，计数归 deleted）
            existing.setTombstoned(true);
            existing.setLastSyncJobId(job.getId());
            existing.setLastSeenAt(now);
            sourceObjectMapper.updateById(existing);
            return "deleted";
        }
        boolean changed = !Objects.equals(existing.getSourceVersion(), source.sourceVersion())
                || !Objects.equals(existing.getSourceUri(), source.sourceUri())
                || !Objects.equals(existing.getContentSha256(), source.contentSha256());
        if (changed) {
            // 版本/地址/摘要变化：更新清单（document_id 不动，待后续摄取转换接线）
            existing.setSourceUri(source.sourceUri());
            existing.setSourceVersion(source.sourceVersion());
            existing.setSourceEtag(source.sourceVersion());
            existing.setContentSha256(source.contentSha256());
            existing.setTombstoned(false);
            existing.setLastSyncJobId(job.getId());
            existing.setLastSeenAt(now);
            sourceObjectMapper.updateById(existing);
            return "updated";
        }
        // 无变化：仅续 last_seen 与最近任务标记（对象仍在源侧存活）
        existing.setLastSyncJobId(job.getId());
        existing.setLastSeenAt(now);
        sourceObjectMapper.updateById(existing);
        return "skipped";
    }

    /** 全量对账：把本轮未 seen 且未墓碑的存量行置墓碑（消失对象），返回收敛行数。 */
    private int tombstoneVanished(SyncJob job) {
        List<SourceObject> vanished = sourceObjectMapper.selectList(new LambdaQueryWrapper<SourceObject>()
                .eq(SourceObject::getTenantId, job.getTenantId())
                .eq(SourceObject::getConnectionId, job.getConnectionId())
                .eq(SourceObject::getTombstoned, false)
                .and(wrapper -> wrapper
                        .ne(SourceObject::getLastSyncJobId, job.getId())
                        .or()
                        .isNull(SourceObject::getLastSyncJobId)));
        for (SourceObject stale : vanished) {
            // 逐行置墓碑（保留行做审计；后续清理任务再物理收敛）
            stale.setTombstoned(true);
            stale.setLastSyncJobId(job.getId());
            sourceObjectMapper.updateById(stale);
        }
        return vanished.size();
    }

    /** 任务失败收尾：条件写回 FAILED + 错误码/原因（原因截断防超长），并回写连接器错误态。 */
    private void fail(SyncJob job, String errorCode, String reason) {
        syncJobMapper.update(null, new LambdaUpdateWrapper<SyncJob>()
                .eq(SyncJob::getId, job.getId())
                .eq(SyncJob::getStatus, STATUS_RUNNING)
                .set(SyncJob::getStatus, STATUS_FAILED)
                .set(SyncJob::getErrorCode, errorCode)
                .set(SyncJob::getErrorDetail, writeErrorDetail(reason))
                .set(SyncJob::getFinishedAt, Instant.now()));
        // 连接器侧同步错误标记（前端卡片「最近错误」；不清 last_success_at，保留历史水位）
        sourceConnectionMapper.update(null, new LambdaUpdateWrapper<SourceConnection>()
                .eq(SourceConnection::getId, job.getConnectionId())
                .set(SourceConnection::getLastErrorCode, errorCode)
                .set(SourceConnection::getLastErrorAt, Instant.now()));
        // 日志只记错误码与任务 id（reason 已脱敏入库，不在日志重复展开源路径）
        log.warn("同步任务失败 jobId={} errorCode={}", job.getId(), errorCode);
    }

    /** 失败对象明细 → error_detail JSON（{"failedObjects":[...]}；序列化失败降级为空）。 */
    private String writeErrorDetail(List<String> failedObjects) {
        if (failedObjects.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("failedObjects", failedObjects));
        } catch (Exception e) {
            // 明细序列化失败不应影响任务终态：返回空对象保底
            return "{\"failedObjects\":[]}";
        }
    }

    /** 失败原因 → error_detail JSON（{"reason":"..."}，截断 512 字符）。 */
    private String writeErrorDetail(String reason) {
        return "{\"reason\":\"" + abbreviate(reason) + "\"}";
    }

    /** 文本截断（512 上限）+ JSON 字符串转义（保守替换引号与反斜杠）。 */
    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String safe = text.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
        return safe.length() > 512 ? safe.substring(0, 512) : safe;
    }
}

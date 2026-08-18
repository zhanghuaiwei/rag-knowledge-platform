package com.ragkb.service.modules.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.connector.dto.ConnectorCreateDto;
import com.ragkb.service.modules.connector.dto.ConnectorUpdateDto;
import com.ragkb.service.modules.connector.dto.ConnectorValidateDto;
import com.ragkb.service.modules.connector.dto.SyncDto;
import com.ragkb.service.modules.connector.persistence.entity.SourceConnection;
import com.ragkb.service.modules.connector.persistence.entity.SyncJob;
import com.ragkb.service.modules.connector.persistence.mapper.SourceConnectionMapper;
import com.ragkb.service.modules.connector.persistence.mapper.SourceObjectMapper;
import com.ragkb.service.modules.connector.persistence.mapper.SyncJobMapper;
import com.ragkb.service.modules.connector.port.ContentConnectorPort;
import com.ragkb.service.modules.connector.service.ConnectorService;
import com.ragkb.service.modules.connector.vo.ConnectorCountsVo;
import com.ragkb.service.modules.connector.vo.ConnectorValidateResultVo;
import com.ragkb.service.modules.connector.vo.ConnectorVo;
import com.ragkb.service.modules.connector.vo.SyncJobVo;
import com.ragkb.service.modules.identity.service.TokenService;
import com.ragkb.service.modules.knowledge.service.KbService;
import com.ragkb.service.modules.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容源连接器用例实现：连接器配置 CRUD / 连通性校验 / 同步任务受理与查询。
 *
 * <p>业务规则来源：{@code deploy/ddl/init.sql} 的 CHECK 约束（sync_mode、
 * source_connection.status、sync_job.job_type/status）、{@code ConnectorController}
 * 端点语义与前端 Connector 契约。
 *
 * <p>多租户隔离（deny-by-default）：所有读写先经 {@link #requireConnection} 校验
 * 「资源存在 + 当前认证租户与连接器归属租户一致」；sync_job 查询显式带 tenant_id。
 *
 * <p>适配器路由：按 {@code provider_key} 从已注册的 {@link ContentConnectorPort}
 * 实现中查找；未覆盖类型（sharepoint/confluence/s3）fail-closed——配置可保存，
 * 连通性校验返回「暂不支持」，同步入口直接拒绝，不假报成功。
 *
 * <p>凭证红线：config 可能含密码/token，任何日志与异常消息都不回显 config 取值
 * （配置校验只报字段名）；secret_ref 留空（secret 管理模块未建，见遗留说明）。
 */
// 无条件注册：ConnectorController 未按 db 开关条件装配（脚手架阶段即可见端点），
// Mapper 依赖用 ObjectProvider 懒获取，db.enabled=false 时调用明确报错而不是启动失败。
@Service
public class ConnectorServiceImpl implements ConnectorService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorServiceImpl.class);

    // ---------- 状态/枚举常量（与 DDL CHECK 约束一一对应，禁止裸写魔法值扩散） ----------

    /** source_connection.status：正常态（可触发同步）。 */
    private static final String CONN_STATUS_ACTIVE = "ACTIVE";
    /** source_connection.status：暂停（保留配置，不接受同步）。 */
    private static final String CONN_STATUS_PAUSED = "PAUSED";
    /** source_connection.status：撤销（删除连接器的终态语义）。 */
    private static final String CONN_STATUS_REVOKED = "REVOKED";
    /** source_connection.sync_mode 默认手动（触发式同步；SCHEDULED/WEBHOOK 留待调度接线）。 */
    private static final String SYNC_MODE_MANUAL = "MANUAL";
    /** sync_job.job_type 白名单（ck_sync_job_type）。 */
    private static final List<String> SYNC_JOB_TYPES = List.of("FULL", "INCREMENTAL", "WEBHOOK", "RECONCILE");
    /** sync_job 可取消的状态窗口（QUEUED/RUNNING → CANCELLED）。 */
    private static final List<String> CANCELABLE_STATUSES = List.of("QUEUED", "RUNNING");
    /** 历史数据 tenant_id 为 0/null 时兜底使用的默认租户（对齐 KbServiceImpl 约定）。 */
    private static final long DEFAULT_TENANT_ID = 1L;
    /** 契约缺 kbId 字段时的默认归属库（对齐 KbServiceImpl 的 index_profile=1 兜底模式）。 */
    private static final long DEFAULT_KB_ID = 1L;

    // ---------- 依赖（本模块持久化 + 跨模块 Service/Port） ----------

    /** 连接器配置表（source_connection）。 */
    private final ObjectProvider<SourceConnectionMapper> connectionMapperProvider;

    /** 同步任务表（sync_job）。 */
    private final ObjectProvider<SyncJobMapper> syncJobMapperProvider;

    /** 异步任务登记（sync 返回 202，前端轮询 tasks/{id}）。 */
    private final TaskService taskService;

    /** 知识库校验（连接器归属库存在性 + 租户一致性；懒获取避免装配期耦合）。 */
    private final ObjectProvider<KbService> kbServiceProvider;

    /** config JSONB（映射 String）↔ Map 的序列化器。 */
    private final ObjectMapper objectMapper;

    /** 已注册适配器按 provider_key 索引（无适配器 bean 时为空 Map，全部类型 fail-closed）。 */
    private final Map<String, ContentConnectorPort> adaptersByProviderKey;

    public ConnectorServiceImpl(ObjectProvider<SourceConnectionMapper> connectionMapperProvider,
                                ObjectProvider<SyncJobMapper> syncJobMapperProvider,
                                TaskService taskService,
                                ObjectProvider<KbService> kbServiceProvider,
                                ObjectMapper objectMapper,
                                List<ContentConnectorPort> adapters) {
        this.connectionMapperProvider = connectionMapperProvider;
        this.syncJobMapperProvider = syncJobMapperProvider;
        this.taskService = taskService;
        this.kbServiceProvider = kbServiceProvider;
        this.objectMapper = objectMapper;
        // 适配器索引：providerKey 重复注册属装配错误，启动即失败（fail-fast）
        this.adaptersByProviderKey = adapters.stream()
                .collect(Collectors.toMap(ContentConnectorPort::providerKey, Function.identity(),
                        (first, duplicate) -> {
                            throw new IllegalStateException("重复的连接器适配器: " + first.providerKey());
                        }));
    }

    // =====================================================================
    // 连接器配置 CRUD
    // =====================================================================

    @Override
    public List<ConnectorVo> listConnectors() {
        SourceConnectionMapper connectionMapper = requireConnectionMapper();
        // 已认证租户只看本租户连接器；dev/未认证上下文全量返回（对齐 listKbs 模式）
        Long currentTenantId = currentTenantIdOrNull();
        LambdaQueryWrapper<SourceConnection> wrapper = new LambdaQueryWrapper<SourceConnection>()
                .orderByDesc(SourceConnection::getId);
        if (currentTenantId != null) {
            wrapper.eq(SourceConnection::getTenantId, currentTenantId);
        }
        return connectionMapper.selectList(wrapper).stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    @Transactional
    public ConnectorVo createConnector(ConnectorCreateDto request, String idempotencyKey) {
        SourceConnectionMapper connectionMapper = requireConnectionMapper();
        // ① provider_key 白名单 + config 必填字段校验（fail-closed，只报字段名不回显取值）
        ConnectorConfigRules.validate(request.providerKey(), request.config());
        // ② 归属库解析：契约 DTO 缺 kbId 字段，约定 config.kbId（可选数字）声明归属，
        //    缺省兜底默认库 1（对齐 KbServiceImpl 的 index_profile=1 兜底模式）。
        long kbId = kbIdFromConfig(request.config());
        requireKbInTenant(kbId);
        // ③ 租户：优先当前认证租户，未认证兜底默认租户 1（dev/种子数据场景）。
        Long currentTenantId = currentTenantIdOrNull();
        long tenantId = currentTenantId != null ? currentTenantId : DEFAULT_TENANT_ID;
        // ④ 落库实体：enabled → status 映射（false=PAUSED 暂不参与同步）；syncMode 默认 MANUAL。
        SourceConnection connection = new SourceConnection();
        connection.setTenantId(tenantId);
        connection.setKbId(kbId);
        connection.setName(request.name().trim());
        connection.setProviderKey(request.providerKey());
        connection.setConfig(writeConfig(request.providerKey(), request.config()));
        connection.setSyncMode(SYNC_MODE_MANUAL);
        connection.setStatus(Boolean.FALSE.equals(request.enabled()) ? CONN_STATUS_PAUSED : CONN_STATUS_ACTIVE);
        try {
            connectionMapper.insert(connection);
        } catch (DuplicateKeyException e) {
            // 撞 uq_source_connection_name (tenant, kb, name)：同名连接器已存在
            throw new ApiException(ErrorCode.CONFLICT, "同名连接器已存在: " + connection.getName());
        }
        // 日志只记元信息（不含 config——可能携带凭证）
        log.info("连接器已创建 tenantId={} kbId={} providerKey={} name={}",
                tenantId, kbId, request.providerKey(), connection.getName());
        return toVo(connection);
    }

    @Override
    public ConnectorVo getConnector(long connectionId) {
        // 详情 = 归属校验 + 实体视图（含最近一次同步计数）
        return toVo(requireConnection(connectionId));
    }

    @Override
    @Transactional
    public ConnectorVo updateConnector(long connectionId, ConnectorUpdateDto request) {
        SourceConnectionMapper connectionMapper = requireConnectionMapper();
        // ① 加载并校验归属（deny-by-default）
        SourceConnection connection = requireConnection(connectionId);
        // 撤销（删除）后的连接器为终态，不允许再编辑
        if (CONN_STATUS_REVOKED.equals(connection.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "连接器已删除，无法编辑");
        }
        // ② 名称变更做 (tenant, kb, name) 内唯一性预检（uq 约束兜底并发窗口）
        if (StringUtils.hasText(request.name())) {
            String name = request.name().trim();
            Long sameNameCount = connectionMapper.selectCount(new LambdaQueryWrapper<SourceConnection>()
                    .eq(SourceConnection::getTenantId, effectiveTenantId(connection))
                    .eq(SourceConnection::getKbId, connection.getKbId())
                    .ne(SourceConnection::getId, connectionId)
                    .eq(SourceConnection::getName, name));
            if (sameNameCount != null && sameNameCount > 0) {
                throw new ApiException(ErrorCode.CONFLICT, "同名连接器已存在: " + name);
            }
            connection.setName(name);
        }
        // ③ 配置整体替换（PATCH 语义）：新 config 需再过必填校验；kbId 不随 PATCH 变更
        //    （新 config 未携带时从旧 config 继承，防止替换后丢归属声明）。
        if (request.config() != null) {
            Map<String, Object> merged = new HashMap<>(request.config());
            if (!merged.containsKey("kbId")) {
                Object legacyKbId = parseConfigToMap(connection.getConfig()).get("kbId");
                if (legacyKbId != null) {
                    merged.put("kbId", legacyKbId);
                }
            }
            ConnectorConfigRules.validate(connection.getProviderKey(), merged);
            connection.setConfig(writeConfig(connection.getProviderKey(), merged));
        }
        // ④ enabled → status 映射（恢复暂停的连接器 / 主动暂停）
        if (request.enabled() != null) {
            connection.setStatus(request.enabled() ? CONN_STATUS_ACTIVE : CONN_STATUS_PAUSED);
        }
        // ⑤ 实体携带 row_version 走乐观锁更新；0 行即并发冲突
        if (connectionMapper.updateById(connection) == 0) {
            throw new ApiException(ErrorCode.CONFLICT, "连接器已被他人修改，请刷新后重试");
        }
        return toVo(connection);
    }

    @Override
    @Transactional
    public void deleteConnector(long connectionId) {
        SourceConnectionMapper connectionMapper = requireConnectionMapper();
        SyncJobMapper syncJobMapper = requireSyncJobMapper();
        // ① 加载并校验归属（危险操作的租户隔离入口）
        SourceConnection connection = requireConnection(connectionId);
        long tenantId = effectiveTenantId(connection);
        // ② 级联取消未完结的同步任务（QUEUED/RUNNING → CANCELLED，防止调度器继续写已删连接器）
        syncJobMapper.update(null, new LambdaUpdateWrapper<SyncJob>()
                .eq(SyncJob::getTenantId, tenantId)
                .eq(SyncJob::getConnectionId, connectionId)
                .in(SyncJob::getStatus, CANCELABLE_STATUSES)
                .set(SyncJob::getStatus, "CANCELLED")
                .set(SyncJob::getFinishedAt, Instant.now()));
        // ③ 连接器置撤销终态 + 软删（@TableLogic → UPDATE del_flag=1，列表不再可见）；
        //    source_object 明细保留（审计/对账，tombstone 由后续清理任务收敛）。
        connection.setStatus(CONN_STATUS_REVOKED);
        connectionMapper.updateById(connection);
        connectionMapper.deleteById(connectionId);
        log.info("连接器已删除（软删+撤销） tenantId={} connectionId={}", tenantId, connectionId);
    }

    // =====================================================================
    // 连通性校验 / 同步任务
    // =====================================================================

    @Override
    public ConnectorValidateResultVo validateConnector(ConnectorValidateDto request) {
        // ① 静态校验：provider_key 白名单 + config 必填字段（失败即返回不可达原因）
        try {
            ConnectorConfigRules.validate(request.providerKey(), request.config());
        } catch (ApiException e) {
            return new ConnectorValidateResultVo(false, e.getMessage());
        }
        // ② 适配器路由：无真实适配器的类型（sharepoint/confluence/s3）fail-closed
        //    报「暂不支持」，不假报成功（SDK 接入前无法真实探测连通性）。
        ContentConnectorPort adapter = adaptersByProviderKey.get(request.providerKey());
        if (adapter == null) {
            return new ConnectorValidateResultVo(false,
                    "连接器类型「" + request.providerKey() + "」暂未接入适配器，无法执行连通性校验");
        }
        // ③ 真实探测：适配器内部异常（目录不存在/索引不可达等）转不可达结论
        try {
            adapter.validate(request.config());
            return new ConnectorValidateResultVo(true, "校验通过");
        } catch (ApiException e) {
            return new ConnectorValidateResultVo(false, e.getMessage());
        }
    }

    @Override
    @Transactional
    public Task syncConnector(long connectionId, SyncDto request, String idempotencyKey) {
        requireSyncJobMapper();
        // ① 加载并校验归属；仅 ACTIVE 连接器可触发同步（暂停/异常需先恢复）
        SourceConnection connection = requireConnection(connectionId);
        if (!CONN_STATUS_ACTIVE.equals(connection.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, "连接器当前状态不可触发同步（暂停或异常）");
        }
        // ② 同步类型白名单（ck_sync_job_type：FULL/INCREMENTAL/WEBHOOK/RECONCILE）
        if (!SYNC_JOB_TYPES.contains(request.syncType())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "非法的同步类型: " + request.syncType());
        }
        // ③ fail-closed：无真实适配器的类型直接拒绝入队（排队后必然失败，不如入口明确报错）
        if (adaptersByProviderKey.get(connection.getProviderKey()) == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "连接器类型「" + connection.getProviderKey() + "」暂未接入同步适配器");
        }
        // ④ 登记同步任务：QUEUED 起步，cursor 记录同步前水位（快照式源不推进游标）
        long tenantId = effectiveTenantId(connection);
        SyncJob job = new SyncJob();
        job.setTenantId(tenantId);
        job.setConnectionId(connectionId);
        job.setJobType(request.syncType());
        job.setStatus("QUEUED");
        job.setIdempotencyKey(StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : "conn-sync-" + connectionId + "-" + UUID.randomUUID());
        job.setCursorBefore(connection.getCursorValue());
        job.setQueuedAt(Instant.now());
        try {
            requireSyncJobMapper().insert(job);
        } catch (DuplicateKeyException e) {
            // 撞 uq_sync_job_idempotency (tenant, connection, key)：同一幂等键的重复提交
            throw new ApiException(ErrorCode.CONFLICT, "同步任务重复提交");
        }
        // ⑤ 登记 202 任务供前端轮询；真实执行由 SyncJobDispatchScheduler 后台推进
        return taskService.submit("SYNC", "QUEUED",
                "「" + connection.getName() + "」同步任务已排队（" + request.syncType() + "）", 0,
                "SYNC_JOB", String.valueOf(job.getId()), "等待同步调度器执行");
    }

    @Override
    public SyncJobVo getSyncJob(long jobId) {
        // ① 按主键加载任务
        SyncJob job = requireSyncJobMapper().selectById(jobId);
        if (job == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        // ② 跨租户访问按「不存在」处理（不泄露其他租户资源的存在性）
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && job.getTenantId() != null && job.getTenantId() > 0
                && !currentTenantId.equals(job.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        return toSyncJobVo(job);
    }

    @Override
    @Transactional
    public void cancelSyncJob(long jobId) {
        // ① 加载并校验归属（跨租户按不存在处理，与 getSyncJob 一致）
        SyncJob job = requireSyncJobMapper().selectById(jobId);
        if (job == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && job.getTenantId() != null && job.getTenantId() > 0
                && !currentTenantId.equals(job.getTenantId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "同步任务不存在");
        }
        // ② 条件更新：仅 QUEUED/RUNNING 可取消（RUNNING 中被取消时执行器最终写回前再校验一次）
        int cancelled = requireSyncJobMapper().update(null, new LambdaUpdateWrapper<SyncJob>()
                .eq(SyncJob::getId, jobId)
                .in(SyncJob::getStatus, CANCELABLE_STATUSES)
                .set(SyncJob::getStatus, "CANCELLED")
                .set(SyncJob::getFinishedAt, Instant.now()));
        if (cancelled == 0) {
            // 终态任务不可取消（SUCCEEDED/FAILED/PARTIAL/CANCELLED）
            throw new ApiException(ErrorCode.CONFLICT, "同步任务已结束，无法取消");
        }
        log.info("同步任务已取消 tenantId={} jobId={}", job.getTenantId(), jobId);
    }

    // =====================================================================
    // 内部工具：归属校验 / 视图映射 / 配置序列化
    // =====================================================================

    /** 加载连接器并做租户归属校验（所有按 connectionId 操作的统一入口，deny-by-default）。 */
    private SourceConnection requireConnection(long connectionId) {
        SourceConnection connection = requireConnectionMapper().selectById(connectionId);
        if (connection == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "连接器不存在");
        }
        // 已认证租户与归属租户不一致 → 拒绝；历史数据 tenant_id=0/null 跳过（对齐 requireKb）
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && connection.getTenantId() != null && connection.getTenantId() > 0
                && !currentTenantId.equals(connection.getTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "无权访问该连接器");
        }
        return connection;
    }

    /** 子表操作使用的有效租户：历史数据 tenant_id=0/null 时兜底默认租户 1。 */
    private static long effectiveTenantId(SourceConnection connection) {
        return (connection.getTenantId() == null || connection.getTenantId() <= 0)
                ? DEFAULT_TENANT_ID
                : connection.getTenantId();
    }

    /** 归属库存在性 + 租户一致性校验（跨模块仅经 KbService，不碰 knowledge 持久化）。 */
    private void requireKbInTenant(long kbId) {
        KbService kbService = kbServiceProvider.getIfAvailable();
        if (kbService == null) {
            // knowledge 模块服务未装配（理论不可达，db 开关下同生共死）：fail-closed 报错
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "知识库服务未装配，无法校验归属库");
        }
        KbService.KbBrief brief = kbService.kbBrief(kbId);
        Long currentTenantId = currentTenantIdOrNull();
        if (currentTenantId != null && brief.tenantId() > 0 && currentTenantId != brief.tenantId()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "目标知识库不属于当前租户");
        }
    }

    /** 从 config 提取可选 kbId（数字声明归属库）；缺失返回默认库 1。 */
    private static long kbIdFromConfig(Map<String, Object> config) {
        if (config != null && config.get("kbId") instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        return DEFAULT_KB_ID;
    }

    /** config Map → JSONB 字符串（ck_source_connection_config 要求 JSON 对象）；失败按内部错误处理。 */
    private String writeConfig(String providerKey, Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Map.of() : config);
        } catch (Exception e) {
            // 序列化失败只报 providerKey，不回显 config 内容（防凭证泄漏）
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "连接器配置序列化失败 providerKey=" + providerKey);
        }
    }

    /** config JSONB 字符串 → Map；空/脏值返回空 Map（必填校验兜底报错）。 */
    private Map<String, Object> parseConfigToMap(String config) {
        if (!StringUtils.hasText(config)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 连接器实体 → 响应视图：聚合最近一次同步计数与游标新鲜度（前端卡片所需）。 */
    private ConnectorVo toVo(SourceConnection connection) {
        SyncJob latestJob = latestSyncJobOf(connection);
        ConnectorCountsVo counts = latestJob == null
                ? new ConnectorCountsVo(0, 0, 0, 0, 0)
                : new ConnectorCountsVo(
                        countOf(latestJob.getDiscoveredCount()),
                        countOf(latestJob.getCreatedCount()),
                        countOf(latestJob.getUpdatedCount()),
                        countOf(latestJob.getDeletedCount()),
                        countOf(latestJob.getFailedCount()));
        // 游标新鲜度：最近成功同步距今的分钟数（无成功记录时 0，前端按 SLA 阈值提示）
        long cursorAgeMin = connection.getLastSuccessAt() == null
                ? 0
                : Math.max(0, Duration.between(connection.getLastSuccessAt(), Instant.now()).toMinutes());
        return new ConnectorVo(
                connection.getId(),
                connection.getName(),
                connection.getProviderKey(),
                connection.getSyncMode(),
                connection.getStatus(),
                connection.getLastSuccessAt(),
                connection.getLastErrorCode(),
                cursorAgeMin,
                counts);
    }

    /** 该连接器最近一次同步任务（按 id 倒序取 1；连接器数量小，逐个查询可接受）。 */
    private SyncJob latestSyncJobOf(SourceConnection connection) {
        if (connection.getId() == null) {
            return null;
        }
        return requireSyncJobMapper().selectOne(new LambdaQueryWrapper<SyncJob>()
                .eq(SyncJob::getTenantId, effectiveTenantId(connection))
                .eq(SyncJob::getConnectionId, connection.getId())
                .orderByDesc(SyncJob::getId)
                .last("LIMIT 1"));
    }

    /** 同步任务实体 → 响应视图（failedObjects 从 error_detail JSON 解析，最多透出 20 个）。 */
    private SyncJobVo toSyncJobVo(SyncJob job) {
        return new SyncJobVo(
                job.getId(),
                job.getConnectionId(),
                job.getJobType(),
                job.getStatus(),
                countOf(job.getDiscoveredCount()),
                parseFailedObjects(job.getErrorDetail()),
                job.getFinishedAt(),
                job.getErrorCode(),
                job.getQueuedAt());
    }

    /** error_detail（JSONB → String）中的 {"failedObjects":[...]} 解析为外部 id 列表。 */
    private List<String> parseFailedObjects(String errorDetail) {
        if (!StringUtils.hasText(errorDetail)) {
            return List.of();
        }
        try {
            Map<String, Object> detail = objectMapper.readValue(errorDetail, Map.class);
            if (detail.get("failedObjects") instanceof List<?> list) {
                // 只透出字符串型外部 id（脏数据忽略），并截断防超长响应
                return list.stream()
                        .filter(item -> item instanceof String)
                        .map(String.class::cast)
                        .limit(20)
                        .toList();
            }
        } catch (Exception e) {
            // 历史脏值：按无失败对象明细处理，不阻断任务查询
            log.debug("同步任务 error_detail 解析失败，按空明细处理");
        }
        return List.of();
    }

    /** 计数空值兜底为 0（NOT NULL DEFAULT 列的历史脏数据防御）。 */
    private static long countOf(Integer value) {
        return value == null ? 0 : value;
    }

    /** 数据访问守卫：db.enabled=false 时 Mapper 未注册，明确报错而非 NPE（fail-closed）。 */
    private SourceConnectionMapper requireConnectionMapper() {
        SourceConnectionMapper mapper = connectionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "数据访问未启用（ragkb.db.enabled=false）");
        }
        return mapper;
    }

    /** 同上：sync_job 表 Mapper 守卫。 */
    private SyncJobMapper requireSyncJobMapper() {
        SyncJobMapper mapper = syncJobMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "数据访问未启用（ragkb.db.enabled=false）");
        }
        return mapper;
    }

    /** 当前 JWT 主体的租户 id；dev/API Key/未认证返回 null。 */
    private Long currentTenantIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal
                && principal.tenantId() > 0) {
            return principal.tenantId();
        }
        return null;
    }

    /** 供同步调度器复用的适配器路由（按 provider_key 查找，未覆盖返回 null）。 */
    ContentConnectorPort adapterOf(String providerKey) {
        return adaptersByProviderKey.get(providerKey);
    }
}

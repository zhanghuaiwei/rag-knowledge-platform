package com.ragkb.service.modules.ingestion.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.ingestion.persistence.entity.ParseTask;
import com.ragkb.service.modules.ingestion.persistence.mapper.ParseTaskMapper;
import com.ragkb.service.modules.ingestion.service.IngestionUseCase;
import com.ragkb.service.modules.integration.port.OutboxPort;
import com.ragkb.service.modules.task.service.TaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@link IngestionUseCase} 的数据库实现：上传/重解析后把摄取任务写入队列。
 *
 * <p>单一职责：在调用方事务内写 {@code parse_task}（SAFETY 阶段、QUEUED）并追加
 * {@code outbox_event}（topic=ingestion），然后向任务中心登记一个 QUEUED 任务供
 * 前端「任务中心」查看。真正推进 {@code ingest_status} 的是 rag-engine worker
 * （消费 outbox 后逐阶段 {@code SAFETY → PARSING → ... → READY}）。
 *
 * <p>⚠️ 边界：当前 rag-engine provider 未装配（{@code RagEngineHttpClient} 为桩），
 * 任务会停留在 QUEUED 不假报已解析（fail-closed，与
 * {@code rag-engine/src/rag_engine/ingestion/service.py} 行为一致）。
 */
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class IngestionUseCaseImpl implements IngestionUseCase {

    /** 安全扫描阶段标识（parse_task.stage CHECK 允许值）。 */
    private static final String STAGE_SAFETY = "SAFETY";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final int MAX_ATTEMPTS = 3;

    private final ParseTaskMapper parseTaskMapper;
    private final OutboxPort outboxPort;
    private final TaskService taskService;

    public IngestionUseCaseImpl(ParseTaskMapper parseTaskMapper, OutboxPort outboxPort, TaskService taskService) {
        this.parseTaskMapper = parseTaskMapper;
        this.outboxPort = outboxPort;
        this.taskService = taskService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Task enqueueIngestion(TenantId tenantId, long documentId, long versionId,
                                 String taskType, String idempotencyKey) {
        // 幂等键必须唯一（parse_task 唯一约束 uq_parse_task_idempotency），为空时自动生成。
        String key = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "ingest-" + taskType + "-" + documentId + "-" + versionId + "-" + UUID.randomUUID();

        ParseTask parseTask = new ParseTask();
        parseTask.setTenantId(tenantId.value());
        parseTask.setDocumentId(documentId);
        parseTask.setVersionId(versionId);
        parseTask.setTaskType(taskType);
        parseTask.setStage(STAGE_SAFETY);
        parseTask.setStatus(STATUS_QUEUED);
        parseTask.setIdempotencyKey(key);
        parseTask.setAttemptCount(0);
        parseTask.setMaxAttempts(MAX_ATTEMPTS);
        parseTask.setProgressPercent(0);
        parseTask.setQueuedAt(Instant.now());
        parseTaskMapper.insert(parseTask);

        // 同事务追加 outbox 事件：rag-engine worker 消费后逐阶段推进摄取状态（ADR-2）。
        outboxPort.append(tenantId.value(), "DOCUMENT", String.valueOf(documentId), 1L,
                "document.uploaded", "ingestion", Map.of(
                        "documentId", documentId,
                        "versionId", versionId,
                        "parseTaskId", parseTask.getId(),
                        "taskType", taskType));

        // 登记任务中心展示用任务（QUEUED；真实进度由 worker 回写 parse_task 后刷新）。
        return taskService.submit(
                "INGEST", STATUS_QUEUED, "文档已进入安全扫描队列", 0,
                "DOCUMENT", String.valueOf(documentId), "等待 rag-engine 安全扫描");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Task retryIngestion(TenantId tenantId, String taskId, String idempotencyKey) {
        ParseTask parseTask = parseTaskMapper.selectById(taskId);
        if (parseTask == null || !parseTask.getTenantId().equals(tenantId.value())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "摄取任务不存在");
        }
        // 重试上限（DDL 默认 3 次）之外不再放行。
        if (parseTask.getAttemptCount() >= parseTask.getMaxAttempts()) {
            throw new ApiException(ErrorCode.CONFLICT, "解析已重试达到上限，请检查内容或联系管理员");
        }
        parseTask.setStatus(STATUS_QUEUED);
        parseTask.setAttemptCount(parseTask.getAttemptCount() + 1);
        parseTask.setErrorCode(null);
        parseTask.setErrorDetail(null);
        parseTask.setQueuedAt(Instant.now());
        parseTask.setFinishedAt(null);
        parseTaskMapper.updateById(parseTask);

        outboxPort.append(tenantId.value(), "DOCUMENT", String.valueOf(parseTask.getDocumentId()),
                parseTask.getAttemptCount() + 1L, "document.reparse", "ingestion", Map.of(
                        "parseTaskId", parseTask.getId(),
                        "documentId", parseTask.getDocumentId(),
                        "versionId", parseTask.getVersionId()));

        return taskService.submit(
                "REPARSE", STATUS_QUEUED, "文档已重新进入解析队列", 0,
                "DOCUMENT", String.valueOf(parseTask.getDocumentId()), "等待 rag-engine 重新解析");
    }
}

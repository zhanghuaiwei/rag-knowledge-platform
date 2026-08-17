package com.ragkb.service.modules.ingestion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragkb.service.common.model.TenantId;
import com.ragkb.service.modules.document.service.DocumentService;
import com.ragkb.service.modules.ingestion.persistence.entity.ParseTask;
import com.ragkb.service.modules.ingestion.persistence.mapper.ParseTaskMapper;
import com.ragkb.service.modules.rag.port.RagEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 摄取投递调度器：把 {@code parse_task} 队列喂给 rag-engine，并回写摄取状态。
 *
 * <p>最小闭环采用**轮询驱动**（非 outbox 消费）：每 {@code rag-engine.dispatch-interval-ms}
 * （默认 5s）执行一轮：
 * <ol>
 *   <li>QUEUED 任务 → 调 rag-engine POST /api/ingest/documents → 置 RUNNING（worker_id=rag taskId），
 *       同时把 document_version.ingest_status 推进到 PARSING；</li>
 *   <li>RUNNING 任务 → 调 rag-engine GET /api/ingest/tasks/{id} 轮询终态，
 *       成功回写 READY + chunk_count，失败回写 FAILED + error_code。</li>
 * </ol>
 *
 * <p>模块边界：document/version 的读取与状态回写一律经 {@link DocumentService}
 * （跨模块只经 Service/Port 协作，不直接访问 document 持久化层）。
 *
 * <p>⚠️ 边界（留给人工迭代）：
 * <ul>
 *   <li>outbox_event 仍被写入（审计/未来异步改造），但本实现直接轮询 parse_task，未消费 outbox；</li>
 *   <li>parse_task.error_detail 为 JSONB，本实现只写 error_code，不写 error_detail；</li>
 *   <li>失败任务不自动重试（attempt_count 语义留待 reparse 端点驱动）。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class IngestionDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionDispatchScheduler.class);

    /** 状态常量与 DDL CHECK 保持一致。 */
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STAGE_PARSING = "PARSING";
    private static final String STAGE_INDEXING = "INDEXING";

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentService documentService;
    private final RagEnginePort ragEnginePort;
    /** 防止上一轮未完成时下一轮重入（@Scheduled 单线程下通常不会，双保险）。 */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public IngestionDispatchScheduler(ParseTaskMapper parseTaskMapper,
                                      DocumentService documentService,
                                      RagEnginePort ragEnginePort) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentService = documentService;
        this.ragEnginePort = ragEnginePort;
    }

    @Scheduled(fixedDelayString = "${rag-engine.dispatch-interval-ms:5000}")
    public void dispatchAndPoll() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            dispatchQueued();
            pollRunning();
        } finally {
            busy.set(false);
        }
    }

    // ------------------------------------------------------------------
    // ① 投递：QUEUED → rag-engine → RUNNING
    // ------------------------------------------------------------------

    private void dispatchQueued() {
        List<ParseTask> queued = parseTaskMapper.selectList(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getStatus, STATUS_QUEUED)
                .orderByAsc(ParseTask::getQueuedAt)
                .last("LIMIT 5"));
        for (ParseTask task : queued) {
            try {
                dispatchOne(task);
            } catch (Exception e) {
                log.warn("摄取投递失败 taskId={}", task.getId(), e);
                task.setStatus(STATUS_FAILED);
                task.setErrorCode("DISPATCH_FAILED");
                parseTaskMapper.updateById(task);
            }
        }
    }

    private void dispatchOne(ParseTask task) {
        DocumentService.DocumentIngestSource source =
                documentService.ingestSource(task.getVersionId());

        String ragTaskId = ragEnginePort.parseDocument(
                new TenantId(task.getTenantId()),
                source.documentId(),
                source.versionId(),
                source.kbId(),
                source.versionNo(),
                source.objectKey(),
                RagEnginePort.defaultKbConfig());

        task.setStatus(STATUS_RUNNING);
        task.setStage(STAGE_PARSING);
        task.setWorkerId(ragTaskId);
        task.setStartedAt(Instant.now());
        task.setLeaseUntil(Instant.now().plusSeconds(300));
        parseTaskMapper.updateById(task);

        documentService.updateIngestStatus(task.getVersionId(), STAGE_PARSING, null, null);
        log.info("摄取任务已投递 rag-engine: parseTaskId={} ragTaskId={}", task.getId(), ragTaskId);
    }

    // ------------------------------------------------------------------
    // ② 轮询：RUNNING → 终态回写 document_version / parse_task
    // ------------------------------------------------------------------

    private void pollRunning() {
        List<ParseTask> running = parseTaskMapper.selectList(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getStatus, STATUS_RUNNING)
                .isNotNull(ParseTask::getWorkerId)
                .last("LIMIT 20"));
        for (ParseTask task : running) {
            try {
                pollOne(task);
            } catch (Exception e) {
                // 轮询失败（网络抖动/rag-engine 重启）：保持 RUNNING，下一轮再试。
                log.warn("摄取状态轮询失败 parseTaskId={} ragTaskId={}", task.getId(), task.getWorkerId(), e);
            }
        }
    }

    private void pollOne(ParseTask task) {
        Map<String, Object> status = ragEnginePort.getIngestTaskStatus(
                new TenantId(task.getTenantId()), task.getWorkerId());
        String pyStatus = String.valueOf(status.getOrDefault("status", ""));
        int vectorCount = status.get("vectorCount") instanceof Number n ? n.intValue() : 0;

        if ("SUCCESS".equals(pyStatus)) {
            task.setStatus(STATUS_SUCCEEDED);
            task.setStage(STAGE_INDEXING);
            task.setProgressPercent(100);
            task.setFinishedAt(Instant.now());
            task.setLeaseUntil(null);
            parseTaskMapper.updateById(task);

            documentService.updateIngestStatus(task.getVersionId(), "READY", vectorCount, null);
            log.info("摄取完成: documentId={} versionId={} chunks={}", task.getDocumentId(), task.getVersionId(), vectorCount);
        } else if ("FAILED".equals(pyStatus)) {
            task.setStatus(STATUS_FAILED);
            task.setErrorCode("INGEST_FAILED");
            task.setFinishedAt(Instant.now());
            task.setLeaseUntil(null);
            parseTaskMapper.updateById(task);

            documentService.updateIngestStatus(task.getVersionId(), "FAILED", null, "INGEST_FAILED");
            log.warn("摄取失败: documentId={} versionId={} pyStatus={}", task.getDocumentId(), task.getVersionId(), status);
        }
        // 其余（RUNNING / 未知）：保持 RUNNING，下一轮继续轮询。
    }
}

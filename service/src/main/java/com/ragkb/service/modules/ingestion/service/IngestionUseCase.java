package com.ragkb.service.modules.ingestion.service;

import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;

/**
 * 文档摄取编排用例：上传/重解析完成后把任务写入摄取队列并发布 outbox 事件。
 *
 * <p>本用例只负责「排队」（落 {@code parse_task} + {@code outbox_event}），
 * 实际的安全扫描 / 解析 / OCR / 分块 / embedding 由 rag-engine worker
 * （{@code docs/api/rag-engine.openapi.yaml}）消费 outbox 后逐阶段推进
 * {@code document_version.ingest_status}。
 */
public interface IngestionUseCase {

    /** 任务类型：与 {@code parse_task.task_type} 的 CHECK 一致。 */
    String TASK_TYPE_INGEST = "INGEST";
    String TASK_TYPE_REPARSE = "REPARSE";

    /**
     * 将一次摄取任务入队（创建 {@code parse_task} 行 + 追加 outbox 事件）。
     *
     * @param tenantId        租户 id
     * @param documentId      文档 id
     * @param versionId       文档版本 id（parse_task 外键指向 document_version）
     * @param taskType        INGEST（首次上传）/ REPARSE（重试解析）
     * @param idempotencyKey  幂等键；为空时自动生成（parse_task 有唯一约束）
     * @return 排队中的摄取任务（QUEUED，resourceId=documentId，供任务中心展示/轮询）
     */
    Task enqueueIngestion(TenantId tenantId, long documentId, long versionId,
                          String taskType, String idempotencyKey);

    /**
     * 重试一个失败/卡住的摄取任务（重新置 QUEUED 并追加一次重试）。
     *
     * @param tenantId       租户 id
     * @param taskId         parse_task id（字符串化）
     * @param idempotencyKey 幂等键
     */
    Task retryIngestion(TenantId tenantId, String taskId, String idempotencyKey);
}

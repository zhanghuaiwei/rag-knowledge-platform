package com.ragkb.service.modules.ingestion.service;

import com.ragkb.service.common.model.Task;
import com.ragkb.service.common.model.TenantId;

/**
 * 文档摄取编排用例占位。
 *
 * <p>TODO：实现扫描、解析、OCR、分块、embedding 和幂等补偿。
 */
public interface IngestionUseCase {

    Task startIngestion(TenantId tenantId, long documentId, long versionNo, String idempotencyKey);

    Task retryIngestion(TenantId tenantId, String taskId, String idempotencyKey);
}

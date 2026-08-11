package com.ragkb.service.modules.governance.service;

import com.ragkb.service.common.api.PageData;
import com.ragkb.service.modules.governance.vo.DeletionReceiptVo;
import com.ragkb.service.modules.governance.vo.DeletionTaskVo;
import com.ragkb.service.modules.governance.vo.LegalHoldVo;
import com.ragkb.service.modules.governance.dto.LegalHoldDto;
import com.ragkb.service.modules.governance.vo.MetadataSchemaVo;
import com.ragkb.service.modules.governance.dto.MetadataSchemaDto;
import com.ragkb.service.modules.governance.vo.RetentionPolicyVo;
import com.ragkb.service.modules.governance.dto.RetentionPolicyDto;
import com.ragkb.service.modules.governance.dto.RetentionPolicyToggleDto;
import com.ragkb.service.modules.governance.dto.ReviewActionDto;
import com.ragkb.service.modules.governance.vo.ReviewItemVo;

import java.util.List;

/**
 * 治理中心用例：元数据 schema / 审核 / 保留与法律保全 / 删除与证明（实现点由人工完成）。
 */
public interface GovernanceService {

    List<MetadataSchemaVo> listMetadataSchemas();

    MetadataSchemaVo createMetadataSchema(MetadataSchemaDto request, String idempotencyKey);

    MetadataSchemaVo publishMetadataSchema(long schemaId, String idempotencyKey);

    PageData<ReviewItemVo> listReviews(int page, int size);

    void approveReview(long reviewId, ReviewActionDto request, String idempotencyKey);

    void rejectReview(long reviewId, ReviewActionDto request, String idempotencyKey);

    void withdrawDocument(long documentId, String idempotencyKey);

    List<RetentionPolicyVo> listRetentionPolicies();

    RetentionPolicyVo createRetentionPolicy(RetentionPolicyDto request, String idempotencyKey);

    RetentionPolicyVo toggleRetentionPolicy(long policyId, RetentionPolicyToggleDto request);

    List<LegalHoldVo> listLegalHolds();

    LegalHoldVo createLegalHold(LegalHoldDto request, String idempotencyKey);

    LegalHoldVo releaseLegalHold(long holdId);

    List<DeletionTaskVo> listDeletionTasks();

    DeletionTaskVo approveDeletion(long taskId);

    List<DeletionReceiptVo> listDeletionReceipts();
}

package com.ragkb.service.application;

import com.ragkb.service.common.PageData;
import com.ragkb.service.interfaces.dto.DocumentDtos.Tag;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionReceipt;
import com.ragkb.service.interfaces.dto.GovernanceDtos.DeletionTask;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHold;
import com.ragkb.service.interfaces.dto.GovernanceDtos.LegalHoldInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchema;
import com.ragkb.service.interfaces.dto.GovernanceDtos.MetadataSchemaInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicy;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyInput;
import com.ragkb.service.interfaces.dto.GovernanceDtos.RetentionPolicyToggleRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewActionRequest;
import com.ragkb.service.interfaces.dto.GovernanceDtos.ReviewItem;

import java.util.List;

/**
 * 治理中心用例：元数据 schema / 审核 / 保留与法律保全 / 删除与证明（实现点由人工完成）。
 */
public interface GovernanceService {

    List<MetadataSchema> listMetadataSchemas();

    MetadataSchema createMetadataSchema(MetadataSchemaInput request, String idempotencyKey);

    MetadataSchema publishMetadataSchema(long schemaId, String idempotencyKey);

    PageData<ReviewItem> listReviews(int page, int size);

    void approveReview(long reviewId, ReviewActionRequest request, String idempotencyKey);

    void rejectReview(long reviewId, ReviewActionRequest request, String idempotencyKey);

    void withdrawDocument(long documentId, String idempotencyKey);

    List<RetentionPolicy> listRetentionPolicies();

    RetentionPolicy createRetentionPolicy(RetentionPolicyInput request, String idempotencyKey);

    RetentionPolicy toggleRetentionPolicy(long policyId, RetentionPolicyToggleRequest request);

    List<LegalHold> listLegalHolds();

    LegalHold createLegalHold(LegalHoldInput request, String idempotencyKey);

    LegalHold releaseLegalHold(long holdId);

    List<DeletionTask> listDeletionTasks();

    DeletionTask approveDeletion(long taskId);

    List<DeletionReceipt> listDeletionReceipts();
}
